"""Read-only exact-image proof of observed smss PMCCNTR and KD UM flag access."""
import struct,hashlib,json
from pathlib import Path
root=Path(__file__).resolve().parents[3]
folder=root/'build-logs/windows-post-linux-reassessment-20261004'
def image(path):
    data=path.read_bytes();pe=struct.unpack_from('<I',data,60)[0]
    n=struct.unpack_from('<H',data,pe+6)[0];o=struct.unpack_from('<H',data,pe+20)[0]
    sections=[]
    for i in range(n):
        p=pe+24+o+i*40;v,va,s,raw=struct.unpack_from('<IIII',data,p+8)
        sections.append((va,v,s,raw,struct.unpack_from('<I',data,p+36)[0]))
    return data,sections
d,sections=image(root/'build-logs/windows-arm64-timer-static-20260913/ntoskrnl.exe')
assert hashlib.sha256(d).hexdigest().upper()=='C667739004D1186DACA2FD3FE7CCDE61394BC068F385B4BB8CD5364D3005A734'
refs=[];target=0xdc9a64
for va,v,s,raw,flags in sections:
    if not flags&0x20000000:continue
    for pos in range(0,s-4,4):
        word=struct.unpack_from('<I',d,raw+pos)[0]
        if word&0x9f000000!=0x90000000:continue
        imm=(((word>>5)&0x7ffff)<<2)|((word>>29)&3)
        if imm&(1<<20):imm-=1<<21
        page=((va+pos)&~4095)+(imm<<12)
        if page!=(target&~4095):continue
        reg=word&31
        for delta in range(4,32,4):
            if pos+delta+4>s:break
            load=struct.unpack_from('<I',d,raw+pos+delta)[0]
            if load&0xffc00000==0x39400000 and (load>>5)&31==reg and page+((load>>10)&4095)==target:
                refs.append(dict(adrp_rva=hex(va+pos),load_rva=hex(va+pos+delta),guard=d[raw+pos:raw+pos+delta+8].hex()))
smss,ss=image(folder/'user-isa-audit/smss.exe')
assert hashlib.sha256(smss).hexdigest().upper()=='B2129DA7BCE9CB1175E498EC270C4CA4532052A51098250F5AEA32CEDA03694D'
window=next(smss[raw+0x1252c-va:raw+0x1256c-va] for va,v,s,raw,f in ss if va<=0x1252c and 0x1256c<=va+s)
assert struct.unpack_from('<I',window,16)[0]==0xd53b9d08 and len(window)==64
assert refs,'No exact instruction consumer of KdIgnoreUmExceptions'
report=dict(smss_fault_rva='0x1253c',instruction='MRS x8, PMCCNTR_EL0',window=window.hex(),kd_flag_rva=hex(target),consumers=refs)
(folder/'smss-pmu-static-audit.json').write_text(json.dumps(report,indent=2));print(json.dumps(report))
