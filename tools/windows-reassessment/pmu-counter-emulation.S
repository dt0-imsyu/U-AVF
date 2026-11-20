// Isolated RAM-only fallback for a trapped EL0 MRS PMCCNTR.
// No Windows userland bytes are changed. CNTVCT is a monotonic fallback,
// NOT a claim that virtual PMU/performance profiling works.
.text
.global uavf_pmu_emulation
.global uavf_pmu_read_call
.global uavf_pmu_fallback_branch
.global uavf_pmu_emulated_point
uavf_pmu_emulation:
    pacibsp
    stp x29, x30, [sp, #-64]!
    stp x19, x20, [sp, #16]
    stp x0, x1, [sp, #32]
    mov x29, sp
    mov x19, x0
    mov x20, x1 // KEXCEPTION_FRAME X19..X28, exact KiEmulateReadRegister.
    ldr w8, [x19, #0x90] // KTRAP_FRAME.Cpsr, verified exact kernel.
    tst w8, #15
    b.ne fallback
    ldr w8, [x19, #0x94] // KTRAP_FRAME.Esr.
    lsr w8, w8, #26
    cbz w8, read_opcode // Undefined instruction injected by hypervisor.
    cmp w8, #24
    b.ne fallback
read_opcode:
    ldr x0, [x19, #0x148] // KTRAP_FRAME.Pc.
    add x1, sp, #48
    mov x2, #0 // User read, fault-safe KiEmulatedReadDword.
uavf_pmu_read_call:
    bl uavf_pmu_read_call // Relocated to exact KiEmulatedReadDword RVA620850.
    cbz w0, fallback
    ldr w8, [sp, #48]
    and w9, w8, #0xffffffe0
    mov w10, #0x9d00
    movk w10, #0xd53b, lsl #16
    cmp w9, w10
    b.ne fallback
    and w8, w8, #31
    mrs x9, CNTVCT_EL0
    cmp w8, #18
    b.hs high_register
    add x10, x19, #0xa0 // X0..X17 layout verified in KiEmulateReadRegister.
    str x9, [x10, x8, lsl #3]
    b recorded
high_register:
    b.eq register18
    cmp w8, #29
    b.hs frame_register
    sub w10, w8, #19
    str x9, [x20, x10, lsl #3]
    b recorded
register18:
    str x9, [x19, #0x130]
    b recorded
frame_register:
    b.eq register29
    cmp w8, #30
    b.ne recorded // XZR discards value but still advances PC.
    str x9, [x19, #0x138]
    b recorded
register29:
    str x9, [x19, #0x140]
recorded:
    mrs x11, PMUSERENR_EL0
uavf_pmu_emulated_point:
    ldr x8, [x19, #0x148]
    add x8, x8, #4
    str x8, [x19, #0x148]
    ldp x19, x20, [sp, #16]
    ldp x29, x30, [sp], #64
    autibsp
    ret
fallback:
    ldp x0, x1, [sp, #32]
    ldp x19, x20, [sp, #16]
    ldp x29, x30, [sp], #64
    autibsp
    pacibsp // Original KiSynchronousException prologue instruction.
uavf_pmu_fallback_branch:
    b uavf_pmu_fallback_branch // Relocated to original entry +4.
