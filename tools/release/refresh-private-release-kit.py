"""Freeze current candidate/docs/source privately. Never commit, install or publish."""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import zipfile
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--apk', type=Path, required=True)
p.add_argument('--previous-kit', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
p.add_argument('--expected-sha256', required=True)
p.add_argument('--version-name', required=True)
p.add_argument('--version-code', type=int, required=True)
p.add_argument('--refresh', action='store_true', help='Refresh only a matching private candidate kit')
a = p.parse_args()
repo = Path(__file__).resolve().parents[2]
expected = a.expected_sha256.upper()
assert re.fullmatch(r'[A-F0-9]{64}', expected)

def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest().upper()

def git(*args):
    return subprocess.run(['git', *args], cwd=repo, check=True,
                          capture_output=True, text=True, encoding='utf-8').stdout

assert sha(a.apk) == expected, 'Not the verified candidate'
if a.output.exists():
    assert a.refresh and sha(a.output/'candidate/U-AVF 1.0.apk') == expected, 'Not the matching private kit'
scope_text = (repo/'LICENSE_SCOPE.md').read_text(encoding='utf-8')
scope = re.findall(r'`(android-app/src/[^`]+\.java)`',
                   scope_text.split('## Enumeration versus source headers')[0])
mixed = re.findall(r'`(android-app/src/[^`]+\.java)`',
                   scope_text.split('## Mixed-origin')[1].split('## Explicit exclusions')[0])
assert scope and len(scope) == len(set(scope)), 'Scope list is empty or repeated'
assert not set(scope) & set(mixed), 'Mixed-origin files must stay out of the scope list'
for rel in scope + mixed:
    assert (repo/rel).is_file(), 'Scoped file is missing: ' + rel
    marker = re.search(r'SPDX-License-Identifier: (\S+)', (repo/rel).read_text(encoding='utf-8'))
    assert marker is None or marker.group(1) == 'LicenseRef-UAVF-1.0', 'Unexpected header: ' + rel
# The Apache text is preserved from the last public commit where LICENSE held it.
# The current LICENSE is a multi-license index, so it cannot be the reference.
BASELINE = '18778a9d88eab1f87cb279484bb1b0dbe28a057e'
original_apache = git('show', BASELINE + ':LICENSE').replace('\r\n', '\n').strip()
assert (repo/'LICENSE-APACHE-2.0.txt').read_text(encoding='utf-8').strip() == original_apache
# Every scoped file is a local change relative to the public baseline. Files that
# are still untracked are covered by the full source checkpoint written below.
modified = scope + mixed
candidate = a.output/'candidate'
internal = a.output/'internal'
candidate.mkdir(parents=True, exist_ok=a.refresh)
internal.mkdir(exist_ok=a.refresh)
shutil.copy2(a.apk, candidate/'U-AVF 1.0.apk')
docs = repo/'docs/release-preparation'
shutil.copy2(docs/'README.md', a.output/'README.md')
for name in ('RELEASE_NOTES.md', 'QUICK_START.md', 'RELEASE_BUILD.md',
             'PRIVACY.md', 'THIRD_PARTY_COMPLIANCE.md', 'PREPUBLICATION_CHECKLIST.md',
             'RELEASE_AUDIT_2026-10-04.md', 'ACCEPTANCE.md', 'FINAL_CANDIDATE_2026-10-04.md',
             'DEVICE_ACCEPTANCE_2026-10-04.md'):
    shutil.copy2(docs/name, candidate/name)
shutil.copy2(docs/'SIGNING_KEY_RECOVERY.md', internal/'SIGNING_KEY_RECOVERY.md')
for name in ('LICENSE', 'LICENSE-UAVF.txt', 'LICENSE-APACHE-2.0.txt', 'LICENSE_SCOPE.md', 'README.md'):
    shutil.copy2(repo/name, candidate/name)
shutil.copy2(docs/'LICENSE_ADOPTION_PLAN.md', candidate/'LICENSE_ADOPTION_PLAN.md')
shutil.copytree(a.previous_kit/'candidate/third-party-notices', candidate/'third-party-notices', dirs_exist_ok=a.refresh)
shutil.copytree(repo/'android-app/assets/legal-notices', candidate/'third-party-notices', dirs_exist_ok=True)
# Carry only explicit compliance evidence; never copy arbitrary private logs,
# tokens, signing material or guest disks from the previous kit.
for name in ('source-evidence', 'source-evidence-rc4', 'upstream-sources'):
    previous = a.previous_kit/'internal'/name
    if previous.is_dir():
        shutil.copytree(previous, internal/name, dirs_exist_ok=a.refresh)
for name in ('deb-provenance.json', 'encoder-deb-provenance.json', 'source-coverage-rc4.json'):
    previous = a.previous_kit/'internal'/name
    if previous.is_file():
        shutil.copy2(previous, internal/name)
diff_index = internal/'_baseline.index'
diff_env = dict(os.environ, GIT_INDEX_FILE=str(diff_index))
try:
    subprocess.run(['git', 'read-tree', BASELINE], cwd=repo, env=diff_env, check=True)
    subprocess.run(['git', 'add', '-N', '--', *modified], cwd=repo, env=diff_env, check=True)
    baseline_diff = subprocess.run(['git', 'diff', '--no-ext-diff', '--no-color'],
                                   cwd=repo, env=diff_env, capture_output=True,
                                   text=True, encoding='utf-8', check=True).stdout
finally:
    diff_index.unlink(missing_ok=True)
(internal/'original-contributions-baseline.diff').write_text(baseline_diff, encoding='utf-8')

# Private checkpoint only: no raw disks/assets, signing credentials, .git or personal logs.
roots = ('android-app/src', 'android-app/native', 'android-app/res', 'android-app/tests',
         'tools/linux-gnome/guest', 'tools/release', 'docs/release-preparation')
extensions = {'.java', '.c', '.h', '.xml', '.py', '.sh', '.socket', '.service', '.yaml', '.ps1', '.md', '.txt'}
files = {path for root in roots for path in (repo/root).rglob('*')
         if path.is_file() and path.suffix in extensions and '__pycache__' not in path.parts}
files.update(repo/name for name in ('LICENSE', 'LICENSE-UAVF.txt', 'LICENSE-APACHE-2.0.txt',
             'LICENSE_SCOPE.md', 'README.md', 'docs/RELEASE_CHECKLIST.md',
             'android-app/AndroidManifest.xml', 'android-app/build-release.ps1'))
inventory = []
with zipfile.ZipFile(internal/'private-source-checkpoint.zip', 'w', zipfile.ZIP_DEFLATED) as out:
    for path in sorted(files):
        rel = path.relative_to(repo).as_posix()
        out.write(path, rel)
        inventory.append({'path': rel, 'bytes': path.stat().st_size, 'sha256': sha(path)})
report = {'publication': 'HOLD', 'commit_created': False, 'apk_sha256': expected,
          'version_name': a.version_name, 'version_code': a.version_code, 'device_acceptance': 'PENDING_NEW_CANDIDATE',
          'source_head': git('rev-parse', 'HEAD').strip(), 'scope_local_checks': 'PASS',
          'modified_source_scope': modified,
          'previous_licenses_preserved': True, 'source_snapshot': inventory,
          'corresponding_source_complete': False,
          'notices_provenance': 'extracted bundled notices plus local U-Boot/EDK2 notices; inventory validation separate',
          'not_publicly_releasable': ['third-party sources/legal/privacy/signing recovery',
                                    'remaining device smoke/lifecycle acceptance; clipboard and expansion tests waived by owner2026-10-04']}
(internal/'preparation-manifest.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
checksums = candidate/'SHA256SUMS'
checksums.write_text(''.join(sha(path)+'  '+path.relative_to(candidate).as_posix()+'\n'
                            for path in sorted(candidate.rglob('*')) if path.is_file() and path != checksums), encoding='utf-8')
for line in checksums.read_text(encoding='utf-8').splitlines():
    digest, rel = line.split('  ', 1)
    assert sha(candidate/rel) == digest
assert git('rev-parse', 'HEAD').strip() == report['source_head']
print('PRIVATE_PREPARATION=PASS')
print('APACHE_PRESERVED=PASS; SCOPED_HEADERS=PASS; SHA256SUMS=PASS')
print('APK='+str(candidate/'U-AVF 1.0.apk'))
print('PUBLICATION=HOLD; COMMIT=NONE; DEVICE_ACCEPTANCE=PENDING_NEW_CANDIDATE')
