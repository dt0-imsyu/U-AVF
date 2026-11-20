// Independent assembler oracle for the C-embedded owned64B trampoline.
.text
mov w0, #1
adr x16, marker
str x0, [x16]
dsb sy
ldr x0, context
ldr x16, entry
br x16
nop
context: .quad 0
entry: .quad 0
marker: .quad 0
wfe
b .-4
