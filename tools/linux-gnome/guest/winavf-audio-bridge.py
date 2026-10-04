#!/usr/bin/python3
"""Capture the guest desktop mix and stream fixed-format PCM to the host."""
import os
import subprocess
import sys
import time


RATE = 48_000
CHANNELS = 2
SINK = "winavf_output"


def run_pactl(*args):
    return subprocess.run(["pactl", *args], check=True, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, text=True).stdout.strip()


def wait_for_pulse():
    for attempt in range(180):
        try:
            run_pactl("info")
            return
        except (OSError, subprocess.CalledProcessError):
            time.sleep(1)
    raise RuntimeError("PipeWire/PulseAudio did not become available")


def ensure_capture_sink():
    sinks = run_pactl("list", "short", "sinks")
    if not any(line.split() and line.split()[1] == SINK for line in sinks.splitlines()):
        run_pactl("load-module", "module-null-sink", f"sink_name={SINK}",
                  f"rate={RATE}", f"channels={CHANNELS}", "format=s16le")
    run_pactl("set-default-sink", SINK)
    # GNOME may have opened playback streams before the host connects. Move
    # those existing streams too; changing the default alone affects only new
    # streams.
    for line in run_pactl("list", "short", "sink-inputs").splitlines():
        fields = line.split()
        if fields:
            run_pactl("move-sink-input", fields[0], SINK)
    return f"{SINK}.monitor"


def main():
    wait_for_pulse()
    monitor = ensure_capture_sink()
    source = subprocess.Popen(
        ["parec", "--raw", "--format=s16le", f"--rate={RATE}",
         f"--channels={CHANNELS}", f"--device={monitor}",
         "--latency-msec=30", "--process-time-msec=10"],
        stdout=subprocess.PIPE, stderr=None, bufsize=0)
    try:
        # 16-byte little-endian stream header:
        # magic, version, channels, rate, sample-format ID, reserved.
        sys.stdout.buffer.write(b"UAVF\x01\x00\x02\x00" +
                                RATE.to_bytes(4, "little") +
                                b"\x01\x00\x00\x00")
        sys.stdout.buffer.flush()
        while True:
            block = source.stdout.read(4 * 1024)
            if not block:
                return 0
            sys.stdout.buffer.write(block)
            sys.stdout.buffer.flush()
    finally:
        if source.poll() is None:
            source.terminate()
            try:
                source.wait(timeout=2)
            except subprocess.TimeoutExpired:
                source.kill()
                source.wait()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except BrokenPipeError:
        raise SystemExit(0)
    except Exception as error:
        print(f"U-AVF audio bridge: {error}", file=sys.stderr, flush=True)
        raise SystemExit(1)
