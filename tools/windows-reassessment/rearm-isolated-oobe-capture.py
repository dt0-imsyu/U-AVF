r"""One-shot cold recovery of our own Setup capture wrapper, never Setup flags.

An already-clean native specialize completed before the old one-shot wrapper
could persist across reboot. Change only SYSTEM\Setup\CmdLine inside its
existing cell, with a full semantic diff and byte-for-byte rollback input.
Not a production registry editor; future boots use native reg in the wrapper.
"""
import argparse
import hashlib
import importlib.util
import io
import json
import struct
import subprocess
import tempfile
import uuid
from pathlib import Path

from Registry import Registry


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", required=True)
    parser.add_argument("--original", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    assert not args.output.exists(), "Preserve earlier recovery evidence"
    folder = Path(__file__).parent
    evidence = load("installed_evidence", folder / "extract-installed-setup-evidence.py")
    bcd = load("isolated_bcd", folder / "isolated-device-bcd.py")
    inventory = load("hive_inventory", folder / "prepare-winpe-bcd-recovery.py").inventory
    guard = bcd.DeviceFat(args.adb, args.output.parent)
    volume = evidence.ColdRoot(args.adb)
    boot = volume.read(512)
    assert boot[3:11] == b"NTFS    " and struct.unpack_from("<I", boot, 72)[0] == 0x3EF669B7
    volume.seek(0)
    fs = evidence.NTFS(volume)
    stream = fs.mft.get("Windows\\System32\\config\\SYSTEM").open()
    assert type(stream).__name__ == "RunlistStream" and stream.block_size == 4096
    original = args.original.read_bytes()
    assert len(original) == stream.size == 7864320
    assert stream.read(stream.size) == original, "Cold hive differs from retained extraction"
    assert original[:4] == b"regf" and original[4:8] == original[8:12], "Clean checkpoint required"
    registry = Registry.Registry(io.BytesIO(original))
    setup = registry.open("Setup")
    assert setup.value("OOBEInProgress").value() == 1
    assert setup.value("SystemSetupInProgress").value() == 0
    assert setup.value("SetupType").value() == 2
    assert registry.open("Setup\\Status\\SysprepStatus").value("GeneralizationState").value() == 7
    assert setup.value("UAVFOriginalCmdLine").value() == "oobe\\windeploy.exe"
    value = setup.value("CmdLine")
    assert value.value() == "oobe\\windeploy.exe" and value.value_type() == Registry.RegSZ
    payload = ("cmd.exe /d /c uavf-firstboot.cmd\0").encode("utf-16le")
    data_offset = value._vkrecord.data_offset() + 4
    record_offset = value._vkrecord.offset()
    cell_size = -struct.unpack_from("<i", original, data_offset - 4)[0]
    assert cell_size == 72 and len(payload) <= cell_size - 4
    changed = bytearray(original)
    changed[data_offset:data_offset + len(payload)] = payload
    struct.pack_into("<I", changed, record_offset + 4, len(payload))
    parsed = Registry.Registry(io.BytesIO(changed))
    before, after = inventory(registry), inventory(parsed)
    differences = [k for k in before if before[k] != after.get(k)]
    assert set(before) == set(after) and differences == ["\\Setup\\CmdLine"]
    assert changed[:4096] == original[:4096]
    args.output.mkdir(parents=True)
    (args.output / "SYSTEM-original.hiv").write_bytes(original)
    (args.output / "SYSTEM-own-capture-rearmed.hiv").write_bytes(changed)

    def physical(offset, size):
        logical = 0
        for lcn, count in stream.runlist:
            length = count * stream.block_size
            if logical <= offset < logical + length:
                assert lcn is not None and offset + size <= logical + length
                return evidence.ROOT_START + lcn * stream.block_size + offset - logical
            logical += length
        raise AssertionError("No backed run for patch")

    patches = [(data_offset, len(payload)), (record_offset + 4, 4)]
    report = dict(scope="Existing own Setup CmdLine only; no success flags", semantic_diff=differences,
                  original_sha256=hashlib.sha256(original).hexdigest(),
                  changed_sha256=hashlib.sha256(changed).hexdigest(), apply=args.apply)
    if args.apply:
        remote = "/data/local/tmp/uavf-own-hook-" + uuid.uuid4().hex + ".bin"
        def write(offset, data):
            guard.cold()
            address = physical(offset, len(data))
            assert evidence.ROOT_START <= address < evidence.ROOT_START + evidence.ROOT_BYTES
            with tempfile.TemporaryDirectory(prefix="uavf-own-hook-") as temp:
                chunk = Path(temp) / "chunk.bin"
                chunk.write_bytes(data)
                subprocess.run([args.adb, "push", str(chunk), remote], check=True, capture_output=True)
                subprocess.run([args.adb, "shell", "chmod", "644", remote], check=True)
                subprocess.run([args.adb, "shell", "run-as", evidence.PACKAGE, "dd", "if=" + remote,
                                "of=" + evidence.DISK, "bs=1", "seek=" + str(address), "conv=notrunc"],
                               check=True, capture_output=True)
            check = subprocess.check_output([args.adb, "exec-out",
                f"run-as {evidence.PACKAGE} dd if={evidence.DISK} bs=1 skip={address} count={len(data)} 2>/dev/null"])
            assert check == data, "Exact range readback failed"
        try:
            for offset, size in patches:
                write(offset, bytes(changed[offset:offset + size]))
            report["result"] = "OWN_CAPTURE_REARM_READBACK_PASS"
        except Exception:
            for offset, size in reversed(patches):
                write(offset, original[offset:offset + size])
            raise
        finally:
            subprocess.run([args.adb, "shell", "rm", "-f", remote], check=True)
    else:
        report["result"] = "SEMANTIC_PREFLIGHT_PASS"
    (args.output / "report.json").write_text(json.dumps(report, indent=2))
    print(json.dumps(report), flush=True)


if __name__ == "__main__":
    main()
