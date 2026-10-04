# Third-party compliance — release hold

Bundling binaries inside an APK/initrd does not remove their upstream obligations.
The inventory and extracted copyright files are evidence, NOT a completed legal
audit or a substitute for corresponding source/build information.

## Concrete evidence collected 2026-10-04

- Current RC4 inventory refreshed from the actual signed APK (three prefixes),
  rather than relabelling the old RC1 inventory. Exact unchanged U-Boot payload
  permits reuse of its private source checkpoint, not a full rebuild PASS.
- Current evidence covers151 unique ELF hashes;145 have no exact binary/package
  association in the preserved provenance records. Unassociated does not mean
  GPL for every file; classification and source/build/relink review remain open.
  Five source-package records checksum verified. Private source-coverage-rc4.json
  records concrete gaps; no complete source-compliance claim.

- Inventoried all three platform-prefix assets in the actual 1.0-rc.1 APK:
  1088/1070/1094 main-CPIO entries respectively; ELF/notice/local payload hashes
  stored in the private bundled-platform-inventory.json.
- Packaged U-Boot binary exactly equals the preserved local u-boot.bin:
  CC7917E2F5522E9B474BC0296680C7D1B94CD38939B756C975FC5924DD007792.
  Rebuilding the ARM64 wrapper from its source with the preserved NDK produces
  the exact shipped Image (93EDA7C4BD54C33F85ADA6F05158C74EC6F5C3232F5CBA73846E5B442895F234).
- Preserved a 38,550-file local U-Boot source/config checkpoint separately.
  Exact full U-Boot rebuild/provenance still needs verification; the wrapper
  reproduction does not establish full bootloader/source completeness.
- APK now includes extracted TigerVNC/x264/GStreamer/font notices and local
  U-Boot GPL2 / EDK2 top-level license text. These do not replace per-file terms
  or missing corresponding Ubuntu/TigerVNC/library sources.
- The older real-gnome fallback also contains Xtigervnc; lack of copyright
  in that initrd must not be confused with absence of the licensed binary.
- Exact shipped Xtigervnc ELF hashes match tigervnc-standalone-server
  1.15.0+dfsg-2build1; renamed pacat/pactl match pulseaudio-utils
  16.1+dfsg1-2ubuntu10.1. Corresponding official DSC and all SHA256-listed source
  components downloaded into private upstream-sources. PGP verification and
  remaining dependency associations/rebuild obligations not yet complete.
- Exact encoder ELF hashes now match libx264-164 2:0.164.3108+git31e19f9-1
  and gstreamer1.0-plugins-ugly 1.24.1-1build1. The latter's libgstx264.so
  matches, not merely its notice. A newer x264 -2build2 matches only copyright
  and is NOT counted as the binary's source association. Both exact source
  DSCs and listed archives fetched/checksummed; four package records retained.
  Source fetcher now appends evidence and refuses conflicting same-name DSCs.
  This still does not close full dependency/build/relink compliance.
- The shipped X0tigervnc scraper also matches the exact 1.13.1+dfsg-2build2
  ELF (8CD3BEB524AAFB0D54635EA2E0FF81F3D008BE780DB41961FC6794F10B90624B).
  Its matching DSC/source components are now checksum verified. This is
  independent of the bundled 1.15 Xvnc fallback and does not replace its sources.

Still needed: exact package/build-source linkage for every shipped GPL/LGPL
binary (including kernel/initrd and fallback), complete required sources/configs,
and replacement/relink compliance. No source-compliance PASS is claimed.

| Component | Known licensing concern | Before public distribution |
| --- | --- | --- |
| Existing U-AVF releases | Earlier Apache-2.0 grants remain; scoped new source-available license is separate | Preserve earlier grants/notices; LICENSE_SCOPE.md does not override third-party obligations |
| U-Boot wrapper | GPL-family bootloader, locally modified build | Exact source/patches/config/toolchain/build instructions and upstream license |
| Linux kernel/initrd userland | Ubuntu-supplied mixture including GPL/LGPL | Inventory exact binary versions and provide required corresponding sources |
| TigerVNC/X0 scraper | GPL-family and additional bundled notices | Verify exact build and make corresponding source/patches available |
| libx264 / GStreamer x264 plugin | GPL/LGPL combinations; package-specific terms | Retain copyright; exact sources/build configs; examine linking/combined-work boundary |
| GStreamer/GLib/other shared libraries | Usually LGPL plus component-specific licenses | Verify dynamic linking, notices, source and replacement/relink requirements |
| EDK2/UEFI components | BSD-family plus individual file terms | Preserve notices and trace local changes/upstream provenance |
| Lexend Deca | SIL Open Font License, supplied alongside font | Include OFL and retain attribution |
| Ubuntu/Windows icons, U-AVF artwork | Copyright/trademark rights separate from code | Verify origin and permitted distribution; no implied endorsement |
| Android/crosvm/VirGL/Mali supplied by OS | External system dependencies | Do not claim ownership or relicense them; distinguish bundled vs system components |

The GPL/LGPL status of any original bridge linked to these components must be
reviewed before imposing restrictive terms on it. IPC/process separation alone
is not an automatic legal clearance. Do not put a blanket proprietary license
over an entire firmware/initrd aggregate.

References:

- Apache2.0: https://www.apache.org/licenses/LICENSE-2.0
- GNU GPL FAQ: https://www.gnu.org/licenses/gpl-faq.html
- GNU LGPL: https://www.gnu.org/licenses/lgpl-3.0.html

## Русский

Новая ограничительная лицензия может относиться только к нашим новым частям,
на которые есть права и которые не обязаны сохранять другую лицензию. Она не
заменяет GPL/LGPL/BSD/OFL встроенных компонентов и не отменяет старую Apache-2.0.
До публикации требуется полный набор исходников/уведомлений там, где это требуется.
