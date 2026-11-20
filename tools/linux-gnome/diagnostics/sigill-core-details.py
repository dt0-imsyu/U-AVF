"""Read existing ELF/core headers and core auxv; no application/CPU changes."""
import json, pathlib, struct, subprocess, urllib.request
report = {}
for name, exe, pc in [('snap-store','/snap/snap-store/1428/bin/snap-store',0xf98a78a89038),('vkmark','/usr/bin/vkmark',0xf4a14e831038)]:
    core = '/tmp/uavf-sigill-cores/'+name+'.core'
    entry = {}
    for kind, path in [('executable',exe),('core',core)]:
        with open(path,'rb') as f:
            h = f.read(64)
            e = struct.unpack('<16sHHIQQQIHHHHHH',h)
            info = {'machine':e[2], 'type':e[1], 'class':h[4], 'endianness':h[5]}
            if kind == 'core':
                segments = []
                for i in range(e[10]):
                    f.seek(e[5]+i*e[9]); p=struct.unpack('<IIQQQQQQ',f.read(56))
                    if p[0]==1 and p[3]<=pc<p[3]+p[6]:
                        segments.append({'start':hex(p[3]),'end':hex(p[3]+p[6]),'flags':p[1],'file_bytes':p[5]})
                info['fault_pc_load_segments']=segments
            else:
                sections=[]
                if e[12] and e[13]<e[12]:
                    f.seek(e[6]+e[13]*e[11]); s=struct.unpack('<IIQQQQIIQQ',f.read(64))
                    f.seek(s[4]); strings=f.read(s[5])
                    for i in range(e[12]):
                        f.seek(e[6]+i*e[11]); s=struct.unpack('<IIQQQQIIQQ',f.read(64))
                        nm=strings[s[0]:].split(b'\0',1)[0].decode(errors='replace')
                        if 'attr' in nm.lower() or 'note' in nm.lower():
                            sections.append({'name':nm,'type':s[1],'size':s[5]})
                info['attributes_and_notes']=sections
            entry[kind]=info
    p=subprocess.run(['gdb','-q','-batch',exe,core,'-ex','info auxv','-ex','info files'],text=True,capture_output=True,timeout=60)
    entry['gdb']={'stdout':p.stdout,'stderr':p.stderr[-5000:]}
    report[name]=entry
payload=json.dumps(report,indent=2).encode()
pathlib.Path('/tmp/uavf-sigill-cores/details.json').write_bytes(payload)
urllib.request.urlopen(urllib.request.Request('http://10.159.93.215:39054/report',data=payload,method='POST'),timeout=20).read()
print('SIGILL_CORE_DETAILS_SAVED',flush=True)
