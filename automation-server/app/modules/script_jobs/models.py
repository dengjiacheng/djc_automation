"""Domain representations for script jobs."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Optional


@dataclass(slots=True)
class ScriptJob:
    id: str
    owner_id: str
    template_id: str
    script_name: str
    script_version: Optional[str]
    schema_hash: str
    total_targets: int
    status: str
    unit_price: Optional[int]
    currency: Optional[str]
    total_price: Optional[int]
    created_at: datetime
    updated_at: Optional[datetime]


@dataclass(slots=True)
class ScriptJobTarget:
    id: str
    job_id: str
    device_id: str
    command_id: Optional[str]
    status: str
    sent_at: Optional[datetime]
    completed_at: Optional[datetime]
    result: Optional[str]
    error_message: Optional[str]
