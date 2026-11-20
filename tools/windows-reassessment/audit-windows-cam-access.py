"""Exact-kernel static feasibility, not a PCI runtime fix or fake MCFG."""
import hashlib,json,struct
from pathlib import Path
root=Path(__file__).resolve().parents[3]
kernel=(root/'build-logs/windows-arm64-timer-static-20260913/ntoskrnl.exe').read_bytes()
assert hashlib.sha256(kernel).hexdigest().upper()=='C667739004D1186DACA2FD3FE7CCDE61394BC068F385B4BB8CD5364D3005A734'
pe=struct.unpack_from('<I',kernel,60)[0]
n=struct.unpack_from('<H',kernel,pe+6)[0];opt=struct.unpack_from('<H',kernel,pe+20)[0]
sections=[struct.unpack_from('<IIII',kernel,pe+24+opt+40*i+8) for i in range(n)]
def data(rva,length):
    for virtual,start,size,offset in sections:
        if start<=rva and rva+length<=start+size:return kernel[offset+rva-start:offset+rva-start+length]
    raise ValueError('Unbacked RVA')
guards={0x49bc4c:0x3970c508,0x49bc60:0x3970e108,0x49bc70:0x3970c908,
        0x49bc58:0x9ac82129,0x49bc64:0x9ac82148,0x49bc78:0x9ac82128}
for rva,word in guards.items():assert data(rva,4)==struct.pack('<I',word)
symbols={'HalpPciMcfgFunShift':0xc03c31,'HalpPciMcfgDevShift':0xc03c32,'HalpPciMcfgBusShift':0xc03c38}
defaults={name:data(rva,1)[0] for name,rva in symbols.items()}
assert defaults==dict(HalpPciMcfgFunShift=12,HalpPciMcfgDevShift=15,HalpPciMcfgBusShift=20)
report=dict(result='STATIC_VARIABLE_SHIFT_PASS',defaults=defaults,
    variables={name:hex(rva) for name,rva in symbols.items()},
    guards={hex(rva):hex(word) for rva,word in guards.items()},
    cam_candidate=dict(function_shift=8,device_shift=11,bus_shift=16),
    unresolved=['Need truthful PCI root/resource/INTx description from actual host FDT',
                'Determine initialization writes and all consumers before changing globals',
                'Verify config access width/256-byte bounds and PCI.sys path',
                'No runtime CAM translation/binding/IRQ PASS yet'],
    production_changes=False)
out=root/'build-logs/windows-post-linux-reassessment-20261004/windows-cam-access-static.json'
out.write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
