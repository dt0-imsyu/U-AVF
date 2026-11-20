#!/bin/sh
set -eu

[ "$(findmnt -n -o SOURCE,FSTYPE /)" = '/dev/vda3 ext4' ]
base=/opt/uavf/mavk
grep -qx 'version=1' "$base/installed-version"
[ -x "$base/installed-kwin" ] && [ -s "$base/installed-kwin.conf" ]
dm=/etc/systemd/system/display-manager.service
manager=$(readlink "$dm")
case "${manager##*/}" in gdm.service|gdm3.service) ;; *) exit 1 ;; esac
if [ ! -s /usr/share/xsessions/plasma.desktop ] || [ ! -x /usr/bin/kwin_x11 ] ||
   [ ! -s /usr/lib/systemd/user/plasma-kwin_x11.service ]; then
    export DEBIAN_FRONTEND=noninteractive
    apt-get -o APT::Update::Error-Mode=any update
    apt-get -y --no-install-recommends install plasma-desktop kwin-x11
fi
[ "$(readlink "$dm")" = "$manager" ]
[ -x /usr/bin/kwin_x11 ] && [ -s /usr/lib/systemd/user/plasma-kwin_x11.service ]
install -D -m 0644 "$base/installed-kwin.conf" \
    /etc/systemd/user/plasma-kwin_x11.service.d/91-uavf-installed-mavk.conf
python3 /usr/local/sbin/uavf-plasma-session.py --running
touch /var/lib/uavf/plasma-x11.ready
