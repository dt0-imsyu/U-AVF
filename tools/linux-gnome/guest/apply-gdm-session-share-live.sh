#!/bin/sh
# Configure an already-installed U-AVF Ubuntu system to share the existing,
# logind/PAM-managed GDM/Xorg desktop. No second GNOME or X server is started.
# Run as root from that guest. This changes runtime/service files only; it does
# not touch the persistent disk layout, user files, ISO, or boot firmware.
set -eu

if [ "$(id -u)" -ne 0 ]; then
  echo 'Run this helper with sudo.' >&2
  exit 77
fi
server=$(command -v x0tigervncserver || command -v x0vncserver || true)
if [ -z "$server" ]; then
  echo 'The TigerVNC existing-display scraper is unavailable.' >&2
  exit 1
fi

systemctl stop winavf-xvnc.service 2>/dev/null || true
systemctl disable winavf-xvnc.service 2>/dev/null || true
systemctl unmask winavf-xvnc.service 2>/dev/null || true

cat > /usr/local/sbin/uavf-gdm-display-share.sh <<'EOF'
#!/bin/sh
set -eu
exec /usr/bin/python3 - /usr/local/sbin/winavf-X0tigervnc <<'PY'
import os, sys, time
server = sys.argv[1]
deadline = time.monotonic() + 180
while True:
    ps = []
    for name in os.listdir('/proc'):
        if not name.isdigit(): continue
        try:
            with open('/proc/'+name+'/comm','rb') as f: comm=f.read().strip().decode('ascii','replace')
            if comm not in ('gnome-shell','gdm-x-session','Xorg'): continue
            uid=os.stat('/proc/'+name).st_uid
            with open('/proc/'+name+'/environ','rb') as f: env=dict(x.decode('utf-8','replace').split('=',1) for x in f.read().split(b'\0') if b'=' in x)
            with open('/proc/'+name+'/cmdline','rb') as f: cmd=f.read().decode('utf-8','replace')
            ps.append((comm,uid,env,cmd))
        except (OSError,ValueError): pass
    for comm, uid, env, _ in ps:
        d=env.get('DISPLAY',''); n=d[1:].split('.',1)[0] if d.startswith(':') else ''
        r=env.get('XDG_RUNTIME_DIR',''); a=env.get('XAUTHORITY','')
        if (comm != 'gnome-shell' or uid == 0 or env.get('XDG_SESSION_TYPE') != 'x11' or
            not n.isdigit() or not os.path.exists('/tmp/.X11-unix/X'+n) or
            not os.path.isfile(a) or not os.access(a,os.R_OK) or r != '/run/user/'+str(uid) or
            not os.path.isdir(r) or not env.get('DBUS_SESSION_BUS_ADDRESS','').startswith('unix:') or
            not any(c=='gdm-x-session' and u==uid for c,u,_,_ in ps) or
            not any(c=='Xorg' and (d in cmd.split('\0') or a in cmd.split('\0')) for c,_,_,cmd in ps)): continue
        out=os.environ.copy(); out.update(env); out.update({'DISPLAY':d,'XAUTHORITY':a,'XDG_RUNTIME_DIR':r})
        print('UAVF_GDM_SHARE=START DISPLAY='+d+' UID='+str(uid),file=sys.stderr,flush=True)
        os.execve(server,[server,'-display',d,'-rfbport','5901','-localhost','-SecurityTypes','None','-AcceptKeyEvents','-AcceptPointerEvents','-AlwaysShared'],out)
    if time.monotonic() >= deadline: print('UAVF_GDM_SHARE=DISPLAY_NOT_READY',file=sys.stderr); sys.exit(1)
    time.sleep(1)
PY
EOF
install -m 0755 "$server" /usr/local/sbin/winavf-X0tigervnc
chmod 0755 /usr/local/sbin/uavf-gdm-display-share.sh

cat > /etc/systemd/system/winavf-xvnc.service <<'EOF'
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
  /etc/systemd/system/graphical.target.wants/winavf-xvnc.service
systemctl daemon-reload
systemctl restart winavf-xvnc.service
systemctl --quiet is-active winavf-xvnc.service
systemctl status --no-pager --full winavf-xvnc.service
