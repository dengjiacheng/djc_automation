"""SQLAlchemy powered repository for device persistence."""

from __future__ import annotations

from datetime import datetime
from typing import Optional, Sequence

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import Device as DeviceModel


class SqlDeviceRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def get_by_id(self, device_id: str) -> DeviceModel | None:
        stmt = select(DeviceModel).where(DeviceModel.id == device_id)
        result = await self._session.execute(stmt)
        return result.scalar_one_or_none()

    async def list_by_username(self, username: str) -> list[DeviceModel]:
        stmt = (
            select(DeviceModel)
            .where(DeviceModel.username == username)
            .order_by(DeviceModel.created_at.desc())
        )
        result = await self._session.execute(stmt)
        return list(result.scalars().all())

    async def list_devices(
        self, skip: int, limit: int, online_only: bool
    ) -> tuple[Sequence[DeviceModel], int]:
        query = select(DeviceModel).order_by(DeviceModel.created_at.desc())
        count_query = select(func.count(DeviceModel.id))
        if online_only:
            predicate = DeviceModel.is_online.is_(True)
            query = query.where(predicate)
            count_query = count_query.where(predicate)

        if skip:
            query = query.offset(skip)
        if limit:
            query = query.limit(limit)

        result = await self._session.execute(query)
        devices = result.scalars().all()
        total = (await self._session.execute(count_query)).scalar() or 0
        return devices, int(total)

    async def create_device(
        self,
        *,
        device_id: str,
        username: str,
        device_name: Optional[str],
        device_model: Optional[str],
        android_version: Optional[str],
        local_ip: Optional[str],
        public_ip: Optional[str],
    ) -> DeviceModel:
        model = DeviceModel(
            id=device_id,
            username=username,
            device_name=device_name,
            device_model=device_model,
            android_version=android_version,
            local_ip=local_ip,
            public_ip=public_ip,
        )
        self._session.add(model)
        await self._session.flush()
        await self._session.refresh(model)
        return model

    async def mark_online(
        self,
        device_id: str,
        *,
        device_name: Optional[str],
        device_model: Optional[str],
        android_version: Optional[str],
        local_ip: Optional[str],
        public_ip: Optional[str],
    ) -> DeviceModel:
        model = await self._fetch_model(device_id)
        if model is None:
            raise ValueError(f"Device not found: {device_id}")

        model.device_name = device_name or model.device_name
        model.device_model = device_model or model.device_model
        model.android_version = android_version or model.android_version
        model.local_ip = local_ip or model.local_ip
        model.public_ip = public_ip or model.public_ip
        model.is_online = True
        model.last_online_at = datetime.utcnow()
        await self._session.flush()
        await self._session.refresh(model)
        return model

    async def mark_offline(self, device_id: str) -> None:
        model = await self._fetch_model(device_id)
        if model is None:
            return
        await self._session.delete(model)
        await self._session.flush()

    async def _fetch_model(self, device_id: str) -> DeviceModel | None:
        stmt = select(DeviceModel).where(DeviceModel.id == device_id)
        result = await self._session.execute(stmt)
        return result.scalar_one_or_none()
