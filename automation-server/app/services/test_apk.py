"""Utilities for managing automation app + test APK bundles."""

from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional


class TestApkError(RuntimeError):
    """Raised when the automation bundle manifest or artifact is invalid."""


@dataclass(frozen=True)
class ApkAsset:
    file_name: str
    checksum_sha256: Optional[str] = None
    package_name: Optional[str] = None
    version_code: Optional[int] = None
    version_name: Optional[str] = None

    @classmethod
    def from_mapping(cls, payload: Dict[str, Any]) -> "ApkAsset":
        try:
            file_name = str(payload["file"])
        except (KeyError, TypeError, ValueError) as exc:
            raise TestApkError("manifest 条目缺少 APK file 字段") from exc

        checksum = payload.get("checksum_sha256")
        package_name = payload.get("package_name")
        version_code = payload.get("version_code")
        version_name = payload.get("version_name")
        parsed_version_code: Optional[int] = None
        if isinstance(version_code, int):
            parsed_version_code = version_code
        elif isinstance(version_code, str) and version_code.isdigit():
            parsed_version_code = int(version_code)
        return cls(
            file_name=file_name,
            checksum_sha256=str(checksum) if checksum else None,
            package_name=str(package_name) if package_name else None,
            version_code=parsed_version_code,
            version_name=str(version_name) if version_name else None,
        )

    def to_mapping(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {"file": self.file_name}
        if self.checksum_sha256:
            payload["checksum_sha256"] = self.checksum_sha256
        if self.package_name:
            payload["package_name"] = self.package_name
        if self.version_code is not None:
            payload["version_code"] = self.version_code
        if self.version_name:
            payload["version_name"] = self.version_name
        return payload


@dataclass(frozen=True)
class AutomationBundle:
    version: str
    version_code: int
    app: ApkAsset
    test: ApkAsset
    created_at: Optional[str] = None

    @classmethod
    def from_mapping(cls, payload: Dict[str, Any]) -> "AutomationBundle":
        try:
            version = str(payload["version"])
            version_code = int(payload["version_code"])
            app_payload = payload["app"]
            test_payload = payload["test"]
        except (KeyError, TypeError, ValueError) as exc:
            raise TestApkError("manifest 条目缺少 version/version_code/app/test 字段") from exc

        if not isinstance(app_payload, dict) or not isinstance(test_payload, dict):
            raise TestApkError("manifest 中 app/test 字段必须为对象")

        created_at = payload.get("created_at")
        return cls(
            version=version,
            version_code=version_code,
            app=ApkAsset.from_mapping(app_payload),
            test=ApkAsset.from_mapping(test_payload),
            created_at=str(created_at) if created_at else None,
        )

    def to_mapping(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {
            "version": self.version,
            "version_code": self.version_code,
            "app": self.app.to_mapping(),
            "test": self.test.to_mapping(),
        }
        if self.created_at:
            payload["created_at"] = self.created_at
        return payload

    @staticmethod
    def now_isoformat() -> str:
        return datetime.now(timezone.utc).isoformat()


class TestApkRepository:
    """Loads and persists automation bundles."""

    def __init__(self, storage_dir: Path):
        self._storage_dir = storage_dir
        storage_dir.mkdir(parents=True, exist_ok=True)
        self._manifest_path = storage_dir / "manifest.json"

    @property
    def storage_dir(self) -> Path:
        return self._storage_dir

    def ensure_storage(self) -> None:
        """Create the storage directory if it does not exist."""
        self._storage_dir.mkdir(parents=True, exist_ok=True)

    def _read_manifest(self) -> Dict[str, Any]:
        if not self._manifest_path.exists():
            return {"artifacts": []}
        try:
            payload = json.loads(self._manifest_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            raise TestApkError("无法读取测试 APK manifest") from exc
        if not isinstance(payload, dict):
            raise TestApkError("测试 APK manifest 结构无效")
        if "artifacts" not in payload:
            payload["artifacts"] = []
        return payload

    def _write_manifest(self, bundles: Iterable[AutomationBundle]) -> None:
        manifest = {"artifacts": [bundle.to_mapping() for bundle in sorted(bundles, key=lambda item: item.version_code)]}
        try:
            self._manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8")
        except OSError as exc:
            raise TestApkError("写入测试 APK manifest 失败") from exc

    def iter_bundles(self) -> Iterable[AutomationBundle]:
        payload = self._read_manifest()
        entries = payload.get("artifacts") or []
        for entry in entries:
            if not isinstance(entry, dict):
                raise TestApkError("测试 APK manifest 条目格式无效")
            yield AutomationBundle.from_mapping(entry)

    def get_latest(self) -> AutomationBundle:
        bundles: List[AutomationBundle] = list(self.iter_bundles())
        if not bundles:
            raise TestApkError("没有可用的测试 APK 条目")
        return max(bundles, key=lambda item: item.version_code)

    def get_by_version_code(self, version_code: int) -> AutomationBundle:
        for bundle in self.iter_bundles():
            if bundle.version_code == version_code:
                return bundle
        raise TestApkError(f"未找到指定 version_code 的测试 APK: {version_code}")

    def save_bundle(self, bundle: AutomationBundle) -> None:
        payload = self._read_manifest()
        existing = payload.get("artifacts") or []

        latest_file_names = {bundle.app.file_name, bundle.test.file_name}

        for entry in existing:
            if not isinstance(entry, dict):
                continue
            try:
                candidate = AutomationBundle.from_mapping(entry)
            except TestApkError:
                continue

            for asset in (candidate.app, candidate.test):
                if asset.file_name not in latest_file_names:
                    path = self._storage_dir / asset.file_name
                    path.unlink(missing_ok=True)

        self._write_manifest([bundle])

    def resolve_file(self, bundle: AutomationBundle, variant: str) -> Path:
        if variant == "app":
            asset = bundle.app
        elif variant == "test":
            asset = bundle.test
        else:
            raise TestApkError(f"无效的 APK 类型: {variant}")
        file_path = self._storage_dir / asset.file_name
        if not file_path.exists() or not file_path.is_file():
            raise TestApkError(f"{variant} APK 文件不存在: {file_path}")
        return file_path
