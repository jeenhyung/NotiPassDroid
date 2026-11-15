#!/usr/bin/env python3
"""
NotiPassDroid APK Web Distribution Server
Allows downloading the compiled APK directly from Android devices over Wi-Fi or local network.
"""

import http.server
import socketserver
import os
import sys
import socket
import datetime
import urllib.parse

PORT = 8080
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
APK_PATH = os.path.join(BASE_DIR, "app", "build", "outputs", "apk", "debug", "app-debug.apk")

def get_ip_addresses():
    ips = []
    # 1. Local Wi-Fi / Ethernet
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        local_ip = s.getsockname()[0]
        s.close()
        if local_ip and local_ip not in ips:
            ips.append(local_ip)
    except Exception:
        pass

    # 2. Add other interfaces (like Tailscale 100.x.x.x)
    try:
        import subprocess
        out = subprocess.check_output(["ifconfig"], text=True)
        for line in out.splitlines():
            line = line.strip()
            if line.startswith("inet ") and not line.startswith("inet 127."):
                ip = line.split()[1]
                if ip not in ips:
                    ips.append(ip)
    except Exception:
        pass

    if not ips:
        ips.append("127.0.0.1")
    return ips

HTML_TEMPLATE = """<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>NotiPassDroid - APK 다운로드</title>
    <style>
        :root {
            --primary: #0F9D58;
            --primary-dark: #0B8043;
            --bg: #F8F9FA;
            --card-bg: #FFFFFF;
            --text-main: #202124;
            --text-muted: #5F6368;
            --border: #DADCE0;
        }
        @media (prefers-color-scheme: dark) {
            :root {
                --primary: #34A853;
                --primary-dark: #2E9549;
                --bg: #121212;
                --card-bg: #1E1E1E;
                --text-main: #E8EAED;
                --text-muted: #9AA0A6;
                --border: #3C4043;
            }
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            background-color: var(--bg);
            color: var(--text-main);
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
            padding: 20px;
        }
        .container {
            width: 100%;
            max-width: 440px;
            background: var(--card-bg);
            border: 1px solid var(--border);
            border-radius: 24px;
            padding: 32px 24px;
            box-shadow: 0 8px 24px rgba(0,0,0,0.06);
            text-align: center;
        }
        .app-icon {
            width: 80px;
            height: 80px;
            background: linear-gradient(135deg, #0F9D58, #34A853);
            border-radius: 20px;
            display: inline-flex;
            justify-content: center;
            align-items: center;
            margin-bottom: 16px;
            box-shadow: 0 4px 16px rgba(15, 157, 88, 0.3);
        }
        .app-icon svg {
            width: 44px;
            height: 44px;
            fill: white;
        }
        h1 {
            font-size: 24px;
            font-weight: 700;
            margin-bottom: 6px;
        }
        .tagline {
            font-size: 14px;
            color: var(--text-muted);
            margin-bottom: 24px;
            line-height: 1.4;
        }
        .info-card {
            background: var(--bg);
            border-radius: 14px;
            padding: 14px;
            display: flex;
            justify-content: space-around;
            margin-bottom: 24px;
            font-size: 13px;
        }
        .info-item span:first-child {
            display: block;
            color: var(--text-muted);
            margin-bottom: 4px;
            font-size: 11px;
        }
        .info-item span:last-child {
            font-weight: 600;
            color: var(--text-main);
        }
        .qr-section {
            background: white;
            padding: 16px;
            border-radius: 16px;
            display: inline-block;
            margin-bottom: 24px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.04);
            border: 1px solid var(--border);
        }
        #qrcode {
            display: flex;
            justify-content: center;
            align-items: center;
        }
        #qrcode img {
            display: block;
            margin: 0 auto;
        }
        .qr-help {
            font-size: 12px;
            color: var(--text-muted);
            margin-top: 8px;
        }
        .btn-download {
            display: block;
            width: 100%;
            background: var(--primary);
            color: white;
            text-decoration: none;
            padding: 16px;
            border-radius: 14px;
            font-size: 16px;
            font-weight: 600;
            transition: background 0.2s, transform 0.1s;
            box-shadow: 0 4px 12px rgba(15, 157, 88, 0.25);
        }
        .btn-download:hover {
            background: var(--primary-dark);
        }
        .btn-download:active {
            transform: scale(0.98);
        }
        .guide-box {
            margin-top: 24px;
            text-align: left;
            background: var(--bg);
            border-radius: 14px;
            padding: 16px;
            font-size: 12px;
            line-height: 1.6;
            color: var(--text-muted);
        }
        .guide-box strong {
            color: var(--text-main);
            display: block;
            margin-bottom: 6px;
        }
        .guide-box ol {
            padding-left: 20px;
        }
        .guide-box li {
            margin-bottom: 4px;
        }
    </style>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js"></script>
</head>
<body>
    <div class="container">
        <div class="app-icon">
            <svg viewBox="0 0 24 24">
                <path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2zm-2 1H8v-6c0-2.48 1.51-4.5 4-4.5s4 2.02 4 4.5v6z"/>
            </svg>
        </div>
        <h1>NotiPassDroid</h1>
        <p class="tagline">스마트폰 알림 가로채기 & 웹훅 자동화 앱</p>

        <div class="info-card">
            <div class="info-item">
                <span>버전</span>
                <span>v1.0.0</span>
            </div>
            <div class="info-item">
                <span>파일 크기</span>
                <span>{apk_size}</span>
            </div>
            <div class="info-item">
                <span>빌드 일시</span>
                <span>{build_time}</span>
            </div>
        </div>

        <div class="qr-section">
            <div id="qrcode"></div>
            <p class="qr-help">📱 모바일 카메라로 QR을 스캔하여 바로 다운로드</p>
        </div>

        <a href="/download" class="btn-download" id="downloadBtn">
            ⬇️ NotiPassDroid APK 다운로드
        </a>

        <div class="guide-box">
            <strong>설치 안내</strong>
            <ol>
                <li>APK 다운로드 완료 후 파일을 터치하여 설치합니다.</li>
                <li>안드로이드 보안 경고 시 <b>[출처를 알 수 없는 앱 설치 허용]</b>을 선택합니다.</li>
                <li>앱 실행 후 <b>[알림 접근 권한]</b> 및 <b>[배터리 최적화 해제]</b>를 활성화합니다.</li>
            </ol>
        </div>
    </div>

    <script>
        const downloadUrl = window.location.origin + "/download";
        new QRCode(document.getElementById("qrcode"), {
            text: downloadUrl,
            width: 160,
            height: 160,
            colorDark: "#202124",
            colorLight: "#ffffff",
            correctLevel: QRCode.CorrectLevel.M
        });
    </script>
</body>
</html>
"""

class ApkDownloadHandler(http.server.SimpleHTTPRequestHandler):
    def do_HEAD(self):
        self.handle_request(head_only=True)

    def do_GET(self):
        self.handle_request(head_only=False)

    def handle_request(self, head_only=False):
        url_parts = urllib.parse.urlparse(self.path)
        path = url_parts.path

        if path == "/" or path == "/index.html":
            if not os.path.exists(APK_PATH):
                self.send_response(404)
                self.send_header("Content-Type", "text/plain; charset=utf-8")
                self.end_headers()
                if not head_only:
                    self.wfile.write(b"APK file not found. Please build it first: ./gradlew assembleDebug")
                return

            size_bytes = os.path.getsize(APK_PATH)
            size_mb = f"{size_bytes / (1024 * 1024):.1f} MB"
            mtime = os.path.getmtime(APK_PATH)
            build_time = datetime.datetime.fromtimestamp(mtime).strftime("%m월 %d일 %H:%M")

            html_content = HTML_TEMPLATE.replace("{apk_size}", size_mb).replace("{build_time}", build_time)

            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(html_content.encode('utf-8'))))
            self.end_headers()
            if not head_only:
                self.wfile.write(html_content.encode('utf-8'))
            return

        elif path == "/download" or path.endswith(".apk"):
            if not os.path.exists(APK_PATH):
                self.send_response(404)
                self.send_header("Content-Type", "text/plain; charset=utf-8")
                self.end_headers()
                if not head_only:
                    self.wfile.write(b"APK file not found.")
                return

            size = os.path.getsize(APK_PATH)
            self.send_response(200)
            self.send_header("Content-Type", "application/vnd.android.package-archive")
            self.send_header("Content-Disposition", 'attachment; filename="NotiPassDroid-v1.0.0.apk"')
            self.send_header("Content-Length", str(size))
            self.end_headers()

            if not head_only:
                with open(APK_PATH, "rb") as f:
                    while chunk := f.read(65536):
                        self.wfile.write(chunk)
            return

        else:
            self.send_response(404)
            self.end_headers()

def main():
    ips = get_ip_addresses()
    print("=" * 60)
    print("🚀 NotiPassDroid APK 다운로드 웹 서버 실행 중!")
    print("=" * 60)
    print(f"📁 대상 APK: {APK_PATH}")
    print()
    print("📲 동일한 Wi-Fi 환경의 스마트폰이나 PC 브라우저에서 아래 주소로 접속하세요:")
    for ip in ips:
        print(f"   ▶ http://{ip}:{PORT}")
    print(f"   ▶ http://localhost:{PORT}")
    print("=" * 60)
    print("종료하려면 Ctrl + C를 누르세요.")

    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(("", PORT), ApkDownloadHandler) as httpd:
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\n서버를 종료합니다.")

if __name__ == "__main__":
    main()
