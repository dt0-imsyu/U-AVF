"""Compare actual single-checkpoint KD observations, never inferred launch state."""
import argparse
import json
import re
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument("shell", type=Path)
p.add_argument("app", type=Path)
p.add_argument("output", type=Path)
a = p.parse_args()
assert not a.output.exists(), "Preserve previous comparison"

def collect(path):
    events = [json.loads(line) for line in path.read_text().splitlines() if line.strip()]
    tags = {tag: [e for e in events if e.get("tag") == tag] for tag in (
        "BOOT_MILESTONE_REACHED", "BOOT_RUNTIME_REGISTERS",
        "BOOT_RUNTIME_SNAPSHOT_RESTORED", "BOOT_GIC_MMIO_STATE", "LOADER_RUNTIME_MMIO", "OBSERVER_STOP")}
    assert len(tags["BOOT_MILESTONE_REACHED"]) <= 1, "Not a single-checkpoint run"
    milestone = next(iter(tags["BOOT_MILESTONE_REACHED"]), None)
    state = next(iter(tags["BOOT_RUNTIME_REGISTERS"]), None)
    return dict(path=str(path), milestone=milestone, state=state,
                restored=bool(tags["BOOT_RUNTIME_SNAPSHOT_RESTORED"]),
                gic_reads=tags["BOOT_GIC_MMIO_STATE"], terminal=tags["OBSERVER_STOP"],
                loader_reads=tags["LOADER_RUNTIME_MMIO"],
                viosock_loads=[e for e in events if e.get("tag") == "LOAD_STATE"
                              and "viosock" in e.get("path", "").lower()],
                firmware_markers=re.findall(rb"WG:[^\r\n\x00]{1,150}",
                    (path.parent / "rx.bin").read_bytes()))

shell, app = collect(a.shell), collect(a.app)
for run in (shell, app):
    run["firmware_markers"] = [x.decode("ascii", "replace") for x in run["firmware_markers"]]
comparison = dict(shell=shell, app=app, differences=[], comparable=False,
                  caveat="Missing KD event is not proof that guest did not execute the checkpoint")
if shell["state"] and app["state"]:
    assert shell["state"]["milestone"] == app["state"]["milestone"]
    assert shell["state"]["valid"] and app["state"]["valid"]
    comparison["comparable"] = True
    comparison["rollback_verified_both"] = shell["restored"] and app["restored"]
    if not comparison["rollback_verified_both"]:
        comparison["rollback_caveat"] = "Actual captured registers may be compared; subsequent boot is censored by unverified observer restoration"
    for field in ("cntv_ctl", "cntfrq", "timer_enabled", "timer_masked", "timer_pending",
                  "midr", "daif", "icc_ctlr", "icc_pmr", "icc_igrpen1"):
        left, right = shell["state"][field], app["state"][field]
        if left != right:
            comparison["differences"].append(dict(field=field, shell=left, app=right))
    # Absolute clock/deadline values are retained above, not semantic differences.
    for field in ("cpsr", "daif_mask", "cam_source", "cam_thunk", "periodic_source", "periodic_thunk"):
        left, right = shell["milestone"][field], app["milestone"][field]
        if left != right:
            comparison["differences"].append(dict(field=field, shell=left, app=right,
                                                   caveat="Relocations in thunk bytes need interpretation"))
comparison["gic_mmio_caveat"] = "Failed MMIO reads are unknown, not zero or equal"
if shell["loader_reads"] and app["loader_reads"]:
    left = shell["loader_reads"][0]["samples"]
    right = app["loader_reads"][0]["samples"]
    comparison["loader_read_comparison"] = dict(
        same_statuses=left == right,
        actual_values_comparable=all(x["value"] is not None for x in left + right),
        caveat="Equal unsupported-read responses do not establish equal GIC hardware state")
comparison["firmware_marker_diff"] = dict(
    shell_only=[x for x in shell["firmware_markers"] if x not in app["firmware_markers"]],
    app_only=[x for x in app["firmware_markers"] if x not in shell["firmware_markers"]],
    caveat="Absent marker does not prove absent execution; no register comparison is inferred")
a.output.write_text(json.dumps(comparison, indent=2) + "\n")
print(json.dumps(dict(comparable=comparison["comparable"], differences=comparison["differences"],
                     shell_reached=bool(shell["milestone"]), app_reached=bool(app["milestone"]))))
