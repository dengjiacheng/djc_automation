"""Device log domain model."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Any, Optional

from app.db import models as orm


@dataclass(slots=True)
class DeviceLog:
    id: int
    device_id: str
    log_type: str
    message: str
    data: Optional[dict[str, Any]]
    created_at: datetime

    @classmethod
    def from_orm(cls, instance: orm.DeviceLog) -> "DeviceLog":
        payload = None
        if instance.data:
            import json

            try:
                payload = json.loads(instance.data)
            except json.JSONDecodeError:
                payload = None
        return cls(
            id=int(instance.id),
            device_id=instance.device_id,
            log_type=instance.log_type,
            message=instance.message or "",
            data=payload,
            created_at=instance.created_at,
        )
