#!/usr/bin/env python3
"""
编译 automation-app 并将生成的 app/test APK 上传到自动化服务端。

示例：
    python scripts/build_and_upload.py \
        --server http://127.0.0.1:8000 \
        --username admin \
        --password admin123
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any, Optional

import subprocess
import urllib.error
import urllib.parse
import urllib.request
import uuid


REPO_ROOT = Path(__file__).resolve().parents[1]
GRADLEW = REPO_ROOT / "gradlew"
APP_APK = REPO_ROOT / "automation-app" / "build" / "outputs" / "apk" / "debug" / "automation-app-debug.apk"
APP_METADATA = APP_APK.parent / "output-metadata.json"
TEST_APK = (
    REPO_ROOT
    / "automation-app"
    / "build"
    / "outputs"
    / "apk"
    / "androidTest"
    / "debug"
    / "automation-app-debug-androidTest.apk"
)
TEST_METADATA = TEST_APK.parent / "output-metadata.json"


def run_gradle_tasks(tasks: list[str]) -> None:
    if not GRADLEW.exists():
        raise SystemExit(f"gradlew not found at {GRADLEW}")

    cmd = [str(GRADLEW)] + tasks
    print(f"[build] running: {' '.join(cmd)}")
    result = subprocess.run(cmd, cwd=REPO_ROOT)
    if result.returncode != 0:
        raise SystemExit(f"Gradle command failed with exit code {result.returncode}")


def read_metadata(path: Path) -> dict[str, Any]:
    if not path.exists():
        raise SystemExit(f"metadata file not found: {path}")
    with path.open("r", encoding="utf-8") as fh:
        data = json.load(fh)
    elements = data.get("elements") or []
    if not elements:
        raise SystemExit(f"metadata file does not contain elements: {path}")
    return elements[0]


def http_post_json(url: str, payload: dict[str, Any], headers: Optional[dict[str, str]] = None,
                   timeout: int = 30) -> tuple[int, bytes]:
    data = json.dumps(payload).encode("utf-8")
    req_headers = {"Content-Type": "application/json"}
    if headers:
        req_headers.update(headers)
    req = urllib.request.Request(url, data=data, headers=req_headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read()
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read()


def http_post_multipart(url: str,
                        fields: dict[str, str],
                        files: dict[str, tuple[str, bytes, str]],
                        headers: Optional[dict[str, str]] = None,
                        timeout: int = 120) -> tuple[int, bytes]:
    boundary = "----AutomationBoundary" + uuid.uuid4().hex
    body = bytearray()

    def add_line(line: str) -> None:
        body.extend(line.encode("utf-8"))

    for name, value in fields.items():
        add_line(f"--{boundary}\r\n")
        add_line(f'Content-Disposition: form-data; name="{name}"\r\n\r\n')
        add_line(f"{value}\r\n")

    for name, (filename, content, content_type) in files.items():
        add_line(f"--{boundary}\r\n")
        add_line(
            f'Content-Disposition: form-data; name="{name}"; filename="{filename}"\r\n')
        add_line(f"Content-Type: {content_type}\r\n\r\n")
        body.extend(content)
        body.extend(b"\r\n")

    add_line(f"--{boundary}--\r\n")

    req_headers = {
        "Content-Type": f"multipart/form-data; boundary={boundary}",
    }
    if headers:
        req_headers.update(headers)

    req = urllib.request.Request(url, data=bytes(body), headers=req_headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read()
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read()


def login(server: str, username: str, password: str) -> dict[str, Any]:
    url = server.rstrip("/") + "/api/auth/admin/login"
    print(f"[api] login {url}")
    status, body = http_post_json(url, {"username": username, "password": password}, timeout=15)
    if status != 200:
        raise SystemExit(f"login failed: {status} {body.decode(errors='ignore')}")
    return json.loads(body.decode("utf-8"))


def upload_bundle(
    server: str,
    token: str,
    version_name: Optional[str],
    version_code: Optional[int],
    app_apk: Path,
    test_apk: Path,
) -> dict[str, Any]:
    url = server.rstrip("/") + "/api/apk/test/upload"
    data: dict[str, str] = {}
    if version_name:
        data["version"] = version_name
    if version_code is not None:
        data["version_code"] = str(version_code)
    data["app_package_name"] = "com.automation"
    data["test_package_name"] = "com.automation.test"

    print(f"[api] uploading bundle to {url}")
    with app_apk.open("rb") as app_fp, test_apk.open("rb") as test_fp:
        files = {
            "app_file": (app_apk.name, app_fp.read(), "application/vnd.android.package-archive"),
            "test_file": (test_apk.name, test_fp.read(), "application/vnd.android.package-archive"),
        }
    status, body = http_post_multipart(
        url,
        data,
        files,
        headers={"Authorization": f"Bearer {token}"},
        timeout=120,
    )
    if status != 200:
        raise SystemExit(f"upload failed: {status} {body.decode(errors='ignore')}")
    return json.loads(body.decode("utf-8"))


def main() -> None:
    parser = argparse.ArgumentParser(description="Build automation app and upload to server")
    parser.add_argument("--server", default="http://127.0.0.1:8000", help="Automation server base URL")
    parser.add_argument("--username", default="admin", help="Login username")
    parser.add_argument("--password", default="admin123", help="Login password")
    parser.add_argument(
        "--skip-build",
        action="store_true",
        help="Skip gradle build and reuse existing APK outputs",
    )
    args = parser.parse_args()

    if not args.skip_build:
        run_gradle_tasks(["automation-app:assembleDebug"])
        run_gradle_tasks(["automation-app:assembleDebugAndroidTest"])

    if not APP_APK.exists() or not TEST_APK.exists():
        raise SystemExit("APK outputs not found, ensure build succeeded.")

    app_meta = read_metadata(APP_METADATA)
    test_meta = read_metadata(TEST_METADATA)

    version_name = app_meta.get("versionName") or test_meta.get("versionName")
    version_code_raw = app_meta.get("versionCode") or test_meta.get("versionCode")
    try:
        version_code = int(version_code_raw) if version_code_raw is not None else None
    except ValueError:
        version_code = None

    login_resp = login(args.server, args.username, args.password)
    token = login_resp.get("access_token")
    if not token:
        raise SystemExit("login response missing access_token")

    upload_resp = upload_bundle(args.server, token, version_name, version_code, APP_APK, TEST_APK)

    print("[done] upload succeeded")
    print(json.dumps(upload_resp, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit("aborted by user")
