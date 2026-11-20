"""Offline template only. No physical writes or VM launch.

Deployment MUST first allocate/retain an owned executable reserved EFI page,
prove the address in the actual memory map and observe exact CPU_ON arguments.
Never borrow Windows restart metadata, kernel/user pages or an assumed hole.
"""
import struct


def template(entry_pa, context):
    assert 0x80000000 <= entry_pa < 0x180000000 and entry_pa % 4 == 0
    assert 0 <= context < 2**64
    # CPU_ON x0 must be the ORIGINAL context on entry to Windows, even when
    # the hypervisor is asked to start our trampoline with context zero.
    # x16 is volatile under the architectural caller contract.
    return struct.pack('<4I2Q',
                       0x58000080,  # ldr x0, literal +16
                       0x580000b0,  # ldr x16, literal +24 (PC is +4)
                       0xd61f0200,  # br x16 (not BL; original LR preserved)
                       0xd503201f,  # nop, keeps literals 8-byte aligned
                       context, entry_pa)


def plan(owned_page, entry_pa, context, zero_hypervisor_context=False):
    assert 0x80000000 <= owned_page < 0x100000000 and owned_page % 4096 == 0
    assert owned_page + 4096 <= 0x100000000
    assert not owned_page <= entry_pa < owned_page + 4096, 'No recursive trampoline'
    return dict(code=template(entry_pa, context),
                cpu_on_entry=owned_page,
                cpu_on_context=0 if zero_hypervisor_context else context,
                restored_windows_x0=context,
                original_entry=entry_pa)


if __name__ == '__main__':
    for entry in (0x1048d3010, 0x8ced3010):
        for context in (0, 0x8000c000, 0xffffffffffffffff):
            for zero in (False, True):
                p = plan(0x90000000, entry, context, zero)
                words = struct.unpack('<4I2Q', p['code'])
                assert len(p['code']) == 32
                # Independently decode ARM64 literal offsets/registers.
                for index, target, register in ((0, 16, 0), (1, 24, 16)):
                    opcode = words[index]
                    assert opcode & 31 == register
                    assert index*4 + ((opcode >> 5) & 0x7ffff)*4 == target
                assert words[2] == 0xd61f0200 and words[4:] == (context, entry)
                assert p['cpu_on_context'] == (0 if zero else context)
    for bad_page in (0x90000010, 0x100000000, 0x70000000):
        try:
            plan(bad_page, 0x1048d3010, 0x8000c000)
        except AssertionError:
            pass
        else:
            raise AssertionError('Unsafe page admitted')
    print('OFFLINE_PSCI_TRAMPOLINE_TEMPLATE_PASS; NOT_ALLOCATED_NOT_DEPLOYED')
    # Independent MOVZ/MOVK decoding of EFI's no-write first-CPU template.
    movz,movk=0x52980000,0x72b00000
    assert movz&31==0 and movk&31==0
    value=((movz>>5)&0xffff) << (((movz>>21)&3)*16)
    shift=((movk>>21)&3)*16
    value=(value&~(0xffff<<shift))|(((movk>>5)&0xffff)<<shift)
    assert value==0x8000c000
    print('STATIC_FIRST_CPU_CONTEXT_RESTORE_DECODE_PASS')
