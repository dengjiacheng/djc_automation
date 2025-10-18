"""Routes for serving and managing automation app + test APK bundles."""
from __future__ import annotations

import hashlib
from pathlib import Path
from typing import Optional, Union

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import FileResponse

from androguard.core.bytecodes.apk import APK

from app.core.config import get_settings
from app.core.security import get_current_account, get_current_admin
from app.schemas import TestApkAssetInfo, TestApkInfo
from app.services import AutomationBundle, ApkAsset, TestApkError, TestApkRepository

router = APIRouter(prefix="/apk/test", tags=["测试APK"])
settings = get_settings()
repository = TestApkRepository(Path(settings.test_apk_storage_dir).resolve())
repository.ensure_storage()

VALID_VARIANTS = {"app", "test"}
ALLOWED_APP_PACKAGE = "com.automation"
ALLOWED_TEST_PACKAGE = "com.automation.test"


def _bundle_to_response(request: Request, bundle: AutomationBundle) -> TestApkInfo:
    try:
        app_file = repository.resolve_file(bundle, "app")
        test_file = repository.resolve_file(bundle, "test")
    except TestApkError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc

    download_base = request.url_for("download_test_apk")
    app_download = download_base.include_query_params(version_code=str(bundle.version_code), variant="app")
    test_download = download_base.include_query_params(version_code=str(bundle.version_code), variant="test")
    return TestApkInfo(
        version=bundle.version,
        version_code=bundle.version_code,
        created_at=bundle.created_at,
        app=TestApkAssetInfo(
            file_name=bundle.app.file_name,
            download_url=str(app_download),
            size_bytes=app_file.stat().st_size,
            checksum_sha256=bundle.app.checksum_sha256,
            package_name=bundle.app.package_name,
            version_code=bundle.app.version_code,
            version_name=bundle.app.version_name,
        ),
        test=TestApkAssetInfo(
            file_name=bundle.test.file_name,
            download_url=str(test_download),
            size_bytes=test_file.stat().st_size,
            checksum_sha256=bundle.test.checksum_sha256,
            package_name=bundle.test.package_name,
            version_code=bundle.test.version_code,
            version_name=bundle.test.version_name,
        ),
    )


def _resolve_bundle(version_code: Optional[int]) -> AutomationBundle:
    if version_code is None:
        return repository.get_latest()
    return repository.get_by_version_code(version_code)


def _sanitize_file_name(raw_name: Optional[str], version_code: Optional[int], variant: str) -> str:
    base = Path(raw_name or "").name or f"{variant}.apk"
    stem = "".join(ch.lower() if ch.isalnum() or ch in {"-", "_"} else "-" for ch in Path(base).stem)
    if not stem:
        stem = variant
    return f"{stem}.apk"


def _normalize_optional(value: Optional[str]) -> Optional[str]:
    if value is None:
        return None
    stripped = value.strip()
    return stripped or None


def _extract_apk_meta(path: Path, variant: str) -> tuple[str, Optional[int], Optional[str]]:
    try:
        apk = APK(str(path))
    except Exception as exc:  # pragma: no cover - library specific
        raise HTTPException(status_code=400, detail=f"无法解析 {variant} APK: {exc}") from exc

    package_name = apk.get_package() or None
    version_code_raw = apk.get_androidversion_code()
    version_name = apk.get_androidversion_name()

    parsed_version_code: Optional[int] = None
    if version_code_raw:
        try:
            parsed_version_code = int(version_code_raw)
        except (TypeError, ValueError):
            parsed_version_code = None

    if version_name:
        version_name = str(version_name)

    return package_name, parsed_version_code, version_name


async def _store_upload(
    upload: UploadFile, version_code: Optional[int], variant: str
) -> tuple[str, str, int, dict[str, Optional[Union[str, int]]]]:
    if not upload.filename:
        raise HTTPException(status_code=400, detail=f"{variant} 文件名不能为空")
    if not upload.filename.lower().endswith(".apk"):
        raise HTTPException(status_code=400, detail=f"{variant} 仅支持上传 .apk 文件")

    safe_name = _sanitize_file_name(upload.filename, version_code, variant)
    target_path = repository.storage_dir / safe_name
    temp_path = target_path.with_suffix(target_path.suffix + ".upload")

    hasher = hashlib.sha256()
    total_size = 0
    try:
        with temp_path.open("wb") as buffer:
            while True:
                chunk = await upload.read(1024 * 1024)
                if not chunk:
                    break
                buffer.write(chunk)
                hasher.update(chunk)
                total_size += len(chunk)
    except OSError as exc:
        temp_path.unlink(missing_ok=True)
        raise HTTPException(status_code=500, detail=f"保存 {variant} 文件失败") from exc
    finally:
        await upload.close()

    if total_size == 0:
        temp_path.unlink(missing_ok=True)
        raise HTTPException(status_code=400, detail=f"{variant} 上传的文件为空")

    try:
        temp_path.replace(target_path)
    except OSError as exc:
        temp_path.unlink(missing_ok=True)
        raise HTTPException(status_code=500, detail=f"写入 {variant} 文件失败") from exc

    package_name, parsed_version_code, version_name = _extract_apk_meta(target_path, variant)

    if isinstance(parsed_version_code, int):
        desired_name = _sanitize_file_name(upload.filename, parsed_version_code, variant)
        desired_path = repository.storage_dir / desired_name
        if desired_path != target_path:
            if desired_path.exists():
                desired_path.unlink()
            target_path.rename(desired_path)
            target_path = desired_path
            safe_name = desired_name

    meta = {
        "package_name": package_name,
        "version_code": parsed_version_code,
        "version_name": version_name,
    }

    return safe_name, hasher.hexdigest(), total_size, meta


@router.get("/latest", response_model=TestApkInfo, summary="获取最新测试 APK 信息")
async def get_latest_bundle(request: Request, _=Depends(get_current_account)) -> TestApkInfo:
    try:
        bundle = repository.get_latest()
    except TestApkError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    return _bundle_to_response(request, bundle)


@router.post("/upload", response_model=TestApkInfo, summary="上传或更新测试 APK 套件")
async def upload_bundle(
    request: Request,
    version: Optional[str] = Form(None),
    version_code: Optional[int] = Form(None),
    app_file: UploadFile = File(...),
    test_file: UploadFile = File(...),
    app_package_name: Optional[str] = Form(None),
    test_package_name: Optional[str] = Form(None),
    created_at: Optional[str] = Form(None),
    _=Depends(get_current_admin),
) -> TestApkInfo:
    repository.ensure_storage()

    version = _normalize_optional(version)
    version_code = version_code if version_code and version_code > 0 else None

    app_name, app_checksum, _, app_meta = await _store_upload(app_file, version_code, "app")
    test_name, test_checksum, _, test_meta = await _store_upload(test_file, version_code, "test")

    app_version_code = app_meta.get("version_code") if isinstance(app_meta.get("version_code"), int) else None
    bundle_version_code = (
        app_version_code or (version_code if version_code and version_code > 0 else None) or 0
    )

    app_package_meta = _normalize_optional(app_meta.get("package_name"))
    test_package_meta = _normalize_optional(test_meta.get("package_name"))

    if app_package_meta != ALLOWED_APP_PACKAGE:
        raise HTTPException(status_code=400, detail=f"主应用 APK 的包名必须为 {ALLOWED_APP_PACKAGE}")
    if test_package_meta != ALLOWED_TEST_PACKAGE:
        raise HTTPException(status_code=400, detail=f"测试 APK 的包名必须为 {ALLOWED_TEST_PACKAGE}")

    app_package_name = ALLOWED_APP_PACKAGE
    test_package_name = ALLOWED_TEST_PACKAGE

    app_version_name = _normalize_optional(app_meta.get("version_name"))
    test_version_name = _normalize_optional(test_meta.get("version_name"))

    created_at = _normalize_optional(created_at) or AutomationBundle.now_isoformat()

    bundle_version = version or app_version_name or str(bundle_version_code)

    bundle = AutomationBundle(
        version=bundle_version,
        version_code=bundle_version_code,
        app=ApkAsset(
            file_name=app_name,
            checksum_sha256=app_checksum,
            package_name=app_package_name,
            version_code=app_version_code,
            version_name=app_version_name,
        ),
        test=ApkAsset(
            file_name=test_name,
            checksum_sha256=test_checksum,
            package_name=test_package_name,
            version_code=test_meta.get("version_code") if isinstance(test_meta.get("version_code"), int) else None,
            version_name=test_version_name,
        ),
        created_at=created_at,
    )

    try:
        repository.save_bundle(bundle)
    except TestApkError as exc:
        for name in {app_name, test_name}:
            path = repository.storage_dir / name
            path.unlink(missing_ok=True)
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    return _bundle_to_response(request, bundle)


@router.get(
    "/download",
    name="download_test_apk",
    summary="下载测试 APK",
    response_class=FileResponse,
)
async def download_bundle_asset(
    variant: str = "test",
    version_code: Optional[int] = None,
    _=Depends(get_current_account),
):
    if variant not in VALID_VARIANTS:
        raise HTTPException(status_code=400, detail="variant 参数必须为 app 或 test")

    try:
        bundle = _resolve_bundle(version_code)
        file_path = repository.resolve_file(bundle, variant)
    except TestApkError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc

    asset = bundle.app if variant == "app" else bundle.test
    return FileResponse(
        path=file_path,
        media_type="application/vnd.android.package-archive",
        filename=asset.file_name,
    )
