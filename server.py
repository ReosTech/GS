import http.server
import socketserver
import os
import mimetypes
import json

PORT = 3000
DIRECTORY = os.path.dirname(os.path.abspath(__file__))

mimetypes.add_type("application/vnd.android.package-archive", ".apk")
mimetypes.add_type("application/octet-stream", ".aab")
mimetypes.add_type("application/zip", ".zip")
mimetypes.add_type("text/html; charset=utf-8", ".html")
mimetypes.add_type("application/json", ".json")

class ReosHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.end_headers()

    def do_GET(self):
        if self.path in ("/health", "/healthz", "/ping", "/_health"):
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok", "service": "reos-download-server"}).encode("utf-8"))
            return
        super().do_GET()

    def end_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Cache-Control", "no-cache")
        super().end_headers()

if __name__ == "__main__":
    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(("0.0.0.0", PORT), ReosHandler) as httpd:
        print(f"Reos Download Server running on http://0.0.0.0:{PORT} serving {DIRECTORY}")
        httpd.serve_forever()
