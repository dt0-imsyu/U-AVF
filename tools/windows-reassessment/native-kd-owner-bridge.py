"""Host-only native Microsoft KD <-> existing isolated app-owned UART channel.

No VM config/image changes, Android shell socket, or display decoder. Owner is
created by the unchanged launcher. Binary streams stay binary after capability
authentication. Firmware bytes gate debugger input; reconnect retains queued RX.
"""
import argparse
import json
from pathlib import Path
import queue
import re
import socket
import subprocess
import threading
import time
import importlib.util

pipe_spec = importlib.util.spec_from_file_location("native_kd_pipe", Path(__file__).with_name("native-kd-pipe.py"))
pipe_module = importlib.util.module_from_spec(pipe_spec)
pipe_spec.loader.exec_module(pipe_module)
tx_spec = importlib.util.spec_from_file_location("native_kd_tx", Path(__file__).with_name("native-kd-tx-batching.py"))
tx_module = importlib.util.module_from_spec(tx_spec)
tx_spec.loader.exec_module(tx_module)

p = argparse.ArgumentParser()
p.add_argument("--adb", required=True)
p.add_argument("--evidence", type=Path, required=True)
p.add_argument("--token-file", type=Path, required=True)
p.add_argument("--commands", type=Path, required=True)
p.add_argument("--kd", default=r"C:\Program Files\WindowsApps\Microsoft.WinDbg_1.2606.22001.0_x64__8wekyb3d8bbwe\amd64\kd.exe")
p.add_argument("--seconds", type=int, default=3600)
p.add_argument("--replay-ack-on-resend", action="store_true")
a = p.parse_args()
assert 120 <= a.seconds <= 3600 and Path(a.kd).is_file() and a.commands.is_file()
token = a.token_file.read_text().strip()
assert re.fullmatch("[0-9a-f]{64}", token)
assert a.evidence.is_dir() and not (a.evidence / "native-bridge.jsonl").exists()
events = (a.evidence / "native-bridge.jsonl").open("x")
event_lock = threading.Lock()


def note(tag, **fields):
    with event_lock:
        value = dict(t=round(time.monotonic(), 3), tag=tag, **fields)
        events.write(json.dumps(value)+"\n"); events.flush()
        print(json.dumps(value), flush=True)


connected, gate, stopping = threading.Event(), threading.Event(), threading.Event()
wire_lock = threading.Lock()
wire_log = (a.evidence / "native-wire-tx.bin").open("xb")
recovery = None
if a.replay_ack_on_resend:
    ack_spec = importlib.util.spec_from_file_location("native_kd_ack", Path(__file__).with_name("native-kd-ack-recovery.py"))
    ack_module = importlib.util.module_from_spec(ack_spec)
    ack_spec.loader.exec_module(ack_module)
    recovery = ack_module.AckRecovery()
pipe = pipe_module.NativePipe(stopping)
pipe_name = pipe.name
incoming = queue.Queue(maxsize=512)  # <=8MiB bounded; backpressure, never silently drop KD bytes.
deadline = time.monotonic()+a.seconds
network = None
kd = None


def read_greeting(stream):
    result = bytearray()
    while not result.endswith(b"\n") and len(result) < 64:
        byte = stream.recv(1)
        if not byte:
            raise EOFError("Owner greeting unavailable")
        result.extend(byte)
    assert result == b"UAVF_WINDOWS_OWNER_OK\n", "Authenticated isolated owner greeting"


def guest_reader():
    marker = b"WG:LOAD=PASS,START_WINDOWS"
    tail = b""
    try:
        with (a.evidence / "native-rx.bin").open("xb") as log:
            while not stopping.is_set():
                data = network.recv(16384)
                if not data:
                    raise EOFError("Owner UART channel closed")
                log.write(data); log.flush()
                if not gate.is_set():
                    tail += data
                    at = tail.find(marker)
                    if at < 0:
                        tail = tail[-len(marker):]
                        continue
                    data = tail[at+len(marker):]
                    tail = b""
                    gate.set(); note("NATIVE_GATE_OPEN")
                if data:
                    if recovery:
                        with wire_lock:
                            for ack in recovery.feed("rx", data):
                                network.sendall(ack)
                                wire_log.write(ack);wire_log.flush()
                                with (a.evidence / "native-recovery-tx.bin").open("ab") as recovered:
                                    recovered.write(ack)
                                note("NATIVE_CONTROL_ACK_REPLAY", id=hex(int.from_bytes(ack[8:12], "little")), attempt=recovery.replays)
                    incoming.put(data, timeout=30)
    except Exception as error:
        note("NATIVE_GUEST_READER_TERMINAL", error=str(error)); stopping.set()


def debugger_receiver():
    pending = b""
    try:
        while not stopping.is_set():
            if not pending:
                try:
                    pending = incoming.get(timeout=.5)
                except queue.Empty:
                    continue
            if not connected.wait(.5):
                continue
            try:
                count = pipe.write(pending)
            except OSError:
                connected.clear(); continue
            if not count:
                raise OSError("Native debugger pipe wrote zero bytes")
            pending = pending[count:]
    except Exception as error:
        note("NATIVE_PIPE_RX_TERMINAL", error=str(error)); stopping.set()


def debugger_sender():
    try:
        with (a.evidence / "native-tx.bin").open("xb") as log:
            batcher = tx_module.TxBatcher()
            while not stopping.is_set():
                pipe.connect()
                connected.set(); note("NATIVE_PIPE_CONNECTED")
                while not stopping.is_set():
                    try:
                        data = pipe.read()
                    except OSError:
                        break
                    if not data:
                        break
                    # Hold KD reset/break-in bytes until U-Boot hands off.
                    while not gate.wait(.5):
                        if stopping.is_set():
                            return
                    for packet in batcher.feed(data):
                        with wire_lock:
                            if recovery:
                                recovery.feed("tx", packet)
                            log.write(packet); log.flush(); network.sendall(packet)
                            wire_log.write(packet);wire_log.flush()
                tail = batcher.flush()
                if tail:
                    with wire_lock:
                        log.write(tail);log.flush();network.sendall(tail)
                        wire_log.write(tail);wire_log.flush()
                connected.clear(); pipe.disconnect()
                note("NATIVE_PIPE_RECONNECT_WAIT")
    except Exception as error:
        if not stopping.is_set():
            note("NATIVE_PIPE_TX_TERMINAL", error=str(error)); stopping.set()


try:
    end = time.monotonic()+30
    while True:
        try:
            network = socket.create_connection(("127.0.0.1", 39101), timeout=2)
            network.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            network.settimeout(10); network.sendall(token.encode("ascii")); read_greeting(network)
            break
        except (OSError, EOFError):
            if network:
                network.close()
            if time.monotonic() >= end:
                raise
            time.sleep(.5)
    identity = subprocess.check_output([a.adb, "shell", "cat /sdcard/Android/data/com.example.uavfwinpetest/files/service-owner-report.txt"], text=True)
    assert "IDENTITY_PASS uid=" in identity and "MUTABLE_INSTALL_TARGET_IDENTITY_PASS; EFI_WIM_FROZEN; NOT_INSTALLED_BOOT_PASS" in identity
    network.settimeout(None)
    note("NATIVE_OWNER_IDENTITY_PASS", scope="Same isolated owner/layout and existing byte channel")
    for method in (guest_reader, debugger_receiver, debugger_sender):
        threading.Thread(target=method, daemon=True).start()
    # Native CLI debugger is hidden; tokens never enter its command line/log.
    arguments = [a.kd, "-b", "-logo", str((a.evidence / "native-kd.log.txt").resolve()),
                 "-k", "com:pipe,port="+pipe_name+",baud=115200,resets=0,reconnect",
                 "-y", str(a.commands.resolve().parent),
                 "-i", str(a.commands.resolve().parent), "-cfr", str(a.commands.resolve())]
    kd = subprocess.Popen(arguments, stdin=subprocess.PIPE, stdout=subprocess.DEVNULL,
                          stderr=subprocess.DEVNULL, creationflags=subprocess.CREATE_NO_WINDOW)
    note("NATIVE_DEBUGGER_STARTED", pid=kd.pid)
    activated = False
    boot_epoch, boot_epoch_at, boot_commands_sent = 0, time.monotonic(), False
    while not stopping.is_set() and time.monotonic() < deadline and kd.poll() is None:
        logfile = a.evidence / "native-kd.log.txt"
        transcript = logfile.read_text(errors="replace") if logfile.exists() else ""
        epochs = transcript.count("Kernel Debugger connection established.")
        if epochs != boot_epoch:
            boot_epoch, boot_epoch_at, boot_commands_sent = epochs, time.monotonic(), False
        # DbgEng -cfr did not execute startup commands at the second BlBdStart.
        # Resume only a visible native boot breakpoint with the SAME guarded file.
        segment = transcript.rsplit("Kernel Debugger connection established.", 1)[-1]
        if (epochs > 1 and not boot_commands_sent and time.monotonic()-boot_epoch_at > 10
                and "brk         #0xF000" in segment and "kd> .sympath" not in segment):
            kd.stdin.write(("$$><"+str(a.commands.resolve())+"\n").encode("ascii"))
            kd.stdin.flush()
            boot_commands_sent = True
            note("NATIVE_GUARDED_COMMANDS_AT_BOOT_RECONNECT", epoch=epochs)
        if any(marker in transcript for marker in ("NATIVE_GUARD_FAILED", "NATIVE_KERNEL_IDENTITY_FAILED", "Unable to load script", "Script provider load failure")):
            note("NATIVE_ADAPTER_GUARD_STOP"); break
        if not activated and "NATIVE_KERNEL_PATCH_PASS" in transcript:
            activated = True
            # Existing viewer/common decoder only; no new display transport.
            subprocess.run([a.adb, "shell", "am", "start", "-f", "0x20000000", "-n",
                            "com.example.uavfwinpetest/com.example.winavf.WindowsDiagnosticActivity",
                            "--es", "token", token, "--ez", "start_display", "true"], check=True, capture_output=True)
            note("NATIVE_ADAPTERS_PASS_DISPLAY_ACTIVATED")
        time.sleep(.5)
    note("NATIVE_BRIDGE_STOP", adapters_pass=activated, debugger_exit=kd.poll())
finally:
    stopping.set()
    if kd and kd.poll() is None:
        kd.terminate(); kd.wait(timeout=10)
    if network:
        network.close()
    pipe.close()
    with wire_lock:
        wire_log.close()
