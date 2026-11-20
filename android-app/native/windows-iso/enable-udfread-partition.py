"""Accept the partition number Microsoft media actually uses.

libudfread 1.2.0 calls _read_vds(input, 0, &vds) and so requires a partition
descriptor whose number is 0. The retail Windows 11 25H2 and Windows 10 22H2
ARM64 ISOs both declare partition 2989, so the Volume Descriptor Sequence is
parsed and then rejected:

    udfread LOG  :   partition 2989 at lba 304, 3541515 blocks
    udfread ERROR: failed reading Volume Descriptor Sequence

windows_iso_probe() maps that to WINDOWS_ISO_INVALID_UDF, which the app shows
as "Could not read the UDF filesystem in this ISO."

Partition numbers in ECMA 167 are arbitrary identifiers, not indices, so the
requested number must not be pinned to 0. Passing -1 selects the first type 1
partition map, which is what a single-partition image needs and what the
partition map table already reports for these ISOs ("ref 2989").
"""
import argparse
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('source', type=Path, help='Extracted libudfread-1.2.0 tree')
source = parser.parse_args().source
path = source / 'src/udfread.c'
text = path.read_bytes().decode('utf-8')

original = '    if (_read_vds(input, 0, &vds) < 0) {'
replacement = '    /* U-AVF: accept the first type 1 partition instead of requiring number 0. */\n    if (_read_vds(input, -1, &vds) < 0) {'

if text.count(original) != 1:
    raise SystemExit('Unexpected libudfread source; expected a single _read_vds call in udfread_open_input')
text = text.replace(original, replacement)
path.write_bytes(text.encode('utf-8'))
print('patched', path)
