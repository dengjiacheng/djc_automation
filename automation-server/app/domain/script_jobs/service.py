"""Domain service for script job management."""

from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime
from typing import Any, Iterable

from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import ScriptJob as ScriptJobModel, ScriptJobTarget as ScriptJobTargetModel
from app.infrastructure.database.repositories.script_job_repository import SqlScriptJobRepository

from .models import ScriptJob, ScriptJobTarget
from .repository import ScriptJobRepository


@dataclass(slots=True)
class ScriptJobService:
    repository: ScriptJobRepository

    @classmethod
    def with_session(cls, session: AsyncSession) -> "ScriptJobService":
        return cls(SqlScriptJobRepository(session))

    async def create_job(
        self,
        *,
        owner_id: str,
        template_id: str,
        script_name: str,
        script_version: str | None,
        schema_hash: str,
        targets: Iterable[dict[str, Any]],
        unit_price: int | None,
        currency: str | None,
        total_price: int | None,
        meta: dict[str, Any] | None = None,
    ) -> tuple[ScriptJob, list[ScriptJobTarget]]:
        targets_list = list(targets)
        total_targets = len(targets_list)
        job_model = await self.repository.create_job(
            owner_id=owner_id,
            template_id=template_id,
            script_name=script_name,
            script_version=script_version,
            schema_hash=schema_hash,
            total_targets=total_targets,
            unit_price=unit_price,
            currency=currency,
            total_price=total_price,
            meta=json.dumps(meta, ensure_ascii=False) if meta else None,
        )
        target_models = await self.repository.add_targets(
            job_model.id,
            targets_list,
        )
        return self._to_job(job_model), [self._to_target(model) for model in target_models]

    async def list_jobs(self, owner_id: str, limit: int = 50, offset: int = 0) -> list[ScriptJob]:
        models = await self.repository.list_jobs(owner_id, limit, offset)
        return [self._to_job(model) for model in models]

    async def get_job(self, job_id: str) -> ScriptJob | None:
        model = await self.repository.get_job(job_id)
        return self._to_job(model) if model else None

    async def get_targets(self, job_id: str) -> list[ScriptJobTarget]:
        models = await self.repository.list_targets(job_id)
        return [self._to_target(model) for model in models]

    async def mark_target_result(
        self,
        *,
        command_id: str,
        status: str,
        result: str | None,
        error_message: str | None,
        completed_at: datetime,
    ) -> ScriptJobTarget | None:
        model = await self.repository.update_target_status(
            command_id=command_id,
            status=status,
            result=result,
            error_message=error_message,
            completed_at=completed_at,
        )
        return self._to_target(model) if model else None

    async def update_job_status(self, job_id: str, status: str) -> None:
        await self.repository.update_job_status(job_id, status)

    @staticmethod
    def _to_job(model: ScriptJobModel) -> ScriptJob:
        return ScriptJob(
            id=model.id,
            owner_id=model.owner_id,
            template_id=model.template_id,
            script_name=model.script_name,
            script_version=model.script_version,
            schema_hash=model.schema_hash,
            total_targets=model.total_targets,
            status=model.status,
            unit_price=model.unit_price,
            currency=model.currency,
            total_price=model.total_price,
            created_at=model.created_at,
            updated_at=model.updated_at,
        )

    @staticmethod
    def _to_target(model: ScriptJobTargetModel) -> ScriptJobTarget:
        return ScriptJobTarget(
            id=model.id,
            job_id=model.job_id,
            device_id=model.device_id,
            command_id=model.command_id,
            status=model.status,
            sent_at=model.sent_at,
            completed_at=model.completed_at,
            result=model.result,
            error_message=model.error_message,
        )
