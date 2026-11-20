"""Cold fixed-size replacement of two explicit isolated Windows test files.

Only existing own screenshot helper and SYSTEM with audited added service.
No NTFS/GPT/FAT metadata, signed Windows executables, Setup flags or user files.
Original files, changed-sector ranges and rollback inputs are retained locally.
"""
import argparse
import hashlib
import importlib.util
import json
import struct
import subprocess
import uuid
from pathlib import Path


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    return module


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", required=True)
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--prepared", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--helper-only", action="store_true", help="Replace own existing broker binary only; never write a registry hive")
    parser.add_argument("--encoder-only", action="store_true", help="Replace own existing diagnostic encoder only; never write a registry hive")
    parser.add_argument('--vsock-only',action='store_true',help='Existing OWN winpe-vsock helper, never viosock signed driver/provider')
    parser.add_argument("--rollback", action="store_true", help="Restore retained original files only if current candidate matches exactly")
    parser.add_argument('--native-gop',action='store_true',help='Preserve exact existing native1200p EFI, no firmware writes')
    parser.add_argument("--wizard-test", action="store_true",
                        help="Target the isolated 1.2 wizard test installation")
    args = parser.parse_args()
    assert sum((args.helper_only,args.encoder_only,args.vsock_only))<=1, "One explicitly scoped own executable"
    assert not args.output.exists(), "Preserve evidence"
    folder = Path(__file__).parent
    evidence = load("cold_root", folder / "extract-installed-setup-evidence.py")
    bcd = load("cold_bcd", folder / "isolated-device-bcd.py")
    expected_serial = 0x3ef669b7
    expected_efi = bcd.RAM_GOP_HANDOFF_AUDIT_EFI_SHA if args.native_gop else bcd.EFI_SHA
    if args.wizard_test:
        assert not (args.native_gop or args.helper_only or args.encoder_only or args.vsock_only)
        evidence.PACKAGE = bcd.PACKAGE = "com.example.winavf.embeddedwinpetest"
        evidence.DISK = bcd.DISK = "files/windows-25h2-setup.raw"
        expected_efi = bcd.EFI_SHA = "688299BB5CA7DD8DE60D69EA03D3049D946CB57BE6CAA6F0BE3CFEA11E6B4957"
        expected_serial = 0x66cc0332cc02fc55
    class DeviceFat(bcd.DeviceFat):
        def read(self, offset, size):
            assert 0 <= offset and 0 < size <= 65536 and offset + size <= bcd.END
            command = (f"run-as {bcd.PACKAGE} dd if={bcd.DISK} iflag=skip_bytes,count_bytes "
                       f"bs=8192 skip={offset} count={size} status=none")
            result = subprocess.check_output([self.adb, "exec-out", command])
            assert len(result) == size
            return result
    guard = DeviceFat(args.adb, args.output.parent, expected_efi_sha=expected_efi)
    if args.wizard_test:
        assert guard.file("/UAVF/BOOTRDY.TAG")[0].strip() == b"UAVF_WINDOWS_BOOT_PREPARED_V1"
    volume = evidence.ColdRoot(args.adb); boot = volume.read(512)
    assert boot[3:11] == b"NTFS    " and boot[510:512] == b"\x55\xaa"
    assert struct.unpack_from("<Q" if args.wizard_test else "<I", boot, 72)[0] == expected_serial
    volume.seek(0); fs = evidence.NTFS(volume)
    manifest = json.loads((args.prepared / "report.json").read_text())
    if args.wizard_test:
        for suffix in (".LOG1", ".LOG2"):
            name = "Windows/System32/config/SYSTEM" + suffix
            assert fs.mft.get(name.replace("/", "\\")).open().read() == (args.evidence / name).read_bytes()
    expected = 'OWNED_VSOCK_BINARY_PREFLIGHT_PASS' if args.vsock_only else ("OWNED_ENCODER_BINARY_PREFLIGHT_PASS" if args.encoder_only else ("OWNED_BROKER_BINARY_PREFLIGHT_PASS" if args.helper_only else "NATIVE_PRIVATE_HIVE_PREFLIGHT_PASS"))
    assert manifest["result"] == expected
    assert manifest["unrelated_values_changed"] == manifest["existing_key_security_changed"] == 0
    args.output.mkdir(parents=True)
    plans = []
    # Helper first; SYSTEM header last. No new file/extent is allocated.
    files = [("Windows/System32/winpe-screen.exe", "winpe-screen-service-slot.exe", "helper"),
             ("Windows/System32/config/SYSTEM", "SYSTEM-display-service-fixed.hiv", "system")]
    if args.helper_only:
        files = files[:1]
    elif args.encoder_only:
        files = [("Windows/System32/winpe-encode.exe", "winpe-encode-observation-slot.exe", "encoder")]
    elif args.vsock_only:
        files = [("Windows/System32/winpe-vsock.exe", "winpe-vsock-observation-slot.exe", "vsock")]
    for name, candidate, label in files:
        original = (args.evidence / name).read_bytes()
        changed = (args.prepared / candidate).read_bytes()
        assert sha(original) == manifest["original_" + label + "_sha256"]
        assert sha(changed) == manifest["changed_" + label + "_sha256"]
        if args.rollback:
            original, changed = changed, original
        assert len(original) == len(changed) and len(original) % 512 == 0
        stream = fs.mft.get(name.replace("/", "\\")).open()
        assert type(stream).__name__ == "RunlistStream" and stream.block_size == 4096
        assert stream.size == len(original) and stream.read(stream.size) == original
        ranges = []
        logical = 0
        for lcn, count in stream.runlist:
            extent = count * 4096
            assert lcn is not None, "No sparse/compressed/encrypted extent"
            for offset in range(logical, min(logical + extent, len(original)), 65536):
                end = min(offset + 65536, logical + extent, len(original))
                # Changed aligned sectors only; run boundaries never crossed.
                for page in range(offset, end, 512):
                    if original[page:page+512] != changed[page:page+512]:
                        address = evidence.ROOT_START + lcn * 4096 + page - logical
                        assert evidence.ROOT_START <= address < evidence.ROOT_START + evidence.ROOT_BYTES
                        if ranges and ranges[-1][0] + ranges[-1][2] == page and ranges[-1][1] + ranges[-1][2] == address and ranges[-1][2] < 65536:
                            a, b, n = ranges[-1]; ranges[-1] = (a, b, n+512)
                        else:
                            ranges.append((page, address, 512))
            logical += extent
        if label == "system":
            # Offreg changed header may share a contiguous body range.
            split = []
            for offset, address, length in ranges:
                if offset < 4096 < offset + length:
                    split.extend([(offset, address, 4096-offset), (4096, address+4096-offset, length-(4096-offset))])
                else:
                    split.append((offset, address, length))
            ranges = sorted(split, key=lambda item: (item[0] < 4096, item[0]))
        (args.output / (label + "-original.bin")).write_bytes(original)
        (args.output / (label + "-candidate.bin")).write_bytes(changed)
        plans.append(dict(name=name, label=label, original=original, changed=changed, ranges=ranges))
        print(json.dumps(dict(phase="PLAN", file=name, sectors=sum(n//512 for _, _, n in ranges), ranges=len(ranges))), flush=True)
    report = dict(scope="Audited fixed existing test files only", apply=args.apply, rollback=args.rollback,
                  files=[dict(file=p["name"], ranges=p["ranges"], original_sha256=sha(p["original"]), changed_sha256=sha(p["changed"])) for p in plans])
    (args.output / "plan.json").write_text(json.dumps(report, indent=2))
    if not args.apply:
        print("FIXED_FILE_PREFLIGHT_PASS"); return
    remote = "/data/local/tmp/uavf-display-service-" + uuid.uuid4().hex + ".bin"
    def write_plan(plan, key):
        guard.cold()
        host = args.output / (plan["label"] + ("-candidate.bin" if key == "changed" else "-original.bin"))
        subprocess.run([args.adb, "push", str(host), remote], check=True, capture_output=True)
        subprocess.run([args.adb, "shell", "chmod", "644", remote], check=True, capture_output=True)
        for offset, address, size in plan["ranges"]:
            guard.cold()
            subprocess.run([args.adb, "shell", "run-as", evidence.PACKAGE, "dd", "if=" + remote,
                            "of=" + evidence.DISK, "bs=512", "skip=" + str(offset//512),
                            "seek=" + str(address//512), "count=" + str(size//512), "conv=notrunc"], check=True, capture_output=True)
    def verify(key):
        guard.cold(); check_volume = evidence.ColdRoot(args.adb); check_fs = evidence.NTFS(check_volume)
        for plan in plans:
            stream = check_fs.mft.get(plan["name"].replace("/", "\\")).open()
            assert stream.size == len(plan[key]) and stream.read(stream.size) == plan[key], "Full file binary readback failed"
    try:
        for plan in plans:
            write_plan(plan, "changed")
            print(json.dumps(dict(phase="FILE_WRITTEN", file=plan["name"])), flush=True)
        verify("changed")
        report["result"] = "DISPLAY_SERVICE_COLD_ROLLBACK_READBACK_PASS" if args.rollback else "DISPLAY_SERVICE_COLD_BINARY_READBACK_PASS"
    except Exception:
        for plan in reversed(plans):
            write_plan(plan, "original")
        verify("original")
        report["result"] = "ROLLED_BACK_BINARY_READBACK_PASS"
        (args.output / "report.json").write_text(json.dumps(report, indent=2))
        raise
    finally:
        subprocess.run([args.adb, "shell", "rm", "-f", remote], check=True, capture_output=True)
    (args.output / "report.json").write_text(json.dumps(report, indent=2))
    print(report["result"], flush=True)


if __name__ == "__main__":
    main()
