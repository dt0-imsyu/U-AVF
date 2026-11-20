// Position-independent EFI virtual-address-change callback, copied to OWN
// EfiRuntimeServicesCode page before EBS. No calls into reclaimed LoaderCode.
.text
.p2align 2
.global psci_callback_begin
.global psci_callback_end
psci_callback_begin:
    stp x29, x30, [sp, #-48]!
    stp x19, x20, [sp, #16]
    mov x19, x1
    str x19, [sp, #32]
    mov x0, #0
    add x1, sp, #32
    ldr x16, [x19, #48] // captured ConvertPointer, valid before VA switch
    blr x16
    str x0, [x19, #56]
    cbnz x0, .Ldone
    ldr x20, [sp, #32]
    str x20, [x19, #40]
    dsb sy
    // UART MMIO is not mapped during SetVirtualAddressMap. One OWN callback
    // checkpoint lets KD record x19/x20, then advance only this own BRK.
    brk #0xf000
.Ldone:
    ldp x19, x20, [sp, #16]
    ldp x29, x30, [sp], #48
    ret
psci_callback_end:
