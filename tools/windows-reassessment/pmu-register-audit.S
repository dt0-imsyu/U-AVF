// Read-only EL1 observation words, independently assembled before KD use.
.text
.global pmu_register_audit
pmu_register_audit:
    mrs x0, MIDR_EL1
    mrs x1, ID_AA64DFR0_EL1
    mrs x2, PMUSERENR_EL0
    mrs x3, PMCR_EL0
