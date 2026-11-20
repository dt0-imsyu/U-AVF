"""Bounded isolated KD observer. Exact-image guarded, reversible software probes.

Alternates adjacent driver-call/return probes, so no single-step or CPU flags.
Never writes a Windows image; KD restores each original instruction before resume.
"""
import argparse, base64, json, queue, re, struct, subprocess, threading, time, socket
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('adb'); p.add_argument('runner'); p.add_argument('directory')
p.add_argument('kernel'); p.add_argument('--seconds',type=int,default=180)
p.add_argument('--trace-refsv1')
p.add_argument('--refs-tail',action='store_true')
p.add_argument('--refs-library',action='store_true')
p.add_argument('--refs-lazy',action='store_true')
p.add_argument('--trace-cache',action='store_true')
p.add_argument('--cache-private',action='store_true')
p.add_argument('--cache-insert',action='store_true')
p.add_argument('--cache-delete',action='store_true')
p.add_argument('--cache-delay',action='store_true')
p.add_argument('--cache-delay-sparse',action='store_true')
p.add_argument('--trace-clock',action='store_true')
p.add_argument('--clock-sparse',action='store_true',help='One-shot timer-test ISR/result probes; no driver/MSR stop loop')
p.add_argument('--wait-clock',action='store_true',help='One-shot delay-call context, then normal clock IRQ and return')
p.add_argument('--wait-program',action='store_true',help='Add one-shot timer writes only after the cache delay call')
p.add_argument('--wait-expiration',action='store_true',help='Read original timer bucket/PRCB and one-shot expiration processing after delay')
p.add_argument('--wait-idle',action='store_true',help='After another wait returns, observe idle entry/return and alternate clock handler')
p.add_argument('--wait-wfi',action='store_true',help='After idle return, one-shot architectural WFI entry/exit probes')
p.add_argument('--wait-gic',action='store_true',help='Read physical GIC timer IRQ state after returned WFI, without writes')
p.add_argument('--wait-gic-map',action='store_true',help='Capture HAL GIC mapping then read mapped IRQ registers after WFI')
p.add_argument('--wait-gic-io',action='store_true',help='Try width-4 KD IO-memory reads after WFI; no writes')
p.add_argument('--sample-timer-registers',action='store_true',help='Reversible four-MRS RAM probe after WFI; restore full context')
p.add_argument('--sample-timer-late',action='store_true',help='Sample after PoIdle returns and caller clears DAIF.I')
p.add_argument('--test-timer-unmask',action='store_true',help='One guarded IMASK-only A/B after validated enabled/masked/expired late sample')
p.add_argument('--sample-after-io',action='store_true',help='Read native timer registers again at IoInitSystem return')
p.add_argument('--sample-pmu-after-io',action='store_true',help='Read-only EL1 MIDR/DFR0/PMUSERENR/PMCR after restored IO timer probe; no feature mutation')
p.add_argument('--periodic-imask-workaround',action='store_true',help='Exact guarded RAM thunk: clear IMASK after enabled periodic CNTV compare update')
p.add_argument('--process-inventory',action='store_true',help='Read bounded active process list at own idle break-in')
p.add_argument('--device-inventory',action='store_true',help='Read bounded reciprocal PnP tree at own break/bugcheck; no device writes')
p.add_argument('--cam-access',action='store_true',help='Paired isolated CAM PCI tables only: exact guarded HAL geometry RAM adaptation')
p.add_argument('--trace-storage',help='Exact signed viostor.sys: one-shot DriverEntry/caller-return probes')
p.add_argument('--break-on-reboot',action='store_true',help='One-shot HalReturnToFirmware caller/process evidence, no reset suppression')
p.add_argument('--break-on-idle',action='store_true')
p.add_argument('--candidate-sha',default='87CD1D4486EEB0C29AB6FBA691F73B3B4C8AE5E0538532BDE14364311A34DB35',help='Explicit audited disposable candidate SHA256; never disable identity check')
p.add_argument('--idle-seconds',type=int,choices=[30,60],default=30,help='Bounded silence before own break; display/driver bootstrap may need 60s')
p.add_argument('--app-bridge-token',help='Separate diagnostic APK owner, authenticated raw TCP KD; never production app')
p.add_argument('--app-bridge-port',type=int,default=39101)
p.add_argument('--app-service-owner',action='store_true',help='Literal shell raw create/start in same-UID APK service; identity path only')
p.add_argument('--prepared-install-media',action='store_true',help='Owner verifies exact original media hash plus journal/grown GPT before starting; target must remain unpartitioned')
p.add_argument('--mutable-install-media',action='store_true',help='Owner validates frozen EFI/WIM plus exact source/MSR/NTFS layout; not installed-boot proof')
p.add_argument('--idd-disposable-bootstrap',action='store_true',help='Pinned own bootstrap WIM and fresh NTFS serial; no working Windows target')
p.add_argument('--rtc-clock-shim',action='store_true',help='Exact isolated clock-only EFI trial; require authenticated owner hash marker, never relax other platform guards')
p.add_argument('--dynamic-gic',action='store_true',help='Exact isolated SMP-safe EFI selector; require authenticated owner marker')
p.add_argument('--fixed-eight-cpu-gic',action='store_true',help='Exact legacy two-address patch, eight CPU only')
p.add_argument('--inspect-psci-cpu-on',action='store_true',help='One restored HVC wrapper call observation; typed CPU_ON operands only')
p.add_argument('--psci-low-memory-ab',action='store_true',help='Explicit isolated 2GiB CPU_ON address-range experiment')
p.add_argument('--psci-zero-x7-ab',action='store_true',help='One CPU_ON HVC reserved x7 A/B after exact stack-save guard; restored by original next instruction')
p.add_argument('--psci-owned-reserve',action='store_true',help='Exact owned NVS allocation proof only, no CPU_ON redirection')
p.add_argument('--psci-first-cpu-entry-ab',action='store_true',help='One first CPU_ON static trampoline, original context restored by own EFI code')
p.add_argument('--psci-runtime-reserve',action='store_true',help='Read-only own EFI virtual mapping control; no CPU_ON redirect')
p.add_argument('--psci-zero-context-ab',action='store_true',help='Exactly one CPU1 CPU_ON context0 via verified owned runtime trampoline')
p.add_argument('--psci-runtime-all-cpus',action='store_true',help='Bounded seven CPU_ON adaptations with separate owned pages')
p.add_argument('--psci-pmu-boundary',action='store_true',help='Two BSP/AP read-only PMCR.N and counter-set observations at exact new HAL failure')
p.add_argument('--pmu-common-counter-ab',action='store_true',help='One guarded BSP counter-set allocation request31->20; native PMCR.N/features unchanged')
p.add_argument('--smp-start-snapshot-after',type=int,choices=[0,30,60],default=0,help='One bounded own break: read CPU0..2 kernel PCs after accepted secondary start')
p.add_argument('--psci-entry-marker',action='store_true',help='Version2 own64B AP entry markers; independent execution proof')
p.add_argument('--psci-defer-return-probe',action='store_true',help='After CPU3+ start, observe next CPU_ON entry instead of immediate HVC return')
p.add_argument('--psci-pre-start-only',action='store_true',help='All CPU observations before HVC; BSP-only PMU allocation, no AP/return breakpoints')
p.add_argument('--psci-startup-wait-probe',action='store_true',help='Typed secondary1..7 BSP startup waits: one entry, first stall return, exit each; no AP stops')
p.add_argument('--psci-cache-publish',action='store_true',help='Native PoC clean of each OWN trampoline after typed literal write, before CPU_ON')
p.add_argument('--windows-ram-gop',action='store_true',help='Exact offline-tested own reserved-RAM GOP candidate; native1200p A/B only')
p.add_argument('--wddm-pnp',action='store_true',help='Accept the exact isolated WDDM PnP SSDT candidate')
p.add_argument('--ram-framebuffer-audit',action='store_true',help='Exactly two32-byte physical pixel observations after actual Default workload; no framebuffer writes')
p.add_argument('--psci-final-start-state',action='store_true',help='One last-common BSP startup handshake and public CPU PC/SP/PSTATE snapshot after all seven native starts')
p.add_argument('--psci-post-start-minimal',action='store_true',help='Observer A/B: retain only distant native startup return, no PPM/IPI rendezvous stops')
p.add_argument('--psci-explicit-ipi-ab',action='store_true',help='One isolated capability A/B: native explicit affinity rather than all-others broadcast; no barrier bypass')
p.add_argument('--match-host-cpus',action='store_true',help='Isolated SMP control, only with dynamic GIC and no legacy oneCPU observations')
p.add_argument('--hold-runtime',action='store_true',help='Keep ACK/debug channel alive after proven boot until overall deadline; no idle break-in')
p.add_argument('--inspect-runtime-after',type=int,default=0,
               help='One explicit late process inventory if no Surface frame appeared; requires hold-runtime')
p.add_argument('--inspect-hot-process',choices=['lsass.exe'],help='One read-only late hot-thread code/CPU inventory; no credentials, stacks, process control or userland writes')
p.add_argument('--inspect-wait-timeout',action='store_true',help='Read only the exact observed LSASS condition-variable timeout and kernel timer metadata; no general register/user-stack dump')
p.add_argument('--inspect-wait-return',action='store_true',help='After one hot inventory, one guarded kernel wait-return observation; exact LSASS thread only, no user data or wait mutation')
p.add_argument('--inspect-clock-seed',action='store_true',help='One first HAL calendar-reference writer call; typed time only, no hot-process inventory')
p.add_argument('--inspect-boot-clock-delta',action='store_true',help='One boot time/counter subtraction site; typed loader time/counter and frequency only')
p.add_argument('--inspect-loader-time',help='Exact winload image: one restored final time-selection probe, typed calendar operands only')
p.add_argument('--correct-boot-calendar-100ns',type=int,default=0,help='Isolated clock A/B: replace only invalid loader calendar before first load; no disk/userland/counter change')
p.add_argument('--allow-runtime-inspection-requests',action='store_true',help='At most two explicit same-VM hot-wait snapshots via bounded evidence request JSON, no commands or arbitrary reads')
p.add_argument('--boot-milestone',choices=['interrupt-init','driver-entry','io-pre-drivers','io-complete'],help='Exactly one cold-boot observation point; no other late probes/samples')
p.add_argument('--boot-runtime-snapshot',action='store_true',help='Read-only register sample within the single logical milestone; full code/context rollback')
p.add_argument('--pmu-cycle-read-ab',action='store_true',help='Human-authorized isolated PMUSERENR.CR write/readback; native GPR/SP restoration, no smss patch')
p.add_argument('--emulate-user-pmcc',action='store_true',help='Isolated exact-kernel trap adapter for EL0 PMCCNTR reads; CNTVCT fallback, no userland patch')
p.add_argument('--quiet-driver-notifications',action='store_true',help='Suppress only DbgLoadImageSymbols diagnostic stops after proven boot fixes; observe app-owned display independently')
p.add_argument('--loader-state',action='store_true',help='One read-only observation at existing winload module notification; no additional breakpoint or feature change')
p.add_argument('--delegate-shell-owner',action='store_true',help='Supported AVF owner FD delegation after viosock load; no CPU/PMU experiment')
p.add_argument('--trace-producer-exit',action='store_true',help='Bounded NtTerminateProcess observation after viosock loads; own producer only, no exit/status mutation')
p.add_argument('--smss-pmu-workaround',action='store_true',help='Exact observed smss PMCCNTR SIGILL only: guarded first-chance RAM A/B to CNTVCT')
a=p.parse_args(); root=Path(a.directory); root.mkdir(parents=True,exist_ok=True)
assert not a.inspect_loader_time or (a.mutable_install_media and a.hold_runtime and a.emulate_user_pmcc and a.quiet_driver_notifications and
    not any((a.dynamic_gic,a.match_host_cpus,a.inspect_runtime_after,a.loader_state,a.boot_milestone,a.inspect_clock_seed,a.inspect_boot_clock_delta,a.correct_boot_calendar_100ns))), 'One isolated loader-time observation only'
assert not a.mutable_install_media or (a.app_service_owner and a.app_bridge_token and not a.prepared_install_media), 'Mutable layout requires authenticated same-UID owner'
assert not a.rtc_clock_shim or (a.mutable_install_media and a.hold_runtime and a.cam_access), 'RTC trial only in unchanged isolated installed contract'
assert not a.dynamic_gic or (a.mutable_install_media and a.hold_runtime and a.cam_access and not a.rtc_clock_shim), 'Exact isolated GIC profile only'
assert not a.fixed_eight_cpu_gic or (a.mutable_install_media and a.hold_runtime and a.cam_access and a.match_host_cpus and not a.dynamic_gic and not a.rtc_clock_shim and not a.inspect_loader_time), 'Exact fixed eight CPU GIC profile only'
assert not a.inspect_psci_cpu_on or (a.fixed_eight_cpu_gic and a.emulate_user_pmcc and a.quiet_driver_notifications), 'PSCI observation only on exact failed eight CPU profile'
assert not a.psci_low_memory_ab or a.inspect_psci_cpu_on, 'Address A/B requires typed CPU_ON observation'
assert not a.psci_zero_x7_ab or (a.inspect_psci_cpu_on and not a.psci_low_memory_ab), 'One isolated CPU_ON ABI A/B only'
assert not a.psci_owned_reserve or (a.fixed_eight_cpu_gic and a.inspect_psci_cpu_on and not a.psci_low_memory_ab and not a.psci_zero_x7_ab), 'Own reserved page proof excludes other A/Bs'
assert not a.psci_first_cpu_entry_ab or a.psci_owned_reserve, 'First CPU entry requires exact owned EFI reserve'
assert not a.psci_runtime_reserve or (a.psci_owned_reserve and not a.psci_first_cpu_entry_ab), 'Runtime map control excludes static entry A/B'
assert not a.psci_zero_context_ab or a.psci_runtime_reserve, 'Context0 A/B requires proven owned runtime mapping'
assert not a.psci_runtime_all_cpus or a.psci_zero_context_ab, 'All CPU adapter requires context0 runtime mapping profile'
assert not a.psci_pmu_boundary or a.psci_runtime_all_cpus, 'PMU boundary requires exact current SMP context0 experiment'
assert not a.pmu_common_counter_ab or a.psci_pmu_boundary, 'Common counter A/B requires exact proven PMU mismatch profile'
assert not a.smp_start_snapshot_after or a.pmu_common_counter_ab, 'SMP snapshot only in current common-PMU startup profile'
assert not a.psci_entry_marker or (a.psci_runtime_all_cpus and a.pmu_common_counter_ab and not a.smp_start_snapshot_after), 'Entry marker requires separate bounded current SMP profile'
assert not a.psci_defer_return_probe or a.psci_entry_marker, 'Deferred startup observation requires exact owned entry markers'
assert not a.psci_pre_start_only or a.psci_defer_return_probe, 'Pre-start-only observation requires exact deferred marker profile'
assert not a.psci_startup_wait_probe or a.psci_pre_start_only, 'Startup wait requires exact pre-start-only marker profile'
assert not a.psci_cache_publish or a.psci_startup_wait_probe, 'PoC publication requires exact owned wait profile'
assert not a.psci_final_start_state or a.psci_cache_publish, 'Final startup state requires exact native publication profile'
assert not a.windows_ram_gop or (a.psci_cache_publish and a.psci_explicit_ipi_ab), 'RAM GOP requires the exact proven eight CPU native IPI path'
assert not a.ram_framebuffer_audit or (a.windows_ram_gop and a.fixed_eight_cpu_gic and a.app_service_owner and a.hold_runtime and not a.inspect_runtime_after), 'Framebuffer observation requires exact isolated eightCPU GOP owner, no generic late inspection'
assert not a.psci_post_start_minimal or a.psci_final_start_state, 'Minimal post-start observation requires exact native final-state profile'
assert not a.psci_explicit_ipi_ab or (a.psci_final_start_state and not a.psci_post_start_minimal), 'Explicit IPI A/B requires exact final-state profile, excludes minimal observer'
assert not a.match_host_cpus or ((a.dynamic_gic or a.fixed_eight_cpu_gic) and a.emulate_user_pmcc and a.quiet_driver_notifications and
    not any((a.inspect_runtime_after,a.boot_milestone,a.loader_state,a.inspect_clock_seed,a.inspect_boot_clock_delta,a.correct_boot_calendar_100ns,a.sample_after_io,a.sample_pmu_after_io))), 'No oneCPU observation scope in SMP'
assert not (a.loader_state and a.boot_milestone), 'Exactly one observation scope'
if a.loader_state:
    assert a.periodic_imask_workaround and a.cam_access
    assert not any((a.sample_after_io,a.sample_pmu_after_io,a.break_on_reboot,a.trace_producer_exit,a.smss_pmu_workaround,a.wait_clock,a.trace_storage,a.break_on_idle)), 'No extra probes in loader state mode'
if a.boot_milestone:
    assert a.periodic_imask_workaround and a.cam_access, 'Keep proven boot fixes'
    assert not any((a.sample_after_io,a.sample_pmu_after_io,a.break_on_reboot,a.trace_producer_exit,a.smss_pmu_workaround,a.wait_clock,a.trace_storage)), 'Single-point mode forbids extra probes/mutations'
assert not a.boot_runtime_snapshot or a.boot_milestone, 'Runtime snapshot requires one selected milestone'
assert not a.pmu_cycle_read_ab or (a.boot_milestone=='driver-entry' and not a.boot_runtime_snapshot), 'PMU A/B uses only the established driver-entry checkpoint'
assert not a.emulate_user_pmcc or not any((a.boot_milestone,a.loader_state,a.sample_after_io,a.sample_pmu_after_io,a.smss_pmu_workaround,a.pmu_cycle_read_ab)), 'Trap adapter has its own minimal observation scope'
assert not a.quiet_driver_notifications or (a.emulate_user_pmcc and a.app_bridge_token), 'Quiet mode is the isolated app-owned display experiment only'
assert not a.inspect_runtime_after or (a.hold_runtime and a.process_inventory and 120<=a.inspect_runtime_after<=1800), 'Bounded late inspection only'
assert not a.inspect_hot_process or a.inspect_runtime_after, 'Hot-thread observation requires explicit bounded late inspection'
assert not a.inspect_wait_return or (a.inspect_hot_process and a.inspect_wait_timeout), 'Wait return requires existing hot-thread/typed wait inspection'
assert not a.inspect_clock_seed or (a.hold_runtime and a.mutable_install_media and a.emulate_user_pmcc and a.quiet_driver_notifications and not a.inspect_runtime_after), 'Clock seed requires frozen isolated runtime and no late inspection'
assert not a.inspect_boot_clock_delta or (a.hold_runtime and a.mutable_install_media and a.emulate_user_pmcc and a.quiet_driver_notifications and not a.inspect_runtime_after and not a.inspect_clock_seed), 'Boot clock delta requires one isolated observation only'
assert not a.correct_boot_calendar_100ns or (a.hold_runtime and a.mutable_install_media and a.emulate_user_pmcc and a.quiet_driver_notifications and not a.inspect_clock_seed and not a.inspect_boot_clock_delta), 'Calendar correction requires exact isolated runtime; no extra early probes'
assert not a.inspect_wait_timeout or a.inspect_hot_process=='lsass.exe', 'Timeout observation requires the proven hot-process inventory'
assert not a.allow_runtime_inspection_requests or a.inspect_wait_timeout, 'Requests require the exact bounded wait observation'
if a.cam_access:
    report=json.loads((Path(__file__).resolve().parents[3]/'build-logs/windows-post-linux-reassessment-20261004/display-disk-report.json').read_text())
    if a.candidate_sha.upper()!=report['sha256']:
        variant=None
        for name in ('direct-accept','shell-lifetime','real-setup','producer-exit','producer-stderr','producer-fault','producer-accept-fix','live','live-name-fix'):
            manifest=Path(__file__).resolve().parents[3]/('build-logs/windows-post-linux-reassessment-20261004/'+name+'-disk-report.json')
            if manifest.exists():
                entry=json.loads(manifest.read_text())
                if entry['sha256']==a.candidate_sha.upper():variant=entry;break
        assert variant is not None, 'No exact approved helper variant'
        assert variant['result']=='PASS' and variant.get('platform_base_sha256',variant['base_sha256'])==report['sha256'] and variant['bytes']==9126805504, 'Variant requires exact platform base identity'
        if 'platform_base_sha256' in variant:
            lifetime=json.loads((Path(__file__).resolve().parents[3]/'build-logs/windows-post-linux-reassessment-20261004/shell-lifetime-disk-report.json').read_text())
            assert lifetime['base_sha256']==report['sha256'], 'Lifetime platform identity'
            if variant['base_sha256']!=lifetime['sha256']:
                media=json.loads((Path(__file__).resolve().parents[3]/'build-logs/windows-post-linux-reassessment-20261004/real-setup-disk-report.json').read_text())
                parent=variant
                if name=='live-name-fix':
                    parent=json.loads((Path(__file__).resolve().parents[3]/'build-logs/windows-post-linux-reassessment-20261004/live-disk-report.json').read_text())
                    assert variant['base_sha256']==parent['sha256'] and parent['result']=='PASS' and parent['platform_base_sha256']==report['sha256'], 'Name correction requires exact live parent'
                    name='live'
                if name=='live':
                    child=parent
                    parent=json.loads((Path(__file__).resolve().parents[3]/'build-logs/windows-post-linux-reassessment-20261004/producer-accept-fix-disk-report.json').read_text())
                    assert child['base_sha256']==parent['sha256'] and parent['result']=='PASS' and parent['platform_base_sha256']==report['sha256'], 'Live requires exact proven Setup control'
                    name='producer-accept-fix'
                if name=='producer-accept-fix':
                    child=parent
                    parent=json.loads((Path(__file__).resolve().parents[3]/'build-logs/windows-post-linux-reassessment-20261004/producer-fault-disk-report.json').read_text())
                    assert child['base_sha256']==parent['sha256'] and parent['result']=='PASS' and parent['platform_base_sha256']==report['sha256'], 'Accept fix requires exact fault candidate'
                if name in ('producer-fault','producer-accept-fix'):
                    child=parent
                    parent=json.loads((Path(__file__).resolve().parents[3]/'build-logs/windows-post-linux-reassessment-20261004/producer-stderr-disk-report.json').read_text())
                    assert child['base_sha256']==parent['sha256'] and parent['result']=='PASS' and parent['platform_base_sha256']==report['sha256'], 'Own fault logger requires exact stderr chain'
                if name in ('producer-stderr','producer-fault','producer-accept-fix'):
                    child=parent
                    parent=json.loads((Path(__file__).resolve().parents[3]/'build-logs/windows-post-linux-reassessment-20261004/producer-exit-disk-report.json').read_text())
                    assert child['base_sha256']==parent['sha256'] and parent['result']=='PASS' and parent['platform_base_sha256']==report['sha256'], 'Own stderr requires exact supervisor chain'
                assert parent['base_sha256']==media['sha256'] and media['base_sha256']==lifetime['sha256'] and media['platform_base_sha256']==report['sha256'], 'Own supervisor requires exact media chain'
        report=variant
    assert a.candidate_sha.upper()==report['sha256'] and report.get('boot_sha256')=='893B7859B6E27598B8392F5EC9A9FB33C948FCAF4EF1918F28072ED5AB402E8B', 'CAM geometry requires exact paired firmware profile'
assert not a.wait_expiration or (a.wait_clock and a.wait_program), 'Expiration requires wait-clock and wait-program'
assert not a.wait_idle or (a.wait_clock and a.wait_program), 'Idle requires wait-clock and wait-program'
assert not a.wait_wfi or a.wait_idle, 'WFI requires wait-idle'
assert not a.wait_gic or a.wait_wfi, 'GIC requires wait-wfi'
assert not a.wait_gic_map or a.wait_wfi, 'Mapped GIC requires wait-wfi'
assert not a.wait_gic_io or a.wait_wfi, 'IO GIC requires wait-wfi'
assert not a.sample_timer_registers or a.wait_wfi, 'Register sample requires wait-wfi'
assert not a.sample_timer_late or a.sample_timer_registers, 'Late requires register sample'
assert not a.test_timer_unmask or a.sample_timer_late, 'Unmask A/B requires validated late sample'
assert not a.sample_after_io or (a.test_timer_unmask or a.periodic_imask_workaround), 'Post-IO sample requires unmask A/B/workaround'
assert not a.sample_pmu_after_io or a.sample_after_io, 'PMU observation requires restored IO sample checkpoint'
kernel=Path(a.kernel).read_bytes()
import hashlib
assert hashlib.sha256(kernel).hexdigest().upper()=='C667739004D1186DACA2FD3FE7CCDE61394BC068F385B4BB8CD5364D3005A734'
pmu_audit=None
if a.smss_pmu_workaround:
    pmu_audit=json.loads((Path(__file__).resolve().parents[3]/'build-logs/windows-post-linux-reassessment-20261004/smss-pmu-static-audit.json').read_text())
    assert pmu_audit['smss_fault_rva']=='0x1253c' and pmu_audit['kd_flag_rva']=='0xdc9a64'
pe=struct.unpack_from('<I',kernel,60)[0]
ns=struct.unpack_from('<H',kernel,pe+6)[0]; opt=struct.unpack_from('<H',kernel,pe+20)[0]
sections=[struct.unpack_from('<IIII',kernel,pe+24+opt+40*i+8) for i in range(ns)]
storage=None;storage_layout=None;storage_entry=None;storage_probed=False
if a.trace_storage:
    storage=Path(a.trace_storage).read_bytes()
    assert hashlib.sha256(storage).hexdigest().upper()=='917BEBB136A2941EC50A5BD47764509B0B60A23C0696B922380E4043FD330790'
    spe=struct.unpack_from('<I',storage,60)[0];sn=struct.unpack_from('<H',storage,spe+6)[0];so=struct.unpack_from('<H',storage,spe+20)[0]
    storage_layout=[struct.unpack_from('<IIII',storage,spe+24+so+40*i+8) for i in range(sn)]
    storage_entry=struct.unpack_from('<I',storage,spe+40)[0]
    assert storage_entry==0x12000
def image_bytes(rva,n,data=kernel,layout=sections):
    for _,start,size,raw in layout:
        if start<=rva and rva+n<=start+size:return data[raw+rva-start:raw+rva-start+n]
    raise ValueError('RVA outside image')

loader_time_image=None;loader_time_layout=None;loader_time_base=None;loader_time_seen=False
if a.inspect_loader_time:
    loader_time_image=Path(a.inspect_loader_time).read_bytes()
    assert hashlib.sha256(loader_time_image).hexdigest().upper()=='F7747F4AC18CCD66EBF6A043D979CF30C7731114C27C33FF2FC3C0610F491D31', 'Exact audited loader only'
    pe=struct.unpack_from('<I',loader_time_image,60)[0]
    count=struct.unpack_from('<H',loader_time_image,pe+6)[0];optional=struct.unpack_from('<H',loader_time_image,pe+20)[0]
    loader_time_layout=[struct.unpack_from('<IIII',loader_time_image,pe+24+optional+40*i+8) for i in range(count)]
    assert image_bytes(0x2e3c8,4,loader_time_image,loader_time_layout)==struct.pack('<I',0x91298289)

class KD:
    def __init__(self):
        self.socket=None; self.proc=None
        # Do not race an unfinished disk transfer with firmware reads.
        disk='/sdcard/Android/data/com.example.uavfwinpetest/files/windows.img' if a.app_bridge_token else '/data/local/tmp/uavf-windows-uart-gas-20261004/windows-kd.img'
        digest_command='run-as com.example.uavfwinpetest sha256sum files/windows-diagnostic.img' if a.app_service_owner else 'sha256sum '+disk
        assert re.fullmatch('[0-9A-Fa-f]{64}',a.candidate_sha), 'Invalid candidate SHA'
        if a.prepared_install_media or a.mutable_install_media:
            assert a.app_service_owner and a.app_bridge_token and a.candidate_sha.upper()=='A1DE9652E81A173BC4E242CFA53F23C4D8AD31C2D54500397650F894D927DF2A', 'Prepared media only through exact guarded owner'
        else:
            digest=subprocess.check_output([a.adb,'shell',digest_command],text=True).split()[0]
            assert digest.upper()==a.candidate_sha.upper(), 'Staged candidate SHA differs; VM not started'
        if a.app_bridge_token:
            assert re.fullmatch('[0-9A-Fa-f]{32,128}',a.app_bridge_token)
            end=time.monotonic()+120
            while True:
                try:
                    self.socket=socket.create_connection(('127.0.0.1',a.app_bridge_port),timeout=2)
                    self.socket.setsockopt(socket.IPPROTO_TCP,socket.TCP_NODELAY,1)
                    self.socket.settimeout(10);self.socket.sendall(a.app_bridge_token.encode('ascii'))
                    hello=bytearray()
                    while not hello.endswith(b'\n') and len(hello)<64:
                        byte=self.socket.recv(1)
                        if not byte:raise EOFError('Forward reached no ready owner listener')
                        hello.extend(byte)
                    assert hello==b'UAVF_WINDOWS_OWNER_OK\n','Diagnostic owner handshake'
                    if a.mutable_install_media:
                        status=subprocess.check_output([a.adb,'shell','cat /sdcard/Android/data/com.example.uavfwinpetest/files/service-owner-report.txt'],text=True)
                        assert 'IDENTITY_PASS uid=' in status and 'MUTABLE_INSTALL_TARGET_IDENTITY_PASS; EFI_WIM_FROZEN; NOT_INSTALLED_BOOT_PASS' in status, 'Frozen platform/layout validation required before start'
                        idd_marker='IDD_DISPOSABLE_BOOTSTRAP_IDENTITY_PASS sha=DD297166CD89B075B7242DD33D3583ABB2159CFB3EDFF533629723B48E7D56B0; ROOT_SERIAL=4AA580661DE89658; NOT_DRIVER_OR_FPS_PASS'
                        assert ('IDD_DISPOSABLE_BOOTSTRAP_IDENTITY_PASS' in status)==a.idd_disposable_bootstrap
                        if a.idd_disposable_bootstrap:
                            assert idd_marker in status and a.windows_ram_gop and a.fixed_eight_cpu_gic and a.match_host_cpus
                        rtc_marker='RTC_CLOCK_SHIM_IDENTITY_PASS sha=AAD53298182B48845DB08F00FC85305A85E4D14EBDFCE171007E883B84446F5D; NO_OTHER_PLATFORM_CHANGE'
                        assert (rtc_marker in status)==a.rtc_clock_shim, 'Explicit clock trial flag must match actual guarded owner EFI'
                        gic_marker='DYNAMIC_GIC_SHIM_IDENTITY_PASS sha=E550FD640FC3864305A6F6F0A794B582A5DB43D7F0556FA69FFD11BE5C1FDC47; CPU_TOPOLOGY='+('MATCH_HOST' if a.match_host_cpus else 'ONE_CPU')
                        assert ('DYNAMIC_GIC_SHIM_IDENTITY_PASS' in status)==a.dynamic_gic, 'Unrequested GIC candidate rejected'
                        assert (gic_marker in status)==a.dynamic_gic, 'Exact GIC selector and CPU topology must match owner'
                        fixed_hash='0347CB7BC2BB33C34C8940392EACB62795CDBE66D77FB081EEBFA29A88CC9007' if a.psci_cache_publish else '597AC0561E32A1210BC030B1B4A4BC432E2AAB1BBACE945862D25368120EBE29' if a.psci_entry_marker else '7F33A06FEE99DD373AB2CAB99A6CFABB8E883342882EC3F79FB6BA238363EA9E' if a.psci_runtime_all_cpus else '20A6364A39A5B58888923DDE027975A4B15771D6F652614397C48C872C8F4AF4' if a.psci_runtime_reserve else '36943A414743D10960C445102BE9EFCA4C2ADCAA46DAEB144CF7AA4F0E11620A' if a.psci_first_cpu_entry_ab else '3B1805F0982AC660A9197F98FBC2A111854B99D01C620849F221DDD7231B9ABD' if a.psci_owned_reserve else '365B733271DA6DEEB5EEA2C262041F6E1793D6DA3C21ECE077BA15FE84B1A865'
                        if a.windows_ram_gop:fixed_hash='359DED6E811F23B011566938B6F3CC8A01DF480D5E8225A320FEFE357CD98E3A'
                        ram_marker='WINDOWS_RAM_GOP_IDENTITY_PASS sha='+fixed_hash+'; NATIVE_MODE_CANDIDATE=1920x1200; NOT_RUNTIME_PASS'
                        pnp_marker='WDDM_PNP_EFI_IDENTITY_PASS sha=3D81FEEA37CC742FD55389FDA2E7DDC02A750A5469A49C8A639243CA8C13D6FA'
                        assert ('WDDM_PNP_EFI_IDENTITY_PASS' in status)==a.wddm_pnp
                        if a.wddm_pnp:
                            assert pnp_marker in status and a.windows_ram_gop and a.fixed_eight_cpu_gic and a.psci_cache_publish
                        else:
                            assert ('WINDOWS_RAM_GOP_IDENTITY_PASS' in status)==a.windows_ram_gop
                            if a.windows_ram_gop:assert ram_marker in status,'Exact RAM GOP owner required'
                        fixed_marker='FIXED_EIGHT_CPU_GIC_IDENTITY_PASS sha='+fixed_hash+'; CPU_TOPOLOGY=MATCH_HOST; HOST_CPUS=8'
                        assert ('FIXED_EIGHT_CPU_GIC_IDENTITY_PASS' in status)==a.fixed_eight_cpu_gic, 'Unrequested fixed GIC candidate rejected'
                        assert (fixed_marker in status)==a.fixed_eight_cpu_gic, 'Exact fixed GIC bank and eight CPU owner required'
                        # This marker is written just before raw creation, after the initial identity marker.
                        # Effective memory is verified at the kernel connection below.
                    if a.prepared_install_media:
                        status=subprocess.check_output([a.adb,'shell','cat /sdcard/Android/data/com.example.uavfwinpetest/files/service-owner-report.txt'],text=True)
                        assert 'IDENTITY_PASS uid=' in status and ('PREPARED_INSTALL_MEDIA_IDENTITY_PASS; TARGET_UNPARTITIONED; NOT_INSTALLED_BOOT' in status or 'INSTALL_GEOMETRY_PASS old_bytes=9126805504 new_bytes=77846282240' in status), 'Authenticated owner must validate original SHA and exact prepared GPT before start'
                    break
                except (OSError,EOFError):
                    if self.socket:self.socket.close();self.socket=None
                    status_name='service-owner-report.txt' if a.app_service_owner else 'owner-report.txt'
                    status=subprocess.check_output([a.adb,'shell','cat /sdcard/Android/data/com.example.uavfwinpetest/files/'+status_name],text=True)
                    if 'OWNER_ERROR=' in status:raise RuntimeError('Diagnostic owner startup failed: '+status.split('OWNER_ERROR=',1)[1])
                    if time.monotonic()>=end:raise
                    time.sleep(.5)
            self.socket.settimeout(None)
        else:self.proc=subprocess.Popen([a.adb,'shell','-T','sh '+a.runner],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        self.q=queue.Queue(); self.buf=bytearray(); self.gate=False
        self.pid=0x80800000; self.last_rx=None; self.last_tx=None; self.pending_tx=None; self.last_ack=None
        self.user_fault_record=None;self.user_fault_context=None
        self.rx=open(root/'rx.bin','wb'); self.tx=open(root/'tx.bin','wb')
        self.events=open(root/'events.jsonl','w',encoding='utf-8')
        self.deadline=time.monotonic()+a.seconds
        if a.rtc_clock_shim:self.note('RTC_CLOCK_SHIM_OWNER_IDENTITY_PASS',scope='Exact clock-only EFI candidate; all original layout/WIM/loader guards retained')
        threading.Thread(target=self.reader,daemon=True).start()
        if self.proc:threading.Thread(target=self.errors,daemon=True).start()
    def reader(self):
        if self.socket:
            try:
                while True:
                    data=self.socket.recv(4096)
                    if not data:break
                    self.q.put(data)
            except OSError:pass
        else:
            for line in self.proc.stdout:
                if line.startswith(b'O:'):self.q.put(base64.b64decode(line[2:]))
                elif line.startswith(b'H:'):
                    self.note('AVF_OWNER_STATUS',text=line.decode('ascii','replace').strip())
                    if line.startswith(b'H:OWNER_FATAL='):self.q.put(None)
        self.q.put(None)
    def errors(self):
        with open(root/'adb.stderr.bin','wb') as out:
            for data in iter(lambda:self.proc.stderr.read1(4096),b''):out.write(data);out.flush()
    def note(self,tag,**fields):
        event=dict(t=round(time.monotonic(),3),tag=tag,**fields)
        self.events.write(json.dumps(event)+'\n');self.events.flush()
        print(json.dumps(event),flush=True)
    def write(self,b):
        self.tx.write(b);self.tx.flush()
        if self.socket:self.socket.sendall(b)
        else:self.proc.stdin.write(base64.b64encode(b)+b'\n');self.proc.stdin.flush()
    def control(self,k,pid=0):
        packet=struct.pack('<IHHII',0x69696969,k,0,pid,0)
        if k==4:self.last_ack=packet
        self.write(packet)
    def request(self,api,union=b''):
        assert len(union)<=40
        body=struct.pack('<IHHI',api,0,getattr(self,'processor',0),0)+bytes(4)+union+bytes(40-len(union))
        self.send_data(2,body)
    def send_data(self,kind,body):
        self.last_tx=struct.pack('<IHHII',0x30303030,kind,len(body),self.pid,sum(body))+body+b'\xaa'
        self.write(self.last_tx)
        self.pending_tx=dict(packet=self.last_tx,pid=self.pid,kind=kind,sent=time.monotonic(),retries=0)
        self.pid^=1
    def retry_pending(self,reason,force=False):
        pending=self.pending_tx
        if not pending or (pending['retries']>=3 and not force):return
        if not force and time.monotonic()-pending['sent']<1.0:return
        self.write(pending['packet']) # Exact packet/ID; never a new request or feature write.
        pending['sent']=time.monotonic();pending['retries']+=1
        self.note('KD_PENDING_REPLAY',reason=reason,id=hex(pending['pid']),kind=pending['kind'],attempt=pending['retries'])
    def packet(self,timeout=5,wake_at=None):
        scheduled=self.deadline if wake_at is None else min(self.deadline,wake_at)
        end=min(time.monotonic()+timeout,scheduled)
        while time.monotonic()<end:
            while len(self.buf)>=16 and self.gate:
                if self.buf[:4] not in (b'0000',b'iiii'):del self.buf[0];continue
                _,kind,n,pid,checksum=struct.unpack_from('<IHHII',self.buf)
                is_control=self.buf[:4]==b'iiii'
                if is_control:
                    if kind not in (4,5,6) or n!=0:del self.buf[0];continue
                else:
                    minimum={2:56,3:16,7:80,11:64}.get(kind)
                    if minimum is None or n<minimum or pid & ~0x801 != 0x80800000:
                        del self.buf[0];continue
                length=16 if is_control else 17+n
                if n>16384:del self.buf[0];continue
                if len(self.buf)<length:break
                body=bytes(self.buf[16:16+n])
                if length>16 and (sum(body)!=checksum or self.buf[length-1]!=170):del self.buf[0];continue
                del self.buf[:length]
                if length==16:
                    if kind==4 and self.pending_tx and pid==(self.pending_tx['pid']&~0x800):
                        self.pending_tx=None
                    if kind==6:self.pid=0x80800000;self.last_rx=None;self.last_tx=None;self.pending_tx=None;self.last_ack=None;self.note('RESET')
                    if kind==5:
                        self.note('RESEND')
                        if self.pending_tx:
                            self.pending_tx['target_resends']=self.pending_tx.get('target_resends',0)+1
                        # The target may still be sending its previous response
                        # because our ACK was lost. In that state the next RPC
                        # itself prompts RESEND; replaying only that RPC loops.
                        # Re-ACK the last complete validated target packet first.
                        # This cannot execute a Windows command or memory write.
                        if self.last_ack:
                            self.write(self.last_ack)
                            self.note('KD_RESPONSE_ACK_REPLAY',id=hex(struct.unpack_from('<I',self.last_ack,8)[0]))
                        if self.pending_tx:self.retry_pending('target-resend',force=True)
                        elif self.last_tx:self.write(self.last_tx)
                    continue
                if pid & 0x800:
                    # BlBdStop/BlBdStart can reset transport without a RESET
                    # control packet first. Negotiate a fresh epoch, just as
                    # the initial standalone probe does; do not reuse counters.
                    self.pid=0x80800000;self.last_rx=None;self.last_tx=None;self.pending_tx=None;self.last_ack=None
                    self.control(6);self.note('PEER_SYNC',id=hex(pid));continue
                self.control(4,pid & ~0x800)
                if self.last_rx==pid:
                    if kind==11 and self.pending_tx and self.pending_tx['kind']==11:
                        self.retry_pending('duplicate-file-request',force=True)
                    continue
                self.last_rx=pid
                # A new target message also establishes progress through the
                # previous exchange if its ACK was lost, as the target sender
                # itself does when receiving data while awaiting an ACK.
                self.pending_tx=None
                if kind==3:
                    text=body[16:].decode('ascii','replace').strip()
                    if 'Unhandled exception' in text:
                        self.user_fault_record=None;self.user_fault_context=None
                    match=re.search(r'\.exr\s+([0-9a-fA-F]{8,16})',text)
                    if match:self.user_fault_record=int(match.group(1),16)
                    match=re.search(r'\.cxr\s+([0-9a-fA-F]{8,16})',text)
                    if match:self.user_fault_context=int(match.group(1),16)
                    self.note('PRINT',text=text)
                    # Complete target messages prove activity. Bound *silence*,
                    # not total time spent serving loader file requests/prints.
                    # The independent overall test deadline remains unchanged.
                    end=min(time.monotonic()+timeout,scheduled)
                    continue
                if kind==11:
                    # Same response as the retained successful DbgEng run:
                    # no debugger-host file override, continue using guest disk.
                    assert len(body)>=64 and struct.unpack_from('<I',body)[0]==0x3430
                    self.note('FILE_OVERRIDE_DECLINED',path=body[64:].decode('utf-16-le','replace').rstrip('\0'))
                    reply=bytearray(body[:64]);struct.pack_into('<I',reply,4,0xc000000f)
                    self.send_data(11,reply)
                    end=min(time.monotonic()+timeout,scheduled)
                    continue
                if kind==7:
                    processor=struct.unpack_from('<H',body,6)[0]
                    assert processor<8, 'Stopped processor outside isolated eight CPU bound'
                    self.processor=processor;self.last_state_change=body
                return kind,body
            try:data=self.q.get(timeout=.2)
            except queue.Empty:
                self.retry_pending('missing-ack')
                continue
            if data is None:raise EOFError('ADB runner closed')
            self.rx.write(data);self.rx.flush();self.buf.extend(data)
            if not self.gate and b'WG:LOAD=PASS,START_WINDOWS' in self.buf:
                self.gate=True;self.control(6);self.note('GATE_OPEN')
        raise TimeoutError('No complete KD response/state within bound')
    def rpc(self,api,union=b'',allow_status=False,extra=b''):
        stopped_state=getattr(self,'last_state_change',None)
        if extra:
            assert len(union)<=40
            self.send_data(2,struct.pack('<IHHI',api,0,getattr(self,'processor',0),0)+bytes(4)+union+bytes(40-len(union))+extra)
        else:self.request(api,union)
        read_reset_used=False
        for attempt in range(3):
            try:kind,body=self.packet()
            except TimeoutError:
                # Captured CID2175: an acknowledged write was followed by two
                # identical READs; the second exchange remained in a cached
                # response/RESEND epoch. Recover only a stopped, idempotent
                # query, once. Never reset/reissue WRITE, CONTINUE or context
                # mutation: their execution may already have happened.
                if (api not in (0x3130,0x315f,0x3146) or extra or
                        read_reset_used or attempt>=2 or time.monotonic()>=self.deadline):raise
                read_reset_used=True
                self.note('KD_STOPPED_READ_EPOCH_RECOVERY',api=hex(api))
                self.control(6)
                self.pid=0x80800000;self.last_rx=None;self.last_tx=None
                self.pending_tx=None;self.last_ack=None
                self.request(api,union)
                kind,body=self.packet()
            if kind==2 and struct.unpack_from('<I',body)[0]==api:
                if api in (0x3130,0x3131) and body[16:28]!=union[:12]:
                    # Retransmitted responses can have the same API as the
                    # next request. Never accept bytes for a different range.
                    self.note('KD_STALE_MEMORY_RESPONSE',api=hex(api),
                              expected_address=hex(struct.unpack_from('<Q',union)[0]),
                              actual_address=hex(struct.unpack_from('<Q',body,16)[0]))
                    if api==0x3130 and attempt<2:
                        self.request(api,union)
                        continue
                    raise ValueError('KD memory response does not match requested range')
                break
            # Loader->kernel RESET can retransmit the identical stopped state
            # while discarding an outstanding READ. Retry only idempotent reads;
            # never hide a new stop or replay an ambiguous write/continue.
            if kind==7 and stopped_state is not None and body==stopped_state and api in (0x3130,0x315f,0x3146) and not extra and attempt<2:
                self.note('KD_IDENTICAL_STOP_READ_RETRY',api=hex(api),attempt=attempt+1)
                self.request(api,union)
                continue
            raise ValueError('Unexpected RPC response expected='+hex(api)+' kind='+str(kind)+' body='+body[:16].hex())
        status=struct.unpack_from('<I',body,8)[0]
        if status and not allow_status:raise ValueError('KD status '+hex(status)+' for '+hex(api))
        return body
    def read(self,addr,n):
        body=self.rpc(0x3130,struct.pack('<QII',addr,n,0))
        actual=struct.unpack_from('<I',body,28)[0]
        if actual!=n or len(body)!=56+n:raise ValueError('Short memory read')
        return body[56:]
    def context(self):
        body=self.rpc(0x315f,struct.pack('<III',0,912,0))
        data=body[56:]
        if len(data)<272:raise ValueError('Short ARM64 context')
        self.last_context=bytes(data)
        return dict(x=list(struct.unpack_from('<31Q',data,8)),sp=struct.unpack_from('<Q',data,256)[0],pc=struct.unpack_from('<Q',data,264)[0],cpsr=struct.unpack_from('<I',data,4)[0])
    def confirm_timed_out_platform_write(self,address,data):
        # Two captured boots: a next WRITE receives only RESEND of the preceding
        # READ epoch. Never replay an ambiguous mutation with a new packet ID.
        # CID2184 had TWO distinct lost transactions in the same guarded
        # adapter, not a repeated ambiguous write at one address. Permit two
        # distinct readback recoveries per boot, never more than one per range.
        # A transport RESET + READBACK can prove an ALREADY completed write,
        # or prove it was NOT applied (exact previously guarded original bytes).
        # Scope is only the existing exact-kernel platform fields/own padding;
        # no general memory/context/continue recovery or ambiguous write replay.
        fields={base+0xc03c31:b'\x08',base+0xc03c32:b'\x0b',base+0xc03c38:b'\x10'}
        # CID2196 exact last pending write was DbgLoadImageSymbols RET, after
        # two original-proven recoveries. Permit ONLY that third range, not
        # additional padding/platform/context mutations. Its original complete
        # prologue is guarded at the call site and independently below.
        notification=(address==base+0x2fbfa0 and data==struct.pack('<I',0xd65f03c0))
        if notification:
            assert image_bytes(0x2fbfa0,8)==bytes.fromhex('7f2303d5f30f1ff8'), 'Exact original diagnostic notification function'
        scoped=(notification or fields.get(address)==data or
                base+0x665b00<=address and address+len(data)<=base+0x665d34)
        sent=self.last_tx or b''
        recovered=getattr(self,'platform_write_recovered_ranges',set())
        write_range=(address,len(data))
        if (not scoped or not 1<=len(data)<=32 or
                len(recovered)>=3 or (len(recovered)>=2 and not notification) or write_range in recovered or
                len(sent)!=73+len(data) or sent[:4]!=b'0000' or
                struct.unpack_from('<H',sent,4)[0]!=2 or
                struct.unpack_from('<I',sent,16)[0]!=0x3131 or
                struct.unpack_from('<Q',sent,32)[0]!=address or
                struct.unpack_from('<I',sent,40)[0]!=len(data) or sent[72:-1]!=data):
            raise TimeoutError('Ambiguous write outside two platform ranges plus exact diagnostic-notification readback')
        self.platform_write_recovered_ranges=recovered|{write_range}
        self.note('KD_PLATFORM_WRITE_READBACK_RECOVERY',address=hex(address),bytes=len(data),
                  scope='RESET then READ; fresh WRITE only after exact original bytes prove not applied; no context/continue retry')
        self.control(6)
        self.pid=0x80800000;self.last_rx=None;self.last_tx=None
        self.pending_tx=None;self.last_ack=None
        actual=self.read(address,len(data))
        if actual!=data:
            original_fields={base+0xc03c31:b'\x0c',base+0xc03c32:b'\x0f',base+0xc03c38:b'\x14',
                             base+0x2fbfa0:struct.pack('<I',0xd503237f)}
            original=original_fields.get(address,bytes(len(data)))
            if actual==original:
                # CID2181 captured32 zero bytes at665ce0 after RESET. That
                # resolves ambiguity: no part of this write was applied. Own
                # padding/CAM originals were guarded before installation.
                self.note('KD_PLATFORM_WRITE_PROVEN_NOT_APPLIED',address=hex(address),bytes=len(data),
                          evidence='Exact original target bytes read back; one fresh write in synchronized epoch')
                response=self.rpc(0x3131,struct.pack('<QII',address,len(data),0),extra=data)
                assert struct.unpack_from('<I',response,28)[0]==len(data), 'Recovered short platform write'
                assert self.read(address,len(data))==data, 'Fresh platform write readback'
                self.note('KD_PLATFORM_FRESH_WRITE_VERIFIED',address=hex(address),bytes=len(data))
                return
            self.note('KD_PLATFORM_WRITE_NOT_CONFIRMED',address=hex(address),bytes=len(data))
            raise TimeoutError('Platform write readback differs; fail closed, do not replay')
        self.note('KD_PLATFORM_WRITE_CONFIRMED_BY_READBACK',address=hex(address),bytes=len(data),
                  evidence='Actual target memory matches requested bytes; original WRITE response remains missing')

    def write_memory(self,address,data):
        # Retained wire evidence: the244-byte adapter write (300-byte payload)
        # gets repeated target RESEND while small RAM writes succeed. Bound host
        # request payloads, keep exact bytes/order, verify every segment; no pacing
        # flags or UART configuration changes, no ambiguous write replay.
        for offset in range(0,len(data),32):
            part=data[offset:offset+32]
            try:
                body=self.rpc(0x3131,struct.pack('<QII',address+offset,len(part),0),extra=part)
                assert struct.unpack_from('<I',body,28)[0]==len(part), 'Short KD RAM write'
            except TimeoutError:
                self.confirm_timed_out_platform_write(address+offset,part)
            assert self.read(address+offset,len(part))==part, 'KD RAM segment verification failed'
        assert self.read(address,len(data))==data, 'KD RAM write verification failed'
    def restore_context(self,data):
        assert len(data)==912, 'Exact ARM64 context size required'
        old=getattr(self,'last_context',b'')
        if (base is not None and len(old)==912 and data[:264]==old[:264] and data[272:]==old[272:] and
                struct.unpack_from('<Q',old,264)[0]==base+0x201370 and
                struct.unpack_from('<Q',data,264)[0]==base+0x201374):
            # CID2183: full968-byte SetContext timed out AFTER inventory and
            # left the guest in the debugger. This known break changes ONLY PC.
            # Exact kernel KdpSetContextEx RVA4f8240: field+18 is INPUT total
            # context size, not zero output BytesCopied. Commit occurs only
            # when offset+count==total. Stage the guarded original context
            # (only PC differs) in bounded32-byte packets; final chunk commits.
            # No ambiguous mutation retry; all GPR/flags/SP bytes unchanged.
            for offset in range(0,912,32):
                part=data[offset:offset+32]
                response=self.rpc(0x3160,struct.pack('<III',offset,len(part),912),extra=part)
                assert struct.unpack_from('<I',response,24)[0]==len(part), 'Exact staged context copy'
            readback=self.rpc(0x315f,struct.pack('<III',0,912,0))
            assert len(readback)==968 and readback[56:]==data, 'Complete unchanged-register/PC readback'
            self.note('KD_RUNTIME_BREAK_PC_ONLY_VERIFIED',pc=hex(base+0x201374),
                      scope='Standard staged32-byte SetContextEx; PC-only semantic change, full readback, no mutation retry')
            return
        self.rpc(0x3133,struct.pack('<I',struct.unpack_from('<I',data)[0]),extra=data)
    def physical_sample(self,address):
        body=self.rpc(0x313d,struct.pack('<QII',address,4,0),allow_status=True)
        status=struct.unpack_from('<I',body,8)[0]
        return dict(address=hex(address),status=hex(status),data=body[56:].hex())
    def resume(self):self.request(0x3136,struct.pack('<I',0x10002))
    def pass_first_chance(self):self.request(0x3136,struct.pack('<I',0x80010001)) # DBG_EXCEPTION_NOT_HANDLED
    def recover_boot_unload_epoch(self):
        state=getattr(self,'last_state_change',b'')
        sent=self.last_tx or b''
        if (getattr(self,'boot_unload_epoch_recovered',False) or len(state)<80 or
                struct.unpack_from('<I',state)[0]!=0x3031 or not state[64] or
                struct.unpack_from('<Q',state,40)[0]!=0xffffffffffffffff or
                len(sent)<37 or sent[:4]!=b'0000' or
                struct.unpack_from('<H',sent,4)[0]!=2 or
                struct.unpack_from('<I',sent,16)[0]!=0x3136):return False
        # CID2176: silent winload unload was still a debugger exchange. A
        # standard KD transport RESET, not another boot/platform experiment,
        # immediately restored kernel entry in the SAME VM. Once per boot,
        # only at this exact unload+CONTINUE boundary; no command reissued.
        self.boot_unload_epoch_recovered=True
        self.note('KD_BOOT_UNLOAD_EPOCH_RECOVERY',scope='Transport RESET only; exact loader unload, no memory/CPU/disk mutation')
        self.control(6)
        self.pid=0x80800000;self.last_rx=None;self.last_tx=None
        self.pending_tx=None;self.last_ack=None
        return True
    def recover_dump_unload_continue_epoch(self):
        state=getattr(self,'last_state_change',b'')
        pending=self.pending_tx
        if (getattr(self,'dump_unload_epoch_recovered',False) or not pending or
                pending.get('target_resends',0)<2 or pending['kind']!=2 or
                len(state)<80 or struct.unpack_from('<I',state)[0]!=0x3031 or not state[64]):return False
        length=struct.unpack_from('<I',state,32)[0]
        path=state[-length:].decode('ascii','replace').rstrip('\0') if 0<length<=len(state)-80 else ''
        sent=pending['packet']
        if (path.lower() not in ('dump_dumpfve.sys','dump_viostor.sys','dump_storport.sys') or
                len(sent)!=73 or sent[:4]!=b'0000' or
                struct.unpack_from('<H',sent,4)[0]!=2 or
                struct.unpack_from('<I',sent,16)[0]!=0x3136):return False
        # CID2199 AND original-EFI CID2200: two target RESENDs after a
        # validated dump-driver unload and the same outstanding CONTINUE.
        # Reset only the wire epoch; never replay a command under a new ID.
        # A fresh target state is required before the normal handler resumes.
        self.dump_unload_epoch_recovered=True
        self.note('KD_DUMP_UNLOAD_EPOCH_RECOVERY',path=path,
                  scope='One transport RESET after captured target RESEND; no memory/context/CPU/disk write')
        self.control(6)
        self.pid=0x80800000;self.last_rx=None;self.last_tx=None
        self.pending_tx=None;self.last_ack=None
        return True
    def cleanup(self):
        if self.socket:
            try:self.socket.shutdown(socket.SHUT_RDWR)
            except OSError:pass
            self.socket.close();self.rx.close();self.tx.close();self.events.close()
            return # Diagnostic owner observes EOF and stops only its own VM.
        # Only this task's shell-owned diagnostic requester. No app VM operations.
        listing=subprocess.check_output([a.adb,'shell','/apex/com.android.virt/bin/vm list'],text=True)
        for block in listing.split('VirtualMachineDebugInfo {')[1:]:
            if 'requesterUid: 2000' not in block:continue
            match=re.search(r'requesterPid:\s*(\d+)',block)
            if not match:continue
            pid=int(match.group(1))
            cmd=subprocess.check_output([a.adb,'shell',f'cat /proc/{pid}/cmdline'])
            if b'/data/local/tmp/uavf-windows-uart-gas-20261004/vm-config.json' in cmd:
                subprocess.run([a.adb,'shell',f'kill -TERM {pid}'],check=False)
        try:self.proc.stdin.close();self.proc.wait(timeout=10)
        except (BrokenPipeError,subprocess.TimeoutExpired):self.proc.kill();self.proc.wait()
        self.rx.close();self.tx.close();self.events.close()

kd=KD(); probes={}; base=None; installed=False; last_driver=None; insert_partition=None; delay_returns=0; clock_samples={}; wait_thread=None; wait_map=None; gic_context=None; register_sample=None; runtime_mapping=None; psci_adapted=[]; smp_break_sent=False
boot_snapshot=None
loader_observed=False
unmask_probe=None
pmu_sample=None
pmu_cycle_probe=None
unmask_done=False
terminate_hits=0
def probe(rva,tag,module_base=None,data=kernel,layout=sections,expected_override=None):
    addr=(base if module_base is None else module_base)+rva
    if tag.startswith('SMP_'):
        assert all(abs(addr-other)>=32 for other in probes), 'No overlapping SMP debugger instruction-window probes'
    expected=image_bytes(rva,4,data,layout) if expected_override is None else expected_override
    assert len(expected)==4
    assert kd.read(addr,4)==expected, 'Instruction guard failed '+tag
    result=kd.rpc(0x3134,struct.pack('<QI',addr,0))
    handle=struct.unpack_from('<I',result,24)[0]
    assert handle,'No breakpoint handle'
    probes[addr]=(handle,tag,expected)
    kd.note('PROBE_SET',tag_name=tag,address=hex(addr),handle=handle)

def smp_gic_mapped_snapshot():
    """Exact native HalpGic3 metadata; no IRQ acknowledgements or writes."""
    assert image_bytes(0x4aefbc,8)==struct.pack('<2I',0x90004928,0xf9053513), 'Exact native HalpGic3 pointer publication'
    context=struct.unpack('<Q',kd.read(base+0xdd2a68,8))[0]
    assert context>>48==0xffff and context%8==0, 'Canonical native GIC context'
    metadata=kd.read(context,0x90+8*0x48)
    count=struct.unpack_from('<I',metadata,0x48)[0]
    assert count==8, 'Exact native eight CPU GIC metadata'
    distributor=struct.unpack_from('<Q',metadata,8)[0]
    assert distributor>>48==0xffff and distributor%4096==0
    def sample(address):
        body=kd.rpc(0x3130,struct.pack('<QII',address,4,0),allow_status=True)
        return dict(address=hex(address),status=hex(struct.unpack_from('<I',body,8)[0]),data=body[56:].hex())
    cpus=[]
    for cpu in range(8):
        offset=(cpu+2)*0x48
        mpidr=struct.unpack_from('<Q',metadata,offset)[0]
        physical,mapping=struct.unpack_from('<2Q',metadata,offset+0x18)
        assert 0x3fef0000<=physical<0x3fff0000 and (physical-0x3fef0000)%0x20000==0 and mapping>>48==0xffff and mapping%4096==0, 'Bounded eight CPU redistributor mapping'
        cpus.append(dict(cpu=cpu,mpidr=hex(mpidr),physical=hex(physical),mapping=hex(mapping),samples=[sample(mapping+offset) for offset in (0x14,0x10080,0x10100,0x10200,0x10300,0x10400)]))
    assert len({cpu['physical'] for cpu in cpus})==8, 'Eight unique native redistributor mappings'
    return dict(context=hex(context),count=count,distributor=sample(distributor),cpus=cpus)

def smp_explicit_ipi_ab():
    # Native type4 optimization tests bit5. Clearing it selects unchanged
    # explicit all-minus-self affinity; no IPI target/wait/barrier is skipped.
    assert a.psci_explicit_ipi_ab and kd.processor==0
    assert image_bytes(0x484d98,8)==struct.pack('<2I',0xb940f509,0x362800c9), 'Exact all-others capability test'
    assert image_bytes(0x484dd0,16)==struct.pack('<4I',0x794167e1,0xb0004c28,0x91028102,0x9102c3e0), 'Exact native explicit affinity alternative'
    controller=struct.unpack('<Q',kd.read(base+0xc100e0,8))[0]
    assert controller>>48==0xffff and controller%8==0
    flags=struct.unpack('<I',kd.read(controller+0xf4,4))[0]
    kd.note('SMP_IPI_CAPABILITY_BEFORE',controller=hex(controller),flags=hex(flags),scope='Read-only native controller capability')
    assert flags==0x82b, 'Only exact registered GIC capability set; no unknown flag mutation'
    gic=smp_gic_mapped_snapshot()
    assert {int(cpu['mpidr'],16) for cpu in gic['cpus']}==set(range(8)), 'Observed native virtual affinities0..7'
    replacement=struct.pack('<I',flags&~0x20)
    kd.write_memory(controller+0xf4,replacement)
    assert kd.read(controller+0xf4,4)==replacement, 'Exact one-bit runtime capability readback'
    kd.note('SMP_EXPLICIT_IPI_CAPABILITY_AB',before=hex(flags),after=hex(flags&~0x20),gic=gic,scope='One transient capability bit only; unchanged native explicit affinity, no timer/CPU/count/barrier writes or signed file edits')

def producer_termination(thread,registers):
    # Exact kernel exported accessors plus matching PDB KTHREAD/KTRAP_FRAME.
    assert image_bytes(0x248000,4)==struct.pack('<I',0xf9412000)
    assert image_bytes(0x2f3f90,4)==struct.pack('<I',0x910ca000)
    assert thread>>48==0xffff and thread%16==0
    process=struct.unpack('<Q',kd.read(thread+0x240,8))[0]
    assert process>>48==0xffff
    name=kd.read(process+0x328,15).split(b'\0')[0].decode('ascii','replace')
    own=name=='winpe-vsock.exe'
    kd.note('PROCESS_TERMINATE_CALL',image=name,process=hex(process),thread=hex(thread),handle=hex(registers[0]),status=hex(registers[1]&0xffffffff),own_producer=own)
    if not own:return False
    try:
        trap=struct.unpack('<Q',kd.read(thread+0x88,8))[0]
        assert trap>>48==0xffff and trap%16==0
        raw=kd.read(trap,336)
        sp=struct.unpack_from('<Q',raw,152)[0]
        lr,fp,pc=struct.unpack_from('<QQQ',raw,312)
        assert 0<sp<0x800000000000 and 0<pc<0x800000000000
        stack=kd.read(sp,512)
        peb=struct.unpack('<Q',kd.read(process+0x2d0,8))[0]
        assert 0<peb<0x800000000000
        loader=struct.unpack('<Q',kd.read(peb+0x18,8))[0]
        assert 0<loader<0x800000000000
        head=loader+0x10;node=struct.unpack('<Q',kd.read(head,8))[0]
        modules=[];seen=set()
        while node!=head:
            assert 0<node<0x800000000000 and node not in seen and len(seen)<64
            seen.add(node);entry=kd.read(node,0x68)
            address=struct.unpack_from('<Q',entry,0x30)[0];size=struct.unpack_from('<I',entry,0x40)[0]
            length=struct.unpack_from('<H',entry,0x58)[0];text=struct.unpack_from('<Q',entry,0x60)[0]
            assert length<=512 and length%2==0 and 0<text<0x800000000000
            modules.append(dict(base=hex(address),size=size,name=kd.read(text,length).decode('utf-16-le','replace')))
            node=struct.unpack_from('<Q',entry,0)[0]
        kd.note('OWN_PRODUCER_TERMINATE_CONTEXT',pc=hex(pc),lr=hex(lr),fp=hex(fp),sp=hex(sp),trap_frame=raw.hex(),stack_words=[hex(x) for x in struct.unpack('<64Q',stack)],modules=modules,scope='Read only original user caller; no status/register/code changes')
    except Exception as error:kd.note('OWN_PRODUCER_TERMINATE_CONTEXT_PARTIAL',error=str(error))
    return True

def install_periodic_workaround():
    cave=0x665b00; source=0x213968; target=0x213910
    text=next(section for section in sections if section[1]==0x200000)
    virtual_end=text[1]+text[0]
    assert virtual_end<=cave and cave+24<=((virtual_end+4095)&~4095), 'Not executable last-page section padding'
    assert image_bytes(cave,24)==bytes(24) and kd.read(base+cave,24)==bytes(24), 'Padding guard failed'
    assert image_bytes(source,4)==struct.pack('<I',0x17ffffea) and kd.read(base+source,4)==image_bytes(source,4), 'Periodic ACK branch guard failed'
    def branch(start,end):
        delta=end-start
        assert delta%4==0 and -(1<<27)<=delta<(1<<27)
        return 0x14000000|((delta//4)&0x3ffffff)
    thunk=struct.pack('<6I',0xd53be330,0x36000090,0x927efa10,0xd51be330,0xd5033fdf,branch(cave+20,target))
    kd.write_memory(base+cave,thunk)
    kd.write_memory(base+source,struct.pack('<I',branch(source,cave)))
    kd.note('PERIODIC_IMASK_RAM_WORKAROUND',source=hex(base+source),cave=hex(base+cave),original_branch='17ffffea',thunk=thunk.hex(),scope='Disposable RAM only; enabled CNTV periodic ACK; no image change')

def kernel_clock_metadata():
    # Public KUSER_SHARED_DATA only: offsets independently visible in the
    # exact ntdll RtlGetSystemTimePrecise/QueryPerformanceCounter implementation.
    # No process memory, credentials, register dump or hardware state mutation.
    shared=0xfffff78000000000
    def value(offset,size=8):return int.from_bytes(kd.read(shared+offset,size),'little')
    def timestamp(offset):
        low,high1,high2=struct.unpack('<III',kd.read(shared+offset,12))
        assert high1==high2,'Coherent stopped shared timestamp'
        return (high1<<32)|low
    before=value(0x340)
    result=dict(system_time_100ns=timestamp(0x14),interrupt_time_100ns=timestamp(8),
                qpc_frequency=value(0x300),baseline_system_time_qpc=value(0x348),
                qpc_system_time_increment=value(0x358),qpc_system_time_increment_shift=value(0x368,1),
                qpc_bypass_flags=value(0x3c6,1),qpc_bias=value(0x3b8))
    after=value(0x340)
    result.update(time_update_lock_before=before,time_update_lock_after=after,
                  projection_fields_coherent=before==after and not(before&1),
                  scope='Public kernel clock metadata only; no inference from incomplete seqlock update')
    return result

def hal_clock_baseline_metadata():
    # Exact original HAL code, fixed kernel clock data only. No pointer chasing
    # into processes, EFI code patching, or mutation of clock state.
    guards=((0x48a804,(0xf0004bc8,0x91058115)),
            (0x48a80c,(0xf94002b7,0x910043e0,0xf840c2b6)),
            (0x48a820,(0xf9400ea8,)),
            (0x47c82c,(0x58000468,0xf94007e9,0xf940010a,0x58000448)))
    for rva,words in guards:
        expected=struct.pack('<'+'I'*len(words),*words)
        assert image_bytes(rva,len(expected))==expected, 'Exact HAL static layout'
        assert kd.read(base+rva,len(expected))==expected, 'Exact HAL runtime layout'
    assert struct.unpack('<QQ',image_bytes(0x47c8b8,16))==(0xfffff78000000014,864000000000)
    block=kd.read(base+0xe05160,40)
    begin=struct.unpack_from('<Q',block,0)[0]
    end=struct.unpack_from('<Q',block,24)[0]
    return dict(reference_qpc=begin,reference_calendar_100ns=struct.unpack_from('<Q',block,12)[0],
                reference_qpc_copy=end,reference_coherent=begin==end,
                rtc_mode=struct.unpack('<I',kd.read(base+0xe04d88,4))[0],
                rtc_error_flags=struct.unpack('<I',kd.read(base+0xe062e0,4))[0],
                floor_source='Current KUSER SystemTime; replaces RTC only when more than one day ahead',
                scope='Fixed HAL clock metadata; no proof of which branch executed')

def hal_clock_seed_evidence(ctx):
    # First reference writer only, after its software probe has been removed.
    assert ctx['pc']==base+0x48a8c0 and ctx['cpsr']&15 in (4,5), 'Exact EL1 HAL writer'
    expected=bytes.fromhex('7f2303d5f353bfa9')
    assert image_bytes(0x48a8c0,8)==expected and kd.read(ctx['pc'],8)==expected
    caller=ctx['x'][30]-base
    assert caller in (0x47a9b4,0x47c79c,0x47c974,0x49c5a0), 'Known exact static HAL caller only'
    arg=ctx['x'][0]
    if arg:
        assert arg>>48==0xffff and arg%8==0, 'Typed kernel calendar pointer only'
        value=struct.unpack('<Q',kd.read(arg,8))[0]
        kind='explicit_calendar_argument'
    else:
        low,high1,high2=struct.unpack('<III',kd.read(0xfffff78000000014,12))
        assert high1==high2, 'Coherent current kernel calendar'
        value=(high1<<32)|low
        kind='null_argument_current_system_time_fallback'
    return dict(caller_rva=hex(caller),source=kind,calendar_100ns=value,
                scope='First writer input only; no stack/process data or clock mutation')

def boot_clock_delta_evidence(ctx):
    assert ctx['pc']==base+0xb17f78 and ctx['cpsr']&15 in (4,5), 'Exact EL1 boot clock subtraction'
    expected=struct.pack('<4I',0xf9452ac8,0x910043e2,0x58000aa1,0xcb08000b)
    assert image_bytes(0xb17f6c,16)==expected and kd.read(base+0xb17f6c,16)==expected
    assert image_bytes(0xb17f14,4)==struct.pack('<I',0xf94526d7)
    x=ctx['x'];pointer=x[22]
    assert pointer>>48==0xffff and pointer%8==0 and ctx['sp']>>48==0xffff and ctx['sp']%16==0
    initial_calendar,saved_counter=struct.unpack('<QQ',kd.read(pointer+0xa48,16))
    assert saved_counter==x[8] and x[1]==10000000, 'Typed loader counter/scale matches exact operands'
    frequency=struct.unpack('<Q',kd.read(ctx['sp']+0x18,8))[0]
    assert 0<frequency<10**12, 'Actual returned counter frequency'
    current=x[0];delta=(current-saved_counter)&((1<<64)-1)
    return dict(loader_calendar_100ns=initial_calendar,adjusted_calendar_100ns=x[23],
                loader_counter=saved_counter,current_counter=current,counter_frequency=frequency,
                counter_went_backwards=current<saved_counter,unsigned_delta=delta,
                predicted_calendar_100ns=(x[23]+delta*10000000//frequency)&((1<<64)-1),
                scope='One exact boot arithmetic site; typed loader/clock operands only; no clock mutation')

def correct_invalid_boot_calendar(ctx,target):
    # A/B only: one typed LOADER_PARAMETER_EXTENSION calendar scalar, before
    # its original LDR executes. Never write signed code, disk, counters or RTC.
    assert ctx['pc']==base+0xb17f14 and ctx['cpsr']&15 in (4,5)
    expected=struct.pack('<3I',0xf94526d7,0xaa0103fb,0xf94532c8)
    assert image_bytes(0xb17f14,12)==expected and kd.read(ctx['pc'],12)==expected
    pointer=ctx['x'][22]
    assert pointer>>48==0xffff and pointer%8==0
    lower=132223104000000000 # 2020-01-01 UTC
    upper=157469184000000000 # 2100-01-01 UTC
    assert lower<=target<upper, 'Trusted contemporary host calendar only'
    address=pointer+0xa48
    original=kd.read(address,8)
    value=struct.unpack('<Q',original)[0]
    if lower<=value<upper:
        return dict(applied=False,original_100ns=value,reason='Already plausible; no mutation')
    replacement=struct.pack('<Q',target)
    kd.write_memory(address,replacement)
    assert kd.read(address,8)==replacement, 'Exact 8-byte loader-calendar readback'
    return dict(applied=True,original_100ns=value,corrected_100ns=target,
                scope='Temporary isolated loader RAM scalar only; no counter/RTC/disk/userland modification')

def process_inventory():
    # PID/Image offsets independently verified in exact exported accessor code.
    assert image_bytes(0x2f3f80,4)==struct.pack('<I',0xf940e000)
    assert image_bytes(0x2f3f90,4)==struct.pack('<I',0x910ca000)
    head=base+0xd7ac70
    current=struct.unpack('<Q',kd.read(head,8))[0];previous=head;seen=set();items=[]
    while current!=head:
        assert current>>48==0xffff and current not in seen and len(seen)<256, 'Invalid/bounded process chain'
        seen.add(current)
        process=current-0x1c8
        data=kd.read(process,0x630)
        following,back=struct.unpack_from('<QQ',data,0x1c8)
        assert back==previous, 'Process chain reciprocity guard'
        name=data[0x328:0x337].split(b'\0')[0].decode('ascii','replace')
        item=dict(address=hex(process),pid=struct.unpack_from('<Q',data,0x1c0)[0],image=name,active_threads=struct.unpack_from('<I',data,0x370)[0],exit_status=hex(struct.unpack_from('<I',data,0x614)[0]),peb=hex(struct.unpack_from('<Q',data,0x2d0)[0]))
        pointer=struct.unpack_from('<Q',data,0x340)[0]
        if pointer>>48==0xffff:
            desc=kd.read(pointer,16);length=struct.unpack_from('<H',desc)[0];buffer=struct.unpack_from('<Q',desc,8)[0]
            if 0<length<=1024 and length%2==0 and buffer>>48==0xffff:item['path']=kd.read(buffer,length).decode('utf-16-le','replace')
        items.append(item);previous=current;current=following
    assert struct.unpack('<Q',kd.read(head+8,8))[0]==previous, 'Process list tail guard'
    kd.note('ACTIVE_PROCESS_INVENTORY',processes=items)
    return items

def hot_wait_timeout(thread,modules,stopped_thread):
    # A narrowly typed timeout value, not a process memory/stack dump. Exact
    # ntdll syscall/caller/code identity is required BEFORE any argument read.
    import hashlib
    if int(thread['thread'],16)!=stopped_thread or thread.get('state')!=2:
        return dict(available=False,reason='Hot thread is not the currently stopped running thread')
    if 'saved_pc' not in thread or 'saved_lr' not in thread or 'instruction_window' not in thread:
        return dict(available=False,reason='Verified syscall code context unavailable')
    recognized=[]
    for name,pc_rva,lr_rva,digest in (
        ('ntdll.dll',0x2e74,0x6c208,'6fe3b5b4bb7d6e4f2a88e1167bdf1baeb508292aa8ed09c1d82fca0a33b58861'),
        ('kernelbase.dll',0x1c7250,0x10cb0c,'866361969389dc7d19ec04e7e359117cb9e4a7f160fc77d355a98e307a57f142')):
        matches=[m for m in modules if m['name'].split('\\')[-1].lower()==name]
        if len(matches)==1:
            address=int(matches[0]['base'],16)
            if int(thread['saved_pc'],16)-address==pc_rva and int(thread['saved_lr'],16)-address==lr_rva:
                recognized.append((name,pc_rva,digest))
    if len(recognized)!=1:return dict(available=False,reason='Not an exact observed condition-variable syscall/result site')
    name,pc_rva,digest=recognized[0]
    source=Path(__file__).resolve().parents[3]/('build-logs/windows-post-linux-reassessment-20261004/lsass-source-code-20261010/'+name)
    data=source.read_bytes()
    assert hashlib.sha256(data).hexdigest()==digest, 'Original signed module identity'
    pe=struct.unpack_from('<I',data,60)[0]
    count=struct.unpack_from('<H',data,pe+6)[0];optional=struct.unpack_from('<H',data,pe+20)[0]
    layout=[struct.unpack_from('<IIII',data,pe+24+optional+i*40+8) for i in range(count)]
    expected=image_bytes(pc_rva-16,48,data,layout)
    assert thread['instruction_window']==expected.hex(), 'Exact original syscall code window'
    trap=struct.unpack('<Q',kd.read(stopped_thread+0x88,8))[0]
    assert trap>>48==0xffff and trap%16==0, 'Exact kernel trap frame'
    cpsr=struct.unpack('<I',kd.read(trap+0x90,4))[0]
    assert cpsr&15==0, 'Saved wait must originate at EL0'
    # Exact ARM64 KTRAP_FRAME: Sp=0x98; X0..X17=0xa0 onward.
    # Read ONLY X1 (PLARGE_INTEGER Timeout), never log registers or stack bytes.
    status=None;caller=None
    if name=='kernelbase.dll':
        # Actual second hot snapshot is BaseSetLastNTError+28, after MOV W0,W19
        # and both SP restores, returning to SleepConditionVariableSRW+3c.
        # Original signed wrapper constructs its one LARGE_INTEGER at SP+10.
        # ONLY that typed8-byte value and typed4-byte returned Win32 error are read.
        # BaseSetLastNTError converts NTSTATUS via RtlNtStatusToDosError before
        # saving W19; W0 here is NOT the caller's original NTSTATUS.
        sp=struct.unpack('<Q',kd.read(trap+0x98,8))[0]
        assert 0<sp<0x7fffffff0000 and sp%16==0, 'Exact restored wrapper stack pointer'
        timeout=struct.unpack('<q',kd.read(sp+0x10,8))[0]
        status=struct.unpack('<I',kd.read(trap+0xa0,4))[0]
        # One code-only return address saved by the exact wrapper STP FP,LR.
        caller=struct.unpack('<Q',kd.read(sp+8,8))[0]
    else:
        timeout_pointer=struct.unpack('<Q',kd.read(trap+0xa8,8))[0]
        timeout=None
        if timeout_pointer:
            sp=struct.unpack('<Q',kd.read(trap+0x98,8))[0]
            if not (0<sp<0x800000000000 and timeout_pointer%8==0 and sp<=timeout_pointer<=sp+65528):
                return dict(available=False,reason='Timeout is outside the bounded current stack range; no user read')
            timeout=struct.unpack('<q',kd.read(timeout_pointer,8))[0]
    raw=kd.read(stopped_thread+0xf8,0x1b0)
    def shared_time(offset):
        low,high1,high2=struct.unpack('<III',kd.read(0xfffff78000000000+offset,12))
        assert high1==high2, 'Stable stopped shared-time snapshot'
        return (high1<<32)|low
    result=dict(available=True,site=name,timeout_100ns=timeout,
                timeout_kind='infinite' if timeout is None else 'relative' if timeout<0 else 'immediate' if timeout==0 else 'absolute',
                timer_inserted=bool(raw[3]&0x40),timer_expired=bool(raw[3]&0x80),
                timer_due_100ns=struct.unpack_from('<Q',raw,0x18)[0],
                wait_reason=raw[0x2a3-0xf8],interrupt_time_100ns=shared_time(8),system_time_100ns=shared_time(0x14),
                scope='Typed timeout/status and one code pointer only; no credentials, general registers/stack dump, writes or patches')
    if status is not None:
        result['return_win32_error']=status
        if timeout==0 and status!=1460:result['timeout_kind']='zero-wrapper-slot: immediate/infinite not distinguished'
    if caller is not None and 16<=caller<0x800000000000:result['caller_pc']=hex(caller)
    if timeout and timeout>0:result['absolute_remaining_100ns']=timeout-result['system_time_100ns']
    return result

def hot_process_inventory(stopped_thread):
    # Exact ntkrnlmp PDB: EPROCESS.ThreadListHead=0x360,
    # ETHREAD.ThreadListEntry=0x558/Cid=0x4e8; KTHREAD.TrapFrame=0x88,
    # KernelTime=0x2ac/UserTime=0x2fc. Trap frame Pc=0x148/Lr=0x138.
    # Only instruction addresses/counters/module names: never credentials,
    # general registers, user stacks or LSASS data/dumps.
    items=process_inventory()
    matches=[item for item in items if item['image']==a.inspect_hot_process]
    assert len(matches)==1, 'Exact hot-process identity'
    process=int(matches[0]['address'],16)
    assert stopped_thread>>48==0xffff and stopped_thread%16==0
    current_process=None
    try:current_process=struct.unpack('<Q',kd.read(stopped_thread+0x240,8))[0]
    except ValueError as error:
        # CID2177 process inventory was valid, but the stopped idle-thread
        # owner field was unavailable. Keep bounded kernel metadata useful;
        # UNKNOWN address space must NEVER permit LSASS user code/PEB reads.
        if str(error)!='KD status 0xc0000001 for 0x3130':raise
        kd.note('HOT_PROCESS_CURRENT_OWNER_UNAVAILABLE',scope='Kernel metadata only; all user address-space reads disabled')
    head=process+0x360
    node=struct.unpack('<Q',kd.read(head,8))[0];previous=head;seen=set();threads=[]
    while node!=head:
        assert node>>48==0xffff and node not in seen and len(seen)<64, 'Bounded reciprocal thread chain'
        seen.add(node);thread=node-0x558
        raw=kd.read(thread,0x568)
        following,back=struct.unpack_from('<QQ',raw,0x558)
        assert back==previous and struct.unpack_from('<Q',raw,0x240)[0]==process, 'Thread owner/list reciprocity'
        pid,tid=struct.unpack_from('<QQ',raw,0x4e8)
        assert pid==matches[0]['pid'], 'Native thread PID matches inventory'
        item=dict(thread=hex(thread),tid=tid,state=raw[0x17c],
                  kernel_ticks=struct.unpack_from('<I',raw,0x2ac)[0],
                  user_ticks=struct.unpack_from('<I',raw,0x2fc)[0])
        trap=struct.unpack_from('<Q',raw,0x88)[0]
        if trap>>48==0xffff and trap%16==0:
            try:
                saved=kd.read(trap+0x138,24)
                lr,fp,pc=struct.unpack('<QQQ',saved)
                item.update(saved_pc=hex(pc),saved_lr=hex(lr))
            except ValueError as error:
                if str(error)!='KD status 0xc0000001 for 0x3130':raise
                item['saved_context_unavailable']='Kernel stack not readable; retain counters, never infer zero PC'
            # Read code only in the verified current process address space.
            if 'saved_pc' in item and current_process==process and 16<=pc<0x800000000000:
                try:item['instruction_window']=kd.read(pc-16,48).hex()
                except Exception as error:item['code_unavailable']=str(error)
        threads.append(item);previous=node;node=following
    assert struct.unpack('<Q',kd.read(head+8,8))[0]==previous, 'Thread list tail'
    modules=[]
    if current_process==process:
        try:
            peb=int(matches[0]['peb'],16)
            assert 0<peb<0x800000000000
            loader=struct.unpack('<Q',kd.read(peb+0x18,8))[0]
            assert 0<loader<0x800000000000
            module_head=loader+0x10;node=struct.unpack('<Q',kd.read(module_head,8))[0];seen=set()
            while node!=module_head:
                assert 0<node<0x800000000000 and node not in seen and len(seen)<128
                seen.add(node);entry=kd.read(node,0x68)
                address=struct.unpack_from('<Q',entry,0x30)[0];size=struct.unpack_from('<I',entry,0x40)[0]
                length=struct.unpack_from('<H',entry,0x58)[0];text=struct.unpack_from('<Q',entry,0x60)[0]
                assert length<=512 and length%2==0 and 0<text<0x800000000000
                modules.append(dict(base=hex(address),size=size,name=kd.read(text,length).decode('utf-16-le','replace')))
                node=struct.unpack_from('<Q',entry,0)[0]
        except Exception as error:kd.note('HOT_PROCESS_MODULES_PARTIAL',error=str(error))
    kd.note('HOT_PROCESS_CODE_INVENTORY',image=a.inspect_hot_process,pid=matches[0]['pid'],
            current_process_matches=current_process==process,
            threads=sorted(threads,key=lambda item:item['user_ticks']+item['kernel_ticks'],reverse=True),
            modules=modules,scope='Read-only saved PC/LR/code/counters/module metadata; no user stacks, credentials or process mutation')
    if getattr(a,'inspect_wait_timeout',False):
        selected=max(threads,key=lambda item:item['user_ticks']+item['kernel_ticks'])
        kd.hot_wait_target=(matches[0]['pid'],selected['tid'],int(selected['thread'],16))
        if current_process!=process:
            kd.note('HOT_WAIT_TIMEOUT',available=False,reason='Current address space not verified; no user read')
        else:
            kd.note('HOT_WAIT_TIMEOUT',pid=matches[0]['pid'],tid=selected['tid'],**hot_wait_timeout(selected,modules,stopped_thread))

def kernel_wait_return_evidence(stopped_thread,context,target):
    # Exact public PDB NtWaitForAlertByThreadId; user timeout copied into the
    # ONE typed kernel LARGE_INTEGER at SP+10. No user stack/register dump.
    assert context['pc']==base+0x8f7d74 and context['cpsr']&15 in (4,5), 'Exact EL1t/EL1h return site'
    assert image_bytes(0x8f7d74,12)==struct.pack('<III',0xa8c27bfd,0xd50323ff,0xd65f03c0), 'Original wait return code'
    assert stopped_thread>>48==0xffff and stopped_thread%16==0, 'Kernel thread identity'
    process=struct.unpack('<Q',kd.read(stopped_thread+0x240,8))[0]
    assert process>>48==0xffff, 'Kernel process metadata'
    name=kd.read(process+0x328,15).split(b'\0')[0].decode('ascii','replace')
    pid,tid=struct.unpack('<QQ',kd.read(stopped_thread+0x4e8,16))
    # Global kernel time is not process/user data, so preserve it even when
    # the first one-shot return belongs to another thread. Never read that
    # thread's return value or typed stack slot.
    low,high1,high2=struct.unpack('<III',kd.read(0xfffff78000000014,12))
    assert high1==high2, 'Coherent stopped system time'
    system_time=(high1<<32)|low
    if name!='lsass.exe' or (pid,tid,stopped_thread)!=target:
        return dict(available=False,reason='First wait return belongs to another thread; global time only, no stack/return-value read',image=name,pid=pid,tid=tid,system_time_100ns=system_time)
    sp=context['sp']
    assert sp>>48==0xffff and sp%16==0, 'Aligned kernel return stack'
    # Exact original exported accessors independently prove both offsets.
    assert image_bytes(0x52f104,4)==struct.pack('<I',0xf9401d00), 'StackBase accessor'
    assert image_bytes(0x52f114,8)==struct.pack('<II',0x9100c108,0xf8bfc100), 'StackLimit accessor'
    limit,top=struct.unpack('<QQ',kd.read(stopped_thread+0x30,16))
    assert limit<=sp and sp+0x20<=top and top-limit<=1024*1024, 'Bounded typed kernel slot'
    timeout=struct.unpack('<q',kd.read(sp+0x10,8))[0]
    status=context['x'][0]&0xffffffff
    return dict(available=True,pid=pid,tid=tid,return_ntstatus=hex(status),timeout_100ns=timeout,
                system_time_100ns=system_time,
                timeout_kind='relative' if timeout<0 else 'absolute' if timeout>0 else 'zero: null/immediate not distinguished',
                scope='One exact typed kernel timeout and returned status; no credentials/user data or wait mutation')
def driver_name(pointer):
    desc=kd.read(pointer+0x38,16)
    size=struct.unpack_from('<H',desc)[0];buf=struct.unpack_from('<Q',desc,8)[0]
    if not size or size>1024:return '<invalid name>'
    return kd.read(buf,size).decode('utf-16-le','replace')

def device_inventory():
    # Public PDB section 24 + 1898760, exact kernel PE section layout.
    pointer=struct.unpack('<Q',kd.read(base+0xc00000+1898760,8))[0]
    seen=set();items=[];pending=[(pointer,0)]
    def text(data,offset):
        length=struct.unpack_from('<H',data,offset)[0];buf=struct.unpack_from('<Q',data,offset+8)[0]
        if not length:return ''
        assert length<=1024 and length%2==0 and buf>>48==0xffff
        return kd.read(buf,length).decode('utf-16-le','replace')
    while pending:
        node,parent=pending.pop()
        if not node:continue
        assert node>>48==0xffff and node not in seen and len(seen)<128
        seen.add(node);data=kd.read(node,0x1a0)
        sibling,child,actual_parent=struct.unpack_from('<QQQ',data)
        assert actual_parent==parent, 'PnP parent reciprocity guard'
        items.append(dict(address=hex(node),instance=text(data,40),service=text(data,56),state=struct.unpack_from('<I',data,300)[0],problem=struct.unpack_from('<I',data,404)[0],problem_status=hex(struct.unpack_from('<I',data,408)[0])))
        pending.extend([(sibling,parent),(child,node)])
    kd.note('PNP_DEVICE_INVENTORY',devices=items)

def timer_queue_snapshot(stage):
    # Public PDB GUID matches image; stripped PDB age is 6 versus RSDS age 1.
    # Offsets are independently corroborated by this exact kernel's instructions:
    # KTIMER +18 due, +20 list; PRCB +1b80 timer table, entries +200, stride 20.
    thread=kd.read(wait_thread,0x400)
    timer=thread[0xf8:0x138]
    processor=struct.unpack_from('<H',timer,0x38)[0]
    bucket=timer[2]
    timer_type=struct.unpack_from('<H',timer,0x3a)[0]
    assert processor<64, 'Unexpected timer processor index'
    assert timer_type<2, 'Unexpected timer table type'
    prcb=struct.unpack('<Q',kd.read(base+0xe0a780+processor*8,8))[0]
    assert prcb>>48==0xffff, 'Invalid processor block'
    entry=prcb+0x1b80+0x200+(timer_type*256+bucket)*0x20
    kd.note('TIMER_WHEEL_SNAPSHOT',stage=stage,thread=hex(wait_thread),thread_state=thread[0x17c],wait_reason=thread[0x2a3],timer_active=bool(struct.unpack_from('<I',thread,0x6c)[0]&0x200),timer=timer.hex(),inserted=bool(timer[3]&0x40),expired=bool(timer[3]&0x80),due=hex(struct.unpack_from('<Q',timer,0x18)[0]),processor=processor,bucket=bucket,prcb=hex(prcb),entry=hex(entry),bucket_bytes=kd.read(entry,0x20).hex(),prcb_dpc_clock=kd.read(prcb+0xb18,0xd8).hex(),timer_table_state=kd.read(prcb+0x5d80,0x18).hex(),clock_timer_state=kd.read(prcb+0x8480,0x98).hex(),shared_time=kd.read(0xfffff78000000008,24).hex())

def remove_probe(pc,handle,expected):
    result=kd.rpc(0x3135,struct.pack('<I',handle),allow_status=True)
    status=struct.unpack_from('<I',result,8)[0]
    # Exact kernel KdpSetCommonState (RVA 4f8168) deletes breakpoints
    # in its [PC, PC+InstructionCount-1] snapshot via aa19a8.
    # Accept already-deleted only with both original code and an empty
    # exact-PDB KdpBreakpointTable entry (40-byte entries at dbc420).
    if status:
        if base is None:
            # Loader also auto-removes the hit breakpoint. Its table is NOT
            # the kernel table: exact PDB BdBreakpointTable3074a0 and guarded
            # BdDeleteBreakpoint21d700 prove24-byte entries, flags at+0.
            assert loader_time_base and pc==loader_time_base+0x2e3c8 and 1<=handle<=32
            expected_delete=struct.pack('<4I',0xd2800308,0x9ba87c14,0xd0000748,0x91128113)
            assert image_bytes(0x21d720,16,loader_time_image,loader_time_layout)==expected_delete
            assert kd.read(loader_time_base+0x21d720,16)==expected_delete
            flags=struct.unpack('<I',kd.read(loader_time_base+0x3074a0+(handle-1)*24,4))[0]
        else:
            flags=struct.unpack('<I',kd.read(base+0xdbc420+(handle-1)*40+32,4))[0]
        assert status==0xc0000001 and flags==0, 'Breakpoint removal failed with active table entry'
        kd.note('PROBE_AUTO_REMOVED',address=hex(pc),handle=handle)
    assert kd.read(pc,4)==expected, 'Original instruction not restored'

def refs_probes(entry):
    if a.cache_delay_sparse:
        probe(0x4c363c,'DELAY_SPARSE_RETURN')
        probe(0x4c37cc,'DELAY_SPARSE_CLEANUP_DONE')
        return
    if a.cache_delay:
        probe(0x4c361c,'DELAY_ARM')
        probe(0x4c363c,'DELAY_RETURN')
        return
    if a.cache_delete:
        for rva in (0x63f898,0x63f8a8,0x4c35d0,0x4c35f0,
                    0x4c3604,0x4c361c,0x4c363c,0x4c37cc,
                    0x22b298,0x22b314,0x22b320,0x4c11f8):
            probe(rva,'DELETE_'+hex(rva))
        return
    if a.cache_insert:
        for rva in (0x63f860,0x4c3f48,0x4c3fac,0x4c3fb8,
                    0x4c3ffc,0x4c4058,0x4c407c,0x4c408c,
                    0x4c40b0,0x4c410c,0x63f87c,0x4c11f8):
            probe(rva,'INSERT_'+hex(rva))
        return
    if a.cache_private:
        for rva in (0x4c11a8,0x22a9d8,0x22aa48,0x22aa6c,
                    0x4c3920,0x4c3988,0x4c3b9c,0x4c3cc8,
                    0x4c3cf4,0x4c3d14,0x4c3d5c,0x4c3df0,
                    0x63f83c,0x4c11f8):
            probe(rva,'CACHE_'+hex(rva))
        return
    if a.trace_cache:
        for rva in (0x4c1174,0x4c11a8,0x4c11f8,0x4c1218,0x4c1230,0x4bfe28):
            probe(rva,'CACHE_'+hex(rva))
        return
    data=Path(a.trace_refsv1).read_bytes()
    assert hashlib.sha256(data).hexdigest().upper()=='8CE06D74F4E86D5E3C32696E2319E5D4D384A9AE57246FD5F2A7EA00F483666D'
    pe=struct.unpack_from('<I',data,60)[0];opt=struct.unpack_from('<H',data,pe+20)[0]
    ns=struct.unpack_from('<H',data,pe+6)[0]
    layout=[struct.unpack_from('<IIII',data,pe+24+opt+40*i+8) for i in range(ns)]
    checkpoints=(0xeac58,0xeae04,0xeae48,0xeae68,0xeae94,0xeaed0,0xeaee0) if a.refs_tail else (0xea094,0xea248,0xea350,0xea9d8,0xeab78,0xeac40)
    if a.refs_library:
        # Separated by >=16 bytes: KdpSetCommonState must not delete a
        # future checkpoint while reporting the current instruction window.
        checkpoints=(0x2f590,0x2f5a8,0x2f5e0,0x2f618,0x2f628,
                     0x56d34,0x56d58,0x56d8c,0x56dbc,0x10d80)
    if a.refs_lazy:
        checkpoints=(0x2ffac,0x30010,0x30074,0x300b0,0x300c4,0x300e0,0x30104)
    for rva in checkpoints:
        probe(rva,'REFS_'+hex(rva),entry-0xea000,data,layout)

def runtime_inspection_owner(kd):
    state=subprocess.check_output([a.adb,'shell','/apex/com.android.virt/bin/vm','list'],text=True)
    owners=re.findall(r'cid: (\d+).*?requesterUid: (\d+).*?requesterPid: (\d+)',state,re.S)
    isolated=[item for item in owners if item[1]=='10042']
    assert len(isolated)==1, 'Exactly one isolated Windows owner for inspection'
    previous=getattr(kd,'inspection_owner',None)
    assert previous is None or previous==isolated[0], 'Do not inspect a replacement VM'
    kd.inspection_owner=isolated[0]

def runtime_inspection_request(root,kd,now):
    # Evidence JSON is DATA only, never an arbitrary command/read/expression.
    # The initial automatic observation must already have run. The caller
    # bounds two extra same-VM requests, at least60seconds apart.
    request=root/'runtime-inspect.request.json'
    if not request.exists():return False
    try:
        assert request.stat().st_size<=1024, 'Request size bound'
        content=json.loads(request.read_text(encoding='utf-8'))
        assert set(content)=={'sequence','kind'} and content['kind']=='hot-wait', 'Exact request schema'
        sequence=content['sequence'];previous=getattr(kd,'inspection_request_sequence',0)
        assert type(sequence) is int and 1<=sequence<=2, 'Two explicit requests only'
        if sequence<=previous:return False
        assert sequence==previous+1, 'No skipped request sequence'
        if now-getattr(kd,'inspection_last_time',now)<60:return False
        runtime_inspection_owner(kd)
    except (AssertionError,ValueError,TypeError) as error:
        kd.note('RUNTIME_INSPECTION_REQUEST_REJECTED',reason=str(error));return False
    kd.inspection_request_sequence=sequence;kd.inspection_last_time=now
    kd.note('RUNTIME_INSPECTION_REQUEST_ACCEPTED',sequence=sequence,scope='Same owner, typed hot-wait observation only; no commands or arbitrary reads')
    return True

try:
    break_sent=False
    runtime_started=None
    clock_seed_seen=False
    late_inspection_considered=False
    wait_return_armed_at=None
    wait_return_attempted=False
    ram_audit=None;ram_break_pending=False
    if a.ram_framebuffer_audit:
        import importlib.util
        spec=importlib.util.spec_from_file_location('ram_framebuffer_audit',Path(__file__).with_name('ram-framebuffer-audit.py'))
        ram_module=importlib.util.module_from_spec(spec);spec.loader.exec_module(ram_module)
        ram_audit=ram_module.Audit()
    while time.monotonic()<kd.deadline:
        wake_at=None
        if ram_audit and installed:wake_at=time.monotonic()+3
        if installed and runtime_started is not None and a.inspect_runtime_after and not late_inspection_considered:
            wake_at=runtime_started+a.inspect_runtime_after
        if wait_return_armed_at is not None:
            wake_at=wait_return_armed_at+60
        # Only the outer retained-runtime wait uses this deadline. RPCs retain
        # their existing protocol bounds; FILE_IO/PRINT cannot starve the
        # explicitly scheduled ONE observation or pending one-shot probe bound.
        try:kind,body=kd.packet(timeout=a.idle_seconds,wake_at=wake_at)
        except TimeoutError:
            if (a.hold_runtime and installed and time.monotonic()>=kd.deadline and
                    wait_return_armed_at is None and not a.inspect_runtime_after and
                    (not (a.inspect_clock_seed or a.inspect_boot_clock_delta or a.correct_boot_calendar_100ns) or clock_seed_seen)):
                pending=kd.pending_tx
                command_safe=(not pending or pending['kind']==11 or
                    (pending['kind']==2 and len(pending['packet'])==73 and
                     struct.unpack_from('<I',pending['packet'],16)[0]==0x3136))
                if command_safe:
                    import importlib
                    gate=importlib.import_module('runtime-bound-surface')
                    def owner_signature():
                        listing=subprocess.check_output([a.adb,'shell','/apex/com.android.virt/bin/vm','list'],text=True)
                        owners=re.findall(r'cid: (\d+).*?requesterUid: (\d+).*?requesterPid: (\d+)',listing,re.S)
                        return owners[0] if len(owners)==1 and owners[0][1]=='10042' else None
                    owner_before=owner_signature()
                    report_path='/sdcard/Android/data/com.example.uavfwinpetest/files/encoded-display-report.txt'
                    before=subprocess.check_output([a.adb,'shell','cat',report_path],text=True)
                    time.sleep(2)
                    after=subprocess.check_output([a.adb,'shell','cat',report_path],text=True)
                    if owner_before and owner_signature()==owner_before and gate.progressing(before,after):
                        kd.note('RUNTIME_BOUND_COMPLETE_LIVE_SURFACE',cid=int(owner_before[0]),pid=int(owner_before[2]),
                                scope='Normal test deadline; same owner, fresh receive/decode/render callbacks, at most2 in-flight frames; no physical FPS claim')
                        break
            if wait_return_armed_at is not None and time.monotonic()-wait_return_armed_at>=60:
                raise TimeoutError('One wait-return observation bound expired; stop isolated diagnostic, no rearm')
            if a.hold_runtime and not installed and time.monotonic()<kd.deadline:
                # The retained installed-OS launch can be silent while winload
                # initializes a boot driver. Silence alone is not a guest halt.
                # Preserve this exact owner up to the explicit overall deadline;
                # do not change UART pacing/VM flags or send speculative breaks.
                owner_state=subprocess.check_output([a.adb,'shell','/apex/com.android.virt/bin/vm','list'],text=True)
                owners=re.findall(r'cid: (\d+).*?requesterUid: (\d+).*?requesterPid: (\d+)',owner_state,re.S)
                if len(owners)!=1 or owners[0][1]!='10042':raise EOFError('Isolated Windows owner ended before kernel')
                if not hasattr(kd,'pre_kernel_owner'):kd.pre_kernel_owner=owners[0]
                assert kd.pre_kernel_owner==owners[0], 'Pre-kernel owner changed'
                if kd.recover_boot_unload_epoch():continue
                kd.note('PRE_KERNEL_OWNER_ALIVE',cid=int(owners[0][0]),pid=int(owners[0][2]),
                        observation='No fresh kernel milestone yet; retain same bounded run, no extra break/VM mutation')
                continue
            if a.hold_runtime and installed and time.monotonic()<kd.deadline:
                if kd.recover_dump_unload_continue_epoch():continue
                now=time.monotonic()
                requested=(a.allow_runtime_inspection_requests and late_inspection_considered
                           and runtime_inspection_request(root,kd,now))
                ram_ready=False
                if ram_audit and len(ram_audit.samples)<2 and now>=ram_audit.next_due:
                    report=subprocess.run([a.adb,'shell','cat','/sdcard/Android/data/com.example.uavfwinpetest/files/encoded-display-report.txt'],capture_output=True,text=True)
                    ram_ready=report.returncode==0 and ram_module.active_workload(report.stdout)
                if ram_ready:
                    ram_break_pending=True;break_sent=True
                    kd.note('RAM_FRAMEBUFFER_OBSERVATION_REQUEST',sample=len(ram_audit.samples)+1,scope='One own debugger break;32 bytes read only; resume between observations')
                    kd.write(b'\x62');kind,body=kd.packet(timeout=10)
                elif (a.smp_start_snapshot_after and not smp_break_sent and runtime_started is not None and
                        now-runtime_started>=a.smp_start_snapshot_after and 2<=len(psci_adapted)<7):
                    smp_break_sent=True
                    kd.note('SMP_START_SNAPSHOT_REQUESTED',accepted_affinities=psci_adapted,scope='One debugger break to locate CPU startup; no CPU features/flags/Windows files changed')
                    kd.write(b'\x62');kind,body=kd.packet(timeout=10)
                elif (requested or (a.inspect_runtime_after and not late_inspection_considered and runtime_started is not None
                        and now-runtime_started>=a.inspect_runtime_after)):
                    late_inspection_considered=True
                    report=subprocess.check_output([a.adb,'shell','cat /sdcard/Android/data/com.example.uavfwinpetest/files/owner-report.txt'],text=True)
                    if a.inspect_hot_process or 'ANDROID_SURFACE_READBACK=PASS' not in report:
                        if a.allow_runtime_inspection_requests:runtime_inspection_owner(kd)
                        kd.inspection_last_time=now
                        break_sent=True
                        kd.note('LATE_RUNTIME_INVENTORY_REQUEST',scope='One own KD break-in; inspect processes, then resume; no platform experiment')
                        kd.write(b'\x62')
                        kind,body=kd.packet(timeout=10)
                        # Process the returned stopped state normally below.
                    else:
                        kd.note('LATE_RUNTIME_INVENTORY_SKIPPED',reason='Current boot already presented a valid Surface frame')
                        continue
                else:
                    kd.note('RUNTIME_OBSERVER_ALIVE',last_driver=last_driver)
                    continue
            else:
                if not a.break_on_idle or break_sent:raise
                break_sent=True;kd.note('IDLE_BREAK_REQUEST',last_driver=last_driver)
                kd.write(b'\x62')
                kind,body=kd.packet(timeout=5)
        if kind!=7:raise ValueError('Unexpected state packet')
        state=struct.unpack_from('<I',body)[0];pc=struct.unpack_from('<Q',body,24)[0]
        if state==0x3031:
            length=struct.unpack_from('<I',body,32)[0]
            path=body[-length:].decode('ascii','replace').rstrip('\0') if 0<length<=len(body)-80 else ''
            module=struct.unpack_from('<Q',body,40)[0];unload=bool(body[64])
            kd.note('LOAD_STATE',pc=hex(pc),path=path,module_base=hex(module),unloading=unload)
            if a.inspect_loader_time and path.lower()=='winload.efi' and not unload and loader_time_base is None:
                assert 0<module<2**32 and module%4096==0, 'Exact physical loader mapping'
                loader_time_base=module
                probe(0x2e3c8,'LOADER_TIME_SELECTION',module_base=module,data=loader_time_image,layout=loader_time_layout)
            if a.loader_state and path.lower()=='winload.efi' and not unload and not loader_observed:
                loader_observed=True
                samples=[]
                # These exact physical MMIO ranges are already read by the unchanged
                # EFI launcher. At this natural pre-EBS stop try virtual reads;
                # unsupported requests remain unknown, never inferred zero.
                for address in (0x3fff0000,0x3fff0080,0x3fff0100,0x3ffd0014,0x3ffe0080,0x3ffe0100,0x3ffe0200):
                    response=kd.rpc(0x3130,struct.pack('<QII',address,4,0),allow_status=True)
                    status=struct.unpack_from('<I',response,8)[0]
                    count=struct.unpack_from('<I',response,28)[0]
                    samples.append(dict(address=hex(address),status=hex(status),
                        value=hex(struct.unpack_from('<I',response,56)[0]) if status==0 and count==4 and len(response)==60 else None))
                kd.note('LOADER_RUNTIME_MMIO',pc=hex(pc),module_base=hex(module),samples=samples,
                        scope='Existing natural winload notification only; no instruction/register write')
            if a.delegate_shell_owner and path.lower().endswith('\\viosock.sys') and not unload:
                if a.trace_producer_exit:
                    assert image_bytes(0x8effa0,8)==bytes.fromhex('7f2303d5fd7bbaa9'), 'Exact NtTerminateProcess prologue'
                    probe(0x8effa0,'PRODUCER_TERMINATE')
                subprocess.check_call([a.adb,'shell','touch /data/local/tmp/uavf-windows-uart-gas-20261004/owner-display-ready'])
                kd.note('AVF_OWNER_DISPLAY_READY',source='Native guest viosock driver load; no timer guess')
            if a.app_bridge_token and path.lower().endswith('\\viosock.sys') and not unload:
                result=subprocess.check_output([a.adb,'shell','am','start','-f','0x20000000','-n',
                    'com.example.uavfwinpetest/com.example.winavf.WindowsDiagnosticActivity',
                    '--es','token',a.app_bridge_token,'--ez','start_display','true'],text=True)
                kd.note('APP_DISPLAY_ACTIVATE_AFTER_VIOSOCK_LOAD',result=result.strip())
            if pc>>48==0xffff and not installed:
                version=kd.rpc(0x3146)
                base=struct.unpack_from('<Q',version,32)[0]
                assert base>>48==0xffff and struct.unpack_from('<H',version,18)[0]==26100
                kd.note('KERNEL_CONNECTED',base=hex(base))
                kd.note('HOST_CROSVM_COMMAND',processes=subprocess.check_output([a.adb,'shell','ps -A -o PID,PPID,ARGS | grep crosvm'],text=True).strip())
                if a.psci_low_memory_ab:
                    effective=subprocess.check_output([a.adb,'shell','ps -A -o PID,PPID,ARGS | grep crosvm_VmRun'],text=True)
                    assert '--mem 2048 ' in effective and '--cpus num-cores=8,' in effective, 'Exact effective 2GiB/eight CPU address A/B'
                if a.cam_access:
                    for rva,word in [(0x49bc4c,0x3970c508),(0x49bc60,0x3970e108),(0x49bc70,0x3970c908)]:
                        assert image_bytes(rva,4)==struct.pack('<I',word) and kd.read(base+rva,4)==image_bytes(rva,4)
                    for rva,original,value in [(0xc03c31,12,8),(0xc03c32,15,11),(0xc03c38,20,16)]:
                        assert image_bytes(rva,1)==bytes([original]) and kd.read(base+rva,1)==bytes([original])
                        kd.write_memory(base+rva,bytes([value]))
                        assert kd.read(base+rva,1)==bytes([value])
                    # CAM functions share a 4K page. HAL's reusable mapping
                    # strips PA low bits; retain them in the access offset.
                    source=base+0x49baec;cave=base+0x665b20
                    assert image_bytes(0x49baec,4)==struct.pack('<I',0x90004b55)
                    assert kd.read(source,4)==image_bytes(0x49baec,4)
                    assert image_bytes(0x665b20,16)==bytes(16) and kd.read(cave,16)==bytes(16)
                    page=((base+0xe03000)&~4095)-(cave&~4095)
                    immediate=(page//4096)&0x1fffff
                    adrp=0x90000015|((immediate&3)<<29)|((immediate>>2)<<5)
                    branch_back=0x14000000|(((base+0x49baf0-(cave+12))//4)&0x03ffffff)
                    thunk=struct.pack('<4I',0x12002c10,0x0b100294,adrp,branch_back)
                    kd.write_memory(cave,thunk);assert kd.read(cave,16)==thunk
                    word=struct.pack('<I',0x14000000|(((cave-source)//4)&0x03ffffff))
                    kd.write_memory(source,word);assert kd.read(source,4)==word
                    assert image_bytes(0x49bacc,4)==struct.pack('<I',0x7140051f) and kd.read(base+0x49bacc,4)==image_bytes(0x49bacc,4)
                    word=struct.pack('<I',0x7104011f)
                    kd.write_memory(base+0x49bacc,word);assert kd.read(base+0x49bacc,4)==word
                    kd.note('WINDOWS_CAM_GEOMETRY_APPLIED',function_shift=8,device_shift=11,bus_shift=16)
                    kd.note('WINDOWS_CAM_SUBPAGE_APPLIED',source_rva='0x49baec',cave_rva='0x665b20',thunk=thunk.hex(),config_limit=256)
                if a.periodic_imask_workaround:
                    install_periodic_workaround()
                    if a.emulate_user_pmcc:
                        import importlib
                        adapter=importlib.import_module('pmu-emulation-thunk')
                        thunk,point=adapter.load_thunk(Path(__file__).resolve().parents[3])
                        source,cave=adapter.SOURCE,adapter.CAVE
                        text=next(section for section in sections if section[1]==0x200000)
                        virtual_end=text[1]+text[0]
                        assert virtual_end<=cave and cave+len(thunk)<=((virtual_end+4095)&~4095)
                        assert cave>=text[1]+text[2], 'Only zero-fill tail beyond raw section bytes'
                        assert kd.read(base+cave,len(thunk))==bytes(len(thunk)), 'Actual mapped executable-page zero padding guard'
                        assert image_bytes(source,8)==bytes.fromhex('7f2303d5f353bba9') and kd.read(base+source,8)==image_bytes(source,8), 'Exact synchronous handler prologue'
                        assert image_bytes(0x620850,8)==bytes.fromhex('230080d2845582d2'), 'Exact fault-safe user read helper'
                        kd.write_memory(base+cave,thunk)
                        if not a.quiet_driver_notifications:
                            probe(cave+point,'USER_PMCC_EMULATED',expected_override=thunk[point:point+4])
                        kd.write_memory(base+source,struct.pack('<I',adapter.branch(source,cave)))
                        kd.note('USER_PMCC_TRAP_ADAPTER_INSTALLED',source=hex(base+source),cave=hex(base+cave),thunk_sha256=hashlib.sha256(thunk).hexdigest(),scope='Isolated RAM only; EL0 exact PMCC opcode; verified trap/exception register layouts; all other exceptions use original handler; CNTVCT fallback is not PMU support')
                        if a.quiet_driver_notifications:
                            notify=0x2fbfa0
                            assert image_bytes(notify,8)==bytes.fromhex('7f2303d5f30f1ff8') and kd.read(base+notify,8)==image_bytes(notify,8), 'Exact diagnostic-only DbgLoadImageSymbols entry'
                            kd.write_memory(base+notify,struct.pack('<I',0xd65f03c0))
                            kd.note('DRIVER_SYMBOL_NOTIFICATIONS_SUPPRESSED',source=hex(base+notify),scope='Diagnostic notification only; driver initialization unchanged; no extra native snapshot breakpoint')
                            result=subprocess.check_output([a.adb,'shell','am','start','-f','0x20000000','-n',
                                'com.example.uavfwinpetest/com.example.winavf.WindowsDiagnosticActivity',
                                '--es','token',a.app_bridge_token,'--ez','start_display','true'],text=True)
                            kd.note('APP_DISPLAY_INDEPENDENT_READINESS',result=result.strip(),scope='App-owned connectVsock retry until real listener; no shell socket')
                        if a.inspect_psci_cpu_on:
                            assert image_bytes(0x4aaf1c,4)==struct.pack('<I',0xd63f01e0), 'Exact CPU_ON-only HVC/SMC dispatch instruction'
                            probe(0x4aaf1c,'PSCI_CPU_ON_OPERANDS')
                            if a.psci_pmu_boundary:
                                assert image_bytes(0x49fd34,12)==struct.pack('<3I',0xd538000a,0xd53b9c08,0x530b3d13), 'Exact native MIDR/PMCR.N extraction'
                                assert image_bytes(0x49fe04,8)==struct.pack('<2I',0x6b08027f,0x54000102), 'Exact hardware vs allocated counter-count comparison'
                                probe(0x49fd3c,'SMP_PMU_HARDWARE')
                        if a.inspect_clock_seed:
                            assert image_bytes(0x48a8c0,8)==bytes.fromhex('7f2303d5f353bfa9')
                            probe(0x48a8c0,'HAL_CLOCK_SEED')
                        if a.inspect_boot_clock_delta:
                            assert image_bytes(0xb17f78,4)==struct.pack('<I',0xcb08000b)
                            probe(0xb17f78,'BOOT_CLOCK_DELTA')
                        if a.correct_boot_calendar_100ns:
                            assert image_bytes(0xb17f14,4)==struct.pack('<I',0xf94526d7)
                            probe(0xb17f14,'BOOT_CALENDAR_CORRECTION')
                    elif a.loader_state:
                        pass # No later observations; retain only the proven boot fixes.
                    elif a.boot_milestone:
                        selected={'interrupt-init':0xb1bf14,'driver-entry':0xb2a1b0,'io-pre-drivers':0xb27f40,'io-complete':0xb27fa8}[a.boot_milestone]
                        probe(selected,'BOOT_MILESTONE')
                    else:
                        for rva,tag in [(0x25c650,'BUGCHECK_INTERNAL'),(0xb27fa8,'IO_INIT_RETURN')]:probe(rva,tag)
                elif a.wait_clock:
                    for rva,tag in [(0x4c3638,'WAIT_CALL'),(0x25c650,'BUGCHECK_INTERNAL'),(0xb27fa8,'IO_INIT_RETURN')]:probe(rva,tag)
                    if a.wait_gic_map:probe(0x4ae2b0,'GIC_MAPPING')
                elif a.clock_sparse:
                    for rva,tag in [(0x49fe60,'TIMER_TEST_ISR'),(0x487fdc,'TIMER_TEST_SUCCESS'),(0x25c650,'BUGCHECK_INTERNAL'),(0xb27fa8,'IO_INIT_RETURN'),(0x4c363c,'DELAY_SPARSE_RETURN')]:probe(rva,tag)
                else:
                    for rva,tag in [(0xb1bf14,'IRQ_DONE'),(0xb2a1b0,'DRIVER_ENTRY'),(0xb27f40,'IO_PRE_DRIVERS_RETURN'),(0xb27fa8,'IO_INIT_RETURN'),(0x25d380,'BUGCHECK')]:probe(rva,tag)
                if a.trace_cache or a.cache_private or a.cache_insert or a.cache_delete or a.cache_delay or a.cache_delay_sparse or a.trace_clock:
                    probe(0x25c650,'BUGCHECK_INTERNAL')
                    probe(0x2131f0,'CLOCK_IRQ')
                if a.trace_clock:
                    for rva in (0x4a1fe8,0x213b24,0x213964):probe(rva,'CLOCK_WRITE_'+hex(rva))
                installed=True
                runtime_started=time.monotonic()
                if a.smss_pmu_workaround:
                    assert image_bytes(0x8e2af0,32)==bytes.fromhex('7f2303d5f353bea9f50b00f9fd7bbda9fd03009100008052c28ee49753c644f9'), 'Exact user-thread startup prologue'
                    probe(0x8e2af0,'SMSS_USER_START')
                if a.break_on_reboot:probe(0x485590,'GUEST_RESET_REQUEST')
            if storage is not None and not storage_probed and path.lower().endswith('\\viostor.sys') and not unload:
                assert module>>48==0xffff
                probe(storage_entry,'VIOSTOR_ENTRY',module_base=module,data=storage,layout=storage_layout)
                storage_probed=True
            kd.resume()
        elif state==0x3030:
            code=struct.unpack_from('<I',body,32)[0]
            ctx=kd.context();pc=ctx['pc'];registers=ctx['x']
            if a.psci_pmu_boundary:
                assert pc==struct.unpack_from('<Q',body,24)[0], 'Context must belong to actual stopped CPU, not frozen BSP'
            if smp_break_sent and a.smp_start_snapshot_after and code==0x80000003 and pc==base+0x201370:
                assert kd.read(pc,4)==struct.pack('<I',0xd43e0000), 'Only known own break instruction may be advanced'
                stopped_processor=kd.processor;original_context=kd.last_context
                snapshots=[]
                try:
                    for processor in range(3):
                        kd.processor=processor
                        try:
                            current=kd.context()
                            snapshots.append(dict(processor=processor,pc=hex(current['pc']),sp=hex(current['sp']),cpsr=hex(current['cpsr']),scope='Public architectural startup state only; no user registers/stack reads'))
                        except ValueError as error:
                            snapshots.append(dict(processor=processor,unavailable=str(error)))
                finally:
                    kd.processor=stopped_processor;kd.last_context=original_context
                    data=bytearray(original_context);struct.pack_into('<Q',data,264,pc+4)
                    kd.restore_context(bytes(data))
                kd.note('SMP_START_CPU_CONTEXTS',snapshots=snapshots,accepted_affinities=psci_adapted,scope='Read-only CPU PCs; exact own break resumed, no PMU/CPU-model mutation')
                kd.resume();continue
            if a.psci_cache_publish and getattr(kd,'psci_publication',None) is not None and code==0x80000003:
                saved,target,owned_va=kd.psci_publication
                assert kd.processor==0 and pc==owned_va+512+36, 'Only exact own publication BRK on BSP'
                expected=struct.pack('<10I',0xd50b7a30,0x91008210,0xd50b7a30,0x91008210,0xd50b7a30,0x91008210,0xd50b7a30,0xd5033f9f,0xd5033fdf,0xd43e0000)
                assert kd.read(owned_va+512,40)==expected, 'Exact40B own publication helper'
                assert registers[16]==owned_va+target*4096+96, 'Only own128B cache range touched'
                assert all(registers[i]==struct.unpack_from('<Q',saved,8+8*i)[0] for i in range(31) if i!=16), 'All other GPRs unchanged by helper'
                assert ctx['sp']==struct.unpack_from('<Q',saved,256)[0], 'Own helper has no stack accesses'
                kd.psci_publication=None
                kd.restore_context(saved)
                kd.note('OWN_TRAMPOLINE_POC_PUBLISHED',affinity=target,address=hex(owned_va+target*4096),bytes=128,
                        scope='Native DC CVAC/DSB/ISB executed on BSP; complete saved Windows CPU_ON context restored')
                kd.resume();continue
            if a.psci_runtime_reserve and code==0x80000003 and runtime_mapping is None:
                serial=subprocess.check_output([a.adb,'exec-out','run-as com.example.uavfwinpetest dd if=files/owner-serial.bin bs=1 count=65536 2>/dev/null'])
                physical=re.findall(rb'WG:PSCI_RT_PAGES=([0-9A-Fa-f]{16})',serial)
                assert len(physical)==1 and b'WG:PSCI_RT_MAP=PASS' in serial, 'Current exact owned runtime EFI allocation'
                owned=int(physical[0],16);owned_va=registers[20]
                assert 0x80000000<=owned<=0xffffe000 and owned%4096==0, 'Owned physical range'
                assert pc==owned+256+52 and registers[19]==owned, 'Only exact own copied callback BRK may be advanced'
                assert owned_va>=0xffff000000000000 and owned_va%4096==0, 'Actual ConvertPointer output'
                assert kd.read(owned_va+256+52,4)==struct.pack('<I',0xd43e0000), 'Own diagnostic BRK mapped readback'
                header=kd.read(owned_va,64)
                assert header[:40]==b'UAVFPSCI'+struct.pack('<4Q',2 if a.psci_entry_marker else 1,owned,8 if a.psci_runtime_all_cpus else 2,4096), 'Exact allocated header'
                assert struct.unpack_from('<Q',header,40)[0]==owned_va and struct.unpack_from('<Q',header,56)[0]==0, 'Successful pointer conversion'
                runtime_mapping=(owned,owned_va)
                data=bytearray(kd.last_context);struct.pack_into('<Q',data,264,pc+4)
                kd.restore_context(bytes(data))
                kd.note('PSCI_RUNTIME_VA_CHECKPOINT_PASS',physical=hex(owned),virtual=hex(owned_va),header=header.hex(),scope='Only own EFI callback BRK advanced; no UART MMIO or Windows instruction writes')
                kd.resume();continue
            if pc not in probes:
                if ram_break_pending and code==0x80000003 and pc-base==0x201370:
                    assert image_bytes(0x201370,4)==struct.pack('<I',0xd43e0000) and kd.read(pc,4)==image_bytes(0x201370,4), 'Exact own debugger break, no arbitrary PC changes'
                    try:
                        sample=ram_audit.observe(kd,(root/'rx.bin').read_bytes())
                        kd.note('RAM_FRAMEBUFFER_PIXELS',**sample)
                    finally:
                        data=bytearray(kd.last_context);struct.pack_into('<Q',data,264,pc+4)
                        kd.restore_context(bytes(data));kd.resume();ram_break_pending=False
                    if sample['result']=='READ_UNAVAILABLE' or len(ram_audit.samples)==2:
                        kd.note('RAM_FRAMEBUFFER_AUDIT_COMPLETE',samples=len(ram_audit.samples),scope='Not a sustained FPS/driver/production capture PASS')
                        break
                    continue
                if a.inspect_runtime_after and break_sent and code==0x80000003 and pc-base==0x201370:
                    assert image_bytes(0x201370,4)==struct.pack('<I',0xd43e0000) and kd.read(pc,4)==image_bytes(0x201370,4), 'Exact own debugger break'
                    try:
                        if a.inspect_hot_process:
                            try:kd.note('KERNEL_CLOCK_METADATA',**kernel_clock_metadata())
                            except Exception as error:kd.note('KERNEL_CLOCK_METADATA_UNAVAILABLE',error=str(error))
                            try:kd.note('HAL_CLOCK_BASELINE_METADATA',**hal_clock_baseline_metadata())
                            except Exception as error:kd.note('HAL_CLOCK_BASELINE_METADATA_UNAVAILABLE',error=str(error))
                        if a.inspect_hot_process:hot_process_inventory(struct.unpack_from('<Q',body,16)[0])
                        else:process_inventory()
                    except Exception as error:
                        kd.note('LATE_PROCESS_INVENTORY_INCOMPLETE',error=str(error))
                    # Always resume our known break even if a read was unavailable.
                    data=bytearray(kd.last_context)
                    struct.pack_into('<Q',data,264,pc+4)
                    kd.restore_context(bytes(data))
                    if a.inspect_wait_return and not wait_return_attempted:
                        assert hasattr(kd,'hot_wait_target'), 'Actual hot-thread identity required'
                        assert image_bytes(0x8f7d74,12)==struct.pack('<III',0xa8c27bfd,0xd50323ff,0xd65f03c0)
                        probe(0x8f7d74,'HOT_WAIT_KERNEL_RETURN')
                        wait_return_attempted=True;wait_return_armed_at=time.monotonic()
                    kd.note('LATE_RUNTIME_INVENTORY_RESUMED',pc=hex(pc+4))
                    kd.resume()
                    continue
                if a.smss_pmu_workaround and code==0xc000001d and 0x1253c<=pc<0x800000000000:
                    window=kd.read(pc-16,64)
                    kd.note('USER_FIRST_CHANCE_ILLEGAL',pc=hex(pc),window=window.hex(),cpsr=hex(ctx['cpsr']))
                    assert window.hex()==pmu_audit['window'], 'Not exact observed smss PMCCNTR site; refuse generic ISA patch'
                    userbase=pc-0x1253c;header=kd.read(userbase,64)
                    assert header[:2]==b'MZ' and 0<struct.unpack_from('<I',header,60)[0]<4096
                    pe_header=kd.read(userbase+struct.unpack_from('<I',header,60)[0],32)
                    assert pe_header[:4]==b'PE\0\0' and struct.unpack_from('<H',pe_header,4)[0]==0xaa64
                    assert ctx['cpsr']&15==0,'Expected native EL0 fault context'
                    kd.write_memory(pc,struct.pack('<I',0xd53be048)) # MRS x8,CNTVCT_EL0, same register/width.
                    kd.note('SMSS_PMU_RAM_AB',pc=hex(pc),original='d53b9d08',replacement='d53be048',scope='Only exact faulting user instruction in RAM, no signed file changes')
                    kd.resume();continue
                kd.note('UNEXPECTED_EXCEPTION',code=hex(code),pc=hex(pc),sp=hex(ctx['sp']),registers=[hex(x) for x in registers])
                # Retained-runtime observation must not leave the entire VM
                # stopped on a native app's first-chance DebugBreak. Dispatch
                # it normally (gn), without swallowing it, advancing PC or
                # changing signed code. Kernel/second-chance/ISA faults retain
                # the existing stop-and-investigate behavior.
                first_chance=struct.unpack_from('<I',body,184)[0] if len(body)>=188 else None
                if (a.hold_runtime and code==0x80000003 and first_chance==1
                        and ctx['cpsr']&15==0 and 0<pc<0x800000000000):
                    kd.note('USER_FIRST_CHANCE_BREAK_DISPATCH',pc=hex(pc),continue_status='DBG_EXCEPTION_NOT_HANDLED',context_write=False)
                    kd.pass_first_chance()
                    continue
                if kd.user_fault_record and kd.user_fault_context:
                    try:
                        assert 0<kd.user_fault_record<0x800000000000 and 0<kd.user_fault_context<0x800000000000
                        record=kd.read(kd.user_fault_record,152)
                        fault_context=kd.read(kd.user_fault_context,912)
                        fault_code=struct.unpack_from('<I',record,0)[0]
                        fault_pc=struct.unpack_from('<Q',record,16)[0]
                        context_pc=struct.unpack_from('<Q',fault_context,264)[0]
                        assert 16<=fault_pc<0x800000000000 and fault_pc==context_pc, 'Fault record/context PC mismatch'
                        window=kd.read(fault_pc-16,64)
                        kd.note('NATIVE_USER_FAULT',code=hex(fault_code),pc=hex(fault_pc),
                                instruction=window[16:20].hex(),window=window.hex(),
                                exception_record=record.hex(),context=fault_context.hex(),
                                scope='Read-only original exception; no register/instruction writes')
                    except Exception as error:kd.note('NATIVE_USER_FAULT_READ_FAILED',error=str(error))
                stack=kd.read(ctx['sp'],256)
                kd.note('STOP_STACK',words=[hex(x) for x in struct.unpack('<32Q',stack)])
                if (a.hold_runtime and a.psci_explicit_ipi_ab and code==0x80000003
                        and pc-base==0x201370 and ctx['cpsr']&15!=0
                        and struct.unpack_from('<2Q',stack,32)==(0x1db,0x1db)):
                    # Already stopped native watchdog, not a new breakpoint or
                    # workaround. Read only typed current GIC state and bounded
                    # kernel frame records; no user stacks or register writes.
                    try:
                        controller=struct.unpack('<Q',kd.read(base+0xc100e0,8))[0]
                        assert controller>>48==0xffff and controller%8==0
                        flags=struct.unpack('<I',kd.read(controller+0xf4,4))[0]
                        kd.note('IPI_WATCHDOG_GIC_STATE',controller=hex(controller),flags=hex(flags),gic=smp_gic_mapped_snapshot(),scope='Read-only state at existing native bugcheck; no capability/CPU/timer writes')
                        frames=[];frame=registers[29];seen=set()
                        for index in range(6):
                            if frame>>48!=0xffff or frame%16 or frame in seen:break
                            seen.add(frame)
                            parent,lr=struct.unpack('<2Q',kd.read(frame,16))
                            frames.append(dict(frame=hex(frame),lr=hex(lr),kernel_rva=hex(lr-base) if base<=lr<base+0x1000000 else None))
                            if parent<=frame or parent-frame>65536:break
                            frame=parent
                        kd.note('IPI_WATCHDOG_KERNEL_FRAMES',frames=frames,scope='At most96B kernel frame chain; no user memory or new breakpoints')
                    except Exception as error:kd.note('IPI_WATCHDOG_STATE_PARTIAL',error=str(error))
                if a.process_inventory and break_sent and code==0x80000003 and pc-base==0x201370:process_inventory()
                if a.device_inventory and break_sent and code==0x80000003 and pc-base==0x201370:device_inventory()
                if a.inspect_hot_process and break_sent and code==0x80000003 and pc-base==0x201370:
                    try:hot_process_inventory(struct.unpack_from('<Q',body,16)[0])
                    except Exception as error:kd.note('HOT_PROCESS_CODE_PARTIAL',error=str(error))
                if (a.test_timer_unmask or a.periodic_imask_workaround) and break_sent and code==0x80000003 and pc-base==0x201370:
                    assert image_bytes(0x201370,4)==struct.pack('<I',0xd43e0000) and kd.read(pc,4)==image_bytes(0x201370,4), 'Explicit debugger break instruction guard'
                    data=bytearray(kd.last_context)
                    struct.pack_into('<Q',data,264,pc+4)
                    kd.restore_context(bytes(data))
                    kd.note('MANUAL_BREAK_RESUMED',pc=hex(pc+4),reason='Bounded own break-in, not guest fault')
                    kd.resume()
                    continue
                break
            handle,tag,expected=probes.pop(pc)
            if tag in ('HOT_WAIT_KERNEL_RETURN','HAL_CLOCK_SEED','BOOT_CLOCK_DELTA','BOOT_CALENDAR_CORRECTION','LOADER_TIME_SELECTION'):
                kd.note('PROBE_HIT',tag_name=tag,pc=hex(pc),scope='Typed wait/clock observation only; no general register dump')
            else:
                kd.note('PROBE_HIT',tag_name=tag,pc=hex(pc),x0=hex(registers[0]),x15=hex(registers[15]),x19=hex(registers[19]),lr=hex(registers[30]),sp=hex(ctx['sp']))
            remove_probe(pc,handle,expected)
            if tag=='SMP_FINAL_START_HANDSHAKE':
                assert a.psci_final_start_state and psci_adapted==list(range(1,8)) and kd.processor==0 and registers[0]==4, 'All native secondary starts completed on BSP'
                assert registers[26]>=0xffff000000000000 and registers[26]%8==0, 'Exact native boot handshake pointer'
                loader=struct.unpack('<Q',kd.read(registers[26],8))[0]
                assert loader>=0xffff000000000000 and loader%8==0, 'Native loader block, no user data'
                pending=struct.unpack('<Q',kd.read(loader+0x88,8))[0]
                original_context=kd.last_context;snapshots=[]
                try:
                    for processor in range(8):
                        kd.processor=processor
                        current=kd.context()
                        snapshots.append(dict(processor=processor,pc=hex(current['pc']),sp=hex(current['sp']),cpsr=hex(current['cpsr']),available=bool(current['pc'] and current['sp'] and current['cpsr']),kernel_rva=hex(current['pc']-base) if base<=current['pc']<base+0x2000000 else None))
                finally:
                    kd.processor=0;kd.last_context=original_context
                kd.note('SMP_FINAL_START_HANDSHAKE',pending=hex(pending),snapshots=snapshots,
                        scope='Read-only native boot field and CPU PC/SP/PSTATE at known common milestone; no stacks/user/credential data')
                assert image_bytes(0xb360b0,4)==struct.pack('<I',0xaa1303e0), 'Exact native handshake completion'
                probe(0xb360b0,'SMP_FINAL_START_HANDSHAKE_COMPLETE')
                kd.resume();continue
            if tag=='SMP_FINAL_START_HANDSHAKE_COMPLETE':
                assert a.psci_final_start_state and psci_adapted==list(range(1,8)) and kd.processor==0, 'Exact all-secondary BSP completion'
                kd.note(tag,scope='Native KeStartAllProcessors cleared boot handshake; no state changes')
                if a.psci_explicit_ipi_ab:
                    assert image_bytes(0x4f895c,4)==struct.pack('<I',0x97f5f559), 'Exact native time synchronization IPI call'
                    probe(0x4f895c,'SMP_TIME_IPI_CALL')
                    probe(0xb363bc,'SMP_START_ALL_RETURN')
                    kd.resume();continue
                assert image_bytes(0xb362dc,8)==struct.pack('<2I',0x94002c55,0x37f80700), 'Exact PpmInitHeteroEngine call/result boundary'
                assert image_bytes(0xb363bc,4)==struct.pack('<I',0xd65f03c0), 'Exact KeStartAllProcessors return'
                probe(0xb362dc,'SMP_POST_START_PPM_BOUNDARY')
                probe(0xb363bc,'SMP_START_ALL_RETURN')
                kd.resume();continue
            if tag in ('SMP_POST_START_PPM_BOUNDARY','SMP_PPM_ENGINE_RETURN','SMP_PPM_PHASE_COMPLETE','SMP_SCHED_CONFIG_CALL','SMP_SCHED_CONFIG_RETURN','SMP_SECOND_HAL_CALL','SMP_SECOND_HAL_INIT_COMPLETE','SMP_START_ALL_RETURN'):
                assert a.psci_final_start_state and psci_adapted==list(range(1,8)) and kd.processor==0, 'Exact all-secondary post-start BSP metadata'
                count=struct.unpack('<I',kd.read(base+0xe08a80,4))[0]
                barrier=struct.unpack('<I',kd.read(base+0xe08c38,4))[0]
                assert 1<=count<=8 and barrier in (0,1), 'Exact public KeNumberProcessors/KiBarrierWait boot fields'
                kd.note(tag,ke_number_processors=count,ki_barrier_wait=barrier,
                        scope='Read-only two matching-PDB public processor initialization fields; no state writes')
                # KdpSetCommonState removes every breakpoint in its forward
                # instruction snapshot. Arm adjacent return ONLY after this
                # stop is delivered/restored, never alongside the call probe.
                if tag=='SMP_POST_START_PPM_BOUNDARY':
                    assert image_bytes(0xb362e0,4)==struct.pack('<I',0x37f80700), 'Exact PpmInitHeteroEngine return'
                    probe(0xb362e0,'SMP_PPM_ENGINE_RETURN')
                elif tag=='SMP_PPM_ENGINE_RETURN':
                    kd.note('SMP_PPM_ENGINE_STATUS',status=hex(registers[0]&0xffffffff),scope='Native return status only')
                    if not registers[0]&0x80000000:
                        assert image_bytes(0xb362e8,4)==struct.pack('<I',0x97fd485a), 'Exact PpmUpdateArchitecturalEfficiencyClass return'
                        probe(0xb362e8,'SMP_PPM_PHASE_COMPLETE')
                elif tag=='SMP_PPM_PHASE_COMPLETE':
                    assert image_bytes(0xb36354,4)==struct.pack('<I',0x9400022b), 'Exact KiConfigureAllSchedulingInformation call'
                    probe(0xb36354,'SMP_SCHED_CONFIG_CALL')
                elif tag=='SMP_SCHED_CONFIG_CALL':
                    assert image_bytes(0xb36358,4)==struct.pack('<I',0xd0000648), 'Exact scheduling initialization return'
                    probe(0xb36358,'SMP_SCHED_CONFIG_RETURN')
                elif tag=='SMP_SCHED_CONFIG_RETURN':
                    assert image_bytes(0xb36370,4)==struct.pack('<I',0xd63f01e0), 'Exact native second HAL init call'
                    probe(0xb36370,'SMP_SECOND_HAL_CALL')
                elif tag=='SMP_SECOND_HAL_CALL':
                    assert registers[0]==1 and registers[1]==0 and base<=registers[15]<base+0x2000000, 'Native HAL phase1 operands'
                    kd.note('SMP_SECOND_HAL_TARGET',rva=hex(registers[15]-base),scope='Native dispatch target, no pointer/argument mutation')
                    assert image_bytes(0xb36374,4)==struct.pack('<I',0x52800028), 'Exact second HAL init return'
                    probe(0xb36374,'SMP_SECOND_HAL_INIT_COMPLETE')
                    assert registers[15]==base+0x4cdb50, 'Observed native xHalTscSynchronization dispatch only'
                    assert image_bytes(0x4f895c,4)==struct.pack('<I',0x97f5f559), 'Exact time adjustment IPI call'
                    probe(0x4f895c,'SMP_TIME_IPI_CALL')
                kd.resume();continue
            if tag=='SMP_TIME_IPI_CALL':
                assert a.psci_final_start_state and kd.processor==0 and psci_adapted==list(range(1,8))
                assert registers[0]==base+0xa982e0 and registers[1]==ctx['sp'], 'Exact KiCalibrateTimeAdjustment callback and native stack context'
                kd.note(tag,callback_rva=hex(registers[0]-base),scope='Native time synchronization IPI dispatch; no mutations')
                if a.psci_explicit_ipi_ab:
                    smp_explicit_ipi_ab()
                    assert image_bytes(0x4f8960,4)==struct.pack('<I',0x52800020), 'Exact time adjustment IPI return'
                    probe(0x4f8960,'SMP_TIME_IPI_RETURN')
                    kd.resume();continue
                assert image_bytes(0x275f3c,4)==struct.pack('<I',0xb9400268), 'Exact IPI sent/count wait'
                assert image_bytes(0x4f8960,4)==struct.pack('<I',0x52800020), 'Exact time adjustment IPI return'
                probe(0x275f3c,'SMP_TIME_IPI_WAIT')
                probe(0x4f8960,'SMP_TIME_IPI_RETURN')
                kd.resume();continue
            if tag=='SMP_TIME_IPI_WAIT':
                assert a.psci_final_start_state and kd.processor==0 and registers[24]==base+0xa982e0
                counter_pointer=registers[19]
                assert counter_pointer>=0xffff000000000000 and counter_pointer%128==0, 'Native aligned IPI synchronization structure'
                pending=struct.unpack('<I',kd.read(counter_pointer,4))[0]
                assert 0<=pending<=8, 'Native pending IPI count'
                original_context=kd.last_context
                snapshots=[]
                try:
                    for processor in range(8):
                        kd.processor=processor
                        current=kd.context()
                        snapshots.append(dict(processor=processor,pc=hex(current['pc']),cpsr=hex(current['cpsr']),available=bool(current['pc'] and current['sp'] and current['cpsr']),kernel_rva=hex(current['pc']-base) if base<=current['pc']<base+0x2000000 else None))
                finally:
                    kd.processor=0;kd.last_context=original_context
                kd.note(tag,pending=pending,snapshots=snapshots,scope='Read-only native IPI count and CPU PC/PSTATE; no stack/user data or writes')
                assert image_bytes(0x275f70,4)==struct.pack('<I',0xb9400268), 'Exact subsequent native IPI wait loop'
                probe(0x275f70,'SMP_TIME_IPI_WAIT_LOOP')
                kd.resume();continue
            if tag=='SMP_TIME_IPI_WAIT_LOOP':
                assert a.psci_final_start_state and kd.processor==0 and registers[24]==base+0xa982e0
                pointer=registers[19]
                assert pointer>=0xffff000000000000 and pointer%128==0
                pending,iterations=struct.unpack('<II',kd.read(pointer,4)+kd.read(pointer+128,4))
                assert 0<=pending<=8 and iterations>0, 'Native IPI loop counter bounds'
                kd.note(tag,pending=pending,iterations=iterations,gic=smp_gic_mapped_snapshot(),scope='Read-only native IPI wait and exact HAL-mapped GIC registers; no acknowledgements or writes')
                kd.resume();continue
            if tag=='SMP_TIME_IPI_RETURN':
                assert a.psci_final_start_state and kd.processor==0
                kd.note(tag,scope='Native cross-processor time calibration returned; no mutations')
                kd.resume();continue
            if tag in ('SMP_STARTUP_WAIT_ENTRY','SMP_STARTUP_STALL_RETURN','SMP_STARTUP_WAIT_EXIT'):
                assert a.psci_startup_wait_probe and 1<=len(psci_adapted)<=7 and psci_adapted==list(range(1,len(psci_adapted)+1)) and kd.processor==0, 'Sequential secondary BSP wait only'
                pointer=registers[0] if tag=='SMP_STARTUP_WAIT_ENTRY' else registers[20]
                assert pointer>=0xffff000000000000, 'Native startup flag kernel address only'
                markers={cpu:struct.unpack('<Q',kd.read(runtime_mapping[1]+cpu*4096+48,8))[0] for cpu in psci_adapted}
                assert all(value in (0,1) for value in markers.values()), 'Own execution markers only'
                flag=kd.read(pointer,1)[0]
                kd.note(tag,affinity=psci_adapted[-1],flag_pointer=hex(pointer),startup_flag=flag,own_entry_markers=markers,
                        mode=registers[1]&255 if tag=='SMP_STARTUP_WAIT_ENTRY' else registers[22]&255,
                        iterations=None if tag=='SMP_STARTUP_WAIT_ENTRY' else registers[21]&0xffffffff,
                        result=None if tag!='SMP_STARTUP_WAIT_EXIT' else registers[19]&0xffffffff,
                        scope='One-shot native BSP wait metadata and own markers; no startup flag/timeout/register writes')
                if tag=='SMP_STARTUP_WAIT_ENTRY':
                    assert image_bytes(0x481e94,8)==struct.pack('<2I',0x110006b5,0x17fffff7), 'Exact first native stall return'
                    assert image_bytes(0x481ebc,4)==struct.pack('<I',0x2a1303e0), 'Exact native wait result'
                    probe(0x481e94,'SMP_STARTUP_STALL_RETURN')
                    probe(0x481ebc,'SMP_STARTUP_WAIT_EXIT')
                elif tag=='SMP_STARTUP_WAIT_EXIT':
                    for pending_pc,(pending_handle,pending_tag,pending_expected) in list(probes.items()):
                        if pending_tag=='SMP_STARTUP_STALL_RETURN':
                            probes.pop(pending_pc);remove_probe(pending_pc,pending_handle,pending_expected)
                    if a.psci_final_start_state and psci_adapted[-1]==7 and registers[19]==4:
                        assert image_bytes(0xb36084,12)==struct.pack('<3I',0x71000c1f,0x54000700,0x7100101f), 'Exact KeStartAllProcessors native return gate'
                        if a.psci_post_start_minimal:
                            assert image_bytes(0xb363bc,4)==struct.pack('<I',0xd65f03c0), 'Exact native startup return'
                            probe(0xb363bc,'SMP_START_ALL_RETURN')
                            kd.note('SMP_POST_START_MINIMAL',scope='Only distant native return; no further startup/PPM/IPI stops, same adapters and CPU config')
                        else:
                            probe(0xb36084,'SMP_FINAL_START_HANDSHAKE')
                kd.resume();continue
            if tag=='NEXT_PSCI_CPU_ON_ENTRY':
                assert a.psci_defer_return_probe and registers[0]==len(psci_adapted)+1 and registers[0]<=7, 'Next sequential native CPU_ON function entry'
                markers=[struct.unpack('<Q',kd.read(runtime_mapping[1]+cpu*4096+48,8))[0] for cpu in psci_adapted]
                assert all(value in (0,1) for value in markers), 'Only bounded own markers'
                kd.note('DEFERRED_AP_ENTRY_MARKERS',next_affinity=registers[0],values=dict(zip(psci_adapted,markers)),scope='Later function entry; prior HVC result unobserved, marker1 proves execution')
                probe(0x4aaf1c,'PSCI_CPU_ON_OPERANDS')
                kd.resume();continue
            if tag=='SMP_PMU_HARDWARE':
                assert registers[20]<8 and (a.pmu_common_counter_ab or registers[20]<2), 'Bounded PMU observation processor range'
                kd.note('SMP_PMU_HARDWARE',processor=registers[20],midr=hex(registers[10]),pmcr=hex(registers[8]),hardware_n=(registers[8]>>11)&31,scope='Read-only native MIDR/PMCR values before extraction; no CPU features changed')
                probe(0x49fe04,'SMP_PMU_COUNTER_SET_COMPARE')
                if a.pmu_common_counter_ab and registers[20]==0:
                    assert (registers[10],(registers[8]>>11)&31) in ((0x410fd821,31),(0x410fd811,20)), 'Exact already-proven heterogeneous BSP MIDR/PMCR.N'
                    assert image_bytes(0x49fd6c,8)==struct.pack('<2I',0x2a1303e0,0x941796dc), 'Exact original counter allocation argument/call'
                    if (registers[8]>>11)&31==31:probe(0x49fd70,'SMP_PMU_COMMON_ALLOCATION')
                    else:kd.note('SMP_PMU_COMMON_ALLOCATION_NO_CHANGE',hardware_n=20,scope='BSP already has common20 counters; preserve original allocation')
                kd.resume();continue
            if tag=='SMP_PMU_COMMON_ALLOCATION':
                assert a.pmu_common_counter_ab and registers[20]==0 and registers[0]==31 and registers[19]==31, 'One original BSP allocation request only'
                data=bytearray(kd.last_context);struct.pack_into('<Q',data,8,20)
                kd.restore_context(bytes(data))
                kd.note('SMP_PMU_COMMON_ALLOCATION_AB',before=31,after=20,scope='One allocation argument only; native PMCR.N/MIDR/CPU features and secondary validation unchanged')
                kd.resume();continue
            if tag=='SMP_PMU_COUNTER_SET_COMPARE':
                assert registers[20]<8 and (a.pmu_common_counter_ab or registers[20]<2), 'Bounded PMU observation processor range'
                counter_sets=registers[12]
                assert counter_sets>=0xffff000000000000 and counter_sets%8==0, 'Kernel hardware counter-set metadata only'
                counts=[struct.unpack('<I',kd.read(counter_sets+offset+4,4))[0] for offset in (0,24,48,72)]
                assert sum(counts)==registers[8]&0xffffffff, 'Exact allocated count sum matches comparison'
                kd.note('SMP_PMU_COUNTER_SET_COMPARE',processor=registers[20],hardware_n=registers[19]&0xffffffff,allocated_counts=counts,allocated_total=sum(counts),failure=(registers[19]&0xffffffff)<sum(counts),scope='Four public hardware metadata fields, no counters/user data/feature writes')
                if a.pmu_common_counter_ab:assert sum(counts)==20, 'Actual common allocated counter set'
                if not a.psci_pre_start_only and (registers[20]==0 or (a.pmu_common_counter_ab and registers[20]<7)):probe(0x49fd3c,'SMP_PMU_HARDWARE')
                kd.resume();continue
            if tag=='PSCI_CPU_ON_OPERANDS':
                assert registers[0]==0xc4000003, 'Only CPU_ON64 parameters may be sampled'
                if a.psci_runtime_reserve:
                    serial=subprocess.check_output([a.adb,'exec-out','run-as com.example.uavfwinpetest dd if=files/owner-serial.bin bs=1 count=65536 2>/dev/null'])
                    physical=re.findall(rb'WG:PSCI_RT_PAGES=([0-9A-Fa-f]{16})',serial)
                    assert runtime_mapping is not None and len(physical)==1 and b'WG:PSCI_RT_MAP=PASS' in serial, 'Own runtime address conversion must be observed, never guessed'
                    owned,owned_va=runtime_mapping
                    assert owned==int(physical[0],16), 'Same current runtime allocation'
                    pages=8 if a.psci_runtime_all_cpus else 2
                    assert 0x80000000<=owned and owned+pages*4096<=0x100000000 and owned%4096==0, 'Owned runtime physical range'
                    assert owned_va>=0xffff000000000000 and owned_va%4096==0, 'Converted kernel runtime VA'
                    header=kd.read(owned_va,64)
                    assert header[:40]==b'UAVFPSCI'+struct.pack('<4Q',2 if a.psci_entry_marker else 1,owned,pages,4096), 'Exact own runtime header'
                    assert struct.unpack_from('<Q',header,40)[0]==owned_va and struct.unpack_from('<Q',header,56)[0]==0, 'Successful ConvertPointer and exact same VA'
                    target=registers[1] if a.psci_runtime_all_cpus else 1
                    assert 1<=target<pages, 'Only allocated secondary CPU pages'
                    offset=target*4096
                    literal_offset=32 if a.psci_entry_marker else 16
                    trampoline=kd.read(owned_va+offset,64 if a.psci_entry_marker else 40)
                    expected=(struct.pack('<8I3Q2I',0x52800020,0x10000170,0xf9000200,0xd5033f9f,0x58000080,0x580000b0,0xd61f0200,0xd503201f,0,owned+offset+56,0,0xd503205f,0x17ffffff) if a.psci_entry_marker else struct.pack('<4I2Q2I',0x58000080,0x580000b0,0xd61f0200,0xd503201f,0,owned+offset+32,0xd503205f,0x17ffffff))
                    assert trampoline==expected, 'Actual own template and safe wait, no Windows memory writes'
                    kd.note('PSCI_RUNTIME_MAPPING_READBACK_PASS',physical=hex(owned),virtual=hex(owned_va),header=header.hex(),trampoline=trampoline.hex(),scope='Read-only control; CPU_ON operands unmodified')
                    if a.psci_zero_context_ab:
                        assert registers[15]==base+0x49a870, 'Exact native HVC dispatcher only'
                        assert target==len(psci_adapted)+1 and target not in psci_adapted, 'One sequential call per secondary CPU only'
                        assert registers[3]==0x8000c000 if target==1 else 0x80000000<=registers[3]<0x180000000, 'Actual original Windows physical context range'
                        assert 0x80000000<=registers[2]<0x180000000 and registers[2]%4==0, 'Original Windows entry range/alignment'
                        literals=struct.pack('<2Q',registers[3],registers[2])
                        kd.write_memory(owned_va+offset+literal_offset,literals)
                        assert kd.read(owned_va+offset+literal_offset,16)==literals, 'Exact own literals readback before CPU_ON redirect'
                        data=bytearray(kd.last_context)
                        struct.pack_into('<Q',data,8+2*8,owned+offset)
                        struct.pack_into('<Q',data,8+3*8,0)
                        if a.psci_cache_publish:
                            expected_publication=struct.pack('<10I',0xd50b7a30,0x91008210,0xd50b7a30,0x91008210,0xd50b7a30,0x91008210,0xd50b7a30,0xd5033f9f,0xd5033fdf,0xd43e0000)
                            assert getattr(kd,'psci_publication',None) is None and kd.processor==0, 'One own publication on BSP at a time'
                            assert kd.read(owned_va+512,40)==expected_publication, 'Exact static helper before native execution'
                            kd.psci_publication=(bytes(data),target,owned_va)
                            struct.pack_into('<Q',data,8+16*8,owned_va+offset)
                            struct.pack_into('<Q',data,264,owned_va+512)
                        kd.restore_context(bytes(data))
                        psci_adapted.append(target)
                        kd.note('PSCI_ZERO_CONTEXT_AB',affinity=target,original_entry=hex(registers[2]),original_context=hex(registers[3]),new_entry=hex(owned+offset),new_context=0,literals=literals.hex(),scope='One call per CPU; only own16B literals and x2/x3 changed, trampoline restores original Windows entry/x0; no Windows memory/disk writes')
                        if a.psci_defer_return_probe and (a.psci_pre_start_only or target>=3):
                            assert image_bytes(0x49a7c0,16)==struct.pack('<4I',0xd503237f,0xa9bf7bfd,0x910003fd,0xaa0003e8), 'Exact earlier native CPU_ON function entry'
                            if target<7:probe(0x49a7c0,'NEXT_PSCI_CPU_ON_ENTRY')
                            if a.psci_startup_wait_probe:
                                assert image_bytes(0x481e64,16)==struct.pack('<4I',0xaa0003f4,0x53001c36,0x52800015,0x52800053), 'Exact native startup wait prologue'
                                probe(0x481e64,'SMP_STARTUP_WAIT_ENTRY')
                            kd.note('PSCI_RETURN_OBSERVATION_DEFERRED',affinity=target,scope='No immediate post-HVC breakpoint; exact same CPU_ON operands, next call/entry marker required')
                        else:probe(0x620330,'PSCI_ZERO_CONTEXT_RETURN')
                elif a.psci_owned_reserve:
                    serial=subprocess.check_output([a.adb,'exec-out','run-as com.example.uavfwinpetest dd if=files/owner-serial.bin bs=1 count=65536 2>/dev/null'])
                    matches=re.findall(rb'WG:PSCI_OWNED_NVS_PAGES=([0-9A-Fa-f]{16})',serial)
                    assert len(matches)==1 and b'WG:PSCI_OWNED_NVS_MAP=PASS;NO_REDIRECT' in serial, 'Exact current EFI allocation/map proof'
                    owned=int(matches[0],16)
                    assert 0x80000000<=owned and owned%4096==0 and owned+0x8000<=0x100000000, 'Owned reserve bounds'
                    samples=[kd.physical_sample(owned+at) for at in range(0,40,4)]
                    kd.note('PSCI_OWNED_HEADER_READBACK',address=hex(owned),samples=samples,scope='Read-only allocated NVS header; unavailable physical reads remain unknown')
                    available=all(s['status']=='0x0' and len(s['data'])==8 for s in samples)
                    if available:
                        header=b''.join(bytes.fromhex(s['data']) for s in samples)
                        assert header==b'UAVFPSCI'+struct.pack('<4Q',1,owned,8,4096), 'Actual reservation header after Windows kernel entry: '+header.hex()
                        kd.note('PSCI_OWNED_RESERVATION_RUNTIME_PASS',address=hex(owned),header=header.hex())
                    else:
                        assert a.psci_first_cpu_entry_ab and all(s['status']=='0xc0000001' and not s['data'] for s in samples), 'Own NVS physical header read unavailable or short'
                        kd.note('PSCI_OWNED_RESERVATION_EFI_PROOF_ONLY',address=hex(owned),scope='Exact candidate/map marker; NVS KD read unavailable, no physical write; static initialized code only')
                kd.note('PSCI_CPU_ON_OPERANDS',function_id=hex(registers[0]),target_affinity=hex(registers[1]),entry_point_pa=hex(registers[2]),context_id=hex(registers[3]),
                        scope='One restored exact HVC call breakpoint; no argument/CPU/register mutation')
                # The observed failure concerns CPU_ON entry/context. Inspect only
                # their bounded physical instruction prefix, not unrelated memory.
                entry=registers[2]
                assert 0x80000000<=entry<0x180000000 and entry%4==0, 'Observed Windows RAM CPU entry bounds'
                page=entry&~4095
                samples=[kd.physical_sample(address) for address in list(range(page,page+16,4))+list(range(entry,entry+32,4))]
                kd.note('PSCI_CPU_ENTRY_PHYSICAL_PREFIX',entry_point_pa=hex(entry),page_prefix_pa=hex(page),samples=samples,
                        scope='Read-only 48B total around exact CPU_ON instruction entry; no physical writes')
                if a.psci_first_cpu_entry_ab:
                    assert registers[1]==1 and registers[3]==0x8000c000, 'Static trampoline only for exact repeatedly observed first CPU context'
                    assert registers[15]==base+0x49a870, 'Actual HVC dispatcher required'
                    data=bytearray(kd.last_context)
                    struct.pack_into('<Q',data,8+2*8,owned+4096)
                    struct.pack_into('<Q',data,8+3*8,entry)
                    kd.restore_context(bytes(data))
                    kd.note('PSCI_FIRST_CPU_ENTRY_AB',original_entry=hex(entry),original_context=hex(registers[3]),trampoline_entry=hex(owned+4096),hypervisor_context=hex(entry),
                            scope='Exactly one CPU1 call; own static MOV x16,x0/MOV context/BR x16 restores Windows entry ABI; no Windows/physical/disk code writes')
                    probe(0x620330,'PSCI_FIRST_CPU_ENTRY_RETURN')
                if a.psci_zero_x7_ab:
                    assert image_bytes(0x620310,12)==bytes.fromhex('ff4300d1e70300f9020000d4'), 'Exact stack-save/HVC prologue'
                    probe(0x620318,'PSCI_CPU_ON_X7_AB')
                kd.resume();continue
            if tag=='PSCI_CPU_ON_X7_AB':
                assert a.psci_zero_x7_ab and registers[0]==0xc4000003 and registers[1]==1, 'First CPU_ON64 only'
                saved=struct.unpack('<Q',kd.read(ctx['sp'],8))[0]
                assert saved==registers[7] and saved==ctx['sp']+0x48, 'Original HVC caller output pointer saved and exact frame guard'
                data=bytearray(kd.last_context)
                struct.pack_into('<Q',data,8+7*8,0)
                kd.restore_context(bytes(data))
                kd.note('PSCI_CPU_ON_X7_AB',before=hex(saved),after=0,operands=[hex(x) for x in registers[:7]],
                        scope='Exactly one reserved x7 at CPU_ON HVC; original stack save/next LDR restores pointer, no code/disk mutation')
                probe(0x620330,'PSCI_CPU_ON_X7_RETURN')
                kd.resume();continue
            if tag=='PSCI_CPU_ON_X7_RETURN':
                kd.note('PSCI_CPU_ON_X7_RETURN',result_signed=struct.unpack('<q',struct.pack('<Q',registers[0]))[0],
                        x7=hex(registers[7]),scope='One restored return observation; no argument writes')
                kd.resume();continue
            if tag=='PSCI_FIRST_CPU_ENTRY_RETURN':
                kd.note('PSCI_FIRST_CPU_ENTRY_RETURN',result_signed=struct.unpack('<q',struct.pack('<Q',registers[0]))[0],scope='One native HVC return; all later CPU_ON calls unchanged')
                kd.resume();continue
            if tag=='PSCI_ZERO_CONTEXT_RETURN':
                result=struct.unpack('<q',struct.pack('<Q',registers[0]))[0]
                kd.note('PSCI_ZERO_CONTEXT_RETURN',affinity=psci_adapted[-1],result_signed=result,scope='Exact native HVC result')
                if a.psci_entry_marker:
                    markers=[struct.unpack('<Q',kd.read(runtime_mapping[1]+cpu*4096+48,8))[0] for cpu in psci_adapted]
                    assert all(value in (0,1) for value in markers), 'Only own execution markers'
                    kd.note('OWN_AP_ENTRY_MARKERS',values=dict(zip(psci_adapted,markers)),scope='1 proves own trampoline executed; immediate0 is unknown, not failure')
                if a.psci_runtime_all_cpus and result==0 and len(psci_adapted)<7:probe(0x4aaf1c,'PSCI_CPU_ON_OPERANDS')
                if a.psci_runtime_all_cpus and result==0 and len(psci_adapted)==7:kd.note('ALL_SECONDARY_CPU_ON_ACCEPTED',affinities=psci_adapted,scope='All seven HVC calls succeeded; actual Windows CPU count/Desktop still required')
                kd.resume();continue
            if tag=='LOADER_TIME_SELECTION':
                assert not loader_time_seen and pc==loader_time_base+0x2e3c8
                assert 0<ctx['sp']<2**32 and ctx['sp']%16==0, 'Exact loader physical clock stack'
                clock_pointer=registers[20]
                assert clock_pointer%8==0 and (0<clock_pointer<2**32 or clock_pointer>>48==0xffff), 'Typed loader extension physical or canonical kernel mapping'
                # Only typed clock fields in this exact function's stack frame.
                efi=kd.read(ctx['sp']+0x30,16)
                year,month,day,hour,minute,second=struct.unpack_from('<H5B',efi)
                nanosecond,timezone,daylight=struct.unpack_from('<IhB',efi,8)
                selected=struct.unpack('<Q',kd.read(registers[20]+0xa48,8))[0]
                rtc_converted=struct.unpack('<Q',kd.read(ctx['sp']+0x18,8))[0]
                persisted=struct.unpack('<Q',kd.read(ctx['sp']+0x28,8))[0]
                kd.note('LOADER_TIME_SELECTION',bl_candidate_100ns=registers[21],efi_fields=dict(year=year,month=month,day=day,hour=hour,minute=minute,second=second,nanosecond=nanosecond,timezone=timezone,daylight=daylight),
                        efi_converted_100ns=rtc_converted,persisted_candidate_100ns=persisted,selected_calendar_100ns=selected,
                        primary_kept=bool(registers[23]),utc_flag=bool(registers[22]),
                        scope='One exact loader selection, typed clock fields only; breakpoint restored; no calendar/disk mutation')
                loader_time_seen=True
                kd.resume();continue
            if tag=='BOOT_CALENDAR_CORRECTION':
                assert not clock_seed_seen, 'Exactly one calendar A/B'
                kd.note('BOOT_CALENDAR_CORRECTION',**correct_invalid_boot_calendar(ctx,a.correct_boot_calendar_100ns))
                clock_seed_seen=True
                kd.resume()
                continue
            if tag=='BOOT_CLOCK_DELTA':
                assert not clock_seed_seen, 'Exactly one boot clock observation'
                kd.note('BOOT_CLOCK_DELTA',**boot_clock_delta_evidence(ctx))
                clock_seed_seen=True
                kd.resume()
                continue
            if tag=='HAL_CLOCK_SEED':
                assert not clock_seed_seen, 'Exactly one first writer observation'
                kd.note('HAL_CLOCK_SEED',**hal_clock_seed_evidence(ctx))
                clock_seed_seen=True
                kd.resume()
                continue # Never rearm, no return/late snapshot loop.
            if tag=='HOT_WAIT_KERNEL_RETURN':
                wait_return_armed_at=None
                kd.note('HOT_WAIT_KERNEL_RETURN',**kernel_wait_return_evidence(struct.unpack_from('<Q',body,16)[0],ctx,kd.hot_wait_target))
                kd.resume()
                continue # Exactly one return, no rearm or conditional stop loop.
            if tag=='PRODUCER_TERMINATE':
                terminate_hits+=1
                own=producer_termination(struct.unpack_from('<Q',body,16)[0],registers)
                # Restore/execute original entry, then re-arm only after >=16B.
                # Avoid an immediate re-break at unchanged PC or KD instruction
                # window deletion of an adjacent breakpoint. Bound all hits.
                if not own and terminate_hits<24:probe(0x8effb0,'PRODUCER_TERMINATE_REARM')
            elif tag=='PRODUCER_TERMINATE_REARM':
                probe(0x8effa0,'PRODUCER_TERMINATE')
            if tag=='SMSS_USER_START':
                for rva,word in [(0x248000,0xf9412000),(0x2f3f90,0x910ca000),(0x2f3fc0,0xf9416800)]:
                    assert image_bytes(rva,4)==struct.pack('<I',word) and kd.read(base+rva,4)==image_bytes(rva,4),'Native process accessor guard'
                thread=struct.unpack_from('<Q',body,16)[0]
                kd.note('USER_START_NATIVE_THREAD',thread=hex(thread),x19=hex(registers[19]),x18=hex(registers[18]))
                assert thread>>48==0xffff and thread%16==0,'Native KD thread pointer'
                process=struct.unpack('<Q',kd.read(thread+0x240,8))[0]
                assert process>>48==0xffff
                forward,backward=struct.unpack('<QQ',kd.read(process+0x1c8,16))
                assert struct.unpack('<Q',kd.read(forward+8,8))[0]==process+0x1c8 and struct.unpack('<Q',kd.read(backward,8))[0]==process+0x1c8,'Native process-list reciprocity'
                name=kd.read(process+0x328,15).split(b'\0')[0]
                kd.note('USER_THREAD_START_PROCESS',thread=hex(thread),process=hex(process),name=name.decode('ascii','replace'))
                assert name==b'smss.exe','First user thread was not expected smss; no generic user patch'
                peb=struct.unpack('<Q',kd.read(process+0x2d0,8))[0];userbase=struct.unpack('<Q',kd.read(peb+0x10,8))[0]
                assert 0<peb<0x800000000000 and 0<userbase<0x800000000000
                header=kd.read(userbase,64);assert header[:2]==b'MZ'
                pe_offset=struct.unpack_from('<I',header,60)[0];assert 0<pe_offset<4096
                pe_header=kd.read(userbase+pe_offset,32);assert pe_header[:4]==b'PE\0\0' and struct.unpack_from('<H',pe_header,4)[0]==0xaa64
                fault=userbase+0x1253c;assert kd.read(fault-16,64).hex()==pmu_audit['window'],'Exact signed smss code window guard'
                kd.write_memory(fault,struct.pack('<I',0xd53be048))
                kd.note('SMSS_PMU_PREENTRY_RAM_AB',pc=hex(fault),original='d53b9d08',replacement='d53be048',scope='Exact observed PMCCNTR instruction only, before EL0 execution; no signed file write')
            if tag=='BOOT_MILESTONE':
                kd.note('BOOT_MILESTONE_REACHED',milestone=a.boot_milestone,rva=hex(pc-base),pc=hex(pc),
                        cpsr=hex(ctx['cpsr']),daif_mask=hex(ctx['cpsr']&0x3c0),registers=[hex(x) for x in registers],
                        cam_source=kd.read(base+0x49baec,4).hex(),cam_thunk=kd.read(base+0x665b20,16).hex(),
                        periodic_source=kd.read(base+0x213968,4).hex(),periodic_thunk=kd.read(base+0x665b00,24).hex(),
                        scope='Single restored late breakpoint; no CPU feature or register write')
                if a.pmu_cycle_read_ab:
                    cave=0x665b40
                    text=next(section for section in sections if section[1]==0x200000)
                    virtual_end=text[1]+text[0]
                    assert virtual_end<=cave and cave+48<=((virtual_end+4095)&~4095), 'Executable padding only'
                    assert image_bytes(cave,48)==bytes(48) and kd.read(base+cave,48)==bytes(48), 'Pristine PMU cave guard'
                    assert pc-base==0xb2a1b0 and kd.read(pc,4)==image_bytes(pc-base,4), 'Restored checkpoint guard'
                    def pmu_branch(start,end):
                        delta=end-start
                        assert delta%4==0 and -(1<<27)<=delta<(1<<27)
                        return 0x14000000|((delta//4)&0x3ffffff)
                    words=(0xa9bf07e0,0xa9bf0fe2,0xd53b9e00,0xb27e0001,0xd51b9e01,0xd5033fdf,0xd53b9e02,0xd53b9c03,0xd503201f,0xa8c10fe2,0xa8c107e0,pmu_branch(cave+44,pc-base))
                    thunk=struct.pack('<12I',*words)
                    kd.write_memory(base+cave,thunk);assert kd.read(base+cave,48)==thunk
                    probe(cave+32,'PMU_CYCLE_READBACK',expected_override=struct.pack('<I',0xd503201f))
                    pmu_cycle_probe=(pc,image_bytes(pc-base,4),ctx['sp'],base+cave)
                    kd.write_memory(pc,struct.pack('<I',pmu_branch(pc-base,cave)))
                    kd.note('PMU_CYCLE_READ_AB_ARMED',source=hex(pc),cave=hex(base+cave),scope='Only CR bit; same vCPU, native stack/GPR preservation; no signed userland modification')
                if a.boot_runtime_snapshot:
                    samples=[]
                    for address in (0x3fff0000,0x3fff0080,0x3fff0100,0x3ffd0014,0x3ffe0080,0x3ffe0100,0x3ffe0200):
                        response=kd.rpc(0x3144,struct.pack('<IIIIQI4x',4,0,0,0,address,0),allow_status=True)
                        status=struct.unpack_from('<I',response,8)[0]
                        samples.append(dict(address=hex(address),status=hex(status),value=hex(struct.unpack_from('<I',response,40)[0]) if status==0 and len(response)>=44 else None))
                    kd.note('BOOT_GIC_MMIO_STATE',milestone=a.boot_milestone,samples=samples,scope='Read-only width4; failures remain unknown, not zeros')
                    original=image_bytes(pc-base,36)
                    assert kd.read(pc,36)==original and len(kd.last_context)==912, 'Guard complete original checkpoint'
                    boot_snapshot=(pc,original,kd.last_context)
                    # Independently assembler/objdump verified boot-runtime-state.S.
                    words=(0xd53be320,0xd53be341,0xd53be042,0xd53be003,0xd5380004,0xd53b4225,0xd538cc86,0xd5384607,0xd538cce8)
                    kd.write_memory(pc,struct.pack('<9I',*words))
                    probe(pc-base+36,'BOOT_SNAPSHOT_COMPLETE')
                    kd.note('BOOT_RUNTIME_SNAPSHOT_ARMED',milestone=a.boot_milestone,pc=hex(pc),scope='Same logical checkpoint, one active breakpoint; only MRS reads')
            if tag=='PMU_CYCLE_READBACK':
                assert pmu_cycle_probe is not None
                source,original,original_sp,cave=pmu_cycle_probe
                assert pc==cave+32 and ctx['sp']==original_sp-32, 'Native probe stack guard'
                kd.note('PMU_CYCLE_READ_AB_RESULT',before=hex(registers[0]),requested=hex(registers[1]),after=hex(registers[2]),pmcr=hex(registers[3]),cr_retained=bool(registers[2]&4),scope='Native MSR then ISB/MRS; no context write')
                kd.write_memory(source,original);assert kd.read(source,4)==original
                # Restored NOP -> native LDP/LDP -> original BLR checkpoint.
                # Keep unreferenced cave until VM teardown; no unsafe live erasure.
                kd.note('PMU_CYCLE_READ_NATIVE_RETURN',source=hex(source),register_restore='native LDP/LDP',context_write=False)
                pmu_cycle_probe=None
            if tag=='USER_PMCC_EMULATED':
                frame=registers[19]
                assert frame>>48==0xffff
                trap=kd.read(frame,0x150)
                cpsr,esr=struct.unpack_from('<II',trap,0x90)
                user_pc=struct.unpack_from('<Q',trap,0x148)[0]
                rt=registers[8]
                assert cpsr&15==0 and esr>>26 in (0,24) and 0<=rt<32
                if rt<18:assert struct.unpack_from('<Q',trap,0xa0+rt*8)[0]==registers[9]
                elif rt==18:assert struct.unpack_from('<Q',trap,0x130)[0]==registers[9]
                elif rt<29:assert struct.unpack('<Q',kd.read(registers[20]+(rt-19)*8,8))[0]==registers[9]
                elif rt<31:assert struct.unpack_from('<Q',trap,0x140 if rt==29 else 0x138)[0]==registers[9]
                kd.note('USER_PMCC_NATIVE_EMULATION',pc=hex(user_pc),cpsr=hex(cpsr),esr=hex(esr),rt=rt,counter=hex(registers[9]),pmuserenr=hex(registers[11]),scope='Native trap result written, next instructions advance saved PC by4; original user bytes unchanged; breakpoint removed after first hit')
            if tag=='BOOT_SNAPSHOT_COMPLETE':
                assert boot_snapshot is not None, 'Missing original boot checkpoint'
                saved_pc,saved_code,saved_context=boot_snapshot
                assert pc==saved_pc+36 and ctx['sp']==struct.unpack_from('<Q',saved_context,256)[0], 'Snapshot execution guard'
                ctl,cval,counter,frequency,midr,daif,icc_ctlr,icc_pmr,icc_group1=registers[:9]
                valid=ctl<=7 and frequency>0 and midr!=0
                kd.note('BOOT_RUNTIME_REGISTERS',milestone=a.boot_milestone,valid=valid,
                        cntv_ctl=hex(ctl),cntv_cval=hex(cval),cntvct=hex(counter),cntfrq=frequency,
                        timer_enabled=bool(ctl&1),timer_masked=bool(ctl&2),timer_pending=bool(ctl&4),deadline_delta=cval-counter,
                        midr=hex(midr),daif=hex(daif),icc_ctlr=hex(icc_ctlr),icc_pmr=hex(icc_pmr),icc_igrpen1=hex(icc_group1))
                kd.write_memory(saved_pc,saved_code);kd.restore_context(saved_context)
                restored=kd.context()
                assert restored['pc']==saved_pc and restored['x']==list(struct.unpack_from('<31Q',saved_context,8)), 'Boot snapshot full rollback'
                boot_snapshot=None;kd.note('BOOT_RUNTIME_SNAPSHOT_RESTORED',pc=hex(saved_pc))
                assert valid, 'Invalid native state sample'
            if tag=='TIMER_REGISTERS_READY':
                assert register_sample is not None, 'Missing saved probe state'
                original_pc,original_code,original_context=register_sample
                ctl,cval,counter,frequency=registers[:4]
                valid=ctl<=7 and frequency>0 and ctx['sp']==struct.unpack_from('<Q',original_context,256)[0]
                kd.note('LIVE_TIMER_REGISTERS',source_rva=hex(original_pc-base),valid=valid,ctl=hex(ctl),enabled=bool(ctl&1),masked=bool(ctl&2),istatus=bool(ctl&4),cval=hex(cval),counter=hex(counter),frequency=frequency,deadline_delta_ticks=cval-counter)
                kd.write_memory(original_pc,original_code)
                kd.restore_context(original_context)
                restored=kd.context()
                assert restored['pc']==original_pc and restored['x']==list(struct.unpack_from('<31Q',original_context,8)), 'Context rollback failed'
                register_sample=None
                kd.note('REGISTER_SAMPLE_RESTORED',pc=hex(original_pc))
                assert valid, 'Unvalidated native probe execution; do not interpret registers'
                if a.sample_pmu_after_io and original_pc-base==0xb27fa8:
                    # Exact words independently assembled in pmu-register-audit.S.
                    # Preserve/restore the whole context and original kernel code.
                    pmu_sample=(original_pc,original_code,original_context)
                    kd.write_memory(original_pc,struct.pack('<4I',0xd5380000,0xd5380501,0xd53b9e02,0xd53b9c03))
                    probe(0xb27fb8,'PMU_REGISTERS_READY')
                    kd.note('PMU_READONLY_SAMPLE_ARMED',pc=hex(original_pc))
                if a.test_timer_unmask and not unmask_done:
                    assert ctl==7 and cval<counter and not (struct.unpack_from('<I',original_context,4)[0]&0x80), 'Refuse unmask without exact expired/masked late state'
                    unmask_probe=(original_pc,original_code,original_context)
                    unmask_done=True
                    kd.write_memory(original_pc,struct.pack('<4I',0xd53be320,0x927ef800,0xd51be320,0xd5033fdf))
                    probe(original_pc-base+16,'TIMER_UNMASK_READY')
                    kd.note('IMASK_AB_ARMED',pc=hex(original_pc),only_changed_register='CNTV_CTL.IMASK')
            if tag=='PMU_REGISTERS_READY':
                assert pmu_sample is not None
                original_pc,original_code,original_context=pmu_sample
                assert ctx['sp']==struct.unpack_from('<Q',original_context,256)[0]
                kd.note('LIVE_PLATFORM_PMU_REGISTERS',midr=hex(registers[0]),id_aa64dfr0=hex(registers[1]),perfmon=(registers[1]>>8)&15,pmuserenr=hex(registers[2]),pmcr=hex(registers[3]),scope='Read-only EL1 observation; no feature/register writes')
                kd.write_memory(original_pc,original_code);kd.restore_context(original_context)
                restored=kd.context()
                assert restored['pc']==original_pc and restored['x']==list(struct.unpack_from('<31Q',original_context,8)), 'PMU observer rollback failed'
                pmu_sample=None
                kd.note('PMU_OBSERVER_RESTORED',pc=hex(original_pc))
            if tag=='TIMER_UNMASK_READY':
                assert unmask_probe is not None
                original_pc,original_code,original_context=unmask_probe
                kd.note('IMASK_AB_EXECUTED',written_control=hex(registers[0]))
                kd.write_memory(original_pc,original_code)
                kd.restore_context(original_context)
                restored=kd.context()
                assert restored['pc']==original_pc and restored['x']==list(struct.unpack_from('<31Q',original_context,8)), 'Unmask code/context rollback failed'
                unmask_probe=None
                kd.note('IMASK_AB_CODE_RESTORED',pc=hex(original_pc))
            if tag=='GIC_MAPPING':
                gic_context=registers[0]
                kd.note('GIC_HAL_CONTEXT',address=hex(gic_context),data=kd.read(gic_context,0x100).hex())
            if tag=='WAIT_CALL':
                wait_map=registers[19]
                timer=struct.unpack('<Q',kd.read(base+0xe06428,8))[0]
                kd.note('WAIT_CALL_STATE',cpsr=hex(ctx['cpsr']),irq_masked=bool(ctx['cpsr']&0x80),interval_100ns=struct.unpack('<q',kd.read(registers[2],8))[0],map=hex(registers[19]),refcount=struct.unpack('<Q',kd.read(registers[19]+8,8))[0],timer=hex(timer),descriptor=kd.read(timer,0x100).hex(),shared_time=kd.read(0xfffff78000000008,24).hex(),registers=[hex(x) for x in registers])
                if a.test_timer_unmask:
                    selected={}
                    for name,rva in [('profile',0xe06308),('clock',0xe06428),('always_on',0xe06440),('periodic',0xe06320)]:
                        pointer=struct.unpack('<Q',kd.read(base+rva,8))[0]
                        selected[name]=dict(pointer=hex(pointer),descriptor=kd.read(pointer,0x180).hex() if pointer>>48==0xffff else '')
                    kd.note('SELECTED_HAL_TIMERS',timers=selected)
                probe(0x2131f0,'NORMAL_CLOCK_IRQ')
                probe(0x2134c0,'OTHER_CLOCK_IRQ')
                probe(0x4c363c,'WAIT_RETURN')
                if a.wait_program:
                    probe(0x2597d0,'WAIT_DISPATCH')
                    for rva in (0x4a1fe8,0x213b24,0x213964):probe(rva,'WAIT_TIMER_'+hex(rva))
            if tag in ('WAIT_DISPATCH','WAIT_DISPATCH_RETURN') or tag.startswith('WAIT_TIMER_'):
                kd.note('WAIT_PROGRAM_STATE',stage=tag,cpsr=hex(ctx['cpsr']),shared_time=kd.read(0xfffff78000000008,24).hex(),registers=[hex(x) for x in registers])
                if tag=='WAIT_DISPATCH':
                    wait_thread=registers[0]
                    kd.note('WAIT_TIMER_QUEUE',thread=hex(registers[0]),due=hex(registers[3]),wait_block=kd.read(registers[1],0x48).hex(),thread_timer=kd.read(registers[0]+0xf8,0x40).hex())
                    probe(0x2597d4,'WAIT_DISPATCH_RETURN')
                    if a.wait_expiration:
                        for rva,name in [(0x5101b0,'EXPIRATION_DPC'),(0x2a2d68,'TIMER_EXPIRATION'),(0x2a1460,'EXPIRED_LIST')]:probe(rva,name)
                if tag=='WAIT_DISPATCH_RETURN':
                    kd.note('ORIGINAL_WAITER_SNAPSHOT',original_thread=hex(wait_thread),return_thread=hex(registers[19]),same_thread=wait_thread==registers[19],original_thread_bytes=kd.read(wait_thread,0x400).hex(),map_refcount=struct.unpack('<Q',kd.read(wait_map+8,8))[0])
                    if a.wait_expiration:timer_queue_snapshot(tag)
                    if a.wait_idle:
                        timer_queue_snapshot(tag)
                        for rva,name in [(0x50eeb4,'IDLE_ENTER'),(0x212f30,'ALWAYS_ON_CLOCK_IRQ'),(0x20a870,'CLOCK_IRQ_WORK')]:probe(rva,name)
            if tag in ('EXPIRATION_DPC','TIMER_EXPIRATION','EXPIRED_LIST'):
                kd.note('EXPIRATION_CONTEXT',stage=tag,registers=[hex(x) for x in registers])
                timer_queue_snapshot(tag)
            if tag in ('IDLE_ENTER','IDLE_RETURN','ALWAYS_ON_CLOCK_IRQ','CLOCK_IRQ_WORK','WFI_ENTER','WFI_RETURN'):
                kd.note('IDLE_CLOCK_CONTEXT',stage=tag,cpsr=hex(ctx['cpsr']),registers=[hex(x) for x in registers])
                timer_queue_snapshot(tag)
                if tag=='IDLE_ENTER':probe(0x50eeb8,'IDLE_RETURN')
                if tag=='IDLE_RETURN' and a.wait_wfi:probe(0x4841f8,'WFI_ENTER')
                if tag=='WFI_ENTER':probe(0x4841fc,'WFI_RETURN')
                if tag=='WFI_RETURN' and a.wait_gic:
                    # Exact firmware/MADT GIC addresses; read-only, no IRQ acknowledge.
                    kd.note('GIC_TIMER_STATE',samples=[kd.physical_sample(address) for address in (0x3fff0000,0x3ffd0014,0x3ffe0080,0x3ffe0100,0x3ffe0200,0x3ffe0300,0x3ffe0418)])
                if tag=='WFI_RETURN' and a.wait_gic_map:
                    assert gic_context is not None, 'GIC initialization probe not observed'
                    distributor=struct.unpack('<Q',kd.read(gic_context+8,8))[0]
                    redistributor=struct.unpack('<Q',kd.read(gic_context+0xb0,8))[0]
                    assert distributor>>48==0xffff and redistributor>>48==0xffff, 'Invalid HAL GIC mapping'
                    samples=[]
                    for address in (distributor,redistributor+0x14,redistributor+0x10080,redistributor+0x10100,redistributor+0x10200,redistributor+0x10300,redistributor+0x10418):
                        body=kd.rpc(0x3130,struct.pack('<QII',address,4,0),allow_status=True)
                        samples.append(dict(address=hex(address),status=hex(struct.unpack_from('<I',body,8)[0]),data=body[56:].hex()))
                    kd.note('GIC_MAPPED_TIMER_STATE',distributor=hex(distributor),redistributor=hex(redistributor),samples=samples)
                if tag=='WFI_RETURN' and a.wait_gic_io:
                    samples=[]
                    for address in (0x3fff0000,0x3ffd0014,0x3ffe0080,0x3ffe0100,0x3ffe0200,0x3ffe0300,0x3ffe0418):
                        # DBGKD_READ_WRITE_IO_EXTENDED64: width, Internal bus,
                        # bus 0, memory address space 0, physical IO address.
                        body=kd.rpc(0x3144,struct.pack('<IIIIQI4x',4,0,0,0,address,0),allow_status=True)
                        samples.append(dict(address=hex(address),status=hex(struct.unpack_from('<I',body,8)[0]),union=body[16:56].hex()))
                    kd.note('GIC_IO_TIMER_STATE',samples=samples)
                if tag=='WFI_RETURN' and a.sample_timer_registers and a.sample_timer_late:
                    probe(0x50eebc,'LATE_TIMER_SAMPLE')
                if tag=='WFI_RETURN' and a.sample_timer_registers and not a.sample_timer_late:
                    # This epilogue plus its padding has 16 guarded bytes before
                    # a separate trap at +16. No original epilogue executes until
                    # its code and ALL original registers/PC are rolled back.
                    assert pc-base==0x4841fc and len(kd.last_context)==912
                    original=image_bytes(0x4841fc,16)
                    assert kd.read(pc,16)==original
                    register_sample=(pc,original,kd.last_context)
                    sample=struct.pack('<4I',0xd53be320,0xd53be341,0xd53be042,0xd53be003)
                    kd.write_memory(pc,sample)
                    probe(0x48420c,'TIMER_REGISTERS_READY')
                    kd.note('REGISTER_SAMPLE_ARMED',pc=hex(pc),instructions=4)
            if tag=='LATE_TIMER_SAMPLE':
                assert pc-base==0x50eebc and len(kd.last_context)==912
                original=image_bytes(0x50eebc,16)
                assert kd.read(pc,16)==original and not (ctx['cpsr']&0x80), 'Late probe must have IRQ unmasked'
                register_sample=(pc,original,kd.last_context)
                kd.write_memory(pc,struct.pack('<4I',0xd53be320,0xd53be341,0xd53be042,0xd53be003))
                probe(0x50eecc,'TIMER_REGISTERS_READY')
                kd.note('REGISTER_SAMPLE_ARMED',pc=hex(pc),instructions=4,stage='AFTER_IDLE_IRQ_UNMASKED')
            if tag in ('NORMAL_CLOCK_IRQ','OTHER_CLOCK_IRQ','WAIT_RETURN'):
                kd.note('WAIT_CLOCK_RESULT',stage=tag,cpsr=hex(ctx['cpsr']),registers=[hex(x) for x in registers])
                if tag=='WAIT_RETURN':kd.note('CACHE_WAITER_RETURN',map=hex(registers[19]),same_map=registers[19]==wait_map,status=hex(registers[0]),refcount=struct.unpack('<Q',kd.read(registers[19]+8,8))[0])
            if tag in ('TIMER_TEST_ISR','TIMER_TEST_SUCCESS'):
                pointer=struct.unpack('<Q',kd.read(base+0xe06428,8))[0]
                kd.note('TIMER_TEST_SAMPLE',stage=tag,timer=hex(pointer),descriptor=kd.read(pointer,0x100).hex(),registers=[hex(x) for x in registers])
            if tag.startswith('CLOCK_WRITE_'):
                rva=pc-base; clock_samples[rva]=clock_samples.get(rva,0)+1
                pointer=registers[19] if rva==0x213964 else registers[0]
                value=registers[9] if rva==0x4a1fe8 else registers[10] if rva==0x213964 else registers[1]
                kd.note('CLOCK_PROGRAM',rva=hex(rva),iteration=clock_samples[rva],value=hex(value),timer=hex(pointer),descriptor=kd.read(pointer,0x28).hex(),registers=[hex(x) for x in registers])
                # Rearming at the stopped PC would trap again before the MSR.
                # Alternate pre/post probes so each sample executes the write.
                probe(rva+4,'CLOCK_AFTER_'+hex(rva))
            if tag.startswith('CLOCK_AFTER_'):
                rva=int(tag.removeprefix('CLOCK_AFTER_'),16)
                kd.note('CLOCK_WRITE_EXECUTED',rva=hex(rva),iteration=clock_samples[rva])
                if clock_samples[rva]<8:probe(rva,'CLOCK_WRITE_'+hex(rva))
            if tag=='DELAY_SPARSE_RETURN':
                delay_returns+=1
                kd.note('DELAY_SPARSE_SAMPLE',iteration=delay_returns,map=hex(registers[19]),refcount=struct.unpack('<Q',kd.read(registers[19]+8,8))[0],status=hex(registers[0]&0xffffffff))
                if delay_returns>=8:
                    kd.note('SAMPLE_BOUND_REACHED');break
                if not a.clock_sparse:probe(0x4c362c,'DELAY_SPARSE_NEXT')
            if tag=='DELAY_SPARSE_NEXT':probe(0x4c363c,'DELAY_SPARSE_RETURN')
            if tag=='DELAY_ARM':
                kd.note('DELAY_MAP',address=hex(registers[19]),refcount=struct.unpack('<Q',kd.read(registers[19]+8,8))[0])
                for rva in (0x259570,0x2595f4,0x259624,0x259650,
                            0x2597a0,0x2597d4,0x2596f4,0x259724):
                    probe(rva,'DELAY_'+hex(rva))
            if tag.startswith('DELAY_'):
                rva=pc-base
                kd.note('DELAY_STATE',registers=[hex(x) for x in registers])
                if rva==0x259570:
                    interval=struct.unpack('<q',kd.read(registers[2],8))[0]
                    kd.note('DELAY_INTERVAL',units_100ns=interval,relative_ms=-interval/10000 if interval<0 else None,mode=registers[0],alertable=registers[1])
                if rva in (0x2595f4,0x259624,0x259650,0x2597a0,0x2597d4):
                    thread=registers[19]
                    kd.note('DELAY_THREAD',address=hex(thread),lock=hex(struct.unpack('<Q',kd.read(thread+0x40,8))[0]),timer=kd.read(thread+0x138,0x48).hex(),shared_time=kd.read(0xfffff78000000008,24).hex(),relative_due=hex(registers[24]))
            if tag.startswith('DELETE_'):
                # X18 in this KD context is not a validated PCR address;
                # do not dereference it or mistake an observer read failure
                # for a guest exception.
                kd.note('DELETE_STATE',registers=[hex(x) for x in registers])
                if pc-base in (0x4c35d0,0x4c35f0,0x4c3604,0x4c361c,0x4c363c):
                    kd.note('DELETE_MAP',address=hex(registers[19]),events=kd.read(registers[19]+0x498,0x30).hex(),thread_handle=hex(struct.unpack('<Q',kd.read(registers[19]+0x4c8,8))[0]),refcount=hex(struct.unpack('<Q',kd.read(registers[19]+8,8))[0]))
            if tag.startswith('INSERT_'):
                if pc-base==0x63f860:insert_partition=registers[21]
                if pc-base==0x4c3f48:insert_partition=registers[0]
                partition=insert_partition or 0
                kd.note('INSERT_STATE',partition=hex(partition),registers=[hex(x) for x in registers])
                if partition>>48==0xffff:
                    kd.note('PARTITION_STATE',lock=hex(struct.unpack('<Q',kd.read(partition+0x300,8))[0]),list=[hex(x) for x in struct.unpack('<2Q',kd.read(partition+0x20,16))],global_lock=hex(struct.unpack('<Q',kd.read(base+0xc10600,8))[0]))
            if tag.startswith('CACHE_'):
                flags=kd.read(base+0xc561b4,4)
                lock=kd.read(base+0xdd28e0,8)
                partition_mode=kd.read(base+0xc561e4,1)[0]
                kd.note('CACHE_STATE',initialized=struct.unpack('<I',flags)[0],lock=hex(struct.unpack('<Q',lock)[0]),partition_mode=partition_mode,registers=[hex(x) for x in registers])
            if tag=='DRIVER_ENTRY':
                last_driver=driver_name(registers[19])
                kd.note('DRIVER_CALL',name=last_driver,entry=hex(registers[15]))
                if a.trace_refsv1 and last_driver=='\\FileSystem\\ReFSv1':refs_probes(registers[15])
                probe(0xb2a1b4,'DRIVER_RETURN')
            elif tag=='DRIVER_RETURN':
                kd.note('DRIVER_STATUS',name=last_driver,status=hex(registers[0]&0xffffffff))
                probe(0xb2a1b0,'DRIVER_ENTRY')
            elif tag=='VIOSTOR_ENTRY':
                caller=registers[30]
                assert caller>>48==0xffff and 0<caller-base<0xc00000
                kd.note('VIOSTOR_ENTRY_CONTEXT',driver_object=hex(registers[0]),caller=hex(caller))
                probe(caller-base,'VIOSTOR_RETURN')
            elif tag=='VIOSTOR_RETURN':
                kd.note('VIOSTOR_DRIVER_ENTRY_RESULT',status=hex(registers[0]&0xffffffff))
                if a.process_inventory:process_inventory()
                if a.device_inventory:device_inventory()
            elif tag=='GUEST_RESET_REQUEST':
                kd.note('GUEST_RESET_CALLER',reason=hex(registers[0]),lr=hex(registers[30]),stack=kd.read(ctx['sp'],128).hex())
                if a.process_inventory:process_inventory()
                if a.device_inventory:device_inventory()
            elif tag.startswith('BUGCHECK'):
                kd.note('BUGCHECK',arguments=[hex(x) for x in registers[:5]])
                for enabled,inspect in [(a.process_inventory,process_inventory),(a.device_inventory,device_inventory)]:
                    if enabled:
                        try:inspect()
                        except Exception as error:kd.note('INVENTORY_FAILED',kind=inspect.__name__,error=str(error))
                if registers[0]==0x4c:
                    try:
                        pointer=registers[2]
                        params=struct.unpack('<4Q',kd.read(pointer,32))
                        kd.note('HARD_ERROR_PARAMETERS',values=[hex(x) for x in params])
                        if params[1]&0xffffffff==0xc000001d and 16<=params[2]<0x800000000000:
                            kd.note('USER_ILLEGAL_INSTRUCTION_BYTES',pc=hex(params[2]),window=kd.read(params[2]-16,64).hex())
                    except Exception as error:kd.note('HARD_ERROR_FAULT_READ_FAILED',error=str(error))
                    for index,pointer in enumerate(registers[2:5],2):
                        if pointer>>48==0xffff:
                            try:kd.note('HARD_ERROR_MEMORY',argument=index,address=hex(pointer),data=kd.read(pointer,128).hex())
                            except Exception as error:kd.note('HARD_ERROR_READ_FAILED',argument=index,error=str(error))
                break
            elif tag=='IO_INIT_RETURN':
                kd.note('IO_INIT_COMPLETED',status=hex(registers[0]))
                if a.sample_after_io:
                    assert pc-base==0xb27fa8 and len(kd.last_context)==912
                    original=image_bytes(0xb27fa8,16)
                    assert kd.read(pc,16)==original
                    register_sample=(pc,original,kd.last_context)
                    kd.write_memory(pc,struct.pack('<4I',0xd53be320,0xd53be341,0xd53be042,0xd53be003))
                    probe(0xb27fb8,'TIMER_REGISTERS_READY')
                    kd.note('REGISTER_SAMPLE_ARMED',pc=hex(pc),stage='IO_INIT_RETURN')
                if not (a.test_timer_unmask or a.periodic_imask_workaround):break
            kd.resume()
        else:raise ValueError('Unsupported KD state '+hex(state))
    if wait_return_armed_at is not None:
        raise TimeoutError('Wait-return probe unresolved at observer end; do not retain diagnostic VM')
    if a.inspect_loader_time and not loader_time_seen:
        raise TimeoutError('Loader time selection not reached; no source proof and do not retain armed probe')
    if (a.inspect_clock_seed or a.inspect_boot_clock_delta or a.correct_boot_calendar_100ns) and not clock_seed_seen:
        raise TimeoutError('First clock seed not reached; no clock source PASS')
    if a.inspect_runtime_after and not late_inspection_considered:
        raise TimeoutError('Scheduled late inspection not reached; no runtime inspection PASS')
except Exception as e:kd.note('OBSERVER_STOP',error=str(e),last_driver=last_driver)
finally:kd.cleanup()
