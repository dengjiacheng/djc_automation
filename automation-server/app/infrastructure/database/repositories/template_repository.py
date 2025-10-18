"""SQLAlchemy implementation for script template repository."""

from __future__ import annotations

from typing import Optional, Sequence

from sqlalchemy import select, update, delete
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import ScriptTemplate


class SqlScriptTemplateRepository:
    def __init__(self, session: AsyncSession) -> None:
        self.session = session

    async def create(
        self,
        *,
        owner_id: str,
        script_name: str,
        script_title: str | None,
        script_version: str | None,
        schema_hash: str,
        schema: str,
        defaults: str,
        notes: str | None,
    ) -> ScriptTemplate:
        template = ScriptTemplate(
            owner_id=owner_id,
            script_name=script_name,
            script_title=script_title,
            script_version=script_version,
            schema_hash=schema_hash,
            schema=schema,
            defaults=defaults,
            notes=notes,
        )
        self.session.add(template)
        await self.session.flush()
        await self.session.refresh(template)
        return template

    async def list_by_owner(self, owner_id: str) -> Sequence[ScriptTemplate]:
        stmt = (
            select(ScriptTemplate)
            .where(ScriptTemplate.owner_id == owner_id)
            .where(ScriptTemplate.status != "deleted")
            .order_by(ScriptTemplate.created_at.desc())
        )
        result = await self.session.execute(stmt)
        return result.scalars().all()

    async def get_by_id(self, template_id: str) -> ScriptTemplate | None:
        stmt = select(ScriptTemplate).where(ScriptTemplate.id == template_id)
        result = await self.session.execute(stmt)
        return result.scalars().first()

    async def update_template(
        self,
        template_id: str,
        *,
        defaults: Optional[str] = None,
        notes: Optional[str] = None,
        status: Optional[str] = None,
        schema: Optional[str] = None,
        schema_hash: Optional[str] = None,
        script_version: Optional[str] = None,
        script_title: Optional[str] = None,
    ) -> ScriptTemplate | None:
        values: dict[str, str] = {}
        if defaults is not None:
            values["defaults"] = defaults
        if notes is not None:
            values["notes"] = notes
        if status is not None:
            values["status"] = status
        if schema is not None:
            values["schema"] = schema
        if schema_hash is not None:
            values["schema_hash"] = schema_hash
        if script_version is not None:
            values["script_version"] = script_version
        if script_title is not None:
            values["script_title"] = script_title
        if not values:
            template = await self.get_by_id(template_id)
            return template
        stmt = (
            update(ScriptTemplate)
            .where(ScriptTemplate.id == template_id)
            .values(**values)
            .execution_options(synchronize_session="fetch")
            .returning(ScriptTemplate)
        )
        result = await self.session.execute(stmt)
        updated = result.scalars().first()
        return updated

    async def delete(self, template_id: str) -> None:
        stmt = (
            update(ScriptTemplate)
            .where(ScriptTemplate.id == template_id)
            .values(status="deleted")
            .execution_options(synchronize_session="fetch")
        )
        await self.session.execute(stmt)
