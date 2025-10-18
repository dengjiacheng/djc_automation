"""Account related dependency providers."""

from fastapi import Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.modules.accounts.service import AccountService
from app.infrastructure.database.repositories.account_repository import SqlAccountRepository

from .database import get_db_session


def get_account_repository(db: AsyncSession = Depends(get_db_session)) -> SqlAccountRepository:
    return SqlAccountRepository(db)


def get_account_service(repository: SqlAccountRepository = Depends(get_account_repository)) -> AccountService:
    return AccountService(repository)


__all__ = [
    "get_account_repository",
    "get_account_service",
]
