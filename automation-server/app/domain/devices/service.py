"""Domain service orchestrating device related workflows."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Optional

from sqlalchemy.ext.asyncio import AsyncSession

from app.infrastructure.database.repositories.device_repository import SqlDeviceRepository
from app.db.models import Device as DeviceModel, generate_uuid

from .exceptions import DeviceAlreadyExistsError, DeviceOwnershipError
from .models import Device, DeviceSummary
from .repository import DeviceRepository


@dataclass(slots=True)
class DeviceService:
    repository: DeviceRepository

    @classmethod
    def with_session(cls, session: AsyncSession) -> "DeviceService":
        return cls(SqlDeviceRepository(session))

    async def create_device(
        self,
        *,
        device_id: Optional[str] = None,
        username: str,
        device_name: Optional[str],
        device_model: Optional[str],
        android_version: Optional[str],
        local_ip: Optional[str],
        public_ip: Optional[str],
    ) -> Device:
        device_id = device_id or generate_uuid()
        model = await self.repository.create_device(
            device_id=device_id,
            username=username,
            device_name=device_name,
            device_model=device_model,
            android_version=android_version,
            local_ip=local_ip,
            public_ip=public_ip,
        )
        return self._to_domain(model)

    async def get_device(self, device_id: str) -> Device | None:
        model = await self.repository.get_by_id(device_id)
        return self._to_domain(model) if model else None

    async def list_devices(self, skip: int, limit: int, online_only: bool) -> DeviceSummary:
        models, total = await self.repository.list_devices(skip, limit, online_only)
        devices = [self._to_domain(model) for model in models]
        return DeviceSummary(total=total, devices=devices)

    async def list_devices_by_username(self, username: str) -> DeviceSummary:
        models = await self.repository.list_by_username(username)
        devices = [self._to_domain(model) for model in models]
        return DeviceSummary(total=len(devices), devices=devices)

    async def list_for_admin(
        self,
        *,
        skip: int,
        limit: int,
        online_only: bool,
        username: Optional[str],
    ) -> DeviceSummary:
        if username:
            summary = await self.list_devices_by_username(username)
            devices = summary.devices
            if online_only:
                devices = [device for device in devices if device.is_online]
            total = len(devices)
            sliced = devices[skip : skip + limit] if limit else devices
            return DeviceSummary(total=total, devices=sliced)
        return await self.list_devices(skip, limit, online_only)

    async def ensure_device_for_connection(
        self,
        *,
        device_id: str,
        username: str,
        device_name: Optional[str],
        device_model: Optional[str],
        android_version: Optional[str],
        local_ip: Optional[str],
        public_ip: Optional[str],
    ) -> Device:
        model = await self.repository.get_by_id(device_id)
        if model is None:
            await self.repository.create_device(
                device_id=device_id,
                username=username,
                device_name=device_name,
                device_model=device_model,
                android_version=android_version,
                local_ip=local_ip,
                public_ip=public_ip,
            )

            model = await self.repository.mark_online(
                device_id,
                device_name=device_name,
                device_model=device_model,
                android_version=android_version,
                local_ip=local_ip,
                public_ip=public_ip,
            )
            return self._to_domain(model)

        if model.username != username:
            raise DeviceOwnershipError("device 与账号不匹配")

        updated = await self.repository.mark_online(
            device_id,
            device_name=device_name,
            device_model=device_model,
            android_version=android_version,
            local_ip=local_ip,
            public_ip=public_ip,
        )
        return self._to_domain(updated)

    async def mark_offline(self, device_id: str) -> None:
        await self.repository.mark_offline(device_id)

    async def _username_exists(self, username: str) -> bool:
        models = await self.repository.list_by_username(username)
        return len(models) > 0

    @staticmethod
    def _to_domain(model: DeviceModel) -> Device:
        return Device.from_orm(model)
