"""Command domain model."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Any, Optional

from app.db import models as orm


@dataclass(slots=True)
class Command:
    id: str
    device_id: str
    action: str
    params: Optional[dict[str, Any]]
    user_id: Optional[str]
    status: str
    result: Optional[str]
    error_message: Optional[str]
    created_at: datetime
    sent_at: Optional[datetime]
    completed_at: Optional[datetime]

    @classmethod
    def from_orm(cls, instance: orm.Command) -> "Command":
        params = None
        if instance.params:
            import json

            try:
                params = json.loads(instance.params)
            except json.JSONDecodeError:
                params = None
        return cls(
            id=str(instance.id),
            device_id=instance.device_id,
            action=instance.action,
            params=params,
            user_id=instance.user_id,
            status=instance.status,
            result=instance.result,
            error_message=instance.error_message,
            created_at=instance.created_at,
            sent_at=instance.sent_at,
            completed_at=instance.completed_at,
        )

    def to_response(
        self,
        *,
        user_id: Optional[str] = None,
        params: Optional[dict[str, Any]] = None,
        status_override: Optional[str] = None,
        sent_at: Optional[datetime] = None,
    ):
        """Convert current command to the public API schema."""
        from app.schemas import CommandResponse  # lazy import to avoid cycle

        return CommandResponse(
            command_id=self.id,
            device_id=self.device_id,
            user_id=user_id if user_id is not None else self.user_id,
            action=self.action,
            params=params if params is not None else self.params,
            status=status_override or self.status,
            result=self.result,
            error_message=self.error_message,
            created_at=self.created_at,
            sent_at=sent_at or self.sent_at,
            completed_at=self.completed_at,
        )
