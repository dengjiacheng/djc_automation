"""SQLAlchemy implementation of the account repository."""

from __future__ import annotations

from datetime import datetime
from typing import Sequence

from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import Account as AccountModel
from app.modules.accounts.models import Account
from app.modules.accounts.exceptions import AccountNotFoundError
from app.modules.accounts.repository import AccountRepository


class SqlAccountRepository(AccountRepository):
    """Account repository backed by SQLAlchemy models."""

    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def get_by_id(self, account_id: str) -> Account | None:
        stmt = select(AccountModel).where(AccountModel.id == account_id)
        result = await self._session.execute(stmt)
        model = result.scalar_one_or_none()
        return self._to_domain(model)

    async def get_by_username(self, username: str) -> Account | None:
        stmt = select(AccountModel).where(AccountModel.username == username)
        result = await self._session.execute(stmt)
        model = result.scalar_one_or_none()
        return self._to_domain(model)

    async def list_accounts(self) -> Sequence[Account]:
        stmt = select(AccountModel).order_by(AccountModel.created_at.desc())
        result = await self._session.execute(stmt)
        return [self._to_domain(model) for model in result.scalars().all()]

    async def create_account(
        self,
        *,
        username: str,
        password_hash: str,
        role: str,
        email: str | None,
        is_active: bool,
    ) -> Account:
        model = AccountModel(
            username=username,
            password_hash=password_hash,
            role=role,
            email=email,
            is_active=is_active,
        )
        self._session.add(model)
        await self._session.flush()
        await self._session.refresh(model)
        return self._to_domain(model)

    async def update_account(
        self,
        account_id: str,
        *,
        email: str | None = None,
        is_active: bool | None = None,
        role: str | None = None,
        password_hash: str | None = None,
    ) -> Account:
        stmt = select(AccountModel).where(AccountModel.id == account_id)
        result = await self._session.execute(stmt)
        model = result.scalar_one_or_none()
        if model is None:
            raise AccountNotFoundError(account_id)

        model.email = email
        if is_active is not None:
            model.is_active = is_active
        if role is not None:
            model.role = role
        if password_hash is not None:
            model.password_hash = password_hash

        await self._session.flush()
        await self._session.refresh(model)
        return self._to_domain(model)

    async def set_last_login(self, account_id: str, timestamp: datetime) -> None:
        stmt = (
            update(AccountModel)
            .where(AccountModel.id == account_id)
            .values(last_login_at=timestamp)
        )
        await self._session.execute(stmt)

    @staticmethod
    def _to_domain(model: AccountModel | None) -> Account | None:
        if model is None:
            return None
        return Account(
            id=str(model.id),
            username=model.username,
            role=model.role or "user",
            is_active=bool(model.is_active),
            password_hash=model.password_hash,
            email=model.email,
            created_at=model.created_at,
            updated_at=model.updated_at,
            last_login_at=model.last_login_at,
        )
