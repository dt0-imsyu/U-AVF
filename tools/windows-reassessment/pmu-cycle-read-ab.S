.text
.global uavf_pmu_cycle_read_ab
uavf_pmu_cycle_read_ab:
    stp x0, x1, [sp, #-16]!
    stp x2, x3, [sp, #-16]!
    mrs x0, PMUSERENR_EL0
    orr x1, x0, #4
    msr PMUSERENR_EL0, x1
    isb
    mrs x2, PMUSERENR_EL0
    mrs x3, PMCR_EL0
    nop // One KD breakpoint here; native epilogue restores GPR/SP, no SetContext.
    ldp x2, x3, [sp], #16
    ldp x0, x1, [sp], #16
    b uavf_pmu_cycle_read_ab // Relocated at runtime to the original checkpoint.
