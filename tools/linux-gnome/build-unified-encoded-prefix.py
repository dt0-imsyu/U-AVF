"""Add the production encoded runtime to a COPY of a proven P7 prefix.

Consumes the read-only dependency export; retains early CPIO, kernel/launcher
and all unrelated files. Never opens user disks or the Ubuntu ISO.
"""
import argparse, base64, hashlib, importlib.machinery, json, subprocess, sys
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--prefix', type=Path, required=True)
p.add_argument('--dependencies', type=Path,
               help='Encoder dependency export; omit to retain the audited dependencies from the input prefix')
p.add_argument('--output-dir', type=Path, required=True)
p.add_argument('--runtime-reference', type=Path,
               help='Proven installer prefix supplying X0 scraper/license for Live')
p.add_argument('--installed-mavk-addon', type=Path,
               help='Verified installed Mavk add-on archive for the Ubuntu installer')
p.add_argument('--plasma-x11', action='store_true', help='Provision Plasma X11 in the installation target')
a=p.parse_args()
if a.runtime_reference and a.installed_mavk_addon:
    p.error('Installed Mavk staging requires an installer prefix')
if a.plasma_x11 and not a.installed_mavk_addon:
    p.error('Plasma X11 requires the installed Mavk add-on')
if a.output_dir.exists(): raise SystemExit('Refusing existing output directory')
here=Path(__file__).resolve().parent
guest=here/'guest'
a.output_dir.mkdir(parents=True)
payload=a.output_dir/'payload'; payload.mkdir()
mapping={
 '/usr/lib/aarch64-linux-gnu/gstreamer-1.0/libgstx264.so':'libgstx264.so',
 '/usr/lib/aarch64-linux-gnu/libx264.so.164':'libx264.so.164',
 '/usr/share/doc/gstreamer1.0-plugins-ugly/copyright':'GST_X264_COPYRIGHT',
 '/usr/share/doc/libx264-164/copyright':'X264_COPYRIGHT'}
export=None
if a.dependencies:
    export=json.loads(a.dependencies.read_text())
    assert export['kind']=='encoded-runtime-dependencies'
    for key,name in mapping.items():
        item=export['files'][key]; data=base64.b64decode(item['base64'],validate=True)
        assert hashlib.sha256(data).hexdigest()==item['sha256']
        (payload/name).write_bytes(data)
names=('uavf-encoded-display.py','uavf-encoded.socket','uavf-encoded@.service','stage-uavf-encoded-runtime.sh','stage-uavf-safe-jit.sh','uavf-safe-jit.py')
for name in names: (payload/name).write_bytes((guest/name).read_bytes())
(payload/'uavf-encoded-capture.py').write_bytes((here/'diagnostics/encoded-glx-pbo-probe.py').read_bytes())
zstd=r'C:\msys64\usr\bin\zstd.exe'
source=a.output_dir/'source-initrd'
sha=lambda path: hashlib.sha256(path.read_bytes()).hexdigest().upper()
subprocess.run([sys.executable,str(here/'extract-prefix-initrd.py'),str(a.prefix),str(source),
                '--prefix-sha256',sha(a.prefix)],check=True)
cpio=importlib.machinery.SourceFileLoader('cpio',str(here.parent/'generic-ubuntu/compare-p7-clean-p9.py')).load_module()
data=source.read_bytes()
files,_=cpio.parse_cpio(subprocess.run([zstd,'-d','-q','-c'],input=data[cpio.early_end(data):],capture_output=True,check=True).stdout)
output=a.output_dir/'unified-initrd'
cmd=[sys.executable,str(here.parent/'generic-ubuntu/repack-p7-frame-bridge.py'),
     '--source',str(source),'--output',str(output),'--zstd',zstd,'--zstd-level','19']
entries={'usr/local/sbin/'+path.name:path for path in payload.iterdir()}
canonical={
 'scripts/init-bottom/99-winavf-vsock-systemd':'99-winavf-vsock-systemd',
 'scripts/init-premount/winavf-frame-bridge.py':'winavf-frame-bridge.py',
 'usr/local/sbin/winavf-frame-bridge':'winavf-frame-bridge.py',
 'usr/local/sbin/uavf-install-runtime.sh':'uavf-install-runtime.sh',
 'usr/local/sbin/uavf-gdm-display-share.py':'uavf-gdm-display-share.py',
 'usr/local/sbin/uavf-control.py':'uavf-control.py',
 'usr/local/sbin/uavf-control.socket':'uavf-control.socket',
 'usr/local/sbin/uavf-control@.service':'uavf-control@.service'}
canonical.update({
 'usr/local/sbin/uavf-clipboard.py':'uavf-clipboard.py',
 'usr/local/sbin/uavf-clipboard.socket':'uavf-clipboard.socket',
 'usr/local/sbin/uavf-clipboard@.service':'uavf-clipboard@.service'})
entries.update({key:guest/name for key,name in canonical.items()})
if a.runtime_reference:
    from pyfatfs.PyFatFS import PyFatFS
    with PyFatFS(str(a.runtime_reference),offset=1048576,read_only=True) as fat:
        ref=fat.readbytes('/CASPER/INITRD')
    rf,_=cpio.parse_cpio(subprocess.run([zstd,'-d','-q','-c'],input=ref[cpio.early_end(ref):],capture_output=True,check=True).stdout)
    for key in ('usr/local/sbin/winavf-X0tigervnc','usr/local/share/uavf-licenses/TIGERVNC_X0_COPYRIGHT'):
        path=payload/key.rsplit('/',1)[1]; path.write_bytes(rf[key]); entries[key]=path
    # Live must not gain an autoinstall configuration or storage early-command.
    assert 'autoinstall.yaml' not in files, 'Live source contains autoinstall config'
if a.installed_mavk_addon:
    if 'autoinstall.yaml' not in files:
        raise ValueError('Installed Mavk staging requires autoinstall in the source prefix')
    archive_name='mavk-installed-addon.tar.gz'
    bundle='usr/local/lib/uavf-installer/'
    digest=payload/'mavk-installed-addon.sha256'
    digest.write_bytes(f'{sha(a.installed_mavk_addon).lower()}  {archive_name}\n'.encode('ascii'))
    for name in ('uavf-install-preflight.sh','stage-uavf-installer-mavk.sh',
                 'uavf-plasma-session.py','uavf-plasma-install.sh','uavf-plasma-install.service'):
        entries['usr/local/sbin/'+name]=guest/name
    entries[bundle+archive_name]=a.installed_mavk_addon
    entries[bundle+digest.name]=digest
    if a.plasma_x11:
        profile=payload/'desktop-profile'
        profile.write_bytes(b'plasma-x11\n')
        entries[bundle+profile.name]=profile
for key,path in entries.items():
    if files.get(key)==path.read_bytes(): continue
    cmd+=['--replace-file' if key in files else '--add-file',key+'='+str(path)]
parents=set()
for key in entries:
    parents.update(str(parent).replace('\\','/') for parent in Path(key).parents if str(parent)!='.')
for parent in sorted(parents,key=lambda name:(name.count('/'),name)):
    if parent not in files: cmd+=['--add-dir',parent]
subprocess.run(cmd,check=True)
prefix=a.output_dir/'unified-platform-prefix.img'
subprocess.run([sys.executable,str(here/'replace-esp-initrd.py'),str(a.prefix),str(output),str(prefix),
 '--source-sha256',sha(a.prefix),'--initrd-sha256',sha(output)],check=True)
subprocess.run([sys.executable,str(here/'audit-product-display-payload.py'),str(prefix)],check=True)
with prefix.open('rb') as stream:
    stream.seek(1048576); esp=hashlib.sha256(stream.read(126*1024*1024)).hexdigest().upper()
report={'result':'PASS','prefix_sha256':sha(prefix),'esp_sha256':esp,
        'initrd_sha256':sha(output),'payload':{q.name:sha(q) for q in payload.iterdir()},
        'dependency_versions':export['commands'] if export else 'retained from audited source',
        'installed_mavk_addon_sha256':sha(a.installed_mavk_addon) if a.installed_mavk_addon else None,
        'iso_opened':False}
(a.output_dir/'report.json').write_text(json.dumps(report,indent=2))
print(json.dumps({k:report[k] for k in ('result','prefix_sha256','esp_sha256')}))
