import importlib.util
from pathlib import Path
import struct
spec=importlib.util.spec_from_file_location("ack",Path(__file__).with_name("native-kd-ack-recovery.py"))
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
ack=struct.pack("<IHHII",0x69696969,4,0,0x80800001,0)
resend=struct.pack("<IHHII",0x69696969,5,0,0,0)
reset=struct.pack("<IHHII",0x69696969,6,0,0,0)
obj=module.AckRecovery()
assert obj.feed("rx",resend)==[]
for byte in ack:assert obj.feed("tx",bytes([byte]))==[]
assert obj.feed("rx",resend[:9])==[]
assert obj.feed("rx",resend[9:])==[ack]
assert obj.feed("rx",resend*4)==[ack,ack]
body=bytes(56)
response=struct.pack("<IHHII",0x30303030,2,len(body),0x80800001,sum(body))+body+b'\xaa'
assert obj.feed("rx",response)==[]
assert obj.feed("rx",resend)==[ack]
assert obj.feed("rx",response)==[] # Duplicate response cannot refresh the quota.
assert obj.feed("rx",resend*4)==[ack,ack]
assert obj.feed("rx",reset+resend)==[]
print("NATIVE_ACK_CONTROL_ONLY_FRAGMENTED_BOUNDED_RESET=PASS")
