# Ubuntu release upgrade display incompatibility — 2026-10-05

Fresh tablet serial proves Ubuntu 26.04.1 boots, GDM autologin opens a real
Wayland user session. Production Xorg scraper then reports
UAVF_GDM_SHARE=DISPLAY_NOT_READY; fallback reports
WINAVF_FRAME_BRIDGE=AUTHENTICATED_DISPLAY_NOT_FOUND. Android receives zero
encoded frames. This is not evidence of an Android codec or storage failure.

Ubuntu 26.04 no longer supports GNOME as an Xorg session:
https://documentation.ubuntu.com/release-notes/26.04/summary-for-lts-users/

Changing WaylandEnable=false alone cannot restore the removed session. Do not
force that setting on this upgraded installation, downgrade GNOME blindly or
reinstall/delete persistent user data. Current Xorg production capture remains
the supported Ubuntu 24.04 path. Supporting upgraded GNOME requires a Wayland
capture/input implementation (e.g. Mutter/PipeWire), preserving existing encoded
transport and Android decoder where possible. This has NOT been implemented or
verified by this diagnostic step.

Evidence: build-logs/ubuntu-26-display-upgrade-20261005/, serial and encoded report.
No guest configuration, APK or disk changed; no commit/push.
