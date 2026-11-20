"""Cold, exact8192-byte own EFI replacement only; original retained for rollback."""
import argparse
import importlib.util
import json
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--adb',required=True)
p.add_argument('--candidate',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
p.add_argument('--rollback',type=Path,help='Retained original from a prior apply; exact trial must be present')
p.add_argument('--apply',action='store_true',help='Explicitly write the fixed existing EFI file; otherwise backup/preflight only')
p.add_argument('--dynamic-gic',action='store_true',help='Exact offline-tested SMP-safe GIC selector instead of RTC candidate')
p.add_argument('--fixed-eight-cpu-gic',action='store_true',help='Exact legacy EFI with only two GIC address constants changed; eight CPU only')
p.add_argument('--psci-owned-reserve',action='store_true',help='Exact fixed eight CPU EFI with guarded own NVS allocation; no redirect')
p.add_argument('--psci-first-cpu-entry',action='store_true',help='Exact static first CPU context-restoring own trampoline')
p.add_argument('--psci-runtime-reserve',action='store_true',help='Exact EFI runtime mapping proof; no CPU_ON redirect')
p.add_argument('--psci-runtime-all-cpus',action='store_true',help='Eight own pages; separate trampoline for each secondary CPU')
p.add_argument('--psci-entry-marker',action='store_true',help='Exact separate version2 owned AP-entry marker candidate')
p.add_argument('--psci-cache-publish',action='store_true',help='Exact marker candidate with own native PoC publication helper')
a=p.parse_args()
assert not a.output.exists(),'Preserve prior evidence'
spec=importlib.util.spec_from_file_location('guard',Path(__file__).with_name('isolated-device-bcd.py'))
g=importlib.util.module_from_spec(spec);spec.loader.exec_module(g)
candidate=a.candidate.read_bytes()
assert not (a.dynamic_gic and a.fixed_eight_cpu_gic), 'One GIC candidate only'
assert not a.psci_owned_reserve or a.fixed_eight_cpu_gic, 'Reserve only on explicit eight CPU profile'
assert not a.psci_first_cpu_entry or a.psci_owned_reserve, 'Static entry requires own reserve'
assert not a.psci_runtime_reserve or (a.psci_owned_reserve and not a.psci_first_cpu_entry), 'Runtime mapping is separate from static/NVS entry A/B'
assert not a.psci_runtime_all_cpus or a.psci_runtime_reserve, 'All CPU mapping requires runtime profile'
assert not a.psci_entry_marker or a.psci_runtime_all_cpus, 'Entry marker requires exact all CPU owned runtime profile'
assert not a.psci_cache_publish or a.psci_entry_marker, 'Cache publication requires own marker candidate'
trial_sha=g.PSCI_CACHE_PUBLISH_EFI_SHA if a.psci_cache_publish else g.PSCI_ENTRY_MARKER_EFI_SHA if a.psci_entry_marker else g.PSCI_RUNTIME_ALL_EFI_SHA if a.psci_runtime_all_cpus else g.PSCI_RUNTIME_EFI_SHA if a.psci_runtime_reserve else g.PSCI_FIRST_EFI_SHA if a.psci_first_cpu_entry else g.PSCI_RESERVE_EFI_SHA if a.psci_owned_reserve else g.GIC8_EFI_SHA if a.fixed_eight_cpu_gic else g.GIC_EFI_SHA if a.dynamic_gic else g.RTC_EFI_SHA
trial_kind='GIC8' if a.fixed_eight_cpu_gic else 'GIC' if a.dynamic_gic else 'RTC'
assert len(candidate)==8192 and g.sha(candidate)==trial_sha,'Exact offline-tested selected candidate'
a.output.mkdir(parents=True)
fat=g.DeviceFat(a.adb,a.output,trial_sha if a.rollback else g.EFI_SHA)
original,chain=fat.file('/EFI/BOOT/BOOTAA64.EFI')
assert len(original)==8192
replacement=a.rollback.read_bytes() if a.rollback else candidate
assert len(replacement)==8192 and g.sha(replacement)==(g.EFI_SHA if a.rollback else trial_sha)
metadata=fat.read(0,65536);boot=fat.read(g.PART,512)
(a.output/'original-BOOTAA64.EFI').write_bytes(original)
(a.output/'replacement-BOOTAA64.EFI').write_bytes(replacement)
if not a.apply:
    report=dict(result='ISOLATED_'+trial_kind+'_EFI_PREFLIGHT_PASS',bytes=8192,before_sha256=g.sha(original),
                after_sha256=g.sha(replacement),clusters=chain,scope='Backup and read-only exact-size plan; no device writes')
    (a.output/'report.json').write_text(json.dumps(report,indent=2))
    print(json.dumps(report))
    raise SystemExit(0)
try:
    fat.write_bcd(replacement,chain)
    actual,after=fat.file('/EFI/BOOT/BOOTAA64.EFI')
    assert actual==replacement and after==chain,'Exact file/chain readback'
    assert fat.read(0,65536)==metadata and fat.read(g.PART,512)==boot,'GPT/boot metadata unchanged'
except BaseException:
    fat.write_bcd(original,chain)
    assert fat.file('/EFI/BOOT/BOOTAA64.EFI')[0]==original,'Rollback failed; do not boot'
    raise
report=dict(result='ISOLATED_'+trial_kind+'_EFI_REPLACE_READBACK_PASS',rollback=bool(a.rollback),bytes=8192,
            before_sha256=g.sha(original),after_sha256=g.sha(actual),clusters=chain,
            scope='Existing own EFI file only; no FAT/GPT/NTFS/Windows user files/Ubuntu writes')
(a.output/'report.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report))
