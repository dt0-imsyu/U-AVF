# Third-party components and release checks

U-AVF includes software from other projects in its APK, firmware and guest images. Each component keeps its own license. Including a license notice is not always enough: some components also require corresponding source code, patches or build information.

This page records what has been identified and what still needs work. **It is not a completed legal audit or a claim that release obligations have been met.**

Status terms used here:

- **Verified:** Shipped bytes have been matched to a specific upstream build or checksum.
- **Open:** A requirement has not yet been completed.
- **Unconfirmed:** The exact source or origin has not been established.

## U-AVF 1.2 additions

- **libudfread 1.2.0** (VLC authors and VideoLAN, LGPL-2.1-or-later) ships as the
  shared library `libudfread.so` for the Android Windows ISO reader. Upstream
  archive is pinned by SHA-512 in `android-app/native/windows-iso/build.ps1`.
  A local patch (`enable-udfread-partition.py`) makes the reader accept the UDF
  partition number used by retail Windows ARM64 ISOs. Notice:
  `android-app/assets/legal-notices/libudfread-1.2.0-COPYING.txt`. Corresponding source
  bundle: **Open**.
- **wimlib 1.14.5** is linked statically into `libuavf_windows_wim.so` with
  NTFS-3G and FUSE disabled. Source archive pinned by SHA-256 in
  `android-app/native/windows-iso/build-wim.ps1`. The build applies
  `enable-wimlib-fd.py` to add a read-only file-descriptor entry point, so a
  modified upstream source is shipped as a binary and its patch must accompany
  any source offer. Notices:
  `android-app/assets/legal-notices/wimlib-1.14.5-COPYING.txt`,
  `...-COPYING.GPLv3.txt`, `...-COPYING.LGPL.txt`. Relink recipe:
  [android-app/README.md](../../android-app/README.md#relink-the-windows-wim-adapter).
  Corresponding source bundle: **Open**.
- **FatFs R0.16** is compiled into `libuavf_windows_setup_fat.so` from the
  pinned `ff16.zip` archive (SHA-256 checked in `build-setup-fat.ps1`). Notices
  for the two compiled sources `ff.c` and `ffunicode.c` are included at
  `android-app/assets/legal-notices/FatFs-R0.16-LICENSE.txt`. Corresponding source:
  **Open**.
- **Mesa, Zink and gfxstream** are used by the optional Ubuntu Mavk payload.
  Upstream notices apply; `android-app/assets/legal-notices/gfxstream-LICENSE.txt` is
  included. Android's system Vulkan driver is supplied by the device and is not
  redistributed.
- **Lexend Deca** is bundled under the SIL Open Font License with
  `android-app/assets/legal-notices/LexendDeca-OFL.txt`. Attribution is retained.
- First-party license scope, earlier Apache-2.0 grants and third-party terms are
  unchanged by 1.2. The build writes license texts and the privacy notice into
  the APK under `assets/legal/`: `LICENSE`, `LICENSE-UAVF.txt`,
  `LICENSE-APACHE-2.0.txt`, `LICENSE_SCOPE.md`, `PRIVACY.md`, and a generated
  `THIRD_PARTY_NOTICES.txt` aggregate built from `android-app/assets/legal-notices/`.

## Windows Setup runtime

U-AVF installs Windows from an ISO supplied by the user; the ISO is not bundled. The APK includes a separate helper archive at `assets/windows-setup/iso-boot-runtime.zip`. It has 20 entries: 17 WinPE add-ons and the files `bcd.patch`, `bootaa64.efi` and `qemu_efi.fd`. The versions below were identified in the release candidate.

| Bundled component | Identification | Open release check |
| --- | --- | --- |
| EDK2 guest firmware (`qemu_efi.fd`) | r11 build; build inputs and SHA-256 recorded in [the firmware checkpoint](../R11_DEBUG_UART_ALIGNMENT_RUNTIME_2026-09-08.md) | Match the release kit to that build and carry its BSD-family notices. |
| OpenH264 (`openh264.dll`) | Byte-identical to the Windows ARM64 DLL from Cisco OpenH264 2.5.1. [Cisco release](https://github.com/cisco/openh264/releases/tag/2.5.1) | Resolve terms for bundling the codec inside the APK. [Cisco binary terms](https://www.openh264.org/BINARY_LICENSE.txt) |
| ARM64 VirtIO storage and vsock drivers | Both INFs report `DriverVer=11/10/2025,100.102.104.29300`. Microsoft-signed catalogs verify both INFs and both kernel binaries. | Identify the distribution package that supplied the files and verify the bundled user-mode tools against it. [VirtIO driver license](https://github.com/virtio-win/kvm-guest-drivers-windows/blob/master/LICENSE) |
| Windows boot configuration patch (`bcd.patch`) | Reconstructs a prepared BCD from the selected ISO's stock BCD | Review the literals carried in the patch. |
| EFI loader shim (`bootaa64.efi`) | Pinned by SHA-256 in `tools/release/build-windows-iso-boot-runtime.py` | Confirm the shim's own origin and license. |

The OpenH264 SHA-256 is
`F5976BFF9E6DE84F6253C9BFF0F7C70CCDE446B3B20CF7B7206F7A12C35B460F`, confirmed
against the shipped DLL. Cisco lists delivery to the end user's own device among
the conditions for its AVC patent coverage. The current APK bundles the DLL
inside the WinPE runtime archive rather than providing it as a separate download.
[Cisco binary terms](https://www.openh264.org/BINARY_LICENSE.txt)

No supplied archive was retained for the drivers, so the INF version string and
the valid catalog signatures are the only identification. Both point at the
same build, but the exact package that delivered the files is **Unconfirmed**;
record the archive that produced them before the next release.
[VirtIO download archive](https://fedorapeople.org/groups/virt/virtio-win/direct-downloads/archive-virtio/)

## Earlier audit evidence

The findings below were recorded from a signed APK. They should not be read as proof that all source-distribution requirements have been met.

- Three platform-prefix images were inventoried in full: 1088, 1070 and 1094
  main-CPIO entries. ELF, notice and local payload hashes are stored in a private
  `bundled-platform-inventory.json`.
- The inventory covers 151 unique ELF hashes. Of those, 145 had no exact
  binary-to-package association in the preserved provenance records. An
  unassociated file is not automatically GPL; classification and
  source/build/relink review remain **Open**.
- Packaged U-Boot binary equals the preserved local `u-boot.bin`:
  `CC7917E2F5522E9B474BC0296680C7D1B94CD38939B756C975FC5924DD007792`.
  Rebuilding the ARM64 wrapper from preserved source with the preserved NDK
  reproduces the shipped `Image` exactly
  (`93EDA7C4BD54C33F85ADA6F05158C74EC6F5C3232F5CBA73846E5B442895F234`).
  A separate 38,550-file U-Boot source and config checkpoint is preserved. A
  full bootloader rebuild is **Open**; wrapper reproduction alone does not
  establish source completeness.
- The APK includes extracted TigerVNC, x264 and GStreamer notices plus the
  U-Boot GPL-2.0 and EDK2 top-level license texts, collected into
  `assets/legal/THIRD_PARTY_NOTICES.txt`. In this repository the individual
  notice files are in `android-app/assets/legal-notices/`.
  These do not replace per-file terms or missing corresponding sources.
- The older real-GNOME fallback also contains `Xtigervnc`. Absence of a
  copyright string inside that initrd must not be read as absence of the
  licensed binary.
- Shipped `Xtigervnc` ELF hashes match `tigervnc-standalone-server`
  `1.15.0+dfsg-2build1`. Renamed `pacat`/`pactl` match `pulseaudio-utils`
  `16.1+dfsg1-2ubuntu10.1`. Matching DSC and SHA256-listed source components
  were downloaded into a private `upstream-sources` directory. PGP verification
  and remaining dependency associations are **Open**.
- Shipped encoder ELF hashes match `libx264-164`
  `2:0.164.3108+git31e19f9-1` and `gstreamer1.0-plugins-ugly` `1.24.1-1build1`.
  For the latter, the shipped `libgstx264.so` matches, not merely its notice. A
  newer x264 `-2build2` matches only copyright text and is explicitly not
  counted as the binary's source association.
- The shipped X0tigervnc scraper matches the `1.13.1+dfsg-2build2` ELF
  (`8CD3BEB524AAFB0D54635EA2E0FF81F3D008BE780DB41961FC6794F10B90624B`) with
  checksum-verified DSC and source components. This is independent of the
  bundled 1.15 Xvnc fallback and does not replace its sources.

**Still open:** Exact package-to-source identification for every bundled GPL/LGPL binary (including the kernel, initrd and fallback paths), complete corresponding sources and build configurations, and any applicable library replacement/relink requirements. **No full source-compliance PASS is claimed.**

## Component risk overview

| Component | Known licensing concern | Before public distribution |
| --- | --- | --- |
| Existing U-AVF releases | Earlier Apache-2.0 grants remain; the scoped source-available license is separate | Preserve earlier grants and notices; `LICENSE_SCOPE.md` does not override third-party obligations |
| U-Boot wrapper | GPL-family bootloader, locally modified build | Exact source, patches, config, toolchain and build instructions plus upstream license |
| Linux kernel and initrd userland | Ubuntu-supplied mixture including GPL and LGPL | Inventory exact binary versions and provide required corresponding sources |
| TigerVNC and X0 scraper | GPL-family plus additional bundled notices | Verify the exact build and make corresponding source and patches available |
| libx264 and GStreamer x264 plugin | GPL and LGPL combinations with package-specific terms | Retain copyright, provide exact sources and build configs, examine the linking boundary |
| GStreamer, GLib and other shared libraries | Usually LGPL plus component-specific terms | Verify dynamic linking, notices, source and replacement/relink requirements |
| EDK2 and UEFI components | BSD-family plus individual file terms | Preserve notices and trace local changes and upstream provenance |
| wimlib and libudfread | GPL/LGPL with a shipped local patch | Provide patched corresponding source for each shipped binary |
| OpenH264 | Cisco binary terms and AVC patent conditions | Confirm the bundling and delivery model satisfies the binary terms |
| VirtIO Windows drivers | Red Hat driver license; MS-signed catalogs | Identify the supplying package; keep signatures intact |
| Lexend Deca | SIL Open Font License | Include the OFL and retain attribution |
| Ubuntu and Windows icons, U-AVF artwork | Copyright and trademark rights separate from code | Verify origin and permitted distribution; imply no endorsement |
| Android, crosvm and VirGL supplied by the OS | External system dependencies | Do not claim ownership or relicense them; distinguish bundled from system components |

The GPL/LGPL status of any original bridge linked to these components must be
reviewed before restrictive terms are applied to it. Process or IPC separation
alone is not an automatic legal clearance. A blanket proprietary license must not
be placed over an entire firmware or initrd aggregate.

References:

- Apache-2.0: https://www.apache.org/licenses/LICENSE-2.0
- GNU GPL FAQ: https://www.gnu.org/licenses/gpl-faq.html
- GNU LGPL: https://www.gnu.org/licenses/lgpl-3.0.html

## Русский

Это краткое описание для русскоязычных читателей. Подробные сведения о компонентах, версиях, контрольных суммах и нерешённых вопросах приведены выше. Документ не заменяет юридическую проверку.

В APK и системных образах U-AVF есть сторонние компоненты. Их лицензии действуют независимо от лицензии самого проекта. В версии 1.2 используются libudfread 1.2.0 (LGPL-2.1-or-later, с локальным патчем), wimlib 1.14.5 (статическая сборка с локальным патчем), FatFs R0.16, Mesa/Zink/gfxstream для Mavk и шрифт Lexend Deca (SIL OFL). В установочном архиве Windows также находятся EDK2 r11, OpenH264 2.5.1, драйверы VirtIO, патч BCD и загрузочный shim.

Тексты уведомлений находятся в `android-app/assets/legal-notices/`. В APK они объединены в `assets/legal/THIRD_PARTY_NOTICES.txt`; остальные юридические документы лежат в `assets/legal/`.

Для компонентов под GPL/LGPL могут требоваться соответствующие исходники, изменения, конфигурации сборки и возможность замены или перелинковки библиотек. Эти вопросы **ещё не закрыты**. Наличие уведомлений не означает, что все обязательства по распространению выполнены.

U-AVF Source-Available License применяется только к оригинальному материалу, указанному в `LICENSE_SCOPE.md`. Она не заменяет GPL, LGPL, BSD, OFL или Apache-2.0 и не отменяет ранее предоставленные права. При противоречии действуют условия соответствующего компонента и обязательные требования закона.
