"""Compatibility layer for legacy imports.

The project now uses the domain/infrastructure split under
``app.infrastructure.database.repositories``. This module re-exports the new
implementation to avoid touching all call-sites at once.
"""

from app.infrastructure.database.repositories.device_repository import SqlDeviceRepository

DeviceRepository = SqlDeviceRepository

__all__ = ["DeviceRepository"]
