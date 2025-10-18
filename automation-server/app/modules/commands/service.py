"""Domain service coordinating command lifecycle."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Optional

from sqlalchemy.ext.asyncio import AsyncSession

from app.infrastructure.database.repositories.command_repository import SqlCommandRepository

from app.db.models import Command as CommandModel
from .models import Command
from .repository import CommandRepository


@dataclass(slots=True)
class CommandService:
    repository: CommandRepository

    @classmethod
    def with_session(cls, session: AsyncSession) -> "CommandService":
        return cls(SqlCommandRepository(session))

    async def create_command(
        self,
        device_id: str,
        *,
        action: str,
        params: Optional[dict],
        user_id: Optional[str],
    ) -> Command:
        model = await self.repository.add(
            device_id=device_id,
            action=action,
            params=params,
            user_id=user_id,
        )
        return self._to_domain(model)

    async def get_pending(self, device_id: str) -> list[Command]:
        models = await self.repository.list_pending(device_id)
        return [self._to_domain(model) for model in models]

    async def update_result(
        self,
        command_id: str,
        *,
        status: str,
        result: Optional[str],
        error_message: Optional[str],
    ) -> None:
        await self.repository.update_result(
            command_id,
            status=status,
            result=result,
            error_message=error_message,
        )

    async def get_by_id(self, command_id: str) -> Command | None:
        model = await self.repository.get_by_id(command_id)
        return self._to_domain(model) if model else None

    @staticmethod
    def _to_domain(model: CommandModel) -> Command:
        return Command.from_orm(model)

    async def mark_sent(self, command_id: str, sent_at: datetime) -> Command | None:
        model = await self.repository.mark_sent(command_id, sent_at=sent_at)
        return self._to_domain(model) if model else None
