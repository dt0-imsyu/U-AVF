// Reference for RAM-only HAL CAM sub-page adapter. x16 is ABI scratch.
.text
.global _start
_start:
    and w16,w0,#0xfff
    add w20,w20,w16
    .extern data_page
    adrp x21,data_page
    .extern resume
    b resume
