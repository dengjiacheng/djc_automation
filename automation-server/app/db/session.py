"""Backward-compatible database session helpers.

This module maintains the historical import path (``app.db.session``) while
delegating to the new infrastructure layer under ``app.infrastructure.database``.
"""

from __future__ import annotations

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from app.infrastructure.database import Base, get_session as get_db, init_db  # noqa: F401
from app.infrastructure.database import session as _db_session

__all__ = [
    "Base",
    "AsyncSessionMaker",
    "AsyncSession",
    "get_db",
    "init_db",
    "get_engine",
]

get_engine = _db_session.get_engine


def _ensure_factory() -> async_sessionmaker[AsyncSession]:
    if _db_session.AsyncSessionFactory is None:
        _db_session.get_engine()
    if _db_session.AsyncSessionFactory is None:
        raise RuntimeError("AsyncSessionFactory is not initialised")
    return _db_session.AsyncSessionFactory


AsyncSessionMaker = _ensure_factory()
