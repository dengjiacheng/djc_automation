"""Repository protocol for script template persistence."""

from __future__ import annotations

from typing import Protocol, Sequence

from app.db.models import ScriptTemplate as ScriptTemplateModel


class ScriptTemplateRepository(Protocol):
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
    ) -> ScriptTemplateModel:
        ...

    async def list_by_owner(self, owner_id: str) -> Sequence[ScriptTemplateModel]:
        ...

    async def get_by_id(self, template_id: str) -> ScriptTemplateModel | None:
        ...

    async def update_template(
        self,
        template_id: str,
        *,
        defaults: str | None = None,
        notes: str | None = None,
        status: str | None = None,
        schema: str | None = None,
        schema_hash: str | None = None,
        script_version: str | None = None,
    ) -> ScriptTemplateModel | None:
        ...

    async def delete(self, template_id: str) -> None:
        ...
