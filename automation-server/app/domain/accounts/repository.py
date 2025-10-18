"""Repository protocol for accounts."""

from __future__ import annotations

from datetime import datetime
from typing import Protocol, Sequence

from .models import Account


class AccountRepository(Protocol):
    """Abstract repository interface for account persistence."""

    async def get_by_id(self, account_id: str) -> Account | None:
        ...

    async def get_by_username(self, username: str) -> Account | None:
        ...

    async def list_accounts(self) -> Sequence[Account]:
        ...

    async def create_account(
        self,
        *,
        username: str,
        password_hash: str,
        role: str,
        email: str | None,
        is_active: bool,
    ) -> Account:
        ...

    async def update_account(
        self,
        account_id: str,
        *,
        email: str | None = None,
        is_active: bool | None = None,
        role: str | None = None,
        password_hash: str | None = None,
    ) -> Account:
        ...

    async def set_last_login(self, account_id: str, timestamp: datetime) -> None:
        ...
