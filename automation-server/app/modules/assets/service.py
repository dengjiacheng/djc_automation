"""Template asset service handling storage and retrieval."""

from __future__ import annotations

import base64
import hashlib
import os
from dataclasses import dataclass
from pathlib import Path
from typing import BinaryIO

from fastapi import UploadFile

from app.core.config import get_settings
from app.infrastructure.database.repositories.template_asset_repository import SqlTemplateAssetRepository
from .models import TemplateAsset


@dataclass(slots=True)
class TemplateAssetService:
    repository: SqlTemplateAssetRepository
    storage_root: Path

    @classmethod
    def with_session(cls, session) -> "TemplateAssetService":
        settings = get_settings()
        storage_root = Path(settings.template_asset_storage_dir).resolve()
        return cls(SqlTemplateAssetRepository(session), storage_root)

    async def store_upload(self, owner_id: str, upload: UploadFile) -> TemplateAsset:
        file_name = _sanitize_filename(upload.filename) or "asset"
        storage_dir = self.storage_root / owner_id
        storage_dir.mkdir(parents=True, exist_ok=True)

        # Preserve extension if available
        suffix = Path(file_name).suffix
        target_path = storage_dir / f"{os.urandom(16).hex()}{suffix}"

        hasher = hashlib.sha256()
        total_size = 0

        try:
            with target_path.open("wb") as buffer:
                while True:
                    chunk = await upload.read(1024 * 1024)
                    if not chunk:
                        break
                    buffer.write(chunk)
                    hasher.update(chunk)
                    total_size += len(chunk)
        finally:
            await upload.close()

        if total_size == 0:
            target_path.unlink(missing_ok=True)
            raise ValueError("上传的文件为空")

        return await self.repository.create(
            owner_id=owner_id,
            file_name=file_name,
            content_type=upload.content_type,
            size_bytes=total_size,
            checksum_sha256=hasher.hexdigest(),
            storage_path=str(target_path),
        )

    async def get_asset(self, asset_id: str) -> TemplateAsset | None:
        return await self.repository.get_by_id(asset_id)

    async def ensure_owner(self, asset: TemplateAsset, owner_id: str) -> None:
        if asset.owner_id != owner_id:
            raise PermissionError("无权访问该资源")

    def open_binary(self, asset: TemplateAsset) -> BinaryIO:
        path = Path(asset.storage_path)
        return path.open("rb")

    def read_base64(self, asset: TemplateAsset) -> str:
        with self.open_binary(asset) as stream:
            data = stream.read()
        return base64.b64encode(data).decode("utf-8")

    async def delete_asset(self, asset_id: str) -> None:
        asset = await self.repository.get_by_id(asset_id)
        if asset is None:
            return
        path = Path(asset.storage_path)
        path.unlink(missing_ok=True)
        await self.repository.delete(asset_id)


def _sanitize_filename(filename: str | None) -> str | None:
    if not filename:
        return None
    name = os.path.basename(filename)
    # strip dangerous characters
    return name.replace("\0", "").strip()
