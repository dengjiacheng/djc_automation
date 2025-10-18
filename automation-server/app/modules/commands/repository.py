"""Repository protocol for command persistence."""

from __future__ import annotations

from typing import Protocol
from datetime import datetime

from app.db.models import Command as CommandModel


class CommandRepository(Protocol):
    async def add(
        self,
        *,
        device_id: str,
        action: str,
        params: dict | None,
        user_id: str | None,
    ) -> CommandModel:
        ...

    async def list_pending(self, device_id: str) -> list[CommandModel]:
        ...

    async def get_by_id(self, command_id: str) -> CommandModel | None:
        ...

    async def update_result(
        self,
        command_id: str,
        *,
        status: str,
        result: str | None,
        error_message: str | None,
    ) -> None:
        ...

    async def mark_sent(
        self,
        command_id: str,
        *,
        sent_at: datetime,
    ) -> CommandModel | None:
        ...
