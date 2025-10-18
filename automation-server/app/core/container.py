"""Simple dependency container for wiring core services."""

from __future__ import annotations

from dataclasses import dataclass
from functools import lru_cache

from app.core.config import Settings, get_settings
from app.infrastructure.database.session import get_engine


@dataclass(slots=True)
class ApplicationContainer:
    settings: Settings

    def init_infrastructure(self) -> None:
        """Ensure infrastructure singletons (database engine, etc.) are initialised."""
        get_engine()


@lru_cache()
def get_container() -> ApplicationContainer:
    container = ApplicationContainer(settings=get_settings())
    container.init_infrastructure()
    return container


__all__ = ["ApplicationContainer", "get_container"]
