// Isolated guarded RAM workaround candidate, not a modified Windows image.
// Only acknowledge/rearm path, only when timer ENABLE is already set.
// Preserves flags, SP/LR and all registers except ABI scratch x16.
.text
.global periodic_timer_unmask
periodic_timer_unmask:
    mrs x16, CNTV_CTL_EL0
    tbz x16, #0, done
    bic x16, x16, #2
    msr CNTV_CTL_EL0, x16
    isb
done:
    // Relocated B to exact original epilogue is appended by observer.
    nop
