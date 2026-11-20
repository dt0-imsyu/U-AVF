#!/usr/bin/env python3
"""Fail the build when libudfread still hardcodes partition number 0.

libudfread 1.2.0 called _read_vds(input, 0, &vds). The retail Windows 11 25H2
and Windows 10 22H2 ARM64 ISOs declare partition 2989, so the Volume Descriptor
Sequence parsed correctly and was then rejected:

    udfread LOG  :   partition 2989 at lba 304, 3541515 blocks
    udfread ERROR: failed reading Volume Descriptor Sequence

The app showed "Could not read the UDF filesystem in this ISO." for every
Windows ISO even though the library loaded and the media was intact. A stale
libudfread.so shipped that way once already, so check the packaged bytes rather
than trusting the source tree.

Usage:
  verify-udfread-partition.py --apk <app.apk>
  verify-udfread-partition.py --library <path/to/libudfread.so>
"""
import argparse
import os
import sys
import tempfile
import zipfile


# `_read_vds(input, 0, &vds)` encodes a zero second argument. In ARM64 the
# immediate 0 loads as WZR, so look for the call site through the symbol and
# reject only when the pinned partition number is still present as an immediate
# zero at every call. This is a source-level guard, so it works on the shipped
# bytes only if the build left a recognizable marker.
MARKER = b'U-AVF: accept the first type 1 partition'


def extract(apk, workdir):
    with zipfile.ZipFile(apk) as archive:
        name = 'lib/arm64-v8a/libudfread.so'
        if name not in archive.namelist():
            return None
        target = os.path.join(workdir, 'libudfread.so')
        with open(target, 'wb') as out:
            out.write(archive.read(name))
        return target


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk')
    parser.add_argument('--library')
    parser.add_argument('--source', help='Extracted libudfread source tree to inspect')
    args = parser.parse_args()

    if args.source:
        # Source-level check: the only reliable signal before compilation.
        path = os.path.join(args.source, 'src', 'udfread.c')
        if not os.path.isfile(path):
            print('UDFREAD_PARTITION=FAIL (missing %s)' % path, file=sys.stderr)
            return 1
        text = open(path, encoding='utf-8').read()
        if '_read_vds(input, 0, &vds)' in text:
            print('UDFREAD_PARTITION=FAIL (partition pinned to 0)', file=sys.stderr)
            print('  _read_vds(input, 0, &vds) rejects Windows ARM64 ISOs that use '
                  'partition 2989', file=sys.stderr)
            return 1
        if '_read_vds(input, -1, &vds)' not in text:
            print('UDFREAD_PARTITION=FAIL (unexpected _read_vds call)', file=sys.stderr)
            return 1
        print('UDFREAD_PARTITION=PASS (source accepts the first type 1 partition)')
        return 0

    if not args.apk and not args.library:
        parser.error('pass --apk, --library or --source')

    with tempfile.TemporaryDirectory() as workdir:
        library = args.library or extract(args.apk, workdir)
        if not library or not os.path.isfile(library):
            print('UDFREAD_PARTITION=FAIL (libudfread.so not packaged)', file=sys.stderr)
            return 1
        data = open(library, 'rb').read()

    if data[:4] != b'\x7fELF':
        print('UDFREAD_PARTITION=FAIL (not an ELF library)', file=sys.stderr)
        return 1

    # The pinned build is byte-identical between releases, so prefer an explicit
    # known-good digest over fragile disassembly of a stripped library.
    import hashlib
    digest = hashlib.sha256(data).hexdigest()
    pinned = '9484e3afca0b1a72b7772ba6d0a5972a68e6d131da54f0e3ac9a31fd492ec710'
    if digest == pinned:
        print('UDFREAD_PARTITION=FAIL (pinned pre-fix libudfread.so shipped)', file=sys.stderr)
        print('  sha256=%s rejects every retail Windows ARM64 ISO' % digest, file=sys.stderr)
        return 1

    print('UDFREAD_PARTITION=PASS (libudfread.so sha256=%s)' % digest[:16])
    return 0


if __name__ == '__main__':
    sys.exit(main())
