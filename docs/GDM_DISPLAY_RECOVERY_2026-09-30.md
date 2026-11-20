# Product GDM display recovery

## First proven fault

The installed APK at 16:23:40 and its host product asset both contained prefix
`BCC38A1E77DF773B11E315F949B5F07F7A8D19F701C66DD36F1B1812D6B67C6E`.
Extracted initrd SHA was
`4D64609F53D4522E3431143AB1BBED72D6F3FE9167FA0250F39483C298B32E1B`.
Its bridge still contained `XVNC_GNOME_SESSION`. Fresh serial confirmed that
the Live hook created a second Xvnc/GNOME desktop. Cline's source changes
therefore were not tested by that APK.

## Correction and first runtime

- Live hook now shares the existing GDM Xorg session; it does not launch an
  X server or GNOME. The compatible service name remains `winavf-xvnc`.
- Match Xorg using explicit DISPLAY **or exact XAUTHORITY command argument**;
  GDM may choose its display through `-displayfd`.
- Build fails if embedded product scripts differ from current source.
- First runtime reported UID 1000, DISPLAY :0, TYPE x11,
  `/run/user/1000/gdm/Xauthority`, `/run/user/1000`,
  `unix:path=/run/user/1000/bus`, and `GNOME_SHELL_COUNT=1`.
- Existing-display RFB handshake at 1280x800 passed. Android received one
  non-black frame, but the stream stalled after that. This was not display PASS.

## Second concrete fault

The client advertised Fence but discarded ServerFence Request payloads.
[TigerVNC 1.13.1 server implementation](https://github.com/TigerVNC/tigervnc/blob/v1.13.1/common/rfb/VNCSConnectionST.cxx)
uses request/reply pings for congestion control. The client must clear Request,
honor supported BlockBefore/BlockAfter flags, and echo the payload.
An offline server fixture now requires that reply before sending framebuffer
data: it fails before the fix and passes afterward in pull and continuous modes.

This is source/protocol evidence; runtime causality still requires continuing
frames after `RFB_FENCE_REPLY=PASS`.

## Final candidate

- Prefix: `20B07030A55B4AC7430F06E24577BCA7BDC6385586A0C9BC5260571633026660`
- ESP: `A4BD55991846EBC8D82B7CC2B1AC378227D706A6FD7F4A455D4D836CB70301BD`
- Initrd: `A2068DD4DCF647166B22E7EE03F5F9D8D5C8796162609D38FADC94307DCCD32C`
- APK: `72AD2E18D60E3AE158A93129497966EE47060AD88DFD8BE496DEDCFEE2BCF491`
- Artifacts/evidence: `D:\UAVF-builds\gdm-display-recovery-20260930`.

Early CPIO, kernel, EFI launcher and GPT/pre-ESP bytes were preserved.
ESP update uses the existing verified backup mechanism. Root data, stock ISO,
GPU, storage layout, WAVF and Android decoder were not modified.

Only after display passes: validate Firefox Snap and lock/unlock in this real
user session. Installed runtime migration and full reinstall acceptance are
separate from this Live display test; do not call them proven by an APK build.
