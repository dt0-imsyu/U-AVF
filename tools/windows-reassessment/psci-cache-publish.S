// BSP only: clean exactly128B of OWN runtime trampoline to PoC, then observer
// restores the entire saved Windows CPU_ON context. x16 is the own-page VA.
.text
.global psci_publish_begin
.global psci_publish_end
psci_publish_begin:
dc cvac, x16
add x16, x16, #32
dc cvac, x16
add x16, x16, #32
dc cvac, x16
add x16, x16, #32
dc cvac, x16
dsb sy
isb
brk #0xf000
psci_publish_end:
