"""Repository protocol for device persistence operations."""

from __future__ import annotations

from typing import Protocol, Sequence

from app.db.models import Device as DeviceModel


class DeviceRepository(Protocol):
    async def get_by_id(self, device_id: str) -> DeviceModel | None:
        ...

    async def list_by_username(self, username: str) -> list[DeviceModel]:
        ...

    async def list_devices(
        self, skip: int, limit: int, online_only: bool
    ) -> tuple[Sequence[DeviceModel], int]:
        ...

    async def create_device(
        self,
        *,
        device_id: str,
        username: str,
        device_name: str | None,
        device_model: str | None,
        android_version: str | None,
        local_ip: str | None,
        public_ip: str | None,
    ) -> DeviceModel:
        ...

    async def mark_online(
        self,
        device_id: str,
        *,
        device_name: str | None,
        device_model: str | None,
        android_version: str | None,
        local_ip: str | None,
        public_ip: str | None,
    ) -> DeviceModel:
        ...

    async def mark_offline(self, device_id: str) -> None:
        ...
