// Diskless EL1 register probe. Not a Windows or post-ExitBootServices witness.
.section .text
.global _start
_start:
    b entry
    .long 0
    .quad 0x80000
    .quad image_end-_start
    .quad 2
    .quad 0,0,0
    .long 0x644d5241
    .long 0
entry:
    msr daifset,#15
    adr x8,vectors
    msr vbar_el1,x8
    isb
    mov w0,#'B'
    bl putc
    mrs x9,CurrentEL
    bl hex
    mov w0,#'M'
    bl putc
    mrs x9,MIDR_EL1
    bl hex
    mov w0,#'P'
    bl putc
    mrs x9,ID_AA64PFR0_EL1
    bl hex
    mov w0,#'Q'
    bl putc
    mrs x9,ID_AA64PFR1_EL1
    bl hex
    mov w0,#'T'
    bl putc
    mov x21,#0
    // Exact Windows MPAM1_EL1 read opcode, destination x9 rather than x8.
    .inst 0xd538a509
    cbnz x21,failed
    mov w0,#'R'
    bl putc
    bl hex
    b done
failed:
    mov w0,#'F'
    bl putc
done:
    mov w0,#'D'
    bl putc
halt:
    wfi
    b halt
putc:
    mov x12,#0x3f8
1:  ldrb w13,[x12,#5]
    tbz w13,#5,1b
    strb w0,[x12]
    ret
hex:
    mov x12,#0x3f8
    mov x10,x9
    mov x11,#16
2:  lsr x14,x10,#60
    cmp x14,#10
    add x15,x14,#'0'
    add x14,x14,#('A'-10)
    csel x14,x15,x14,lo
3:  ldrb w13,[x12,#5]
    tbz w13,#5,3b
    strb w14,[x12]
    lsl x10,x10,#4
    subs x11,x11,#1
    b.ne 2b
    mov w14,#10
    strb w14,[x12]
    ret
.balign 2048
vectors:
.rept 16
    b sync
    .space 124
.endr
sync:
    mov x22,x30
    mov x23,x9
    mov x21,#1
    mov w0,#'X'
    bl putc
    mrs x9,ESR_EL1
    bl hex
    mov w0,#'E'
    bl putc
    mrs x9,ELR_EL1
    bl hex
    mrs x8,ELR_EL1
    add x8,x8,#4
    msr ELR_EL1,x8
    mov x9,x23
    mov x30,x22
    eret
image_end:
