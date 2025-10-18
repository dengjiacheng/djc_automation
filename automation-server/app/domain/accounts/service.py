"""Domain services for account management."""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Sequence

from sqlalchemy.ext.asyncio import AsyncSession

from app.infrastructure.database.repositories.account_repository import SqlAccountRepository

from app.core.crypto import hash_password, verify_password

from .exceptions import AccountAlreadyExistsError, AccountNotFoundError
from .models import Account, AccountCreateInput, AccountUpdateInput, UNSET
from .repository import AccountRepository


class AccountService:
    """Encapsulates core account use cases."""

    def __init__(self, repository: AccountRepository) -> None:
        self._repository = repository

    @classmethod
    def with_session(cls, session: AsyncSession) -> "AccountService":
        return cls(SqlAccountRepository(session))

    async def get_by_id(self, account_id: str) -> Account | None:
        return await self._repository.get_by_id(account_id)

    async def get_by_username(self, username: str) -> Account | None:
        return await self._repository.get_by_username(username)

    async def list_accounts(self) -> Sequence[Account]:
        return await self._repository.list_accounts()

    async def authenticate(self, username: str, password: str) -> Account | None:
        account = await self._repository.get_by_username(username)
        if account is None or not account.is_active:
            return None
        if not verify_password(password, account.password_hash):
            return None
        return account

    async def create_account(self, payload: AccountCreateInput) -> Account:
        existing = await self._repository.get_by_username(payload.username)
        if existing is not None:
            raise AccountAlreadyExistsError(f"用户名已存在: {payload.username}")

        password_hash = hash_password(payload.password)
        return await self._repository.create_account(
            username=payload.username,
            password_hash=password_hash,
            role=payload.role,
            email=payload.email,
            is_active=payload.is_active,
        )

    async def update_account(self, account_id: str, payload: AccountUpdateInput) -> Account:
        current = await self._repository.get_by_id(account_id)
        if current is None:
            raise AccountNotFoundError(account_id)

        password_hash = None
        if payload.password is not UNSET and payload.password is not None:
            password_hash = hash_password(payload.password)

        email = (
            payload.email
            if payload.email is not UNSET
            else current.email
        )
        is_active = (
            payload.is_active
            if payload.is_active is not UNSET
            else current.is_active
        )
        role = (
            payload.role
            if payload.role is not UNSET
            else current.role
        )

        return await self._repository.update_account(
            account_id,
            email=email,
            is_active=is_active,
            role=role,
            password_hash=password_hash,
        )

    async def set_last_login(self, account_id: str) -> None:
        await self._repository.set_last_login(account_id, datetime.now(timezone.utc))
