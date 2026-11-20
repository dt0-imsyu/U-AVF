"""Summarize bounded own CPU snapshots; never infer host latency from guest clocks."""
import argparse
import json
import re
from pathlib import Path


def parse(text):
    samples = []
    for line in text.splitlines():
        if line.startswith("CPU_SAMPLE "):
            values = {k: int(v) for k, v in re.findall(r"(\w+)=(\d+)", line)}
            if all(k in values for k in ("seq", "broker", "tick", "system_ok", "idle_100ns", "kernel_100ns", "user_100ns")):
                samples.append(dict(values, processes={}))
        elif samples and line.startswith("CPU_PROCESS "):
            match = re.fullmatch(r"CPU_PROCESS seq=(\d+) pid=(\d+) name=(.*?) threads=(\d+) ok=(\d+) error=(\d+) created_100ns=(\d+) kernel_100ns=(\d+) user_100ns=(\d+)", line)
            if match and int(match[1]) == samples[-1]["seq"]:
                samples[-1]["processes"][int(match[2])] = dict(
                    name=match[3], ok=int(match[5]), created=int(match[7]),
                    kernel=int(match[8]), user=int(match[9]))
    return samples


def summarize(samples):
    intervals = []
    for before, after in zip(samples, samples[1:]):
        if before["broker"] != after["broker"] or not before["system_ok"] or not after["system_ok"]:
            continue
        idle = after["idle_100ns"] - before["idle_100ns"]
        kernel = after["kernel_100ns"] - before["kernel_100ns"]
        user = after["user_100ns"] - before["user_100ns"]
        total = kernel + user  # GetSystemTimes kernel includes idle.
        if idle < 0 or kernel < idle or user < 0 or total <= 0:
            continue
        processes = []
        for pid, current in after["processes"].items():
            previous = before["processes"].get(pid)
            # PID reuse, new/terminated and inaccessible processes are unknown.
            if not previous or not current["ok"] or not previous["ok"] or current["created"] != previous["created"]:
                continue
            pk = current["kernel"] - previous["kernel"]
            pu = current["user"] - previous["user"]
            if pk < 0 or pu < 0:
                continue
            processes.append(dict(pid=pid, name=current["name"],
                                  cpu_percent=round(100*(pk+pu)/total, 3),
                                  kernel_100ns=pk, user_100ns=pu))
        accounted = sum(p["kernel_100ns"]+p["user_100ns"] for p in processes)
        busy = total-idle
        intervals.append(dict(broker=after["broker"], sequence=after["seq"],
                              processors=after.get("processors") if before.get("processors")==after.get("processors") else None,
                              guest_interval_ms=after["tick"]-before["tick"],
                              idle_percent=round(100*idle/total, 3),
                              busy_percent=round(100*busy/total, 3),
                              busy_kernel_percent=round(100*(kernel-idle)/total, 3),
                              user_percent=round(100*user/total, 3),
                              unaccounted_busy_percent=round(100*max(0, busy-accounted)/total, 3),
                              lsass_cpu_percent=next((p['cpu_percent'] for p in processes
                                                      if p['name'].lower() == 'lsass.exe'), None),
                              top_processes=sorted(processes, key=lambda p:p["cpu_percent"], reverse=True)[:12]))
    return dict(intervals=intervals, limitation="Guest CPU counters only. Unaccounted includes inaccessible/new/exited processes and interrupt/DPC time; not proof of interrupt storm. Guest clocks require independent host correlation.")


def self_test():
    text = "\n".join([
        "CPU_SAMPLE seq=0 broker=9 tick=100 system_ok=1 idle_100ns=0 kernel_100ns=0 user_100ns=0",
        "CPU_PROCESS seq=0 pid=4 name=System threads=20 ok=1 error=0 created_100ns=1 kernel_100ns=0 user_100ns=0",
        "CPU_SAMPLE seq=1 broker=9 tick=200 system_ok=1 idle_100ns=20 kernel_100ns=70 user_100ns=30",
        "CPU_PROCESS seq=1 pid=4 name=System threads=20 ok=1 error=0 created_100ns=1 kernel_100ns=10 user_100ns=20",
    ])
    report = summarize(parse(text))["intervals"][0]
    assert report["idle_percent"] == 20 and report["busy_kernel_percent"] == 50
    assert report["top_processes"][0]["cpu_percent"] == 30 and report["unaccounted_busy_percent"] == 50
    processor_text=text.replace('system_ok=1','processors=8 system_ok=1')
    assert summarize(parse(processor_text))['intervals'][0]['processors']==8
    mixed=parse(processor_text);mixed[0]['processors']=1
    assert summarize(mixed)['intervals'][0]['processors'] is None
    samples = parse(text);samples[1]["processes"][4]["created"] = 2
    assert not summarize(samples)["intervals"][0]["top_processes"]
    samples[1]["broker"] = 10
    assert not summarize(samples)["intervals"]
    print("CPU_COUNTER_PARSE_DELTA_IDLE_PID_REUSE_BOOT_BOUNDARY_PASS")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("log", type=Path, nargs="?")
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--output", type=Path, help="Preserve machine-readable evidence in a new file")
    args = parser.parse_args()
    if args.self_test:
        self_test()
    else:
        assert args.log and args.log.stat().st_size <= 1024*1024
        report = json.dumps(summarize(parse(args.log.read_text(errors="replace"))), indent=2)
        if args.output:
            with args.output.open('x', encoding='utf-8') as output:
                output.write(report + '\n')
        print(report)
