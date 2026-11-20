"""Generate a native debugger version of the EXISTING guarded RAM adapters.

No guest/Android writes, image rebuild, kernel modification on disk or new fix.
Microsoft KD executes the same byte writes; exact readback is mandatory.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import struct

p = argparse.ArgumentParser()
p.add_argument("--workspace", type=Path, required=True)
p.add_argument("--output", type=Path, required=True)
a = p.parse_args()
assert not a.output.exists(), "Preserve earlier preparation"
kernel = (a.workspace / "build-logs/windows-arm64-timer-static-20260913/ntoskrnl.exe").read_bytes()
assert hashlib.sha256(kernel).hexdigest().upper() == "C667739004D1186DACA2FD3FE7CCDE61394BC068F385B4BB8CD5364D3005A734"
pe = struct.unpack_from("<I", kernel, 60)[0]
count, opt = struct.unpack_from("<H", kernel, pe+6)[0], struct.unpack_from("<H", kernel, pe+20)[0]
sections = [struct.unpack_from("<IIII", kernel, pe+24+opt+40*i+8) for i in range(count)]


def image(rva, size):
    for virtual_size, start, raw_size, raw in sections:
        end = start+max(virtual_size, raw_size)
        if start == 0x200000:
            end = (start+virtual_size+4095) & ~4095
            assert all(other_start >= end for _, other_start, _, _ in sections if other_start > start)
        if start <= rva and rva+size <= end:
            data = kernel[raw+rva-start:raw+min(rva-start+size, raw_size)] if rva-start < raw_size else b""
            return data + bytes(size-len(data))
    raise ValueError("Outside exact kernel image")


def branch(source, target):
    delta = target-source
    assert delta % 4 == 0 and -(1 << 27) <= delta < (1 << 27)
    return 0x14000000 | ((delta//4) & 0x3ffffff)


patches = []


def patch(name, rva, original, changed):
    assert image(rva, len(original)) == original and len(original) == len(changed)
    patches.append(dict(name=name, rva=rva, original=list(original), changed=list(changed)))


for rva, old, new in ((0xc03c31, 12, 8), (0xc03c32, 15, 11), (0xc03c38, 20, 16)):
    patch("CAM shift", rva, bytes([old]), bytes([new]))
immediate = ((0xe03000//4096)-(0x665b20//4096)) & 0x1fffff
adrp = 0x90000015 | ((immediate & 3) << 29) | ((immediate >> 2) << 5)
cam = struct.pack("<4I", 0x12002c10, 0x0b100294, adrp, branch(0x665b2c, 0x49baf0))
patch("CAM offset thunk", 0x665b20, bytes(16), cam)
patch("CAM offset branch", 0x49baec, struct.pack("<I", 0x90004b55), struct.pack("<I", branch(0x49baec, 0x665b20)))
patch("CAM bound", 0x49bacc, struct.pack("<I", 0x7140051f), struct.pack("<I", 0x7104011f))
timer = struct.pack("<6I", 0xd53be330, 0x36000090, 0x927efa10, 0xd51be330, 0xd5033fdf, branch(0x665b14, 0x213910))
patch("Existing CNTV ACK thunk", 0x665b00, bytes(24), timer)
patch("Existing CNTV ACK branch", 0x213968, struct.pack("<I", 0x17ffffea), struct.pack("<I", branch(0x213968, 0x665b00)))
spec = importlib.util.spec_from_file_location("pmcc", Path(__file__).with_name("pmu-emulation-thunk.py"))
pmcc = importlib.util.module_from_spec(spec); spec.loader.exec_module(pmcc)
thunk, _ = pmcc.load_thunk(a.workspace)
patch("Existing EL0 PMCC trap adapter", pmcc.CAVE, bytes(len(thunk)), thunk)
patch("Existing EL0 PMCC branch", pmcc.SOURCE, bytes.fromhex("7f2303d5"), struct.pack("<I", pmcc.branch(pmcc.SOURCE, pmcc.CAVE)))
patch("Existing diagnostic notification quieting", 0x2fbfa0, bytes.fromhex("7f2303d5"), bytes.fromhex("c0035fd6"))
anchors = [dict(rva=rva, bytes=list(image(rva, 4))) for rva in (0x49bc4c, 0x49bc60, 0x49bc70)]
identity = dict(pe=pe, timestamp=struct.unpack_from("<I", kernel, pe+8)[0],
                imageSize=struct.unpack_from("<I", kernel, pe+80)[0])
javascript = '"use strict";\nconst patches=' + json.dumps(patches) + ';\nconst anchors=' + json.dumps(anchors) + ';\nconst identity=' + json.dumps(identity) + ';\n' + r'''
let applied=false;
function command(text) { for (const line of host.namespace.Debugger.Utility.Control.ExecuteCommand(text)) host.diagnostics.debugLog(line,"\n"); }
function bytes(at,n) { return Array.from(host.memory.readMemoryValues(at,n,1,false)); }
function equal(a,b) { return a.length===b.length && a.every((v,i)=>v===b[i]); }
function expect(at,wanted,label) { if (!equal(bytes(at,wanted.length),wanted)) throw new Error("NATIVE_GUARD_FAILED "+label); }
function word(at) { return Number(host.memory.readMemoryValues(at,1,4,false)[0]); }
function onKernel() {
    command("r @$t19=0"); // Fail closed: outer command resumes only on success/defer.
    let base;
    try {
        for (const line of host.namespace.Debugger.Utility.Control.ExecuteCommand("lm m nt")) {
            const match=/^\s*([0-9a-f`]+)\s+[0-9a-f`]+\s+nt\s/i.exec(line);
            if (match) {base=host.parseInt64(match[1].replace(/`/g,""),16);break;}
        }
        if (!base) throw new Error("Kernel module not present");
    }
    catch (error) {host.diagnostics.debugLog("NATIVE_WAIT_KERNEL_MODULE\n");command("r @$t19=1");return false;}
    const pc=host.evaluateExpression("@$ip");
    if (typeof pc==="number" || pc.compareTo(host.parseInt64("ffff000000000000",16))<0) {
        host.diagnostics.debugLog("NATIVE_WAIT_KERNEL_EXECUTION\n");command("r @$t19=1");return false;
    }
    expect(base,[0x4d,0x5a],"MZ");
    if (word(base.add(60))!==identity.pe || word(base.add(identity.pe+8))!==identity.timestamp || word(base.add(identity.pe+80))!==identity.imageSize) throw new Error("NATIVE_KERNEL_IDENTITY_FAILED");
    if (patches.every(p=>equal(bytes(base.add(p.rva),p.changed.length),p.changed))) {
        command("r @$t19=1");return true;
    }
    for (const guard of anchors) expect(base.add(guard.rva),guard.bytes,"anchor");
    // Preflight EVERY original byte before the first write, including cave padding.
    for (const patch of patches) expect(base.add(patch.rva),patch.original,patch.name);
    for (const patch of patches) {
        for (let offset=0;offset<patch.changed.length;offset+=32) {
            const part=patch.changed.slice(offset,offset+32), at=base.add(patch.rva+offset);
            command("eb "+at.toString(16)+" "+part.map(v=>("0"+v.toString(16)).slice(-2)).join(" "));
            expect(at,part,"readback "+patch.name);
        }
        host.diagnostics.debugLog("NATIVE_RAM_READBACK_PASS ",patch.name,"\n");
    }
    applied=true;
    host.diagnostics.debugLog("NATIVE_KERNEL_PATCH_PASS base=",base,"\n");
    command("r @$t19=1");
    return true;
}
function initializeScript() {return [new host.apiVersionSupport(1,3)];}
'''
a.output.mkdir(parents=True)
script = (a.output / "native-windows-ram-adapters.js").resolve()
script.write_text(javascript)
provider = Path(r"C:\Program Files\WindowsApps\Microsoft.WinDbg_1.2606.22001.0_x64__8wekyb3d8bbwe\amd64\winext\JsProvider.dll")
assert provider.is_file(), "Use existing installed debugger provider; no environment setup/download"
commands = '.sympath ' + str(a.output.resolve()) + '\n.load JsProvider\n.scriptload "' + str(script) + '"\nsxe -c "dx @$scriptContents.onKernel(); .if (@$t19==1) {g}" ld:ntoskrnl.exe\ndx @$scriptContents.onKernel()\n.if (@$t19==1) {g}\n'
(a.output / "native-bootstrap.cmd").write_text(commands)
(a.output / "manifest.json").write_text(json.dumps(dict(identity=identity, patches=patches,
    scope="Same isolated RAM-only adaptations as existing Python observer; no guest writes during preparation", guest_writes=0), indent=2))
print("EXACT_EXISTING_NATIVE_RAM_ADAPTER_PREPARATION=PASS; RUNTIME_UNTESTED")
