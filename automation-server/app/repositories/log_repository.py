"""Compatibility wrapper for legacy log repository imports."""

from app.infrastructure.database.repositories.log_repository import SqlLogRepository

DeviceLogRepository = SqlLogRepository

__all__ = ["DeviceLogRepository"]
