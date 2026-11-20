"""Add only diagnostic FFmpeg packages; refuse upgrades/removals."""
import json, os, subprocess, sys, urllib.request
env = dict(os.environ, LC_ALL='C', DEBIAN_FRONTEND='noninteractive')
package = sys.argv[1] if len(sys.argv)>1 else 'ffmpeg'
if package not in ('ffmpeg','gstreamer1.0-plugins-ugly'):
    raise ValueError('Not a permitted diagnostic package')
base = ['apt-get', '--no-upgrade', '--no-install-recommends', 'install', package]
sim = subprocess.run(base[:1] + ['-s'] + base[1:], capture_output=True, text=True, env=env, timeout=30)
report = {'simulation': sim.stdout + sim.stderr, 'simulation_exit': sim.returncode}
if sim.returncode or '0 upgraded,' not in sim.stdout or '0 to remove' not in sim.stdout:
    report['refused'] = 'Must not upgrade/remove working packages'
else:
    p = subprocess.run(['sudo', '-n'] + base[:1] + ['-y'] + base[1:], capture_output=True, text=True, env=env, timeout=300)
    report['install_exit'] = p.returncode
    report['install_output'] = p.stdout + p.stderr
data = json.dumps(report, indent=2).encode()
print(data.decode(), flush=True)
urllib.request.urlopen(urllib.request.Request('http://10.159.93.215:39054/report', data=data, method='POST'), timeout=15).read()
