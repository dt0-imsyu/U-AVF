// Read-only host FDT evidence. No disk/device configuration writes.
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
    mov x20,x0
    cbz x20,halt
    ldr w10,[x20]
    rev w10,w10
    mov w9,#0xfeed
    movk w9,#0xd00d,lsl #16
    cmp w9,w10
    b.ne halt
    ldr w21,[x20,#4]
    rev w21,w21
    cmp w21,#40
    b.lo halt
    mov w9,#0x20000
    cmp w21,w9
    b.hi halt
    adr x22,marker
print_marker:
    ldrb w0,[x22],#1
    cbz w0,loop
    bl putc
    b print_marker
loop:
    ldrb w9,[x20],#1
    adr x10,digits
    lsr w11,w9,#4
    ldrb w0,[x10,x11]
    bl putc
    and w11,w9,#15
    ldrb w0,[x10,x11]
    bl putc
    subs w21,w21,#1
    b.ne loop
    mov w0,#10
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
marker: .asciz "UAVF_FDT_HEX\n"
digits: .ascii "0123456789abcdef"
.balign 4096
image_end:
