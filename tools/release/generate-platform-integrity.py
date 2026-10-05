"""Generate signed-in-code integrity constants from the actual packaged APK.

Release/UI builds preserve tested payloads, not necessarily local asset copies.
Never derive expected hashes from mutable files on the Android device.
"""
import argparse
import hashlib
import json
import zipfile
from pathlib import Path

ASSETS = {
    'uavf-install-platform-prefix-gdm-share.img': 134217728,
    'p7-platform-prefix-virgl-gbm-linux-first-cu120-xdamage-composite-overlay.img': 134217728,
    'p7-platform-prefix-virgl-gbm-linux-first-cu120-xdamage-real-gnome.img': 134217728,
    'p33-gpt-trailer.bin': 16896,
}

def inventory(apk):
    results = {}
    with zipfile.ZipFile(apk) as package:
        for name, expected_size in ASSETS.items():
            entry = package.getinfo('assets/' + name)
            if entry.file_size != expected_size:
                raise ValueError('Invalid platform size: ' + name)
            digest = hashlib.sha256()
            with package.open(entry) as data:
                for block in iter(lambda: data.read(1024 * 1024), b''):
                    digest.update(block)
            results[name] = digest.hexdigest().upper()
    return results

def generate(apk, output):
    hashes = inventory(apk)
    source = ['// Generated from packaged assets; do not edit.',
              'package com.example.winavf;', 'final class BundledPlatformAssets {',
              '  static final String[] NAMES = {']
    source += [f'    "{name}",' for name in hashes]
    source += ['  };', '  static String sha256(String name) { return switch(name) {']
    source += [f'    case "{name}" -> "{digest}";' for name, digest in hashes.items()]
    source += ['    default -> throw new IllegalArgumentException("Unknown platform asset: " + name);',
               '  }; }', '  static long size(String name) { return switch(name) {']
    source += [f'    case "{name}" -> {size}L;' for name, size in ASSETS.items()]
    source += ['    default -> throw new IllegalArgumentException("Unknown platform asset: " + name);',
               '  }; }', '}']
    target = Path(output) / 'com/example/winavf/BundledPlatformAssets.java'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text('\n'.join(source) + '\n', encoding='utf-8')
    print(json.dumps(hashes, indent=2))

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', required=True)
    parser.add_argument('--generated', required=True)
    args = parser.parse_args()
    generate(args.apk, args.generated)
