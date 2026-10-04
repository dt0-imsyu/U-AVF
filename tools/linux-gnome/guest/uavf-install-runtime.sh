#!/bin/sh
# Idempotently install the U-AVF guest relays into the dedicated Ubuntu root
# partition while the official desktop installer has it mounted at /target.
set -eu

target=/target
log=/var/log/uavf-install-runtime.log
staged="$target/var/lib/uavf/runtime-staged-v5"

say() {
  line="UAVF_INSTALL_RUNTIME=$1"
  printf '%s\n' "$line" >> "$log" 2>/dev/null || true
  printf '%s\n' "$line" >/dev/console 2>/dev/null || true
}

if [ "${1:-}" != '--installer-complete' ]; then
  say LATE_COMMAND_ONLY
  exit 64
fi

  if mountpoint -q "$target" && [ -r "$target/etc/os-release" ]; then
    mounted=$(findmnt -n -o SOURCE,FSTYPE --target "$target" 2>/dev/null || true)
    set -- $mounted
    source=${1:-}
    fstype=${2:-}
    partlabel=$(lsblk -no PARTLABEL "$source" 2>/dev/null | sed 's/[[:space:]]*$//' || true)
    esp_source=$(findmnt -n -o SOURCE --target "$target/boot/efi" 2>/dev/null || true)
    esp_label=$(lsblk -no PARTLABEL "$esp_source" 2>/dev/null | sed 's/[[:space:]]*$//' || true)

    # Only write to the pre-created dedicated target partition. Never follow
    # the installer UI's whole-disk target and never touch ESP or ISO media.
    if [ "$fstype" = ext4 ] && [ "$partlabel" = 'U-AVF Ubuntu Root' ] \
        && mountpoint -q "$target/boot/efi" \
        && [ "$esp_label" = 'U-AVF Platform' ]; then
      . "$target/etc/os-release"
      if [ "${ID:-}" = ubuntu ] && [ "${VERSION_ID:-}" = 24.04 ] \
          && [ -x "$target/usr/bin/python3" ] \
          && [ -x "$target/usr/bin/gnome-shell" ] \
          && [ -s "$target/boot/grub/grub.cfg" ] \
          && [ -s "$target/boot/efi/EFI/ubuntu/grubaa64.efi" ]; then
        # BEGIN_ACCOUNT_SAFETY: no shared fallback or locked-only installation.
        if ! python3 - "$target" <<'ACCOUNT_PY'
from pathlib import Path
import sys
root = Path(sys.argv[1])
users = [line.split(':') for line in (root/'etc/passwd').read_text().splitlines()]
shadow = {parts[0]: parts[1] for line in (root/'etc/shadow').read_text().splitlines()
          if len(parts := line.split(':')) >= 2}
usable = any(len(user) >= 7 and user[2].isdigit() and 1000 <= int(user[2]) < 60000
             and user[6] not in ('/usr/sbin/nologin', '/bin/false')
             and shadow.get(user[0], '').startswith('$')
             and len(shadow.get(user[0], '')) >= 20 for user in users)
if not usable:
    raise SystemExit('No user-selected password account; bootstrap not marked ready')
ACCOUNT_PY
        then
          say USER_ACCOUNT_NOT_READY
          exit 1
        fi
        say USER_ACCOUNT_READY
        # END_ACCOUNT_SAFETY
        if [ -f "$staged" ] && grep -qx 'version=5' "$staged" \
            && grep -qx 'account_policy=installer' "$staged" \
            && grep -qx 'audio=installed' "$staged" \
            && [ -s "$target/usr/local/sbin/winavf-audio-bridge.py" ] \
            && [ -s "$target/etc/systemd/system/winavf-audio.socket" ] \
            && [ -L "$target/etc/systemd/system/sockets.target.wants/winavf-audio.socket" ] \
            && [ -x "$target/usr/local/sbin/winavf-X0tigervnc" ] \
            && [ -s "$target/usr/local/sbin/uavf-control.py" ] \
            && cmp -s /usr/local/sbin/uavf-control.py "$target/usr/local/sbin/uavf-control.py" \
            && cmp -s /usr/local/sbin/uavf-clipboard.py "$target/usr/local/sbin/uavf-clipboard.py" \
            && cmp -s /usr/local/sbin/uavf-clipboard.socket "$target/etc/systemd/system/uavf-clipboard.socket" \
            && cmp -s /usr/local/sbin/uavf-clipboard@.service "$target/etc/systemd/system/uavf-clipboard@.service" \
            && [ -L "$target/etc/systemd/system/sockets.target.wants/uavf-clipboard.socket" ] \
            && [ -s "$target/etc/systemd/system/uavf-control@.service" ] \
            && cmp -s /usr/local/sbin/uavf-control@.service "$target/etc/systemd/system/uavf-control@.service" \
            && [ -L "$target/etc/systemd/system/sockets.target.wants/uavf-control.socket" ] \
            && [ -s "$target/usr/share/doc/tigervnc-scraping-server/copyright" ]; then
          if [ -s /usr/local/sbin/stage-uavf-encoded-runtime.sh ]; then
            sh /usr/local/sbin/stage-uavf-encoded-runtime.sh /usr/local/lib/uavf-encoded-payload "$target"
          fi
          say RUNTIME_V5_ALREADY_PRESENT
          exit 0
        fi

        # P7 boots Linux from FDT and has no working SVE contract. Preserve
        # these proven kernel arguments on the installed GRUB path and on
        # later update-grub regenerations.
        grub_defaults="$target/etc/default/grub"
        if [ -f "$grub_defaults" ]; then
          sed -i '/^GRUB_CMDLINE_LINUX=/d' "$grub_defaults"
          printf '%s\n' 'GRUB_CMDLINE_LINUX="acpi=off arm64.nosve console=ttyS0,115200n8"' \
            >> "$grub_defaults"
        fi
        sed -i -E '/^[[:space:]]*linux[[:space:]]/ { /acpi=off/! s/$/ acpi=off arm64.nosve console=ttyS0,115200n8/; }' \
          "$target/boot/grub/grub.cfg"
        if ! grep -Eq '^[[:space:]]*linux[[:space:]].*acpi=off.*arm64.nosve' \
            "$target/boot/grub/grub.cfg"; then
          say INSTALLED_GRUB_CMDLINE_MISSING
          exit 1
        fi

        bridge=/run/winavf-frame-bridge.py
        if [ ! -s "$bridge" ]; then
          say FRAME_BRIDGE_PAYLOAD_MISSING
          exit 1
        fi

        install -d "$target/usr/local/sbin" "$target/etc/systemd/system" \
          "$target/etc/systemd/system/graphical.target.wants" \
          "$target/etc/systemd/system/sockets.target.wants" "$target/var/lib/uavf"
        install -m 0755 "$bridge" "$target/usr/local/sbin/winavf-frame-bridge.py"

        # Additive UCTL v1 service. Root/ESP validation above remains mandatory.
        # No boot/display modification is needed for this independent channel.
        control=/usr/local/sbin/uavf-control.py
        control_units=/usr/local/sbin
        if ! grep -q "growth_version.*1" "$control"; then
          say STORAGE_GROWTH_PAYLOAD_MISSING
          exit 1
        fi
        for payload in "$control" "$control_units/uavf-control.socket" "$control_units/uavf-control@.service"; do
          if [ ! -s "$payload" ]; then
            say CONTROL_PAYLOAD_MISSING
            exit 1
          fi
        done
        install -m 0755 "$control" "$target/usr/local/sbin/uavf-control.py"
        install -m 0644 "$control_units/uavf-control.socket" "$target/etc/systemd/system/uavf-control.socket"
        install -m 0644 "$control_units/uavf-control@.service" "$target/etc/systemd/system/uavf-control@.service"
        ln -sfn ../uavf-control.socket "$target/etc/systemd/system/sockets.target.wants/uavf-control.socket"
        say CONTROL_STAGE_V2_STORAGE_GROWTH_INSTALLED

        # Provision the already proven independent UCIP4054 channel for every
        # installed user session. Socket activation discovers GDM and drops UID;
        # no private per-user terminal/autostart setup is required.
        for name in uavf-clipboard.py uavf-clipboard.socket uavf-clipboard@.service; do
          if [ ! -s "/usr/local/sbin/$name" ]; then
            say CLIPBOARD_PAYLOAD_MISSING
            exit 1
          fi
        done
        install -m 0755 /usr/local/sbin/uavf-clipboard.py "$target/usr/local/sbin/uavf-clipboard.py"
        install -m 0644 /usr/local/sbin/uavf-clipboard.socket "$target/etc/systemd/system/uavf-clipboard.socket"
        install -m 0644 /usr/local/sbin/uavf-clipboard@.service "$target/etc/systemd/system/uavf-clipboard@.service"
        ln -sfn ../uavf-clipboard.socket "$target/etc/systemd/system/sockets.target.wants/uavf-clipboard.socket"
        say CLIPBOARD_STAGE_INSTALLED_PORT_4054

        # Same production capture/encoder/socket as Live; configure offline.
        # First boot systemd starts the socket, never a second desktop session.
        if [ -s /usr/local/sbin/stage-uavf-encoded-runtime.sh ]; then
          sh /usr/local/sbin/stage-uavf-encoded-runtime.sh /usr/local/lib/uavf-encoded-payload "$target"
          say ENCODED_STAGE_V3_INSTALLED
        fi

        # Share the genuine GDM/Xorg session. The scraper is deliberately
        # given the session discovered at service start; it never starts Xorg,
        # Xvnc, or another GNOME session.
        x0vnc=/usr/local/sbin/winavf-X0tigervnc
        if [ ! -x "$x0vnc" ]; then
          say X0VNC_PAYLOAD_MISSING
          exit 1
        fi
        install -m 0755 "$x0vnc" "$target/usr/local/sbin/winavf-X0tigervnc"
        license=/usr/local/share/uavf-licenses/TIGERVNC_X0_COPYRIGHT
        if [ ! -s "$license" ]; then
          say X0VNC_LICENSE_PAYLOAD_MISSING
          exit 1
        fi
        install -d "$target/usr/share/doc/tigervnc-scraping-server"
        install -m 0644 "$license" \
          "$target/usr/share/doc/tigervnc-scraping-server/copyright"
        cat > "$target/usr/local/sbin/uavf-gdm-display-share.sh" <<'EOF'
#!/bin/sh
set -eu
exec /usr/bin/python3 - /usr/local/sbin/winavf-X0tigervnc <<'PY'
import os, sys, time

server = sys.argv[1]
deadline = time.monotonic() + 180
while True:
    processes = []
    for name in os.listdir('/proc'):
        if not name.isdigit():
            continue
        try:
            with open('/proc/' + name + '/comm', 'rb') as f:
                comm = f.read().strip().decode('ascii', 'replace')
            if comm not in ('gnome-shell', 'gdm-x-session', 'Xorg'):
                continue
            uid = os.stat('/proc/' + name).st_uid
            with open('/proc/' + name + '/environ', 'rb') as f:
                env = dict(x.decode('utf-8', 'replace').split('=', 1)
                           for x in f.read().split(b'\0') if b'=' in x)
            with open('/proc/' + name + '/cmdline', 'rb') as f:
                cmdline = f.read().decode('utf-8', 'replace')
            processes.append((comm, uid, env, cmdline))
        except (OSError, ValueError):
            pass
    for comm, uid, env, _ in processes:
        display = env.get('DISPLAY', '')
        number = display[1:].split('.', 1)[0] if display.startswith(':') else ''
        runtime = env.get('XDG_RUNTIME_DIR', '')
        authority = env.get('XAUTHORITY', '')
        if (comm != 'gnome-shell' or uid == 0 or
                env.get('XDG_SESSION_TYPE') != 'x11' or not number.isdigit() or
                not os.path.exists('/tmp/.X11-unix/X' + number) or
                not os.path.isfile(authority) or not os.access(authority, os.R_OK) or
                runtime != '/run/user/' + str(uid) or not os.path.isdir(runtime) or
                not env.get('DBUS_SESSION_BUS_ADDRESS', '').startswith('unix:') or
                not any(c == 'gdm-x-session' and u == uid for c, u, _, _ in processes) or
                not any(c == 'Xorg' and (display in cmd.split('\0') or authority in cmd.split('\0'))
                        for c, _, _, cmd in processes)):
            continue
        selected = os.environ.copy()
        selected.update(env)
        selected.update({'HOME': env.get('HOME', '/home/' + str(uid)),
                         'DISPLAY': display, 'XAUTHORITY': authority,
                         'XDG_RUNTIME_DIR': runtime})
        print('UAVF_GDM_SHARE=START DISPLAY=' + display +
              ' UID=' + str(uid), file=sys.stderr, flush=True)
        os.execve(server, [server, '-display', display, '-rfbport', '5901',
                           '-localhost', '-SecurityTypes', 'None',
                           '-AcceptKeyEvents', '-AcceptPointerEvents',
                           '-AlwaysShared'], selected)
    if time.monotonic() >= deadline:
        print('UAVF_GDM_SHARE=DISPLAY_NOT_READY', file=sys.stderr, flush=True)
        sys.exit(1)
    time.sleep(1)
PY
EOF
        chmod 0755 "$target/usr/local/sbin/uavf-gdm-display-share.sh"
        cat > "$target/etc/systemd/system/winavf-xvnc.service" <<'EOF'
[Unit]
Description=U-AVF share of the authenticated GDM/Xorg desktop
After=display-manager.service systemd-user-sessions.service
Wants=display-manager.service

[Service]
Type=simple
Restart=on-failure
RestartSec=3
ExecStart=/usr/local/sbin/uavf-gdm-display-share.sh

[Install]
WantedBy=graphical.target
EOF
        ln -sfn ../winavf-xvnc.service \
          "$target/etc/systemd/system/graphical.target.wants/winavf-xvnc.service"

        cat > "$target/etc/systemd/system/winavf-frame.socket" <<'EOF'
[Unit]
Description=U-AVF Ubuntu display and input channel

[Socket]
ListenStream=vsock::4052
Accept=yes

[Install]
WantedBy=sockets.target
EOF
        cat > "$target/etc/systemd/system/winavf-frame@.service" <<'EOF'
[Unit]
Description=U-AVF Ubuntu display and input session
After=graphical.target

[Service]
StandardInput=socket
StandardOutput=socket
StandardError=journal+console
ExecStart=/usr/bin/python3 /usr/local/sbin/winavf-frame-bridge.py
EOF
        ln -sfn ../winavf-frame.socket \
          "$target/etc/systemd/system/sockets.target.wants/winavf-frame.socket"

        cat > "$target/etc/systemd/system/winavf-vsock.socket" <<'EOF'
[Unit]
Description=U-AVF installed-runtime readiness marker

[Socket]
ListenStream=vsock::4051
Accept=yes

[Install]
WantedBy=sockets.target
EOF
        cat > "$target/usr/local/sbin/uavf-ready-reply.sh" <<'EOF'
#!/bin/sh
if [ -f /var/lib/uavf/installed-runtime-v5 ]; then
  if grep -qx 'audio=installed' /var/lib/uavf/installed-runtime-v5 \
      && [ -s /usr/local/sbin/winavf-audio-bridge.py ] \
      && systemctl is-active --quiet winavf-audio.socket; then
    printf 'LVH1\002\000\000\000\000\000\000\000\007\000\000\000'
  else
    printf 'LVH1\002\000\000\000\000\000\000\000\003\000\000\000'
  fi
else
  printf 'LVH1\001\000\000\000\000\000\000\000\001\000\000\000'
fi
EOF
        chmod 0755 "$target/usr/local/sbin/uavf-ready-reply.sh"
        cat > "$target/etc/systemd/system/winavf-vsock@.service" <<'EOF'
[Unit]
Description=U-AVF installed-runtime readiness reply

[Service]
StandardInput=socket
StandardOutput=socket
ExecStart=/usr/local/sbin/uavf-ready-reply.sh
EOF
        ln -sfn ../winavf-vsock.socket \
          "$target/etc/systemd/system/sockets.target.wants/winavf-vsock.socket"

        # The init-bottom hook stages an additional copy in the Live root.
        # /run survives switch_root on the tested profile, but late-command
        # execution may use a different runtime namespace; use the durable
        # installer payload as fallback rather than failing a completed OS
        # install because that transient mount is not visible.
        audio_bridge=
        audio_pactl=
        audio_parec=
        for candidate in /run/winavf-audio-bridge.py \
            /usr/local/lib/uavf-installer/winavf-audio-bridge.py; do
          if [ -s "$candidate" ]; then audio_bridge=$candidate; break; fi
        done
        for candidate in /run/winavf-pactl \
            /usr/local/lib/uavf-installer/winavf-pactl; do
          if [ -s "$candidate" ] && [ -x "$candidate" ]; then audio_pactl=$candidate; break; fi
        done
        for candidate in /run/winavf-parec \
            /usr/local/lib/uavf-installer/winavf-parec; do
          if [ -s "$candidate" ] && [ -x "$candidate" ]; then audio_parec=$candidate; break; fi
        done
        say "AUDIO_PAYLOAD bridge=$([ -n "$audio_bridge" ] && echo yes || echo no) pactl=$([ -n "$audio_pactl" ] && echo yes || echo no) parec=$([ -n "$audio_parec" ] && echo yes || echo no)"
        audio_status=not-available
        if [ -n "$audio_bridge" ] && [ -n "$audio_pactl" ] && [ -n "$audio_parec" ]; then
          install -d "$target/usr/bin"
          install -m 0755 "$audio_bridge" \
            "$target/usr/local/sbin/winavf-audio-bridge.py"
          install -m 0755 "$audio_pactl" "$target/usr/bin/pactl"
          install -m 0755 "$audio_parec" "$target/usr/bin/parec"
        cat > "$target/usr/local/sbin/uavf-audio-run.sh" <<'EOF'
#!/bin/sh
set -eu
exec /usr/bin/python3 - <<'AUDIO_SESSION_PY'
import importlib.util
import os
import pwd
import time

spec = importlib.util.spec_from_file_location('uavf_session',
    '/usr/local/sbin/winavf-frame-bridge.py')
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)
deadline = time.monotonic() + 180
while time.monotonic() < deadline:
    env = helper.find_display()
    if env:
        uid = int(env['XDG_RUNTIME_DIR'].rsplit('/', 1)[1])
        if 1000 <= uid < 60000:
            account = pwd.getpwuid(uid)
            env.update(HOME=account.pw_dir, USER=account.pw_name,
                       LOGNAME=account.pw_name,
                       PULSE_SERVER='unix:/run/user/%d/pulse/native' % uid)
            os.initgroups(account.pw_name, account.pw_gid)
            os.setgid(account.pw_gid)
            os.setuid(uid)
            os.execve('/usr/bin/python3', ['python3',
                '/usr/local/sbin/winavf-audio-bridge.py'], env)
    time.sleep(1)
raise RuntimeError('Authenticated user audio session unavailable')
AUDIO_SESSION_PY
EOF
          chmod 0755 "$target/usr/local/sbin/uavf-audio-run.sh"
          cat > "$target/etc/systemd/system/winavf-audio.socket" <<'EOF'
[Unit]
Description=U-AVF Ubuntu audio stream

[Socket]
ListenStream=vsock::4053
Accept=yes

[Install]
WantedBy=sockets.target
EOF
          cat > "$target/etc/systemd/system/winavf-audio@.service" <<'EOF'
[Unit]
Description=U-AVF Ubuntu PCM audio connection
After=graphical.target

[Service]
StandardInput=socket
StandardOutput=socket
StandardError=journal+console
ExecStart=/usr/local/sbin/uavf-audio-run.sh
EOF
          ln -sfn ../winavf-audio.socket \
            "$target/etc/systemd/system/sockets.target.wants/winavf-audio.socket"
          audio_status=installed
        fi
        say "AUDIO_STAGE=$audio_status"
        if [ -f /run/uavf-audio-payload-ready ] && [ "$audio_status" != installed ]; then
          say AUDIO_STAGE_INCONSISTENT
          exit 1
        fi

        # Keep the installer/user's authentication policy. The production
        # display also supports the GDM greeter; forcing autologin is unnecessary.
        gdm="$target/etc/gdm3/custom.conf"
        if [ ! -f "$gdm" ]; then
          say GDM_CONFIG_MISSING
          exit 1
        fi
        if ! grep -q '^\[daemon\]' "$gdm"; then
          printf '\n[daemon]\n' >> "$gdm"
        fi
        for setting in 'WaylandEnable=false'; do
          key=${setting%%=*}
          if grep -Eq "^[#[:space:]]*${key}[[:space:]]*=" "$gdm"; then
            sed -i -E "s|^[#[:space:]]*${key}[[:space:]]*=.*|${setting}|" "$gdm"
          else
            sed -i "/^\[daemon\]/a ${setting}" "$gdm"
          fi
        done

        cat > "$target/usr/local/sbin/uavf-firstboot-runtime.sh" <<'EOF'
#!/bin/sh
set -eu
marker=/var/lib/uavf/installed-runtime-v5
if [ -f "$marker" ]; then
  systemctl start uavf-session-journal-follow.service
  exit 0
fi
if [ ! -x /usr/bin/python3 ] || [ ! -x /usr/bin/gnome-shell ]; then
  echo 'UAVF_FIRSTBOOT=REQUIRED_PACKAGES_MISSING' >/dev/console 2>/dev/null || true
  exit 1
fi
systemctl daemon-reload
systemctl start uavf-control.socket
systemctl --quiet is-active uavf-control.socket
systemctl start winavf-vsock.socket winavf-frame.socket \
  uavf-session-journal-follow.service
# Keep one real logind/GDM session. Share that session through the
# existing-display VNC server; do not start a second GNOME/X server.
systemctl unmask winavf-xvnc.service
systemctl enable winavf-xvnc.service
systemctl start winavf-xvnc.service
systemctl --quiet is-active winavf-xvnc.service
audio_status=not-available
if [ -s /usr/local/sbin/winavf-audio-bridge.py ] \
    && [ -s /etc/systemd/system/winavf-audio.socket ]; then
  systemctl start winavf-audio.socket
  if systemctl is-active --quiet winavf-audio.socket; then
    audio_status=installed
  else
    echo 'UAVF_AUDIO_RUNTIME=SOCKET_START_FAILED' >/dev/console 2>/dev/null || true
    exit 1
  fi
else
  echo 'UAVF_AUDIO_RUNTIME=PAYLOAD_MISSING' >/dev/console 2>/dev/null || true
fi
tmp="$marker.tmp.$$"
printf 'version=5\nframe_port=4052\nready_port=4051\naudio_port=4053\naudio=%s\n' \
  "$audio_status" > "$tmp"
chmod 0644 "$tmp"
mv -f "$tmp" "$marker"
sync
echo 'UAVF_FIRSTBOOT=RUNTIME_ACTIVE' >/dev/console 2>/dev/null || true
echo "UAVF_AUDIO_RUNTIME=$audio_status" >/dev/console 2>/dev/null || true
EOF
        chmod 0755 "$target/usr/local/sbin/uavf-firstboot-runtime.sh"
        cat > "$target/etc/systemd/system/uavf-firstboot-runtime.service" <<'EOF'
[Unit]
Description=U-AVF first-boot runtime activation
After=multi-user.target
ConditionPathExists=!/var/lib/uavf/installed-runtime-v5

[Service]
Type=oneshot
ExecStart=/usr/local/sbin/uavf-firstboot-runtime.sh
RemainAfterExit=yes

[Install]
WantedBy=graphical.target
EOF
        ln -sfn ../uavf-firstboot-runtime.service \
          "$target/etc/systemd/system/graphical.target.wants/uavf-firstboot-runtime.service"

        cat > "$target/usr/local/sbin/uavf-session-journal-follow.sh" <<'EOF'
#!/bin/sh
set -eu
mkdir -p /var/log/uavf
exec timeout 900 journalctl -b -f -o short-monotonic \
  -u gdm.service -u winavf-xvnc.service -u systemd-logind.service \
  -u systemd-coredump.service | tee -a /var/log/uavf/session-journal.log
EOF
        chmod 0755 "$target/usr/local/sbin/uavf-session-journal-follow.sh"
        cat > "$target/etc/systemd/system/uavf-session-journal-follow.service" <<'EOF'
[Unit]
Description=U-AVF bounded GDM and desktop-session diagnostics
After=multi-user.target

[Service]
Type=simple
ExecStart=/usr/local/sbin/uavf-session-journal-follow.sh
StandardOutput=journal+console
StandardError=journal+console
TimeoutStartSec=920

[Install]
WantedBy=graphical.target
EOF
        ln -sfn ../uavf-session-journal-follow.service \
          "$target/etc/systemd/system/graphical.target.wants/uavf-session-journal-follow.service"

        tmp="$staged.tmp.$$"
        printf 'version=5\naccount_policy=installer\nframe_port=4052\nready_port=4051\naudio=%s\n' \
          "$audio_status" > "$tmp"
        chmod 0644 "$tmp"
        mv -f "$tmp" "$staged"
        install -d "$target/boot/efi/EFI/UAVF"
        printf 'version=1\nroot_partition=U-AVF Ubuntu Root\n' \
          > "$target/boot/efi/EFI/UAVF/installed.ready.tmp"
        mv -f "$target/boot/efi/EFI/UAVF/installed.ready.tmp" \
          "$target/boot/efi/EFI/UAVF/installed.ready"
        sync
        say RUNTIME_STAGED
        exit 0
      fi
    fi
  fi
say INSTALLER_COMPLETION_OR_TARGET_VALIDATION_FAILED
exit 1
