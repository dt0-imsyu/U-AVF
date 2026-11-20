import importlib.util
from pathlib import Path
import struct
import sys
spec=importlib.util.spec_from_file_location('tx',Path(__file__).with_name('native-kd-tx-batching.py'))
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
body=bytes(56)
packet=struct.pack('<IHHII',0x30303030,2,len(body),0x80800000,sum(body))+body+b'\xaa'
control=struct.pack('<IHHII',0x69696969,4,0,0x80800000,0)
data=b'b'+control+packet+control
for width in (1,2,8,31,65536):
    batch=module.TxBatcher();sent=[]
    for at in range(0,len(data),width):sent+=batch.feed(data[at:at+width])
    assert b''.join(sent)+batch.flush()==data
    assert packet in sent and control in sent
for file in sys.argv[1:]:
    original=Path(file).read_bytes();batch=module.TxBatcher();sent=[]
    for byte in original:sent+=batch.feed(bytes([byte]))
    assert b''.join(sent)+batch.flush()==original
print('NATIVE_KD_BATCHING_ALL_BYTES_IDENTICAL_AND_ONE_BYTE_INPUT=PASS')
