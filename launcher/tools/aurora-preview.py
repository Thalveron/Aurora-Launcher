#!/usr/bin/env python3
"""Local-only development preview; no device credentials and no dependency installation."""
import argparse
import hashlib
import http.server
import json
from pathlib import Path
import socketserver
import urllib.parse

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/assets/aurora"


class Preview(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    def do_GET(self):
        path = urllib.parse.urlsplit(self.path).path
        query = urllib.parse.urlsplit(self.path).query
        if path in ("/console/", "/console/index.html", "/console/settings.html"):
            self.send_response(302)
            self.send_header("Location", "/console/app.html" + ("?" + query if query else ""))
            self.end_headers()
            return
        if path == "/console/app.html":
            text = (ROOT / "console/app.html").read_text()
            if urllib.parse.parse_qs(query).get("mock") == ["1"]:
                text = text.replace('<script src="/core/api.js">', '<script src="/core/mock.js"></script><script src="/core/api.js">')
            data = text.encode()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        # Review-only UI samples live outside APK assets.
        if path in ("/console/shell-preview.html", "/console/shell-preview.js"):
            source = Path(__file__).resolve().parent / "console-preview" / path.rsplit("/", 1)[1]
            data = source.read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", self.guess_type(str(source)) + "; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        if path == "/":
            self.send_response(302)
            self.send_header("Location", "/core/preview.html")
            self.end_headers()
            return
        if path == "/__version":
            signature = hashlib.sha256()
            for entry in sorted(ROOT.rglob("*")):
                if entry.is_file():
                    signature.update(f"{entry.relative_to(ROOT)}:{entry.stat().st_mtime_ns}".encode())
            data = json.dumps({"version": signature.hexdigest()}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        super().do_GET()

    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        if urllib.parse.urlsplit(self.path).path.startswith("/console/"):
            # Match the native console policy so preview cannot hide inline-script bugs.
            self.send_header("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; media-src 'self' blob:; font-src 'self'; connect-src 'self' ws://127.0.0.1:8899; object-src 'none'; frame-ancestors 'none'")
        super().end_headers()

    def log_message(self, *_):
        pass


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", default=8765, type=int)
    options = parser.parse_args()
    server = http.server.ThreadingHTTPServer(("127.0.0.1", options.port), Preview)
    print(f"Preview: http://127.0.0.1:{options.port}/core/preview.html", flush=True)
    server.serve_forever()
