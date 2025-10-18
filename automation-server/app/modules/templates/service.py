"""Application service handling script template workflows."""

from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any, Iterable, Optional

from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import ScriptTemplate as ScriptTemplateModel
from app.infrastructure.database.repositories.template_repository import SqlScriptTemplateRepository

from .models import ScriptTemplate
from .repository import ScriptTemplateRepository


@dataclass(slots=True)
class ScriptTemplateService:
    repository: ScriptTemplateRepository

    @classmethod
    def with_session(cls, session: AsyncSession) -> "ScriptTemplateService":
        return cls(SqlScriptTemplateRepository(session))

    async def create_template(
        self,
        *,
        owner_id: str,
        script_name: str,
        script_title: str | None,
        script_version: str | None,
        schema_hash: str,
        schema: dict[str, Any],
        defaults: dict[str, Any],
        notes: str | None,
    ) -> ScriptTemplate:
        model = await self.repository.create(
            owner_id=owner_id,
            script_name=script_name,
            script_title=script_title,
            script_version=script_version,
            schema_hash=schema_hash,
            schema=json.dumps(schema, ensure_ascii=False, sort_keys=True),
            defaults=json.dumps(defaults, ensure_ascii=False, sort_keys=True),
            notes=notes,
        )
        return self._to_domain(model)

    async def list_templates(self, owner_id: str) -> list[ScriptTemplate]:
        models = await self.repository.list_by_owner(owner_id)
        return [self._to_domain(model) for model in models]

    async def get_template(self, template_id: str) -> ScriptTemplate | None:
        model = await self.repository.get_by_id(template_id)
        return self._to_domain(model) if model else None

    async def update_template(
        self,
        template_id: str,
        *,
        defaults: Optional[dict[str, Any]] = None,
        notes: Optional[str] = None,
        status: Optional[str] = None,
        schema: Optional[dict[str, Any]] = None,
        schema_hash: Optional[str] = None,
        script_version: Optional[str] = None,
        script_title: Optional[str] = None,
    ) -> ScriptTemplate | None:
        model = await self.repository.update_template(
            template_id,
            defaults=json.dumps(defaults, ensure_ascii=False, sort_keys=True) if defaults is not None else None,
            notes=notes,
            status=status,
            schema=json.dumps(schema, ensure_ascii=False, sort_keys=True) if schema is not None else None,
            schema_hash=schema_hash,
            script_version=script_version,
            script_title=script_title,
        )
        return self._to_domain(model) if model else None

    async def delete_template(self, template_id: str) -> None:
        await self.repository.delete(template_id)

    @staticmethod
    def _to_domain(model: ScriptTemplateModel) -> ScriptTemplate:
        schema = {}
        defaults = {}
        try:
            schema = json.loads(model.schema) if model.schema else {}
        except json.JSONDecodeError:
            schema = {}
        try:
            defaults = json.loads(model.defaults) if model.defaults else {}
        except json.JSONDecodeError:
            defaults = {}
        return ScriptTemplate(
            id=model.id,
            owner_id=model.owner_id,
            script_name=model.script_name,
            script_title=model.script_title,
            script_version=model.script_version,
            schema_hash=model.schema_hash,
            schema=schema,
            defaults=defaults,
            notes=model.notes,
            status=model.status,
            created_at=model.created_at,
            updated_at=model.updated_at,
        )
