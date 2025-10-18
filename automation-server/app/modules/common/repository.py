"""Repository abstractions for domain services."""

from __future__ import annotations

from typing import Generic, TypeVar

from sqlalchemy.ext.asyncio import AsyncSession

ModelT = TypeVar("ModelT")


class AsyncRepository(Generic[ModelT]):
    """Base repository exposing the SQLAlchemy session."""

    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    @property
    def session(self) -> AsyncSession:
        return self._session

    async def add(self, instance: ModelT) -> ModelT:
        self.session.add(instance)
        await self.session.flush()
        return instance
