// Isolated Windows CAM adapter experiment only. Ranges/INTx from exact
// host-fdt.dtb 2026-10-07; requires guarded HAL CAM access, not stock ECAM.
DefinitionBlock ("", "SSDT", 2, "UAVF  ", "WCAM0001", 1)
{
    Scope (\_SB)
    {
        Device (PCI0)
        {
            Name (_HID, EisaId ("PNP0A03"))
            Name (_UID, Zero)
            Name (_SEG, Zero)
            Name (_BBN, Zero)
            Name (_CCA, One)
            Name (_STA, 0x0F)
            Name (_CRS, ResourceTemplate ()
            {
                WordBusNumber (ResourceProducer, MinFixed, MaxFixed, PosDecode,
                    0, 0, 0, 0, 1)
                DWordMemory (ResourceProducer, PosDecode, MinFixed, MaxFixed,
                    NonCacheable, ReadWrite, 0, 0x2C000000, 0x2DFFFFFF, 0, 0x02000000)
                QWordMemory (ResourceProducer, PosDecode, MinFixed, MaxFixed,
                    Prefetchable, ReadWrite, 0, 0x0000000180800000,
                    0x000000FFFFFFFFFF, 0, 0x000000FE7F800000)
            })
            Name (_PRT, Package ()
            {
                Package () {0x0001FFFF, 0, 0, 36},
                Package () {0x0002FFFF, 0, 0, 37},
                Package () {0x0003FFFF, 0, 0, 38},
                Package () {0x0004FFFF, 0, 0, 39},
                Package () {0x0005FFFF, 0, 0, 40},
                Package () {0x0006FFFF, 0, 0, 41},
                Package () {0x0007FFFF, 0, 0, 42},
                Package () {0x0008FFFF, 0, 0, 43}
            })
        }
    }
}
