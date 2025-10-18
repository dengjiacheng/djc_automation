"""Device domain models."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Optional

from app.db import models as orm


@dataclass(slots=True)
class Device:
    id: str
    username: str
    device_name: Optional[str]
    device_model: Optional[str]
    android_version: Optional[str]
    local_ip: Optional[str]
    public_ip: Optional[str]
    is_online: bool
    created_at: datetime
    updated_at: Optional[datetime]
    last_online_at: Optional[datetime]

    @classmethod
    def from_orm(cls, instance: orm.Device) -> "Device":
        return cls(
            id=str(instance.id),
            username=instance.username,
            device_name=instance.device_name,
            device_model=instance.device_model,
            android_version=instance.android_version,
            local_ip=instance.local_ip,
            public_ip=instance.public_ip,
            is_online=bool(instance.is_online),
            created_at=instance.created_at,
            updated_at=instance.updated_at,
            last_online_at=instance.last_online_at,
        )


@dataclass(slots=True)
class DeviceSummary:
    """Simplified view used for listings with pagination."""

    total: int
    devices: list[Device]
