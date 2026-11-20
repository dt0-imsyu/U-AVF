"""Independent assembler vs C template and bounded literal/marker/ABI oracle."""
import re
import struct
import sys
from pathlib import Path

source=Path(__file__).with_name('psci-entry-reserve.h').read_text()
block=source.split('if(PSCI_RUNTIME_ENTRY_MARKER) {',1)[1].split('} else {',1)[0]
words=re.search(r'UINT32 words\[\]=\{([^}]+)\}',block).group(1)
expected=struct.pack('<16I',*[int(x.strip(),0) for x in words.split(',')])
obj=Path(sys.argv[1]).read_bytes()
machine,count,_,_,_,optional,_=struct.unpack_from('<HHIIIHH',obj)
assert machine==0xaa64 and optional==0
code=None
for index in range(count):
    at=20+index*40
    if obj[at:at+8].rstrip(b'\0')!=b'.text':continue
    size,raw,_=struct.unpack_from('<III',obj,at+16)
    assert struct.unpack_from('<H',obj,at+32)[0]==0
    code=obj[raw:raw+size]
assert code==expected and len(code)==64

def signed(value,bits):return value-(1<<bits) if value>>(bits-1) else value
adr=struct.unpack_from('<I',code,4)[0]
assert adr&0x9f00001f==0x10000010
assert 4+signed(((adr>>5)&0x7ffff)<<2|((adr>>29)&3),21)==48
for pc,target,register in ((16,32,0),(20,40,16)):
    word=struct.unpack_from('<I',code,pc)[0]
    assert word&0xff00001f==0x58000000|register
    assert pc+signed((word>>5)&0x7ffff,19)*4==target
assert struct.unpack_from('<I',code,24)[0]==0xd61f0200
assert struct.unpack_from('<I',code,60)[0]==0x17ffffff
# Known compiled opcodes: MOV w0,1 / ADR x16,marker / STR x0,[x16] / DSB SY.
# Only x0/x16 are scratch, and both final values match the original trampoline.
registers=[0x10000000+i for i in range(31)];original=registers[:]
memory=bytearray(code);context=0x8000c000;entry=0x1178d3010
struct.pack_into('<2Q',memory,32,context,entry)
registers[0]=1;registers[16]=48
struct.pack_into('<Q',memory,registers[16],registers[0])
registers[0],registers[16]=struct.unpack_from('<2Q',memory,32)
assert registers[0]==context and registers[16]==entry
assert struct.unpack_from('<Q',memory,48)[0]==1
assert all(registers[i]==original[i] for i in range(31) if i not in (0,16))
print('ASSEMBLER_C_ENTRY_MARKER64B_LITERAL_BOUNDS_FINAL_WINDOWS_ABI_PASS')
