"""Compatibility wrapper for legacy import paths."""

from app.infrastructure.database.repositories.command_repository import SqlCommandRepository

CommandRepository = SqlCommandRepository

__all__ = ["CommandRepository"]
