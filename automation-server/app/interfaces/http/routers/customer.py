"""Customer-facing endpoints for viewing own devices and managing script templates."""
from __future__ import annotations

import copy
import hashlib
import json
from datetime import datetime, timezone
from typing import Any, Iterable

from fastapi import APIRouter, Depends, HTTPException, Path, status, UploadFile, File, Request
from fastapi.responses import FileResponse
from sqlalchemy.ext.asyncio import AsyncSession

from app.interfaces.http.deps import get_db_session
from app.core.config import get_settings
from app.core.security import get_current_account
from app.modules.accounts import Account as AccountDomain
from app.modules.devices import DeviceService
from app.modules.templates import ScriptTemplateService
from app.modules.script_jobs import ScriptJobService
from app.modules.script_jobs.models import ScriptJob, ScriptJobTarget
from app.modules.wallets import WalletService
from app.modules.topups import TopupService
from app.modules.commands import CommandService
from app.modules.assets import TemplateAssetService
from app.schemas import (
    AccountResponse,
    DeviceListResponse,
    DeviceResponse,
    ScriptCapabilityInfo,
    ScriptCapabilityListResponse,
    ScriptDeviceInfo,
    ScriptDeviceListResponse,
    ScriptJobListResponse,
    ScriptJobResponse,
    ScriptJobTargetResponse,
    ScriptJobCreateRequest,
    WalletSnapshotResponse,
    WalletTransactionListResponse,
    WalletTransactionResponse,
    WalletTopupRequest,
    WalletTopupResponse,
    WalletTopupListResponse,
    ScriptParameterSpec,
    ScriptTemplateCreate,
    ScriptTemplateDetail,
    ScriptTemplateListResponse,
    ScriptTemplateSummary,
    ScriptTemplateUpdate,
    TemplateAssetResponse,
)
from app.interfaces.ws.manager import manager

router = APIRouter()

FILE_PARAMETER_TYPES = {"file", "image"}


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


@router.get("/wallet", response_model=WalletSnapshotResponse, summary="获取钱包余额")
async def get_wallet_snapshot(
    account: AccountDomain = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db_session),
) -> WalletSnapshotResponse:
    wallet_service = WalletService.with_session(db)
    snapshot = await wallet_service.ensure_wallet(account.id)
    await db.commit()
    return WalletSnapshotResponse(balance_cents=snapshot.balance_cents, currency=snapshot.currency)


@router.get(
    "/wallet/transactions",
    response_model=WalletTransactionListResponse,
    summary="获取钱包交易记录",
)
async def list_wallet_transactions(
    limit: int = 50,
    offset: int = 0,
    account: AccountDomain = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db_session),
) -> WalletTransactionListResponse:
    wallet_service = WalletService.with_session(db)
    records = await wallet_service.list_transactions(account.id, limit, offset)
    transactions = [
        WalletTransactionResponse(
            id=record.id,
            amount_cents=record.amount_cents,
            currency=record.currency,
            type=record.type,
            description=record.description,
            created_at=record.created_at,
            job_id=record.job_id,
        )
        for record in records
    ]
    return WalletTransactionListResponse(transactions=transactions)


@router.post(
    "/wallet/topups",
    response_model=WalletTopupResponse,
    status_code=status.HTTP_201_CREATED,
    summary="创建充值订单",
)
async def create_wallet_topup(
    payload: WalletTopupRequest,
    account: AccountDomain = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db_session),
) -> WalletTopupResponse:
    topup_service = TopupService.with_session(db)
    wallet_service = WalletService.with_session(db)
    await wallet_service.ensure_wallet(account.id)
    order = await topup_service.create_order(
        account_id=account.id,
        amount_cents=payload.amount_cents,
        currency="CNY",
        payment_channel=payload.payment_channel,
        reference_no=payload.reference_no,
    )
    await db.commit()
    return WalletTopupResponse(
        id=order.id,
        amount_cents=order.amount_cents,
        currency=order.currency,
        status=order.status,
        payment_channel=order.payment_channel,
        reference_no=order.reference_no,
        created_at=order.created_at,
        confirmed_at=order.confirmed_at,
    )


@router.get(
    "/wallet/topups",
    response_model=WalletTopupListResponse,
    summary="获取充值订单列表",
)
async def list_wallet_topups(
    status_filter: str | None = None,
    limit: int = 50,
    offset: int = 0,
    account: AccountDomain = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db_session),
) -> WalletTopupListResponse:
    topup_service = TopupService.with_session(db)
    orders = await topup_service.list_orders(account.id, limit, offset, status_filter)
    return WalletTopupListResponse(orders=[
        WalletTopupResponse(
            id=order.id,
            amount_cents=order.amount_cents,
            currency=order.currency,
            status=order.status,
            payment_channel=order.payment_channel,
            reference_no=order.reference_no,
            created_at=order.created_at,
            confirmed_at=order.confirmed_at,
        )
        for order in orders
    ])


@router.post(
    "/assets",
    response_model=TemplateAssetResponse,
    status_code=status.HTTP_201_CREATED,
    summary="上传脚本资源文件",
)
async def upload_template_asset(
    request: Request,
    file: UploadFile = File(...),
    account: AccountDomain = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db_session),
) -> TemplateAssetResponse:
    asset_service = TemplateAssetService.with_session(db)
    try:
        asset = await asset_service.store_upload(account.id, file)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    await db.commit()
    download_url = request.url_for("download_template_asset", asset_id=asset.id)
    return TemplateAssetResponse(
        id=asset.id,
        file_name=asset.file_name,
        content_type=asset.content_type,
        size_bytes=asset.size_bytes,
        checksum_sha256=asset.checksum_sha256,
        created_at=asset.created_at,
        download_url=str(download_url),
    )


@router.get(
    "/assets/{asset_id}",
    summary="下载脚本资源文件",
)
async def download_template_asset(
    asset_id: str,
    account: AccountDomain = Depends(get_current_account),
    db: AsyncSession = Depends(get_db_session),
):
    asset_service = TemplateAssetService.with_session(db)
    asset = await asset_service.get_asset(asset_id)
    if asset is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="资源不存在")
    if account.role not in {"admin", "super_admin"} and asset.owner_id != account.id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="无权访问该资源")
    return FileResponse(
        asset.storage_path,
        media_type=asset.content_type or "application/octet-stream",
        filename=asset.file_name,
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

    # task_name 必须与脚本名称保持一致，忽略前端传入的值
    normalized_config["task_name"] = payload.script_name

    asset_service = TemplateAssetService.with_session(db)
    normalized_config = await _sanitize_template_config(
        normalized_config,
        schema_payload.get("parameters", []),
        account.id,
        asset_service,
    )

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
) -> ScriptTemplateListResponse:
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

    asset_service = TemplateAssetService.with_session(db)
    updated_config = copy.deepcopy(template.defaults)
    if payload.config is not None:
        try:
            partial_update = _normalize_template_config(
                template.schema.get("parameters", []), payload.config, allow_partial=True
            )
            _deep_merge(updated_config, partial_update)
        except ValueError as exc:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
        updated_config = await _sanitize_template_config(
            updated_config,
            template.schema.get("parameters", []),
            account.id,
            asset_service,
        )
    # 保证 task_name 始终与脚本名称一致
    updated_config["task_name"] = template.script_name

    updated = await service.update_template(
        template_id,
        defaults=updated_config if payload.config is not None else None,
        notes=payload.notes,
        script_title=payload.script_title,
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


@router.get(
    "/scripts/{script_name}/devices",
    response_model=ScriptDeviceListResponse,
    summary="获取脚本可执行设备列表",
)
async def list_script_devices(
    script_name: str,
    account: AccountDomain = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db_session),
) -> ScriptDeviceListResponse:
    scripts = _collect_script_capabilities()
    capability = scripts.get(script_name)
    service = DeviceService.with_session(db)
    summary = await service.list_devices_by_username(account.username)
    devices: list[ScriptDeviceInfo] = []
    for device in summary.devices:
        status = _device_compatibility(device.id, capability)
        devices.append(
            ScriptDeviceInfo(
                device_id=device.id,
                device_name=device.device_name,
                device_model=device.device_model,
                is_online=manager.is_online(device.id),
                compatibility=status,
            )
        )
    return ScriptDeviceListResponse(script_name=script_name, devices=devices)


@router.post(
    "/script-jobs",
    response_model=ScriptJobResponse,
    status_code=status.HTTP_201_CREATED,
    summary="创建脚本执行任务",
)
async def create_script_job(
    payload: ScriptJobCreateRequest,
    account: AccountDomain = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db_session),
) -> ScriptJobResponse:
    template_service = ScriptTemplateService.with_session(db)
    job_service = ScriptJobService.with_session(db)
    wallet_service = WalletService.with_session(db)
    topup_service = TopupService.with_session(db)
    command_service = CommandService.with_session(db)

    template = await template_service.get_template(payload.template_id)
    if template is None or template.owner_id != account.id or template.status == "deleted":
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="模板不存在")

    scripts = _collect_script_capabilities()
    capability = scripts.get(template.script_name)
    if capability is None:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="脚本当前不可用，无法执行")
    if _determine_compatibility(template.schema_hash, capability) != "active":
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="模板与脚本参数不一致，请先更新模板")

    device_service = DeviceService.with_session(db)
    summary = await device_service.list_devices_by_username(account.username)
    device_map = {device.id: device for device in summary.devices}

    unique_device_ids = []
    for device_id in payload.device_ids:
        if device_id not in unique_device_ids:
            unique_device_ids.append(device_id)

    selected_devices = []
    for device_id in unique_device_ids:
        device = device_map.get(device_id)
        if device is None:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"设备 {device_id} 不属于当前账号")
        status_flag = _device_compatibility(device_id, capability)
        if status_flag != "active":
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"设备 {device.device_name or device_id} 当前不支持该脚本或参数已失配")
        selected_devices.append(device)

    if not selected_devices:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="请选择至少一个可执行设备")

    job_response = await _start_job_execution(
        account=account,
        template=template,
        capability=capability,
        devices=selected_devices,
        db=db,
        job_service=job_service,
        wallet_service=wallet_service,
        command_service=command_service,
    )
    await db.commit()
    return job_response


@router.get(
    "/script-jobs",
    response_model=ScriptJobListResponse,
    summary="获取脚本执行任务列表",
)
async def list_script_jobs(
    account: AccountDomain = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db_session),
) -> ScriptJobListResponse:
    job_service = ScriptJobService.with_session(db)
    template_service = ScriptTemplateService.with_session(db)
    device_service = DeviceService.with_session(db)

    jobs = await job_service.list_jobs(account.id)
    templates = await template_service.list_templates(account.id)
    template_map = {template.id: template for template in templates}
    device_summary = await device_service.list_devices_by_username(account.username)
    device_map = {
        device.id: (device.device_name or device.device_id)
        for device in device_summary.devices
    }

    responses: list[ScriptJobResponse] = []
    for job in jobs:
        targets = await job_service.get_targets(job.id)
        template = template_map.get(job.template_id)
        template_title = None
        if template is not None:
            template_title = template.script_title or template.script_name
        responses.append(
            _to_job_response(
                job,
                targets,
                template_title=template_title,
                device_map=device_map,
            )
        )
    return ScriptJobListResponse(jobs=responses)


@router.get(
    "/script-jobs/{job_id}",
    response_model=ScriptJobResponse,
    summary="获取脚本执行任务详情",
)
async def get_script_job(
    job_id: str,
    account: AccountDomain = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db_session),
) -> ScriptJobResponse:
    job_service = ScriptJobService.with_session(db)
    job = await job_service.get_job(job_id)
    if job is None or job.owner_id != account.id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="任务不存在")
    targets = await job_service.get_targets(job.id)
    template_service = ScriptTemplateService.with_session(db)
    template = await template_service.get_template(job.template_id)
    device_service = DeviceService.with_session(db)
    device_summary = await device_service.list_devices_by_username(account.username)
    device_map = {
        device.id: (device.device_name or device.device_id)
        for device in device_summary.devices
    }
    template_title = None
    if template is not None:
        template_title = template.script_title or template.script_name
    return _to_job_response(job, targets, template_title=template_title, device_map=device_map)


@router.post(
    "/script-jobs/{job_id}/retry",
    response_model=ScriptJobResponse,
    summary="重试脚本任务的失败设备",
)
async def retry_script_job(
    job_id: str,
    account: AccountDomain = Depends(get_current_customer),
    db: AsyncSession = Depends(get_db_session),
) -> ScriptJobResponse:
    job_service = ScriptJobService.with_session(db)
    template_service = ScriptTemplateService.with_session(db)
    wallet_service = WalletService.with_session(db)
    topup_service = TopupService.with_session(db)
    command_service = CommandService.with_session(db)
    job = await job_service.get_job(job_id)
    if job is None or job.owner_id != account.id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="任务不存在")
    targets = await job_service.get_targets(job.id)
    failed_devices = [target.device_id for target in targets if target.status != "success"]
    if not failed_devices:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="没有失败的设备需要重试")
    template = await template_service.get_template(job.template_id)
    if template is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="模板不存在或已删除")
    scripts = _collect_script_capabilities()
    capability = scripts.get(template.script_name)
    if capability is None:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="脚本当前不可用，无法重试")

    device_service = DeviceService.with_session(db)
    summary = await device_service.list_devices_by_username(account.username)
    device_map = {device.id: device for device in summary.devices}
    selected_devices = []
    for device_id in failed_devices:
        device = device_map.get(device_id)
        if device is None:
            continue
        if _device_compatibility(device_id, capability) != "active":
            continue
        selected_devices.append(device)
    if not selected_devices:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="失败设备当前不可执行脚本")

    job_response = await _start_job_execution(
        account=account,
        template=template,
        capability=capability,
        devices=selected_devices,
        db=db,
        job_service=job_service,
        wallet_service=wallet_service,
        command_service=command_service,
    )
    await db.commit()
    return job_response


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
    pricing = _normalize_pricing(script.get("pricing") or entry.get("meta", {}).get("pricing"))
    record = aggregated.get(script_name)
    if record is None:
        aggregated[script_name] = {
            "schema": schema,
            "schema_hash": schema_hash,
            "source_devices": {device_id: schema_hash},
            "pricing": pricing,
        }
        return
    sources = record.setdefault("source_devices", {})
    if isinstance(sources, set):
        sources = {item: schema_hash for item in sources}
    sources[device_id] = schema_hash
    record["source_devices"] = sources
    if pricing:
        record["pricing"] = pricing
    if record["schema_hash"] != schema_hash:
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


def _build_param_type_map(parameters: Iterable[dict[str, Any]]) -> dict[str, str]:
    mapping: dict[str, str] = {}
    if not parameters:
        return mapping
    for spec in parameters:
        name = spec.get("name")
        if not name:
            continue
        mapping[name] = (spec.get("type") or "").lower()
    return mapping


async def _sanitize_template_config(
    config: dict[str, Any],
    parameters: Iterable[dict[str, Any]],
    owner_id: str,
    asset_service: TemplateAssetService,
) -> dict[str, Any]:
    if not parameters:
        return config
    type_map = _build_param_type_map(parameters)
    flat = _flatten_dict(config)
    sanitized_flat: dict[str, Any] = {}
    for path, value in flat.items():
        param_type = type_map.get(path)
        if param_type in FILE_PARAMETER_TYPES:
            sanitized_flat[path] = await _sanitize_file_value(value, owner_id, param_type, asset_service)
        else:
            sanitized_flat[path] = value
    sanitized: dict[str, Any] = {}
    for path, value in sanitized_flat.items():
        _assign_path(sanitized, path, value)
    return sanitized


async def _resolve_execution_config(
    config: dict[str, Any],
    parameters: Iterable[dict[str, Any]],
    owner_id: str,
    asset_service: TemplateAssetService,
) -> dict[str, Any]:
    if not parameters:
        return config
    type_map = _build_param_type_map(parameters)
    flat = _flatten_dict(config)
    resolved_flat: dict[str, Any] = {}
    for path, value in flat.items():
        param_type = type_map.get(path)
        if param_type in FILE_PARAMETER_TYPES:
            resolved_flat[path] = await _resolve_file_value(value, owner_id, param_type, asset_service)
        else:
            resolved_flat[path] = value
    resolved: dict[str, Any] = {}
    for path, value in resolved_flat.items():
        _assign_path(resolved, path, value)
    return resolved


async def _sanitize_file_value(
    raw_value: Any,
    owner_id: str,
    param_type: str,
    asset_service: TemplateAssetService,
) -> Any:
    if raw_value is None:
        return None
    if isinstance(raw_value, str):
        raw_value = {"source": "base64", "value": raw_value}
    if not isinstance(raw_value, dict):
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="文件参数格式错误")

    payload = dict(raw_value)
    source = (payload.get("source") or "").lower()
    if not source and payload.get("asset_id"):
        source = "asset"

    if source == "asset":
        asset_id = payload.get("asset_id")
        if not asset_id:
            raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="文件参数缺少 asset_id")
        asset = await asset_service.get_asset(asset_id)
        if asset is None:
            raise HTTPException(status.HTTP_404_NOT_FOUND, detail="文件资源不存在")
        try:
            await asset_service.ensure_owner(asset, owner_id)
        except PermissionError:
            raise HTTPException(status.HTTP_403_FORBIDDEN, detail="无权访问该文件资源") from None
        download_path = payload.get("download_path")
        settings = get_settings()
        if not download_path:
            download_path = f"{settings.api_prefix}/customer/assets/{asset.id}"
        sanitized = {
            "type": param_type,
            "source": "asset",
            "asset_id": asset.id,
            "name": payload.get("name") or asset.file_name,
            "mime": payload.get("mime") or asset.content_type,
            "size": asset.size_bytes,
            "checksum": payload.get("checksum") or asset.checksum_sha256,
            "download_path": download_path,
        }
        if payload.get("download_url"):
            sanitized["download_url"] = payload["download_url"]
        return sanitized

    if source in {"url", "path"}:
        value = payload.get("value")
        if not value:
            raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="文件参数缺少 value")
        sanitized = {
            "type": param_type,
            "source": source,
            "value": value,
        }
        for key in ("name", "mime", "checksum", "size"):
            if payload.get(key) is not None:
                sanitized[key] = payload[key]
        return sanitized

    if source == "base64":
        value = payload.get("value")
        if not value:
            raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="文件参数缺少 value")
        sanitized = {
            "type": param_type,
            "source": "base64",
            "value": value,
        }
        for key in ("name", "mime", "checksum", "size"):
            if payload.get(key) is not None:
                sanitized[key] = payload[key]
        return sanitized

    raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="暂不支持的文件来源类型")


async def _resolve_file_value(
    value: Any,
    owner_id: str,
    param_type: str,
    asset_service: TemplateAssetService,
) -> Any:
    if value is None:
        return None
    if not isinstance(value, dict):
        return value
    payload = dict(value)
    source = (payload.get("source") or "").lower()
    if not source and payload.get("asset_id"):
        source = "asset"

    if source == "asset":
        asset_id = payload.get("asset_id")
        if not asset_id:
            raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="文件参数缺少 asset_id")
        asset = await asset_service.get_asset(asset_id)
        if asset is None:
            raise HTTPException(status.HTTP_404_NOT_FOUND, detail="文件资源不存在")
        try:
            await asset_service.ensure_owner(asset, owner_id)
        except PermissionError:
            raise HTTPException(status.HTTP_403_FORBIDDEN, detail="无权访问该文件资源") from None
        settings = get_settings()
        download_path = payload.get("download_path") or f"{settings.api_prefix}/customer/assets/{asset.id}"
        result = {
            "type": param_type,
            "source": "asset",
            "asset_id": asset.id,
            "name": payload.get("name") or asset.file_name,
            "mime": payload.get("mime") or asset.content_type,
            "size": asset.size_bytes,
            "checksum": payload.get("checksum") or asset.checksum_sha256,
            "download_path": download_path,
        }
        if payload.get("download_url"):
            result["download_url"] = payload["download_url"]
        return result

    if source in {"base64", "url", "path"}:
        payload.setdefault("type", param_type)
        return payload

    return payload


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
        source_devices=sorted(data.get("source_devices", {}).keys()),
        unit_price=_extract_unit_price(data.get("pricing")),
        currency=_extract_currency(data.get("pricing")),
        pricing=data.get("pricing"),
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


def _to_job_response(
    job: ScriptJob,
    targets: list[ScriptJobTarget],
    *,
    template_title: str | None = None,
    device_map: dict[str, str] | None = None,
) -> ScriptJobResponse:
    device_map = device_map or {}
    return ScriptJobResponse(
        id=job.id,
        template_id=job.template_id,
        template_title=template_title,
        script_name=job.script_name,
        script_version=job.script_version,
        status=job.status,
        total_targets=job.total_targets,
        unit_price=job.unit_price,
        currency=job.currency,
        total_price=job.total_price,
        created_at=job.created_at,
        updated_at=job.updated_at,
        targets=[
            ScriptJobTargetResponse(
                id=target.id,
                device_id=target.device_id,
                device_name=target.device_name or device_map.get(target.device_id),
                command_id=target.command_id,
                status=target.status,
                sent_at=target.sent_at,
                completed_at=target.completed_at,
                result=target.result,
                error_message=target.error_message,
            )
            for target in targets
        ],
    )


async def _start_job_execution(
    *,
    account: AccountDomain,
    template,
    capability: dict[str, Any],
    devices,
    db: AsyncSession,
    job_service: ScriptJobService,
    wallet_service: WalletService,
    command_service: CommandService,
) -> ScriptJobResponse:
    pricing = capability.get("pricing") or {}
    unit_price = _extract_unit_price(pricing)
    currency = _extract_currency(pricing) or "CNY"
    total_price = unit_price * len(devices) if unit_price is not None else None

    if total_price and total_price > 0:
        snapshot = await wallet_service.ensure_wallet(account.id, currency)
        if snapshot.balance_cents < total_price:
            raise HTTPException(status_code=status.HTTP_402_PAYMENT_REQUIRED, detail="余额不足，请先充值后执行")

    asset_service = TemplateAssetService.with_session(db)
    execution_config = copy.deepcopy(template.defaults)
    command_config = await _resolve_execution_config(
        copy.deepcopy(execution_config),
        capability.get("schema", {}).get("parameters", []),
        account.id,
        asset_service,
    )
    command_params = {
        "task_name": template.script_name,
        "config": command_config,
    }

    targets_payload = []
    now = datetime.now(timezone.utc)
    sent_count = 0

    for device in devices:
        command = await command_service.create_command(
            device.id,
            action="start_task",
            params=copy.deepcopy(command_params),
            user_id=account.id,
        )
        command_payload = command.to_response(
            user_id=account.id,
            params=copy.deepcopy(command_params),
            status_override="sent",
            sent_at=now,
        )
        sent = await manager.send_command(device.id, command_payload)
        status_flag = "sent" if sent else "failed"
        sent_at = now if sent else None
        if sent:
            sent_count += 1
            await command_service.mark_sent(command.id, now)
        targets_payload.append(
            {
                "device_id": device.id,
                "device_name": device.device_name,
                "command_id": command.id if sent else None,
                "status": status_flag,
                "sent_at": sent_at,
                "completed_at": None,
            }
        )

    job_meta = {
        "currency": currency,
        "pricing": pricing,
    }
    job, targets = await job_service.create_job(
        owner_id=account.id,
        template_id=template.id,
        script_name=template.script_name,
        script_version=template.script_version,
        schema_hash=template.schema_hash,
        targets=targets_payload,
        unit_price=unit_price,
        currency=currency if unit_price is not None else None,
        total_price=total_price,
        meta=job_meta,
    )

    if total_price and total_price > 0:
        try:
            await wallet_service.freeze_amount(
                account_id=account.id,
                job_id=job.id,
                amount_cents=total_price,
                currency=currency,
                description=f"脚本 {template.script_name} 执行冻结",
            )
        except ValueError as exc:
            await job_service.update_job_status(job.id, "failed")
            raise HTTPException(status_code=status.HTTP_402_PAYMENT_REQUIRED, detail=str(exc)) from exc

    if sent_count == 0:
        await job_service.update_job_status(job.id, "failed")
    elif sent_count == len(targets_payload):
        await job_service.update_job_status(job.id, "running")
    else:
        await job_service.update_job_status(job.id, "partial")

    persisted_job = await job_service.get_job(job.id)
    persisted_targets = await job_service.get_targets(job.id)
    device_map = {
        device.id: (device.device_name or device.device_id)
        for device in devices
    }
    template_title = template.script_title or template.script_name
    return _to_job_response(
        persisted_job or job,
        persisted_targets or targets,
        template_title=template_title,
        device_map=device_map,
    )


def _extract_unit_price(pricing: dict[str, Any] | None) -> int | None:
    if not isinstance(pricing, dict):
        return None
    value = pricing.get("unit_price") or pricing.get("price")
    if value is None:
        return None
    try:
        # Store as分 (integer). Assume pricing is in元 float
        cents = int(round(float(value) * 100))
        return cents
    except (ValueError, TypeError):
        return None


def _extract_currency(pricing: dict[str, Any] | None) -> str | None:
    if not isinstance(pricing, dict):
        return None
    currency = pricing.get("currency") or pricing.get("unit")
    if isinstance(currency, str):
        return currency.upper()
    return None


def _normalize_pricing(pricing: dict[str, Any] | None) -> dict[str, Any] | None:
    if not isinstance(pricing, dict):
        return None
    normalized: dict[str, Any] = {}
    if "currency" in pricing:
        normalized["currency"] = pricing.get("currency")
    if "unit" in pricing and "currency" not in normalized:
        normalized["currency"] = pricing.get("unit")
    if pricing.get("unit_price") is not None:
        normalized["unit_price"] = pricing["unit_price"]
    elif pricing.get("price") is not None:
        normalized["unit_price"] = pricing["price"]
    tiers = pricing.get("tiers")
    if isinstance(tiers, list):
        normalized_tiers = []
        for tier in tiers:
            if not isinstance(tier, dict):
                continue
            entry = {
                "threshold": tier.get("threshold"),
                "price": tier.get("price"),
                "label": tier.get("label"),
            }
            normalized_tiers.append(entry)
        if normalized_tiers:
            normalized["tiers"] = normalized_tiers
    if pricing.get("billing"):
        normalized["billing"] = pricing.get("billing")
    if pricing.get("description"):
        normalized["description"] = pricing.get("description")
    return normalized


def _device_compatibility(device_id: str, capability: dict[str, Any] | None) -> str:
    if capability is None:
        return "unavailable"
    sources = capability.get("source_devices") or {}
    if device_id not in sources:
        return "unsupported"
    current_hash = capability.get("schema_hash")
    device_hash = sources.get(device_id)
    if device_hash == current_hash:
        return "active"
    return "stale"
