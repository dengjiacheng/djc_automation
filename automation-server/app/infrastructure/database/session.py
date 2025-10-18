"""Async SQLAlchemy engine and session management."""

from __future__ import annotations

from collections.abc import AsyncGenerator
from typing import Any

from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)

from app.core.config import get_settings
from app.infrastructure.database.base import Base

_engine: AsyncEngine | None = None
AsyncSessionFactory: async_sessionmaker[AsyncSession] | None = None


def _build_engine() -> AsyncEngine:
    settings = get_settings()
    engine_kwargs: dict[str, Any] = {
        "echo": settings.database.echo or settings.debug,
        "future": True,
    }
    if settings.database.pool_size is not None:
        engine_kwargs["pool_size"] = settings.database.pool_size
    if settings.database.max_overflow is not None:
        engine_kwargs["max_overflow"] = settings.database.max_overflow

    return create_async_engine(settings.database_url, **engine_kwargs)


def get_engine() -> AsyncEngine:
    global _engine, AsyncSessionFactory
    if _engine is None:
        _engine = _build_engine()
        AsyncSessionFactory = async_sessionmaker(
            bind=_engine,
            class_=AsyncSession,
            expire_on_commit=False,
        )
    return _engine


async def get_session() -> AsyncGenerator[AsyncSession, None]:
    if AsyncSessionFactory is None:
        get_engine()

    assert AsyncSessionFactory is not None  # for mypy
    async with AsyncSessionFactory() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise


async def init_db() -> None:
    """Create database tables in development mode (migrations preferred)."""
    # 延迟导入模型，避免循环依赖
    from app.db import models  # noqa: F401

    engine = get_engine()
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
