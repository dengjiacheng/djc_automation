"""SQLAlchemy repository for device logs."""

from __future__ import annotations

import json

from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import DeviceLog as DeviceLogModel


class SqlLogRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def add_log(
        self,
        *,
        device_id: str,
        log_type: str,
        message: str,
        data: dict | None,
    ) -> DeviceLogModel:
        model = DeviceLogModel(
            device_id=device_id,
            log_type=log_type,
            message=message,
            data=json.dumps(data, ensure_ascii=False) if data else None,
        )
        self._session.add(model)
        await self._session.flush()
        await self._session.refresh(model)
        return model
