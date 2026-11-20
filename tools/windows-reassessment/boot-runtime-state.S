.text
.global uavf_boot_runtime_state
uavf_boot_runtime_state:
    mrs x0, cntv_ctl_el0
    mrs x1, cntv_cval_el0
    mrs x2, cntvct_el0
    mrs x3, cntfrq_el0
    mrs x4, midr_el1
    mrs x5, daif
    mrs x6, icc_ctlr_el1
    mrs x7, icc_pmr_el1
    mrs x8, icc_igrpen1_el1
