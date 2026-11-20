#!/usr/bin/env python3
"""Fail the build when a Java native method has no implementation in the packaged .so.

The Android build previously shipped libuavf_windows_iso.so with one of five JNI
symbols, so WindowsInstallIso.installDigest() threw UnsatisfiedLinkError at the
first ISO selection while every static check passed. This check makes that
failure a build error instead of a runtime surprise.

Usage:
  verify-native-symbols.py --apk <app.apk> --source <android-app/src> [--nm <llvm-nm>]
"""
import argparse
import glob
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

NATIVE_RE = re.compile(r'\bnative\s+[\w$.\[\]<>,\s]+\s+(\w+)\s*\(')
PACKAGE_RE = re.compile(r'^package\s+([\w.]+);', re.M)


def java_native_methods(source_root):
    """Map JNI-mangled class name -> set of declared native methods."""
    declared = {}
    for path in glob.glob(os.path.join(source_root, '**', '*.java'), recursive=True):
        text = open(path, encoding='utf-8').read()
        package = PACKAGE_RE.search(text)
        if not package:
            continue
        class_name = os.path.basename(path)[:-5]
        mangled = (package.group(1) + '.' + class_name).replace('.', '_')
        methods = set(NATIVE_RE.findall(text))
        if methods:
            declared.setdefault(mangled, set()).update(methods)
    return declared


def apk_jni_symbols(apk, nm, workdir):
    """Map packaged library name -> set of exported Java_* symbols."""
    symbols = {}
    with zipfile.ZipFile(apk) as archive:
        for entry in archive.namelist():
            if not entry.startswith('lib/arm64-v8a/') or not entry.endswith('.so'):
                continue
            target = os.path.join(workdir, os.path.basename(entry))
            with open(target, 'wb') as out:
                out.write(archive.read(entry))
            listing = subprocess.run(
                [nm, '-D', '--defined-only', target],
                capture_output=True, text=True, check=False).stdout
            found = {line.split()[-1] for line in listing.splitlines()
                     if line.strip() and 'Java_' in line}
            symbols[os.path.basename(entry)] = found
    return symbols


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', required=True)
    parser.add_argument('--source', required=True)
    parser.add_argument('--nm', help='llvm-nm path; searched on PATH when omitted')
    parser.add_argument('--allow-missing', action='append', default=[],
                        help='Class.method tolerated as absent (documented Java fallback)')
    args = parser.parse_args()

    nm = args.nm or shutil.which('llvm-nm') or shutil.which('nm')
    if not nm:
        configured = os.environ.get('ANDROID_NDK_HOME') or os.environ.get('NDK_HOME')
        if configured:
            candidate = os.path.join(configured, 'toolchains', 'llvm', 'prebuilt',
                                     'windows-x86_64', 'bin', 'llvm-nm.exe')
            if os.path.isfile(candidate):
                nm = candidate
    if not nm:
        # Fall back to whichever SDK the machine actually has, not a fixed profile.
        sdk_roots = []
        local = os.environ.get('LOCALAPPDATA')
        if local:
            sdk_roots.append(os.path.join(local, 'Android', 'Sdk', 'ndk'))
        sdk_roots.append(os.path.join(os.path.expanduser('~'), 'Android', 'Sdk', 'ndk'))
        for root in sdk_roots:
            if not os.path.isdir(root):
                continue
            for version in sorted(os.listdir(root), reverse=True):
                candidate = os.path.join(root, version, 'toolchains', 'llvm',
                                         'prebuilt', 'windows-x86_64', 'bin', 'llvm-nm.exe')
                if os.path.isfile(candidate):
                    nm = candidate
                    break
            if nm:
                break
    if not nm:
        # A missing disassembler must not silently accept an unverified APK.
        print('NATIVE_SYMBOLS=FAIL (llvm-nm not found; set --nm or ANDROID_NDK_HOME)',
              file=sys.stderr)
        return 1

    declared = java_native_methods(args.source)
    if not declared:
        print('NATIVE_SYMBOLS=SKIPPED (no native declarations)')
        return 0

    with tempfile.TemporaryDirectory() as workdir:
        packaged = apk_jni_symbols(args.apk, nm, workdir)

    available = set()
    for found in packaged.values():
        available |= found

    failures = []
    tolerated = set(args.allow_missing)
    for mangled, methods in sorted(declared.items()):
        for method in sorted(methods):
            symbol = 'Java_%s_%s' % (mangled, method)
            if symbol in available:
                continue
            key = '%s.%s' % (mangled, method)
            if key in tolerated:
                print('NATIVE_SYMBOL_TOLERATED=%s (documented Java fallback)' % key)
                continue
            failures.append(key)

    if failures:
        print('NATIVE_SYMBOLS=FAIL', file=sys.stderr)
        for item in failures:
            print('  missing implementation: %s' % item, file=sys.stderr)
        print('  packaged libraries: %s' % ', '.join(sorted(packaged)), file=sys.stderr)
        return 1

    print('NATIVE_SYMBOLS=PASS (%d methods across %d libraries)'
          % (sum(len(m) for m in declared.values()), len(packaged)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
