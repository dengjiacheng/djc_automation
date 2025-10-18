"""Protocol for script job persistence"""

from __future__ import annotations

from typing import Protocol, Sequence

from app.db.models import ScriptJob as ScriptJobModel, ScriptJobTarget as ScriptJobTargetModel


class ScriptJobRepository(Protocol):
    async def create_job(
        self,
        *,
        owner_id: str,
        template_id: str,
        script_name: str,
        script_version: str | None,
        schema_hash: str,
        total_targets: int,
        unit_price: int | None,
        currency: str | None,
        total_price: int | None,
        meta: str | None = None,
    ) -> ScriptJobModel:
        ...

    async def add_targets(
        self,
        job_id: str,
        entries: Sequence[dict],
    ) -> Sequence[ScriptJobTargetModel]:
        ...

    async def get_job(self, job_id: str) -> ScriptJobModel | None:
        ...

    async def list_jobs(self, owner_id: str, limit: int, offset: int) -> Sequence[ScriptJobModel]:
        ...

    async def list_targets(self, job_id: str) -> Sequence[ScriptJobTargetModel]:
        ...

    async def update_target_status(
        self,
        *,
        command_id: str,
        status: str,
        result: str | None,
        error_message: str | None,
        completed_at,
    ) -> ScriptJobTargetModel | None:
        ...

    async def update_job_status(self, job_id: str, status: str) -> None:
        ...
