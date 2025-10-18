"""Domain models for script templates."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Any, Optional


@dataclass(slots=True)
class ScriptTemplate:
    id: str
    owner_id: str
    script_name: str
    script_title: Optional[str]
    script_version: Optional[str]
    schema_hash: str
    schema: dict[str, Any]
    defaults: dict[str, Any]
    notes: Optional[str]
    status: str
    created_at: datetime
    updated_at: Optional[datetime]
