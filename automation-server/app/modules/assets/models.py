"""Domain models for template assets."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Optional


@dataclass(slots=True)
class TemplateAsset:
    id: str
    owner_id: str
    file_name: str
    content_type: Optional[str]
    size_bytes: int
    checksum_sha256: str
    storage_path: str
    created_at: datetime
