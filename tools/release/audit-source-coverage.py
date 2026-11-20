"""Map exact current APK ELF hashes to preserved package/source evidence.

Read-only inputs; writes a private coverage report, never a compliance PASS.
"""
import argparse
import hashlib
import json
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--inventory', type=Path, required=True)
p.add_argument('--kit', type=Path, required=True)
a = p.parse_args()
internal = a.kit / 'internal'
inventory = json.loads(a.inventory.read_text(encoding='utf-8'))
sources = internal / 'upstream-sources'
manifest = json.loads((sources / 'download-manifest.json').read_text(encoding='utf-8'))

def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest().upper()

for record in manifest:
    assert sha(sources / record['dsc']) == record['dsc_sha256']
    for artifact in record['artifacts']:
        path = sources / artifact['name']
        assert path.stat().st_size == artifact['bytes']
        assert sha(path) == artifact['sha256']

matches = {}
for filename in ('deb-provenance.json', 'encoder-deb-provenance.json'):
    records = json.loads((internal / filename).read_text(encoding='utf-8'))
    for record in records:
        for match in record['matches']:
            matches.setdefault(match['sha256'], []).append({
                'package': record['package'], 'version': record['version'],
                'source': record['source'], 'deb_sha256': record['deb_sha256'],
                'package_file': match['package_file'],
            })
rows = []
for platform in inventory['platforms']:
    for item in platform['binary_notice_and_local_inventory']:
        if not item.get('elf'):
            continue
        rows.append({'asset': platform['asset'], **item,
                     'exact_package_evidence': matches.get(item['sha256'], [])})
unknown = [r for r in rows if not r['exact_package_evidence']]
report = {
    'apk_sha256': inventory['apk_sha256'], 'publication': 'HOLD',
    'source_archive_checksums': 'PASS', 'source_package_records': len(manifest),
    'unique_elf_hashes': len({r['sha256'] for r in rows}),
    'unique_unassociated_elf_hashes': len({r['sha256'] for r in unknown}),
    'elf_occurrences': rows,
    'limits': ['Package hash association is not a complete source/build/relink audit',
               'Kernel, EFI firmware/launcher and U-Boot completeness are separate',
               'DSC PGP signatures have not been verified'],
}
target = internal / 'source-coverage-rc4.json'
target.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print('SOURCE_ARCHIVE_CHECKSUMS=PASS; SOURCE_PACKAGES=' + str(len(manifest)))
print('UNIQUE_ELF=' + str(report['unique_elf_hashes']) +
      '; UNASSOCIATED_ELF=' + str(report['unique_unassociated_elf_hashes']))
print('SOURCE_COMPLIANCE=INCOMPLETE; PUBLICATION=HOLD; REPORT=' + str(target))
