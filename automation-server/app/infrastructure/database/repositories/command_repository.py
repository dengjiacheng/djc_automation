"""SQLAlchemy repository implementation for commands."""

from __future__ import annotations

import json
from datetime import datetime
from typing import Optional

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import Command as CommandModel


class SqlCommandRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def add(
        self,
        *,
        device_id: str,
        action: str,
        params: dict | None,
        user_id: str | None,
    ) -> CommandModel:
        model = CommandModel(
            device_id=device_id,
            user_id=user_id,
            action=action,
            params=json.dumps(params) if params else None,
            status="pending",
        )
        self._session.add(model)
        await self._session.flush()
        await self._session.refresh(model)
        return model

    async def list_pending(self, device_id: str) -> list[CommandModel]:
        stmt = (
            select(CommandModel)
            .where(CommandModel.device_id == device_id, CommandModel.status == "pending")
            .order_by(CommandModel.created_at)
        )
        result = await self._session.execute(stmt)
        models = result.scalars().all()
        now = datetime.utcnow()
        for model in models:
            model.status = "sent"
            model.sent_at = now
        await self._session.flush()
        return list(models)

    async def get_by_id(self, command_id: str) -> CommandModel | None:
        stmt = select(CommandModel).where(CommandModel.id == command_id)
        result = await self._session.execute(stmt)
        return result.scalar_one_or_none()

    async def update_result(
        self,
        command_id: str,
        *,
        status: str,
        result: Optional[str],
        error_message: Optional[str],
    ) -> None:
        stmt = select(CommandModel).where(CommandModel.id == command_id)
        result_query = await self._session.execute(stmt)
        model = result_query.scalar_one_or_none()
        if model is None:
            return
        model.status = status
        model.result = result
        model.error_message = error_message
        model.completed_at = datetime.utcnow()
        await self._session.flush()

    async def mark_sent(
        self,
        command_id: str,
        *,
        sent_at: datetime,
    ) -> Optional[CommandModel]:
        stmt = select(CommandModel).where(CommandModel.id == command_id)
        result_query = await self._session.execute(stmt)
        model = result_query.scalar_one_or_none()
        if model is None:
            return None
        model.status = "sent"
        model.sent_at = sent_at
        await self._session.flush()
        return model
