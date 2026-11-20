"""Read-only Windows binary/source reassessment; never opens a guest disk or VM."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess


def inspect_image(path, objdump):
    data = path.read_bytes()
    pe = struct.unpack_from('<I', data, 0x3c)[0]
    assert data[pe:pe + 4] == b'PE\0\0'
    machine = struct.unpack_from('<H', data, pe + 4)[0]
    optional = pe + 24
    assert machine == 0xaa64 and struct.unpack_from('<H', data, optional)[0] == 0x20b
    entry = struct.unpack_from('<I', data, optional + 16)[0]
    base = struct.unpack_from('<Q', data, optional + 24)[0]
    result = subprocess.run([str(objdump), '-d', str(path)],
                            capture_output=True, text=True, check=True)
    lines = result.stdout.splitlines()
    instructions = []
    for line in lines:
        match = re.match(r'\s*([0-9a-f]+):\s+([0-9a-f]{8})\s+(.*)', line)
        if match:
            instructions.append((int(match[1], 16), match[3], line.strip()))
    midr = []
    pfr0 = []
    for index, (address, asm, _) in enumerate(instructions):
        if re.search(r'\bmrs\b.*\bmidr_el1\b', asm):
            midr.append({'va': hex(address), 'context': [v[2] for v in
                         instructions[max(0, index - 3):index + 13]]})
        if re.search(r'\bmrs\b.*\bid_aa64pfr0_el1\b', asm):
            pfr0.append({'va': hex(address), 'context': [v[2] for v in
                          instructions[max(0, index - 1):index + 15]]})
    entry_lines = [line for address, asm, line in instructions
                   if base + entry <= address < base + entry + 0x1b0]
    sve_candidates = [line for address, asm, line in instructions
                      if re.search(r'\bz\d+\.|\bp\d+/[mz]', asm)]
    return {'path': str(path), 'sha256': hashlib.sha256(data).hexdigest().upper(),
            'image_base': hex(base), 'entry_rva': hex(entry),
            'entry_preferred_va': hex(base + entry),
            'midr_sites': midr, 'pfr0_sites': pfr0, 'entry_disassembly': entry_lines,
            'sve_linear_disassembly_candidate_count': len(sve_candidates),
            'sve_linear_disassembly_candidates': sve_candidates[:16],
            'limitations': 'Preferred VAs are not runtime PCs. Linear executable '
            'sections can contain literal pools; SVE candidates do not establish '
            'instructions, reachability or a fault. Entry absence cannot exclude '
            'SVE/other feature use deeper in Windows.'}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--workspace', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    ws = args.workspace.resolve()
    repo = ws / 'Non-root-Windows-VM'
    objdump = ws / 'tools/arm-gnu-toolchain-15.2/bin/aarch64-none-elf-objdump.exe'
    binaries = ws / 'build-logs/windows-arm64-timer-static-20260913'
    images = {name: inspect_image(binaries / name, objdump)
              for name in ('winload.efi', 'ntoskrnl.exe')}
    expected = {'winload.efi': 'F7747F4AC18CCD66EBF6A043D979CF30C7731114C27C33FF2FC3C0610F491D31',
                'ntoskrnl.exe': 'C667739004D1186DACA2FD3FE7CCDE61394BC068F385B4BB8CD5364D3005A734'}
    assert all(images[name]['sha256'] == digest for name, digest in expected.items())
    edk2 = ws / 'firmware-work/edk2'
    sources = [edk2 / 'OvmfPkg/Library/BaseCachingPciExpressLib/PciExpressLib.c',
               edk2 / 'DynamicTablesPkg/Library/FdtHwInfoParserLib/Pci/PciConfigSpaceParser.c',
               edk2 / 'ArmVirtPkg/KvmtoolCfgMgrDxe/ConfigurationManager.c']
    fingerprints = {str(p): hashlib.sha256(p.read_bytes()).hexdigest().upper() for p in sources}
    # Source-proven CAM versus standard ECAM: no MMIO is issued by this test.
    addresses = [{'bdf': f'00:{device:02x}.0',
                  'cam': hex(0x2e000000 + (device << 11)),
                  'ecam': hex(0x2e000000 + (device << 15)),
                  'same': device == 0} for device in (0, 1, 5)]
    report = {'scope': 'offline only; no guest mutation or runtime A/B',
              'images': images, 'source_fingerprints': fingerprints,
              'cam_vs_ecam_address_comparison': addresses,
              'verdict': 'Same-base MCFG would address the wrong functions/devices. '
              'This establishes why a fake ECAM description is invalid, not why '
              'Windows stalls. No observed Windows kernel-entry PC exists.'}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2), encoding='utf-8')
    for name, image in images.items():
        print(name, 'SHA=PASS', 'ENTRY=' + image['entry_preferred_va'],
              'MIDR_READ_SITES=' + str(len(image['midr_sites'])),
              'SVE_LINEAR_CANDIDATES=' + str(image['sve_linear_disassembly_candidate_count']))
    print('CAM_VS_ECAM=' + json.dumps(addresses))
    print('REPORT=' + str(args.output))


if __name__ == '__main__':
    main()
