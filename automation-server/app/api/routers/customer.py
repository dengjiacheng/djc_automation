"""Customer-facing endpoints for viewing own devices and managing script templates."""
from __future__ import annotations

import copy
import hashlib
import json
from typing import Any, Dict, Iterable

from fastapi import APIRouter, Depends, HTTPException, Path, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_db_session
from app.core.security import get_current_account
from app.domain.accounts import Account as AccountDomain
from app.domain.devices import DeviceService
from app.domain.templates import ScriptTemplateService
from app.schemas import (
    AccountResponse,
    DeviceListResponse,
    DeviceResponse,
    ScriptCapabilityInfo,
    ScriptCapabilityListResponse,
    ScriptParameterSpec,
    ScriptTemplateCreate,
    ScriptTemplateDetail,
    ScriptTemplateListResponse,
    ScriptTemplateSummary,
    ScriptTemplateUpdate,
)
from app.websocket.manager import manager

router = APIRouter()


async def get_current_customer(account: AccountDomain = Depends(get_current_account)) -> AccountDomain:
    """确保当前账号为普通客户"""
    if account.role in {"admin", "super_admin"}:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="当前账号不属于客户角色")
    return account


@router.get("/me", response_model=AccountResponse, summary="获取当前客户信息")
async def customer_profile(account: AccountDomain = Depends(get_current_customer)) -> AccountResponse:
    return AccountResponse.model_validate(account)


@router.get("/devices", response_model=DeviceListResponse, summary="获取客户自己的设备列表")
async def customer_devices(
    account: AccountDomain = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db_session),
) -> DeviceListResponse:
    service = DeviceService.with_session(db)
    summary = await service.list_devices_by_username(account.username)
    return DeviceListResponse(
        total=summary.total,
        devices=[DeviceResponse.model_validate(device) for device in summary.devices],
    )


@router.get(
    "/templates/scripts",
    response_model=ScriptCapabilityListResponse,
    summary="列举当前在线设备可用的脚本能力",
)
async def list_available_scripts(
    _: AccountDomain = Depends(get_current_customer),
) -> ScriptCapabilityListResponse:
    scripts = _collect_script_capabilities()
    return ScriptCapabilityListResponse(
        scripts=[_to_script_capability_info(name, data) for name, data in scripts.items()]
    )


@router.post(
    "/templates",
    response_model=ScriptTemplateDetail,
    status_code=status.HTTP_201_CREATED,
    summary="创建脚本模板",
)
async def create_script_template(
    payload: ScriptTemplateCreate,
    account: AccountDomain = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db_session),
) -> ScriptTemplateDetail:
    scripts = _collect_script_capabilities()
    script = scripts.get(payload.script_name)
    if script is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="脚本当前不可用")

    schema_payload = script["schema"]
    schema_hash = script["schema_hash"]

    try:
        normalized_config = _normalize_template_config(schema_payload["parameters"], payload.config)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc

    service = ScriptTemplateService.with_session(db)
    template = await service.create_template(
        owner_id=account.id,
        script_name=payload.script_name,
        script_title=payload.script_title or script.get("script_title"),
        script_version=payload.script_version or schema_payload.get("version"),
        schema_hash=schema_hash,
        schema=schema_payload,
        defaults=normalized_config,
        notes=payload.notes,
    )
    await db.commit()

    compatibility = _determine_compatibility(template.schema_hash, script)
    return _to_template_detail_response(template, compatibility)


@router.get(
    "/templates",
    response_model=ScriptTemplateListResponse,
    summary="获取当前账户的脚本模板列表",
)
async def list_script_templates(
    account: AccountDomain = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db_session),
) -> dict[str, list[ScriptTemplateSummary]]:
    service = ScriptTemplateService.with_session(db)
    templates = await service.list_templates(account.id)
    scripts = _collect_script_capabilities()
    summaries: list[ScriptTemplateSummary] = []
    for template in templates:
        capability = scripts.get(template.script_name)
        compatibility = _determine_compatibility(template.schema_hash, capability)
        summaries.append(_to_template_summary_response(template, compatibility))
    return ScriptTemplateListResponse(templates=summaries)


@router.get(
    "/templates/{template_id}",
    response_model=ScriptTemplateDetail,
    summary="获取模板详情",
)
async def get_script_template(
    template_id: str = Path(..., description="模板ID"),
    account: AccountDomain = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db_session),
) -> ScriptTemplateDetail:
    service = ScriptTemplateService.with_session(db)
    template = await service.get_template(template_id)
    if template is None or template.owner_id != account.id or template.status == "deleted":
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="模板不存在")

    scripts = _collect_script_capabilities()
    capability = scripts.get(template.script_name)
    compatibility = _determine_compatibility(template.schema_hash, capability)
    return _to_template_detail_response(template, compatibility)


@router.patch(
    "/templates/{template_id}",
    response_model=ScriptTemplateDetail,
    summary="更新模板配置",
)
async def update_script_template(
    payload: ScriptTemplateUpdate,
    template_id: str = Path(..., description="模板ID"),
    account: AccountDomain = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db_session),
) -> ScriptTemplateDetail:
    service = ScriptTemplateService.with_session(db)
    template = await service.get_template(template_id)
    if template is None or template.owner_id != account.id or template.status == "deleted":
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="模板不存在")

    updated_config = copy.deepcopy(template.defaults)
    if payload.config is not None:
        try:
            partial_update = _normalize_template_config(
                template.schema.get("parameters", []), payload.config, allow_partial=True
            )
            _deep_merge(updated_config, partial_update)
        except ValueError as exc:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc

    updated = await service.update_template(
        template_id,
        defaults=updated_config if payload.config is not None else None,
        notes=payload.notes,
    )
    await db.commit()
    if updated is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="模板不存在")

    scripts = _collect_script_capabilities()
    capability = scripts.get(updated.script_name)
    compatibility = _determine_compatibility(updated.schema_hash, capability)
    return _to_template_detail_response(updated, compatibility)


@router.delete(
    "/templates/{template_id}",
    response_model=dict[str, bool],
    summary="删除脚本模板",
)
async def delete_script_template(
    template_id: str = Path(..., description="模板ID"),
    account: AccountDomain = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db_session),
) -> dict[str, bool]:
    service = ScriptTemplateService.with_session(db)
    template = await service.get_template(template_id)
    if template is None or template.owner_id != account.id or template.status == "deleted":
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="模板不存在")
    await service.delete_template(template_id)
    await db.commit()
    return {"success": True}


def _collect_script_capabilities() -> dict[str, dict[str, Any]]:
    """Aggregate script capabilities from currently online devices."""
    aggregated: dict[str, dict[str, Any]] = {}
    for device_id, capabilities in manager.device_capabilities.items():
        for entry in capabilities or []:
            action = entry.get("action") or ""
            meta = entry.get("meta") or {}
            if meta.get("scripts"):
                for script in meta["scripts"]:
                    _merge_script_capability(aggregated, device_id, entry, script)
            elif action.startswith("start_task:"):
                script_name = action.split("start_task:", 1)[1].strip()
                if not script_name:
                    continue
                synthetic_script = {
                    "name": script_name,
                    "version": entry.get("meta", {}).get("version"),
                    "description": entry.get("description"),
                    "parameters": entry.get("params", []),
                }
                _merge_script_capability(aggregated, device_id, entry, synthetic_script)
    return aggregated


def _merge_script_capability(
    aggregated: dict[str, dict[str, Any]],
    device_id: str,
    entry: dict[str, Any],
    script: dict[str, Any],
) -> None:
    script_name = script.get("name")
    if not script_name:
        return
    schema = {
        "script_name": script_name,
        "script_title": script.get("title") or script.get("name"),
        "version": script.get("version"),
        "description": script.get("description"),
        "parameters": copy.deepcopy(script.get("parameters") or entry.get("params") or []),
    }
    schema_hash = _compute_schema_hash(schema)
    record = aggregated.get(script_name)
    if record is None:
        aggregated[script_name] = {
            "schema": schema,
            "schema_hash": schema_hash,
            "source_devices": {device_id},
        }
        return
    record["source_devices"].add(device_id)
    if record["schema_hash"] != schema_hash:
        # Prefer the latest hash; replace schema if new hash appears
        record["schema"] = schema
        record["schema_hash"] = schema_hash


def _compute_schema_hash(schema: dict[str, Any]) -> str:
    payload = {
        "script_name": schema.get("script_name"),
        "version": schema.get("version"),
        "parameters": schema.get("parameters", []),
    }
    serialized = json.dumps(payload, sort_keys=True, ensure_ascii=False)
    return hashlib.sha256(serialized.encode("utf-8")).hexdigest()


def _normalize_template_config(
    parameters: Iterable[dict[str, Any]],
    incoming: dict[str, Any],
    *,
    allow_partial: bool = False,
) -> dict[str, Any]:
    incoming = incoming or {}
    flattened = _flatten_dict(incoming)
    config: dict[str, Any] = {}
    missing_required: list[str] = []

    for spec in parameters:
        name = spec.get("name")
        if not name:
            continue
        required = bool(spec.get("required"))
        default = spec.get("default")
        value_provided = name in flattened
        if value_provided:
            value = flattened[name]
        else:
            if default is not None:
                value = default
            elif required and not allow_partial:
                missing_required.append(name)
                continue
            else:
                continue
        _assign_path(config, name, value)

    if missing_required:
        raise ValueError(f"缺少必填参数: {', '.join(missing_required)}")
    return config


def _flatten_dict(data: dict[str, Any], prefix: str = "") -> dict[str, Any]:
    flattened: dict[str, Any] = {}
    for key, value in data.items():
        if not isinstance(key, str):
            continue
        new_key = f"{prefix}.{key}" if prefix else key
        if isinstance(value, dict):
            flattened.update(_flatten_dict(value, new_key))
        else:
            flattened[new_key] = value
    return flattened


def _assign_path(target: dict[str, Any], path: str, value: Any) -> None:
    parts = path.split(".")
    cursor = target
    for index, part in enumerate(parts):
        if index == len(parts) - 1:
            cursor[part] = value
        else:
            if part not in cursor or not isinstance(cursor[part], dict):
                cursor[part] = {}
            cursor = cursor[part]


def _determine_compatibility(template_hash: str, capability: dict[str, Any] | None) -> str:
    if capability is None:
        return "unavailable"
    current_hash = capability.get("schema_hash")
    if template_hash == current_hash:
        return "active"
    return "stale"


def _deep_merge(target: dict[str, Any], updates: dict[str, Any]) -> None:
    for key, value in updates.items():
        if key in target and isinstance(target[key], dict) and isinstance(value, dict):
            _deep_merge(target[key], value)
        else:
            target[key] = copy.deepcopy(value)


def _to_script_capability_info(name: str, data: dict[str, Any]) -> ScriptCapabilityInfo:
    schema = data["schema"]
    parameters = [
        ScriptParameterSpec.model_validate(param) for param in schema.get("parameters", [])
    ]
    return ScriptCapabilityInfo(
        script_name=name,
        script_title=schema.get("script_title"),
        description=schema.get("description"),
        version=schema.get("version"),
        schema_hash=data["schema_hash"],
        parameters=parameters,
        source_devices=sorted(data.get("source_devices", [])),
    )


def _to_template_summary_response(template, compatibility: str) -> ScriptTemplateSummary:
    return ScriptTemplateSummary(
        id=template.id,
        script_name=template.script_name,
        script_title=template.script_title,
        script_version=template.script_version,
        status=template.status,
        schema_hash=template.schema_hash,
        compatibility=compatibility,
        created_at=template.created_at,
        updated_at=template.updated_at,
    )


def _to_template_detail_response(template, compatibility: str) -> ScriptTemplateDetail:
    summary = _to_template_summary_response(template, compatibility)
    schema_dict = copy.deepcopy(template.schema)
    config_dict = copy.deepcopy(template.defaults)
    return ScriptTemplateDetail(
        **summary.model_dump(),
        schema=schema_dict,
        config=config_dict,
        notes=template.notes,
    )
