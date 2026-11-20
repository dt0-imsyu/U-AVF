// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Enumeration control for the isolated U-AVF WDDM guest.
DefinitionBlock ("", "SSDT", 2, "UAVF  ", "MAVKGPU0", 1)
{
    Scope (\_SB)
    {
        Device (MVKG)
        {
            Name (_HID, "UAV0001")
            Name (_UID, Zero)
            Name (_STA, 0x0F)
        }
    }
}
