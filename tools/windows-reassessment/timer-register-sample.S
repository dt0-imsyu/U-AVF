// Read-only register sample for reversible, exact-image-guarded KD RAM probe.
// Caller restores all instructions and the original complete ARM64 context.
.text
.global timer_register_sample
timer_register_sample:
    mrs x0, CNTV_CTL_EL0
    mrs x1, CNTV_CVAL_EL0
    mrs x2, CNTVCT_EL0
    mrs x3, CNTFRQ_EL0
.global timer_unmask_probe
timer_unmask_probe:
    mrs x0, CNTV_CTL_EL0
    bic x0, x0, #2
    msr CNTV_CTL_EL0, x0
    isb
