.section .rdata,"dr"
.global cam_ssdt_begin
.global cam_ssdt_end
cam_ssdt_begin:
.incbin "build-logs/windows-post-linux-reassessment-20261004/windows-cam-pci.aml"
cam_ssdt_end:
