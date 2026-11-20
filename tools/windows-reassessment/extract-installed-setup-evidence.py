"""Cold, read-only bounded NTFS evidence from the isolated Windows test root.

Uses dissect.ntfs; no mount, disk copy, device writes or production access.
"""
import argparse
import hashlib
import importlib.util
import io
import json
import struct
import subprocess
from pathlib import Path

from dissect.ntfs import NTFS

ROOT_START = 17858560 * 512
ROOT_BYTES = (150978559 + 1) * 512 - ROOT_START
PACKAGE = "com.example.uavfwinpetest"
DISK = "files/windows-diagnostic.img"


class ColdRoot(io.RawIOBase):
    def __init__(self, adb):
        self.adb, self.pos, self.cache = adb, 0, {}
        self.read_count, self.read_bytes = 0, 0

    def readable(self):
        return True

    def writable(self):
        return False

    def seekable(self):
        return True

    def tell(self):
        return self.pos

    def seek(self, offset, whence=0):
        self.pos = offset if whence == 0 else (self.pos if whence == 1 else ROOT_BYTES) + offset
        assert 0 <= self.pos <= ROOT_BYTES
        return self.pos

    def read(self, size=-1):
        assert 0 <= size <= 8 * 1024 * 1024, "Unbounded parser read refused"
        result = bytearray()
        while size and self.pos < ROOT_BYTES:
            index, offset = divmod(self.pos, 65536)
            if index not in self.cache:
                assert self.read_count < 2048 and self.read_bytes < 64 * 1024 * 1024, "Read budget exhausted"
                count = min(65536, ROOT_BYTES - index * 65536)
                command = (f"run-as {PACKAGE} dd if={DISK} bs=512 "
                           f"skip={(ROOT_START + index * 65536) // 512} count={count // 512} 2>/dev/null")
                data = subprocess.check_output([self.adb, "exec-out", command], stderr=subprocess.DEVNULL)
                assert len(data) == count, "Exact binary read required"
                self.cache[index] = data
                self.read_count += 1
                self.read_bytes += count
            data = self.cache[index]
            take = min(size, len(data) - offset)
            result += data[offset:offset + take]
            self.pos += take
            size -= take
        return bytes(result)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--system-hive", action="store_true")
    parser.add_argument("--time-hive-only", action="store_true", help="Only SYSTEM and its transaction logs, for cold time-state analysis")
    parser.add_argument("--runtime-cpu-only", action="store_true", help="Only the existing own bounded CPU metadata log; no hive or user files")
    parser.add_argument("--broker-only", action="store_true", help="Only the existing owned display broker executable; no hive or user files")
    parser.add_argument("--boot-time-only", action="store_true", help="Only Windows bootstat.dat, read-only loader time-source evidence")
    parser.add_argument("--display-bootstrap", action="store_true", help="Read only existing own helper and SYSTEM recovery logs")
    args = parser.parse_args()
    assert not args.broker_only or not (args.system_hive or args.time_hive_only or args.runtime_cpu_only or args.boot_time_only or args.display_bootstrap), "Owned helper only"
    assert not args.boot_time_only or not (args.system_hive or args.time_hive_only or args.runtime_cpu_only or args.display_bootstrap), "Boot-time evidence only"
    assert not args.output.exists(), "Preserve earlier evidence"
    spec = importlib.util.spec_from_file_location("cold_bcd", Path(__file__).with_name("isolated-device-bcd.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    guard = module.DeviceFat(args.adb, args.output.parent)
    root = ColdRoot(args.adb)
    boot = root.read(512)
    assert boot[3:11] == b"NTFS    " and boot[510:512] == b"\x55\xaa"
    assert struct.unpack_from("<I", boot, 72)[0] == 0x3EF669B7, "Exact clean test root serial required"
    root.seek(0)
    fs = NTFS(root)
    args.output.mkdir(parents=True)
    files = ["ProgramData/UAVF/firstboot.txt", "ProgramData/UAVF/firstboot-state.txt",
             "Windows/Panther/setuperr.log", "Windows/Panther/setupact.log",
             "Windows/Panther/UnattendGC/setuperr.log", "Windows/Panther/UnattendGC/setupact.log"]
    if args.system_hive:
        files.append("Windows/System32/config/SYSTEM")
    if args.time_hive_only:
        assert not args.display_bootstrap, "Time evidence must not enumerate display logs"
        files = ["Windows/System32/config/SYSTEM", "Windows/System32/config/SYSTEM.LOG1",
                 "Windows/System32/config/SYSTEM.LOG2"]
    if args.runtime_cpu_only:
        assert not (args.time_hive_only or args.system_hive or args.display_bootstrap), "CPU metadata only"
        files = ["ProgramData/UAVF/runtime-cpu.log"]
    if args.boot_time_only:
        files = ["Windows/bootstat.dat"]
    if args.broker_only:
        files = ["Windows/System32/winpe-screen.exe"]
    own_records = {}
    if args.display_bootstrap:
        files.extend(["Windows/System32/config/SYSTEM.LOG1", "Windows/System32/config/SYSTEM.LOG2",
                      "Windows/System32/winpe-screen.exe", "ProgramData/UAVF/producer-restarts.txt"])
        files.append("ProgramData/UAVF/display-broker.log")
        files.append("ProgramData/UAVF/runtime-cpu.log")
        # Read only our bounded capture diagnostics, including the newest
        # producer generations. Never enumerate or copy user profile data.
        def own_logs(path, depth=0, record=None):
            if depth > 2:
                return
            try:
                directory = record if record is not None else fs.mft.get(path.replace("/", "\\"))
                entries = list(directory.iterdir(ignore_dos=True))
            except FileNotFoundError:
                return
            indexed = {entry.attribute.file_name: entry for entry in entries
                       if entry.attribute.file_name not in (".", "..")}
            for name in sorted(indexed)[-32:]:
                child = path + "/" + name
                child_record = indexed[name].dereference()
                if child_record.is_dir():
                    own_logs(child, depth + 1, child_record)
                elif name in ("vsock-live-report.txt", "vsock-fault-report.txt",
                              "live-encode-report.txt", "encoder-stderr.txt"):
                    files.append(child)
                    own_records[child] = child_record
        own_logs("ProgramData/UAVF/WindowsDisplay")
    report = []
    for name in files:
        try:
            record = own_records.get(name)
            if record is None:
                record = fs.mft.get(name.replace("/", "\\"))
            stream = record.open()
            limit = 32 * 1024 * 1024 if name.endswith("/SYSTEM") else 8 * 1024 * 1024
            chunks = []
            size = 0
            while True:
                chunk = stream.read(65536)
                if not chunk:
                    break
                size += len(chunk)
                assert size <= limit, "Evidence file bound exceeded"
                chunks.append(chunk)
            data = b"".join(chunks)
            target = args.output / name
            target.parent.mkdir(parents=True, exist_ok=True)
            with target.open("xb") as output:
                output.write(data)
            report.append(dict(file=name, bytes=len(data), sha256=hashlib.sha256(data).hexdigest()))
            print(json.dumps(report[-1]), flush=True)
        except FileNotFoundError:
            report.append(dict(file=name, missing=True))
            print(json.dumps(report[-1]), flush=True)
    guard.cold()
    report.append(dict(read_only=True, reads=root.read_count, device_bytes_read=root.read_bytes))
    with (args.output / "extraction-report.json").open("x") as output:
        json.dump(report, output, indent=2)


if __name__ == "__main__":
    main()
