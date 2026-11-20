"""Actual native GIC snapshot: bounded ranges, no IRQ acknowledgements/writes."""
import ast
import struct
from pathlib import Path
tree=ast.parse(Path(__file__).with_name('trace-kd-boot-drivers.py').read_text())
functions=[n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='smp_gic_mapped_snapshot']
assert len(functions)==1
code=compile(ast.fix_missing_locations(ast.Module(body=functions,type_ignores=[])),'<actual-native-gic>','exec')
base=0xffff800000000000;context=base+0x300000;distributor=base+0x400000
class KD:
    def __init__(self,count=8,duplicate=False):self.reads=[];self.samples=[];self.count=count;self.duplicate=duplicate
    def read(self,address,length):
        self.reads.append((address,length))
        if address==base+0xdd2a68:assert length==8;return struct.pack('<Q',context)
        assert address==context and length==0x90+8*0x48
        data=bytearray(length);struct.pack_into('<Q',data,8,distributor);struct.pack_into('<I',data,0x48,self.count)
        for cpu in range(8):
            off=(cpu+2)*0x48
            struct.pack_into('<Q',data,off,cpu)
            struct.pack_into('<2Q',data,off+0x18,0x3fef0000+(0 if self.duplicate else 7-cpu)*0x20000,base+0x500000+cpu*0x20000)
        return bytes(data)
    def rpc(self,api,union,allow_status):
        assert api==0x3130 and allow_status
        address,length,flags=struct.unpack('<QII',union);assert length==4 and flags==0
        allowed={distributor}|{base+0x500000+cpu*0x20000+off for cpu in range(8) for off in (0x14,0x10080,0x10100,0x10200,0x10300,0x10400)}
        assert address in allowed;self.samples.append(address)
        return bytes(56)+bytes(4)
def image_bytes(rva,length):assert rva==0x4aefbc and length==8;return struct.pack('<2I',0x90004928,0xf9053513)
for count,duplicate in ((8,False),(9,False),(8,True)):
    kd=KD(count,duplicate);state=dict(kd=kd,base=base,struct=struct,image_bytes=image_bytes)
    exec(code,state)
    try:result=state['smp_gic_mapped_snapshot']()
    except AssertionError:assert count!=8 or duplicate
    else:
        assert count==8 and not duplicate and len(kd.samples)==49
        assert result['cpus'][0]['physical']=='0x3ffd0000'
print('ACTUAL_NATIVE_GIC_MAPPED_READS_BOUNDS_AND_REORDERED_MAPPING_PASS')
