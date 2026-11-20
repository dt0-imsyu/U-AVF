"""Temporary loopback-only delivery/evidence endpoint; close after one test."""
from http.server import SimpleHTTPRequestHandler, HTTPServer
from pathlib import Path
import sys

class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(Path(__file__).parent), **kwargs)

    def do_GET(self):
        if self.path[1:] in ('uavf-safe-jit.py','stage-uavf-safe-jit.sh'):
            data=Path(__file__).parent.parent.joinpath('guest',self.path[1:]).read_bytes()
            self.send_response(200); self.send_header('Content-Length',str(len(data))); self.end_headers()
            self.wfile.write(data); return
        if self.path[1:] in ('uavf-encoded-display.py','uavf-encoded.socket','uavf-encoded@.service','install-uavf-encoded-addon.sh','winavf-frame-bridge.py'):
            data=Path(__file__).parent.parent.joinpath('guest',self.path[1:]).read_bytes()
            self.send_response(200); self.send_header('Content-Length',str(len(data))); self.end_headers()
            self.wfile.write(data); return
        if self.path == '/uavf-encoded-capture.py':
            data=Path(__file__).with_name('encoded-glx-pbo-probe.py').read_bytes()
            self.send_response(200); self.send_header('Content-Length',str(len(data))); self.end_headers()
            self.wfile.write(data); return
        if self.path[1:] in ('uavf-control.py','uavf-control.socket','uavf-control@.service','install-uavf-control-addon.sh'):
            data=Path(__file__).parent.parent.joinpath('guest',self.path[1:]).read_bytes()
            self.send_response(200); self.send_header('Content-Length',str(len(data))); self.end_headers()
            self.wfile.write(data); return
        if self.path == '/uavf-fast-delta.py':
            data = Path(__file__).parent.parent.joinpath('guest/uavf-fast-delta.py').read_bytes()
            self.send_response(200); self.send_header('Content-Length', str(len(data))); self.end_headers()
            self.wfile.write(data); return
        super().do_GET()

    def do_POST(self):
        length = int(self.headers.get('Content-Length', '0'))
        # Matched full-resolution lossless PNG evidence can exceed 4 MiB.
        if self.path != '/report' or not 0 < length <= 32 * 1024 * 1024:
            self.send_error(400); return
        Path(sys.argv[1]).write_bytes(self.rfile.read(length))
        self.send_response(200); self.end_headers(); self.wfile.write(b'OK')

HTTPServer(('127.0.0.1', 39054), Handler).serve_forever()
