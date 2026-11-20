#!/bin/sh
# Stage the verified add-on in the installed system before the first boot.
set -eu

bundle=/usr/local/lib/uavf-installer
archive=mavk-installed-addon.tar.gz
target=/target
scripts=/usr/local/sbin
profile="$bundle/desktop-profile"
plasma=false

case "${1:-}" in
  --verify|--stage) ;;
  *) exit 64 ;;
esac
(cd "$bundle" && sha256sum -c mavk-installed-addon.sha256)
for name in uavf-plasma-session.py uavf-plasma-install.sh uavf-plasma-install.service; do
  [ -s "$scripts/$name" ]
done
if [ -e "$profile" ]; then
  [ "$(cat "$profile")" = plasma-x11 ]
  plasma=true
fi
[ "$1" != --verify ] || exit 0

mountpoint -q "$target"
[ "$(findmnt -n -o SOURCE,FSTYPE --target "$target")" = '/dev/vda3 ext4' ]
[ "$(lsblk -no PARTLABEL /dev/vda3 | sed 's/[[:space:]]*$//')" = 'U-AVF Ubuntu Root' ]
if "$plasma"; then
  manager=$(readlink "$target/etc/systemd/system/display-manager.service")
  case "${manager##*/}" in gdm.service|gdm3.service) ;; *) exit 1 ;; esac
  if [ ! -s "$target/usr/share/xsessions/plasma.desktop" ] ||
     [ ! -x "$target/usr/bin/kwin_x11" ] ||
     [ ! -s "$target/usr/lib/systemd/user/plasma-kwin_x11.service" ]; then
    printf '%s\n' 'UAVF_INSTALL_RUNTIME=PLASMA_DOWNLOADING' >/dev/console || true
    curtin in-target --target="$target" -- apt-get -o APT::Update::Error-Mode=any update
    curtin in-target --target="$target" -- env DEBIAN_FRONTEND=noninteractive \
      apt-get -y --no-install-recommends install plasma-desktop kwin-x11
  fi
  [ "$(readlink "$target/etc/systemd/system/display-manager.service")" = "$manager" ]
fi
if [ -f "$target/opt/uavf/mavk/installed-version" ]; then
  cmp -s "$bundle/mavk-installed-addon.sha256" "$target/var/lib/uavf/installer-mavk.sha256"
else
  install -d -m 0755 "$target/var/lib/uavf"
  work=$(mktemp -d "$target/var/lib/uavf/mavk-stage.XXXXXXXX")
  trap 'rm -rf -- "$work"' EXIT
  tar --extract --gzip --file "$bundle/$archive" --directory "$work"
  install -m 0644 "$bundle/mavk-installed-addon.sha256" "$target/var/lib/uavf/installer-mavk.sha256"
  curtin in-target --target="$target" -- /bin/sh "${work#"$target"}/install-mavk-installed.sh" --offline
  grep -qx 'version=1' "$target/opt/uavf/mavk/installed-version"
fi
for name in uavf-plasma-session.py uavf-plasma-install.sh; do
  install -D -m 0755 "$scripts/$name" "$target/usr/local/sbin/$name"
done
install -D -m 0644 "$scripts/uavf-plasma-install.service" \
  "$target/etc/systemd/system/uavf-plasma-install.service"
if "$plasma"; then
  [ -s "$target/etc/systemd/user/plasma-kwin_x11.service.d/91-uavf-installed-mavk.conf" ]
  python3 "$scripts/uavf-plasma-session.py" "$target"
  touch "$target/var/lib/uavf/plasma-x11.ready"
  printf '%s\n' 'UAVF_INSTALL_RUNTIME=PLASMA_X11_STAGED' >/dev/console || true
fi
