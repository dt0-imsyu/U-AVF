"""Read-only candidate audit; only writes its own report under private internal/."""
import argparse
import hashlib
import json
import re
import subprocess
import zipfile
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--kit',type=Path,required=True)
p.add_argument('--baseline',type=Path,required=True)
p.add_argument('--aapt2',type=Path,required=True)
a=p.parse_args()
repo=Path(__file__).resolve().parents[2]
candidate=a.kit/'candidate'
apk=candidate/'U-AVF 1.0.apk'
def sha(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source,'sha256').hexdigest().upper()
def git(*args):
    return subprocess.run(['git',*args],cwd=repo,capture_output=True,text=True,
                          encoding='utf-8',check=True).stdout
def check(condition,name):
    if not condition:raise SystemExit('AUDIT_FAIL='+name)
    checks.append(name)
checks=[]
listed={}
for line in (candidate/'SHA256SUMS').read_text(encoding='utf-8').splitlines():
    digest,rel=line.split('  ',1)
    path=(candidate/rel).resolve()
    check(path.is_relative_to(candidate.resolve()),'checksum_path_safe:'+rel)
    check(sha(path)==digest,'checksum:'+rel)
    listed[rel]=digest
actual={path.relative_to(candidate).as_posix() for path in candidate.rglob('*')
        if path.is_file() and path.name!='SHA256SUMS'}
check(actual==set(listed),'all_candidate_files_hashed')
preparation=json.loads((a.kit/'internal/preparation-manifest.json').read_text(encoding='utf-8'))
check(sha(apk)==preparation['apk_sha256'],'prepared_candidate_identity')
badging=subprocess.run([str(a.aapt2),'dump','badging',str(apk)],capture_output=True,
                       text=True,encoding='utf-8',check=True).stdout
check("versionCode='"+str(preparation['version_code'])+"'" in badging and
      "versionName='"+preparation['version_name']+"'" in badging,'prepared_candidate_version')
check(re.search(r"(?:minSdkVersion|sdkVersion):'36'",badging) is not None,'minimum_android_api_36')
xml=subprocess.run([str(a.aapt2),'dump','xmltree','--file','AndroidManifest.xml',str(apk)],
                   capture_output=True,text=True,encoding='utf-8',check=True).stdout
for attr in ('debuggable','allowBackup'):
    line=next(line for line in xml.splitlines() if 'android:'+attr+'(' in line)
    check('0x0' in line,'manifest_'+attr+'_false')
payloads=[]
with zipfile.ZipFile(apk) as release,zipfile.ZipFile(a.baseline) as baseline:
    check(release.testzip() is None,'apk_zip_crc')
    for entry in baseline.infolist():
        if entry.is_dir() or not entry.filename.startswith(('assets/','lib/')) or entry.filename.startswith('assets/legal/'):continue
        with release.open(entry.filename) as left,baseline.open(entry.filename) as right:
            check(hashlib.file_digest(left,'sha256').digest()==hashlib.file_digest(right,'sha256').digest(),
                  'runtime_payload:'+entry.filename)
        payloads.append(entry.filename)
    names=release.namelist()
    sensitive=[name for name in names if name.lower().endswith(('.jks','.keystore','.dpapi','.p12'))
               or 'service-account' in name.lower() or '/internal/' in name.lower()]
    check(not sensitive,'no_signing_credentials_or_private_kit_in_apk')
    for name in ('LICENSE','LICENSE-UAVF.txt','LICENSE-APACHE-2.0.txt','LICENSE_SCOPE.md','PRIVACY.md','THIRD_PARTY_NOTICES.txt'):
        check('assets/legal/'+name in names,'accessible_legal_asset:'+name)
check(len(payloads)==43,'43_runtime_payloads_identical')
check((repo/'LICENSE-APACHE-2.0.txt').read_text(encoding='utf-8').strip()==git('show','HEAD:LICENSE').strip(),
      'historical_apache_text_preserved')
scope_text=(repo/'LICENSE_SCOPE.md').read_text(encoding='utf-8')
scope=re.findall(r'`(android-app/src/[^`]+\.java)`',scope_text.split('## Explicit exclusions')[0])
for ref in ('HEAD','origin/main'):
    check(not set(scope).intersection(git('ls-tree','-r','--name-only',ref).splitlines()),'new_scope_absent_from_'+ref)
for rel in scope:
    check('SPDX-License-Identifier: LicenseRef-UAVF-1.0' in (repo/rel).read_text(encoding='utf-8'),'scope_header:'+rel)
check(git('rev-parse','HEAD').strip()=='18778a9d88eab1f87cb279484bb1b0dbe28a057e','head_unchanged_no_commit')
issues=[
 {'id':'INSTALL_ACCOUNT_ACCEPTANCE','status':'SEE_DEVICE_REPORT','evidence':'RC4 isolated clean custom-account install and repeat password login recorded in DEVICE_ACCEPTANCE_2026-10-04.md; this static audit does not certify runtime acceptance'},
 {'id':'CORRESPONDING_SOURCE','status':'BLOCK_PUBLIC_RELEASE','evidence':'Notices and original-source checkpoint exist; exact complete GPL/LGPL sources/build configs/relink obligations are not fulfilled'},
 {'id':'PRIVACY','status':'BLOCK_PUBLIC_RELEASE','evidence':'Policy is a draft; controller contact, retention/deletion and live backend abuse controls not fully verified'},
 {'id':'CANDIDATE_DEVICE_SMOKE','status':'PENDING','evidence':'New build requires device/hot-toggle acceptance; static validation is not runtime acceptance'},
 {'id':'STORAGE_GROWTH','status':'WAIVED_BY_OWNER','evidence':'Owner waived real partition/ext4 growth acceptance on2026-10-04; fixtures pass but real growth is unverified, not PASS'},
 {'id':'CLIPBOARD_ACCEPTANCE','status':'WAIVED_BY_OWNER','evidence':'Owner waived hot-toggle/bidirectional acceptance on2026-10-04; active socket is not proof of bidirectional operation'},
 {'id':'KEY_RECOVERY','status':'PENDING_OWNER_CHECK','evidence':'Signing works; portable recovery outside this Windows DPAPI context not demonstrated'},
 {'id':'LIVE_FIREBASE_RULES','status':'NOT_VERIFIED','evidence':'No live Firebase rules tool available in this session; local source audit only'}]
report={'status':'LOCAL_STATIC_CHECKS_PASS_PUBLIC_RELEASE_HOLD','apk_sha256':sha(apk),
        'automated_checks':checks,'runtime_payload_count':len(payloads),'issues':issues,
        'device_modified':False,'vm_restarted':False,'committed':False,'published':False,
        'scope_limits':'No destructive reinstall, real storage growth, foreground keyboard/pointer or sustained FPS test in this audit'}
destination=a.kit/'internal/release-audit-2026-10-04.json'
destination.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('CANDIDATE_SHA256SUMS=PASS; APK_ZIP_CRC=PASS; MANIFEST_RELEASE_FLAGS=PASS')
print('RUNTIME_PAYLOADS_IDENTICAL=PASS (43); LICENSE_SCOPE_CHECKS=PASS; NO_COMMIT=PASS')
print('PUBLIC_RELEASE=HOLD; DEVICE_SMOKE=PENDING; REPORT='+str(destination))
