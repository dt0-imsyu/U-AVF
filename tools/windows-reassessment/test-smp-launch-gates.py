"""Execute the actual observer's isolated GIC/SMP admission assertions offline."""
import ast
from pathlib import Path
from types import SimpleNamespace

source = ast.parse(Path(__file__).with_name('trace-kd-boot-drivers.py').read_text())
messages = {'Exact isolated GIC profile only', 'No oneCPU observation scope in SMP', 'Exact fixed eight CPU GIC profile only', 'PSCI observation only on exact failed eight CPU profile', 'Address A/B requires typed CPU_ON observation', 'One isolated CPU_ON ABI A/B only', 'Own reserved page proof excludes other A/Bs', 'First CPU entry requires exact owned EFI reserve'}
checks = [node for node in source.body if isinstance(node, ast.Assert)
          and isinstance(node.msg, ast.Constant) and node.msg.value in messages]
checks += [node for node in source.body if isinstance(node, ast.Assert) and isinstance(node.msg, ast.Constant) and node.msg.value == 'Runtime map control excludes static entry A/B']
checks += [node for node in source.body if isinstance(node, ast.Assert) and isinstance(node.msg, ast.Constant) and node.msg.value == 'Context0 A/B requires proven owned runtime mapping']
checks += [node for node in source.body if isinstance(node, ast.Assert) and isinstance(node.msg, ast.Constant) and node.msg.value == 'All CPU adapter requires context0 runtime mapping profile']
checks += [node for node in source.body if isinstance(node, ast.Assert) and isinstance(node.msg, ast.Constant) and node.msg.value == 'PMU boundary requires exact current SMP context0 experiment']
checks += [node for node in source.body if isinstance(node, ast.Assert) and isinstance(node.msg, ast.Constant) and node.msg.value == 'Common counter A/B requires exact proven PMU mismatch profile']
checks += [node for node in source.body if isinstance(node, ast.Assert) and isinstance(node.msg, ast.Constant) and node.msg.value == 'SMP snapshot only in current common-PMU startup profile']
checks += [node for node in source.body if isinstance(node, ast.Assert) and isinstance(node.msg, ast.Constant) and node.msg.value == 'Entry marker requires separate bounded current SMP profile']
checks += [node for node in source.body if isinstance(node, ast.Assert) and isinstance(node.msg, ast.Constant) and node.msg.value == 'Deferred startup observation requires exact owned entry markers']
checks += [node for node in source.body if isinstance(node, ast.Assert) and isinstance(node.msg, ast.Constant) and node.msg.value == 'Pre-start-only observation requires exact deferred marker profile']
checks += [node for node in source.body if isinstance(node, ast.Assert) and isinstance(node.msg, ast.Constant) and node.msg.value == 'Startup wait requires exact pre-start-only marker profile']
checks += [node for node in source.body if isinstance(node, ast.Assert) and isinstance(node.msg, ast.Constant) and node.msg.value == 'PoC publication requires exact owned wait profile']
checks += [node for node in source.body if isinstance(node, ast.Assert) and isinstance(node.msg, ast.Constant) and node.msg.value == 'Final startup state requires exact native publication profile']
assert len(checks) == 20
program = compile(ast.fix_missing_locations(ast.Module(body=checks, type_ignores=[])), '<actual-smp-gates>', 'exec')
defaults = dict(psci_pre_start_only=False, psci_defer_return_probe=False, psci_entry_marker=False, smp_start_snapshot_after=0, pmu_common_counter_ab=False, psci_pmu_boundary=False, psci_runtime_all_cpus=False, psci_zero_context_ab=False, psci_runtime_reserve=False, dynamic_gic=True, fixed_eight_cpu_gic=False, inspect_psci_cpu_on=False, psci_low_memory_ab=False, psci_zero_x7_ab=False, psci_owned_reserve=False, psci_first_cpu_entry_ab=False, inspect_loader_time=None, mutable_install_media=True, hold_runtime=True,
                cam_access=True, rtc_clock_shim=False, match_host_cpus=True,
                emulate_user_pmcc=True, quiet_driver_notifications=True,
                inspect_runtime_after=0, boot_milestone=None, loader_state=False,
                inspect_clock_seed=False, inspect_boot_clock_delta=False,
                correct_boot_calendar_100ns=0, sample_after_io=False,
                sample_pmu_after_io=False)

def allowed(**changes):
    try:
        exec(program, {'a': SimpleNamespace(**(dict(psci_final_start_state=False,psci_cache_publish=False,psci_startup_wait_probe=False) | defaults | changes))})
        return True
    except AssertionError:
        return False

assert allowed()
assert allowed(match_host_cpus=False)
for key in ('dynamic_gic', 'mutable_install_media', 'hold_runtime', 'cam_access',
            'emulate_user_pmcc', 'quiet_driver_notifications'):
    assert not allowed(**{key: False}), key
for key in ('rtc_clock_shim', 'inspect_runtime_after', 'boot_milestone',
            'loader_state', 'inspect_clock_seed', 'inspect_boot_clock_delta',
            'correct_boot_calendar_100ns', 'sample_after_io', 'sample_pmu_after_io'):
    assert not allowed(**{key: True}), key
print('ACTUAL_SMP_GATES_POSITIVE_AND_15_REJECTIONS_PASS')
assert allowed(dynamic_gic=False, fixed_eight_cpu_gic=True)
for change in (dict(match_host_cpus=False),dict(dynamic_gic=True),dict(rtc_clock_shim=True),dict(inspect_loader_time='loader')):
    assert not allowed(**(dict(dynamic_gic=False,fixed_eight_cpu_gic=True)|change))
print('FIXED_EIGHT_CPU_GATES_POSITIVE_AND_REJECTIONS_PASS')
assert allowed(dynamic_gic=False,fixed_eight_cpu_gic=True,inspect_psci_cpu_on=True,psci_low_memory_ab=True)
assert not allowed(psci_low_memory_ab=True)
assert not allowed(inspect_psci_cpu_on=True)
assert not allowed(dynamic_gic=False,fixed_eight_cpu_gic=True,psci_low_memory_ab=True)
print('TYPED_PSCI_LOW_ADDRESS_AB_GATES_PASS')
assert allowed(dynamic_gic=False,fixed_eight_cpu_gic=True,inspect_psci_cpu_on=True,psci_zero_x7_ab=True)
assert not allowed(psci_zero_x7_ab=True)
assert not allowed(dynamic_gic=False,fixed_eight_cpu_gic=True,inspect_psci_cpu_on=True,psci_zero_x7_ab=True,psci_low_memory_ab=True)
print('ONE_TYPED_PSCI_X7_AB_GATES_PASS')
owned=dict(dynamic_gic=False,fixed_eight_cpu_gic=True,inspect_psci_cpu_on=True,psci_owned_reserve=True)
assert allowed(**owned)
for changes in (dict(inspect_psci_cpu_on=False),dict(psci_low_memory_ab=True),dict(psci_zero_x7_ab=True),dict(match_host_cpus=False)):
    assert not allowed(**(owned|changes))
print('OWN_RESERVED_NVS_PROOF_ADMISSION_PASS')
assert allowed(**(owned|dict(psci_first_cpu_entry_ab=True)))
assert not allowed(psci_first_cpu_entry_ab=True)
assert not allowed(**(owned|dict(psci_first_cpu_entry_ab=True,psci_owned_reserve=False)))
print('STATIC_FIRST_CPU_ENTRY_AB_ADMISSION_PASS')
assert allowed(**(owned|dict(psci_runtime_reserve=True)))
assert not allowed(psci_runtime_reserve=True)
assert not allowed(**(owned|dict(psci_runtime_reserve=True,psci_first_cpu_entry_ab=True)))
print('RUNTIME_MAP_CONTROL_SEPARATE_ADMISSION_PASS')
assert allowed(**(owned|dict(psci_runtime_reserve=True,psci_zero_context_ab=True)))
assert not allowed(**(owned|dict(psci_zero_context_ab=True)))
print('ONE_CONTEXT0_RUNTIME_MAPPING_AB_ADMISSION_PASS')
assert allowed(**(owned|dict(psci_runtime_reserve=True,psci_zero_context_ab=True,psci_runtime_all_cpus=True)))
assert not allowed(**(owned|dict(psci_runtime_reserve=True,psci_runtime_all_cpus=True)))
print('BOUNDED_ALL_CPU_MAPPING_ADMISSION_PASS')
assert allowed(**(owned|dict(psci_runtime_reserve=True,psci_zero_context_ab=True,psci_runtime_all_cpus=True,psci_pmu_boundary=True)))
assert not allowed(psci_pmu_boundary=True)
print('READ_ONLY_NEW_PMU_BOUNDARY_ADMISSION_PASS')
assert allowed(**(owned|dict(psci_runtime_reserve=True,psci_zero_context_ab=True,psci_runtime_all_cpus=True,psci_pmu_boundary=True,pmu_common_counter_ab=True)))
assert not allowed(pmu_common_counter_ab=True)
print('EXACT_COMMON_PMU_ALLOCATION_AB_ADMISSION_PASS')
assert allowed(**(owned|dict(psci_runtime_reserve=True,psci_zero_context_ab=True,psci_runtime_all_cpus=True,psci_pmu_boundary=True,pmu_common_counter_ab=True,smp_start_snapshot_after=30)))
assert not allowed(smp_start_snapshot_after=30)
print('ONE_READ_ONLY_SMP_START_SNAPSHOT_ADMISSION_PASS')
marker=owned|dict(psci_runtime_reserve=True,psci_zero_context_ab=True,psci_runtime_all_cpus=True,psci_pmu_boundary=True,pmu_common_counter_ab=True,psci_entry_marker=True)
assert allowed(**marker)
for change in (dict(smp_start_snapshot_after=30),dict(pmu_common_counter_ab=False),dict(psci_runtime_all_cpus=False)):
    assert not allowed(**(marker|change))
assert not allowed(psci_entry_marker=True)
print('SEPARATE_VERSION2_AP_MARKER_ADMISSION_PASS')
assert allowed(**(marker|dict(psci_defer_return_probe=True)))
assert not allowed(psci_defer_return_probe=True)
print('DEFERRED_POST_START_OBSERVATION_ONLY_ADMISSION_PASS')
assert allowed(**(marker|dict(psci_defer_return_probe=True,psci_pre_start_only=True)))
assert not allowed(**(marker|dict(psci_pre_start_only=True)))
print('ONLY_PRE_START_BSP_OBSERVATION_ADMISSION_PASS')
assert allowed(**(marker|dict(psci_defer_return_probe=True,psci_pre_start_only=True,psci_startup_wait_probe=True)))
assert not allowed(**(marker|dict(psci_startup_wait_probe=True)))
print('ONE_TYPED_THIRD_SECONDARY_WAIT_ADMISSION_PASS')
assert allowed(**(marker|dict(psci_defer_return_probe=True,psci_pre_start_only=True,psci_startup_wait_probe=True,psci_cache_publish=True)))
assert not allowed(**(marker|dict(psci_cache_publish=True)))
print('OWN_NATIVE_POC_PUBLICATION_ADMISSION_PASS')
assert allowed(**(marker|dict(psci_defer_return_probe=True,psci_pre_start_only=True,psci_startup_wait_probe=True,psci_cache_publish=True,psci_final_start_state=True)))
assert not allowed(**(marker|dict(psci_final_start_state=True)))
print('ONE_LAST_COMMON_STARTUP_STATE_ADMISSION_PASS')
