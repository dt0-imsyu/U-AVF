"""Execute actual observer gate; do not import its networking/boot harness."""
import ast
import itertools
from pathlib import Path
from types import SimpleNamespace

tree=ast.parse(Path(__file__).with_name('trace-kd-boot-drivers.py').read_text())
gates=[node for node in tree.body if isinstance(node,ast.Assert) and
       any(isinstance(value,ast.Attribute) and value.attr=='windows_ram_gop' for value in ast.walk(node)) and
       not any(isinstance(value,ast.Attribute) and value.attr=='ram_framebuffer_audit' for value in ast.walk(node))]
assert len(gates)==1
code=compile(ast.fix_missing_locations(ast.Module(body=gates,type_ignores=[])),'actual-ram-gop-gate','exec')
checks=0
for enabled in (False,True):
    for cached in (False,True):
        for ipi in (False,True):
            a=SimpleNamespace(windows_ram_gop=enabled,psci_cache_publish=cached,psci_explicit_ipi_ab=ipi)
            try:exec(code,{'a':a})
            except AssertionError:assert enabled and not(cached and ipi)
            else:assert not enabled or(cached and ipi)
            checks+=1
audit_gates=[node for node in tree.body if isinstance(node,ast.Assert) and
       any(isinstance(value,ast.Attribute) and value.attr=='ram_framebuffer_audit' for value in ast.walk(node))]
assert len(audit_gates)==1
audit_code=compile(ast.fix_missing_locations(ast.Module(body=audit_gates,type_ignores=[])),'actual-framebuffer-gate','exec')
for enabled,gop,eight,owner,hold,generic in itertools.product((False,True),repeat=6):
    a=SimpleNamespace(ram_framebuffer_audit=enabled,windows_ram_gop=gop,
        fixed_eight_cpu_gic=eight,app_service_owner=owner,hold_runtime=hold,inspect_runtime_after=generic)
    permitted=not enabled or (gop and eight and owner and hold and not generic)
    try:exec(audit_code,{'a':a})
    except AssertionError:assert not permitted
    else:assert permitted
    checks+=1
# Execute actual module initialization separately from networking. Existing
# harness uses `module` as an integer PE address; it must not alias this helper.
main_try=next(node for node in tree.body if isinstance(node,ast.Try) and
    any(isinstance(child,ast.While) for child in node.body))
init=next(node for node in main_try.body if isinstance(node,ast.If) and
    isinstance(node.test,ast.Attribute) and node.test.attr=='ram_framebuffer_audit')
scope={'a':SimpleNamespace(ram_framebuffer_audit=True),'__file__':str(Path(__file__).with_name('trace-kd-boot-drivers.py')),'Path':Path}
exec(compile(ast.fix_missing_locations(ast.Module(body=[init],type_ignores=[])),'actual-framebuffer-init','exec'),scope)
scope['module']=1234
call=next(node for node in ast.walk(tree) if isinstance(node,ast.Call) and
    isinstance(node.func,ast.Attribute) and node.func.attr=='active_workload')
function=eval(compile(ast.Expression(body=call.func),'actual-framebuffer-helper','eval'),scope)
assert callable(function) and not function('')
checks+=1
owner=Path(__file__).parent/'android-owner'
service=(owner/'WindowsVmOwnerService.java').read_text()
runner=(owner/'run-service-test.ps1').read_text()
activity=(owner/'WindowsDiagnosticActivity.java').read_text()
assert 'if(windowsRamGop&&(!psciCachePublish||!fixedEightCpuGic||!matchHostCpus||!resumeWindowsInstallTarget||rtcClockShim||dynamicGic))' in service
assert '359DED6E811F23B011566938B6F3CC8A01DF480D5E8225A320FEFE357CD98E3A' in service
assert 'ownerIntent.putExtra("windows_ram_gop"' in activity
assert "'--ei','display_width','1920','--ei','display_height','1200'" in runner
assert "$env:UAVF_WINDOWS_RAM_GOP -eq '1' -and (!$FixedEightCpuGic -or !$MatchHostCpus" in runner
print(f'ACTUAL_RAM_GOP_OBSERVER_GATE_CASES={checks} PASS; APK_SOURCE_WIRING_AND_NATIVE_DIMENSIONS=PASS')
