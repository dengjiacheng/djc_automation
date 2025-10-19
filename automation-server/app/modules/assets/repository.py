"""Repository abstraction for template assets."""

from __future__ import annotations

from sqlalchemy import select, delete
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import TemplateAsset as TemplateAssetModel
from .models import TemplateAsset


class TemplateAssetRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def create(
        self,
        *,
        owner_id: str,
        file_name: str,
        content_type: str | None,
        size_bytes: int,
        checksum_sha256: str,
        storage_path: str,
    ) -> TemplateAsset:
        model = TemplateAssetModel(
            owner_id=owner_id,
            file_name=file_name,
            content_type=content_type,
            size_bytes=size_bytes,
            checksum_sha256=checksum_sha256,
            storage_path=storage_path,
        )
        self._session.add(model)
        await self._session.flush()
        await self._session.refresh(model)
        return self._to_domain(model)

    async def get_by_id(self, asset_id: str) -> TemplateAsset | None:
        stmt = select(TemplateAssetModel).where(TemplateAssetModel.id == asset_id)
        result = await self._session.execute(stmt)
        model = result.scalar_one_or_none()
        return self._to_domain(model) if model else None

    async def delete(self, asset_id: str) -> None:
        stmt = delete(TemplateAssetModel).where(TemplateAssetModel.id == asset_id)
        await self._session.execute(stmt)

    @staticmethod
    def _to_domain(model: TemplateAssetModel) -> TemplateAsset:
        return TemplateAsset(
            id=str(model.id),
            owner_id=model.owner_id,
            file_name=model.file_name,
            content_type=model.content_type,
            size_bytes=model.size_bytes,
            checksum_sha256=model.checksum_sha256,
            storage_path=model.storage_path,
            created_at=model.created_at,
        )
