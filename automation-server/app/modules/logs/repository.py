"""Repository protocol for persisting device logs."""

from __future__ import annotations

from typing import Protocol

from app.db.models import DeviceLog as DeviceLogModel


class LogRepository(Protocol):
    async def add_log(
        self,
        *,
        device_id: str,
        log_type: str,
        message: str,
        data: dict | None,
    ) -> DeviceLogModel:
        ...
