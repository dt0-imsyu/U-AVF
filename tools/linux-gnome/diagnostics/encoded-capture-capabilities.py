"""Read-only guest capture capability inventory; no service/display changes."""
import json
import subprocess
import urllib.request

report = {"kind": "encoded-capture-capabilities", "commands": {}}
commands = {
    "renderer": ["glxinfo", "-B"],
    "pipewire_source": ["gst-inspect-1.0", "pipewiresrc"],
    "gl_upload": ["gst-inspect-1.0", "glupload"],
    "gl_download": ["gst-inspect-1.0", "gldownload"],
    "gl_convert": ["gst-inspect-1.0", "glcolorconvert"],
    "gnome_package": ["dpkg-query", "-W", "gnome-shell", "libmutter-14-0"],
    "processes": ["ps", "-eo", "pid,pcpu,comm"],
}
for name, command in commands.items():
    try:
        result = subprocess.run(command, capture_output=True, text=True, timeout=15)
        report["commands"][name] = {
            "exit": result.returncode,
            "stdout": result.stdout[:30000],
            "stderr": result.stderr[:4000],
        }
    except (OSError, subprocess.TimeoutExpired) as error:
        report["commands"][name] = {"error": str(error)}
data = json.dumps(report, indent=2).encode()
urllib.request.urlopen(urllib.request.Request(
    "http://10.159.93.215:39054/report", data=data, method="POST"), timeout=15).read()
print("CAPTURE_CAPABILITIES_SAVED", flush=True)
