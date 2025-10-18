"""SQLAlchemy implementation for ScriptJobRepository"""

from __future__ import annotations

from datetime import datetime
from typing import Iterable, Sequence

from sqlalchemy import select, update, desc
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import ScriptJob, ScriptJobTarget


class SqlScriptJobRepository:
    def __init__(self, session: AsyncSession) -> None:
        self.session = session

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
    ) -> ScriptJob:
        job = ScriptJob(
            owner_id=owner_id,
            template_id=template_id,
            script_name=script_name,
            script_version=script_version,
            schema_hash=schema_hash,
            total_targets=total_targets,
            unit_price=unit_price,
            currency=currency,
            total_price=total_price,
            meta=meta,
        )
        self.session.add(job)
        await self.session.flush()
        await self.session.refresh(job)
        return job

    async def add_targets(self, job_id: str, entries: Iterable[dict]) -> Sequence[ScriptJobTarget]:
        targets = [
            ScriptJobTarget(
                job_id=job_id,
                device_id=entry["device_id"],
                command_id=entry.get("command_id"),
                status=entry.get("status", "pending"),
                sent_at=entry.get("sent_at"),
                completed_at=entry.get("completed_at"),
                result=entry.get("result"),
                error_message=entry.get("error_message"),
            )
            for entry in entries
        ]
        self.session.add_all(targets)
        await self.session.flush()
        return targets

    async def get_job(self, job_id: str) -> ScriptJob | None:
        stmt = select(ScriptJob).where(ScriptJob.id == job_id)
        result = await self.session.execute(stmt)
        return result.scalars().first()

    async def list_jobs(self, owner_id: str, limit: int, offset: int) -> Sequence[ScriptJob]:
        stmt = (
            select(ScriptJob)
            .where(ScriptJob.owner_id == owner_id)
            .order_by(desc(ScriptJob.created_at))
            .offset(offset)
            .limit(limit)
        )
        result = await self.session.execute(stmt)
        return result.scalars().all()

    async def list_targets(self, job_id: str) -> Sequence[ScriptJobTarget]:
        stmt = select(ScriptJobTarget).where(ScriptJobTarget.job_id == job_id)
        result = await self.session.execute(stmt)
        return result.scalars().all()

    async def update_target_status(
        self,
        *,
        command_id: str,
        status: str,
        result: str | None,
        error_message: str | None,
        completed_at: datetime,
    ) -> ScriptJobTarget | None:
        stmt = (
            update(ScriptJobTarget)
            .where(ScriptJobTarget.command_id == command_id)
            .values(
                status=status,
                result=result,
                error_message=error_message,
                completed_at=completed_at,
            )
            .execution_options(synchronize_session="fetch")
            .returning(ScriptJobTarget)
        )
        res = await self.session.execute(stmt)
        return res.scalars().first()

    async def update_job_status(self, job_id: str, status: str) -> None:
        stmt = (
            update(ScriptJob)
            .where(ScriptJob.id == job_id)
            .values(status=status)
            .execution_options(synchronize_session="fetch")
        )
        await self.session.execute(stmt)
