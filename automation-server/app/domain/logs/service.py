"""Domain service for device log operations."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Optional

from sqlalchemy.ext.asyncio import AsyncSession

from app.infrastructure.database.repositories.log_repository import SqlLogRepository
from app.db.models import DeviceLog as DeviceLogModel

from .models import DeviceLog
from .repository import LogRepository


@dataclass(slots=True)
class LogService:
    repository: LogRepository

    @classmethod
    def with_session(cls, session: AsyncSession) -> "LogService":
        return cls(SqlLogRepository(session))

    async def create_log(
        self,
        *,
        device_id: str,
        log_type: str,
        message: str,
        data: Optional[dict] = None,
    ) -> DeviceLog:
        model = await self.repository.add_log(
            device_id=device_id,
            log_type=log_type,
            message=message,
            data=data,
        )
        return self._to_domain(model)

    @staticmethod
    def _to_domain(model: DeviceLogModel) -> DeviceLog:
        return DeviceLog.from_orm(model)
