"""Historical frozen-development kit. For current RC8 use refresh-private-release-kit.py."""
import argparse
import hashlib
import importlib.util
import json
import shutil
import subprocess
import zipfile
from datetime import datetime, timezone
from pathlib import Path

EXPECTED_APK = '992E89EB9DA884649F837ECFF78F027E5A6D0A471BE354E0CD6DBB19DAB06F32'


def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for data in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(data)
    return digest.hexdigest().upper()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--zstd', required=True, type=Path)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[2]
    if args.output.exists():
        raise SystemExit('Refusing an existing output directory')
    if sha(args.apk) != EXPECTED_APK:
        raise SystemExit('Not the frozen tablet-tested APK')
    candidate = args.output / 'candidate'
    internal = args.output / 'internal'
    review = args.output / 'licensing-review'
    for directory in (candidate, internal, review):
        directory.mkdir(parents=True)
    docs = repo / 'docs/release-preparation'
    for name in ('README.md', 'PREPUBLICATION_CHECKLIST.md'):
        shutil.copy2(docs / name, args.output / name)
    for name in ('RELEASE_NOTES.md', 'QUICK_START.md', 'PRIVACY_DRAFT.md', 'THIRD_PARTY_COMPLIANCE.md'):
        shutil.copy2(docs / name, candidate / name)
    for name in ('FIRST_PARTY_LICENSE_DRAFT.txt', 'LICENSE_ADOPTION_PLAN.md'):
        shutil.copy2(docs / name, review / name)
    shutil.copy2(repo / 'LICENSE-APACHE-2.0.txt', review / 'CURRENT_APACHE_LICENSE.txt')
    apk = candidate / 'U-AVF-preview-2026-10-03.apk'
    shutil.copy2(args.apk, apk)

    # Explicit source allowlist: no keys, tokens, guest disks or personal logs.
    roots = ('android-app/src', 'android-app/native', 'android-app/res',
             'tools/linux-gnome/guest', 'tools/linux-gnome/diagnostics', 'firmware')
    extensions = {'.java', '.c', '.h', '.xml', '.py', '.sh', '.socket', '.service', '.yaml', '.ps1'}
    files = []
    for name in roots:
        for path in (repo / name).rglob('*'):
            if path.is_file() and path.suffix in extensions and '__pycache__' not in path.parts:
                files.append(path)
    files += [repo / name for name in ('LICENSE', 'android-app/AndroidManifest.xml',
              'android-app/build.ps1', 'android-app/build-ui-update.ps1',
              'tools/linux-gnome/build-unified-encoded-prefix.py',
              'tools/linux-gnome/audit-product-display-payload.py',
              'docs/RELEASE_CHECKLIST.md')]
    files += [path for path in (repo / 'android-app/assets/product-ui').iterdir() if path.is_file()]
    source_inventory = []
    with zipfile.ZipFile(internal / 'private-source-checkpoint.zip', 'w', zipfile.ZIP_DEFLATED) as checkpoint:
        for path in sorted(set(files)):
            relative = path.relative_to(repo).as_posix()
            checkpoint.write(path, relative)
            source_inventory.append({'path': relative, 'sha256': sha(path), 'bytes': path.stat().st_size})

    notices = candidate / 'third-party-notices'
    notices.mkdir()
    shutil.copy2(repo / 'android-app/assets/product-ui/LexendDeca-OFL.txt', notices / 'LexendDeca-OFL.txt')
    helper_path = repo / 'tools/generic-ubuntu/compare-p7-clean-p9.py'
    spec = importlib.util.spec_from_file_location('release_cpio', helper_path)
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    from pyfatfs.PyFatFS import PyFatFS
    entries = []
    payloads = []
    with zipfile.ZipFile(apk) as package:
        for entry in package.infolist():
            if entry.is_dir():
                continue
            digest = hashlib.sha256()
            with package.open(entry) as stream:
                for data in iter(lambda: stream.read(1024 * 1024), b''):
                    digest.update(data)
            entries.append({'path': entry.filename, 'bytes': entry.file_size, 'sha256': digest.hexdigest().upper()})
            if entry.filename.startswith('assets/') and entry.filename.endswith('.img') and entry.file_size == 134217728:
                # Only work on extracted copies; never open a VM/user disk.
                extracted = internal / ('audit-' + Path(entry.filename).name)
                with package.open(entry) as source, extracted.open('xb') as target:
                    shutil.copyfileobj(source, target, 1024 * 1024)
                try:
                    with PyFatFS(str(extracted), offset=1048576, read_only=True) as fat:
                        initrd = fat.readbytes('/CASPER/INITRD')
                    archive = subprocess.run([str(args.zstd), '-d', '-q', '-c'],
                            input=initrd[helper.early_end(initrd):], capture_output=True, check=True).stdout
                    cpio, _ = helper.parse_cpio(archive)
                    exported = []
                    for name, contents in cpio.items():
                        if name.rsplit('/', 1)[-1] not in {'GST_X264_COPYRIGHT', 'X264_COPYRIGHT', 'TIGERVNC_X0_COPYRIGHT', 'TIGERVNC_1.15_COPYRIGHT'}:
                            continue
                        destination = notices / (Path(entry.filename).stem + '-' + name.rsplit('/', 1)[-1] + '.txt')
                        destination.write_bytes(contents)
                        exported.append({'cpio_path': name, 'file': destination.name, 'sha256': sha(destination)})
                    payloads.append({'apk_asset': entry.filename, 'initrd_sha256': hashlib.sha256(initrd).hexdigest().upper(), 'notices': exported})
                finally:
                    extracted.unlink()
    # Generated metadata is an artifact, not a change to application source.
    git = subprocess.run(['git', 'rev-parse', 'HEAD'], cwd=repo, capture_output=True, text=True, check=True).stdout.strip()
    inventory = {'status': 'PRIVATE_PREPARATION_NOT_PUBLISHABLE',
                 'prepared_utc': datetime.now(timezone.utc).isoformat(), 'base_commit': git,
                 'working_tree_snapshot_not_a_commit': True, 'apk_sha256': sha(apk),
                 'apk_bytes': apk.stat().st_size, 'version_code': 0, 'debuggable': True,
                 'signing': 'existing Android debug certificate', 'entries': entries,
                 'embedded_prefixes': payloads, 'source_checkpoint': source_inventory,
                 'checkpoint_is_not_complete_corresponding_source': True}
    (internal / 'inventory.json').write_text(json.dumps(inventory, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    paths = sorted(path for path in candidate.rglob('*') if path.is_file())
    (candidate / 'SHA256SUMS').write_text(''.join(sha(path) + '  ' + path.relative_to(candidate).as_posix() + '\n' for path in paths), encoding='ascii')
    print('PREPARATION=PASS')
    print('PUBLICATION=HOLD')
    print('APK_SHA256=' + sha(apk))
    print('EXTRACTED_UPSTREAM_NOTICES=' + str(len(list(notices.iterdir()))))
    print('SOURCE_CHECKPOINT_FILES=' + str(len(source_inventory)))
    print('OUTPUT=' + str(args.output))


if __name__ == '__main__':
    main()
