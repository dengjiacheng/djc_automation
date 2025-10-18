"""Administrative endpoints for managing accounts and devices."""
from datetime import datetime
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import delete, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_db_session
from app.core.security import create_access_token, get_current_admin, get_super_admin
from app.db.models import Device, Command
from app.domain.accounts import (
    Account as AccountDomain,
    AccountAlreadyExistsError,
    AccountCreateInput,
    AccountNotFoundError,
    AccountService,
    AccountUpdateInput,
    UNSET,
)
from app.domain.devices import DeviceService, DeviceAlreadyExistsError as DeviceCreationError
from app.domain.commands import CommandService
from app.schemas import (
    AccountCreate,
    AccountLoginResponse,
    AccountResponse,
    AccountUpdate,
    AdminLoginRequest,
    AdminCommandRequest,
    AdminStatsResponse,
    DeviceCreate,
    DeviceListResponse,
    DeviceResponse,
    CommandResponse,
    DeviceCapabilitiesResponse,
    SuccessResponse,
)
from app.websocket.manager import manager

router = APIRouter()


@router.post("/login", response_model=AccountLoginResponse)
async def admin_login(
    payload: AdminLoginRequest,
    db: AsyncSession = Depends(get_db_session),
):
    account_service = AccountService.with_session(db)
    account = await account_service.authenticate(payload.username, payload.password)
    if account is None or not account.is_admin():
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="用户名或密码错误")

    await account_service.set_last_login(account.id)
    await db.commit()

    token = create_access_token(account.id, account.username, account.role)
    return AccountLoginResponse(
        access_token=token,
        account_id=account.id,
        username=account.username,
        role=account.role,
        is_super_admin=account.role == "super_admin",
    )


@router.get("/me", response_model=AccountResponse)
async def current_admin(admin: AccountDomain = Depends(get_current_admin)):
    return admin


@router.get("/users", response_model=List[str])
async def list_users(admin: AccountDomain = Depends(get_current_admin), db: AsyncSession = Depends(get_db_session)):
    result = await db.execute(select(Device.username).distinct().order_by(Device.username))
    return [row[0] for row in result.all() if row[0]]


@router.get("/devices", response_model=DeviceListResponse)
async def admin_list_devices(
    skip: int = 0,
    limit: int = 100,
    online_only: bool = False,
    username: Optional[str] = None,
    admin: AccountDomain = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db_session),
):
    service = DeviceService.with_session(db)
    summary = await service.list_for_admin(
        skip=skip,
        limit=limit,
        online_only=online_only,
        username=username,
    )
    return DeviceListResponse(
        total=summary.total,
        devices=[DeviceResponse.model_validate(device) for device in summary.devices],
    )


@router.post("/devices", response_model=DeviceResponse)
async def admin_create_device(
    payload: DeviceCreate,
    admin: AccountDomain = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db_session),
):
    account_service = AccountService.with_session(db)
    try:
        await account_service.create_account(
            AccountCreateInput(
                username=payload.username,
                password=payload.password,
                role="user",
            )
        )
    except AccountAlreadyExistsError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="账号已存在") from exc

    service = DeviceService.with_session(db)
    try:
        device = await service.create_device(
            username=payload.username,
            device_name=payload.device_name,
            device_model=payload.device_model,
            android_version=payload.android_version,
            local_ip=payload.local_ip,
            public_ip=payload.public_ip,
        )
    except DeviceCreationError as exc:
        await db.rollback()
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc

    await db.commit()

    return DeviceResponse.model_validate(device)


@router.get("/devices/{device_id}", response_model=DeviceResponse)
async def admin_get_device(
    device_id: str,
    admin: AccountDomain = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db_session),
):
    service = DeviceService.with_session(db)
    device = await service.get_device(device_id)
    if device is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="设备不存在")
    return DeviceResponse.model_validate(device)


@router.get("/devices/{device_id}/capabilities", response_model=DeviceCapabilitiesResponse)
async def admin_get_device_capabilities(
    device_id: str,
    admin: AccountDomain = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db_session),
):
    stmt = select(Device.id).where(Device.id == device_id)
    exists = (await db.execute(stmt)).scalar_one_or_none()
    if exists is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="设备不存在")

    capabilities = manager.get_capabilities(device_id)
    if capabilities is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="设备未在线或未上报能力")

    return DeviceCapabilitiesResponse(device_id=device_id, capabilities=capabilities)


@router.delete("/devices/{device_id}", response_model=SuccessResponse)
async def admin_delete_device(device_id: str, admin: AccountDomain = Depends(get_current_admin), db: AsyncSession = Depends(get_db_session)):
    result = await db.execute(delete(Device).where(Device.id == device_id))
    await db.commit()
    if result.rowcount == 0:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="设备不存在")
    return SuccessResponse(message="设备删除成功")


@router.post("/commands", response_model=CommandResponse, status_code=status.HTTP_201_CREATED)
async def admin_send_command(
    payload: AdminCommandRequest,
    admin: AccountDomain = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db_session),
):
    device_id = payload.device_id
    if not manager.is_online(device_id):
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="设备离线")

    command_service = CommandService.with_session(db)
    try:
        command = await command_service.create_command(
            device_id=device_id,
            action=payload.action,
            params=payload.params,
            user_id=admin.id,
        )
        sent_at = datetime.utcnow()
        response_payload = CommandResponse(
            command_id=command.id,
            device_id=device_id,
            user_id=admin.id,
            action=command.action,
            params=payload.params,
            status="sent",
            result=None,
            error_message=None,
            created_at=command.created_at,
            sent_at=sent_at,
            completed_at=None,
        )
        success = await manager.send_command(device_id, response_payload)
        if not success:
            await db.rollback()
            raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="指令发送失败")

        updated_command = await command_service.mark_sent(command.id, sent_at)
        await db.commit()

        if updated_command:
            response_payload = CommandResponse(
                command_id=updated_command.id,
                device_id=updated_command.device_id,
                user_id=updated_command.user_id,
                action=updated_command.action,
                params=updated_command.params,
                status=updated_command.status,
                result=updated_command.result,
                error_message=updated_command.error_message,
                created_at=updated_command.created_at,
                sent_at=updated_command.sent_at,
                completed_at=updated_command.completed_at,
            )
        return response_payload
    except HTTPException:
        raise
    except Exception as exc:  # pylint: disable=broad-except
        await db.rollback()
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="指令发送失败") from exc


@router.get("/accounts", response_model=List[AccountResponse])
async def list_accounts(
    admin: AccountDomain = Depends(get_super_admin),
    db: AsyncSession = Depends(get_db_session),
):
    account_service = AccountService.with_session(db)
    accounts = await account_service.list_accounts()
    return [AccountResponse.model_validate(account) for account in accounts]


@router.post("/accounts", response_model=AccountResponse)
async def create_account(
    payload: AccountCreate,
    admin: AccountDomain = Depends(get_super_admin),
    db: AsyncSession = Depends(get_db_session),
):
    account_service = AccountService.with_session(db)
    try:
        account = await account_service.create_account(
            AccountCreateInput(
                username=payload.username,
                password=payload.password,
                role=payload.role,
                email=payload.email,
            )
        )
    except AccountAlreadyExistsError as exc:
        await db.rollback()
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="用户名已存在") from exc

    await db.commit()
    return AccountResponse.model_validate(account)


@router.get("/stats", response_model=AdminStatsResponse)
async def get_admin_stats(
    admin: AccountDomain = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db_session),
):
    today_start = datetime.utcnow().replace(hour=0, minute=0, second=0, microsecond=0)

    total_devices = (await db.execute(select(func.count()).select_from(Device))).scalar_one()
    online_devices = (
        await db.execute(select(func.count()).select_from(Device).where(Device.is_online.is_(True)))
    ).scalar_one()
    today_commands = (
        await db.execute(
            select(func.count()).select_from(Command).where(Command.created_at >= today_start)
        )
    ).scalar_one()

    return AdminStatsResponse(
        device_total=total_devices,
        device_online=online_devices,
        device_offline=max(total_devices - online_devices, 0),
        today_commands=today_commands,
    )


@router.patch("/accounts/{account_id}", response_model=AccountResponse)
async def update_account(
    account_id: str,
    payload: AccountUpdate,
    admin: AccountDomain = Depends(get_super_admin),
    db: AsyncSession = Depends(get_db_session),
):
    account_service = AccountService.with_session(db)
    update_data = payload.model_dump(exclude_unset=True)
    update_input = AccountUpdateInput(
        email=update_data.get("email", UNSET),
        is_active=update_data.get("is_active", UNSET),
        role=update_data.get("role", UNSET),
        password=update_data.get("password", UNSET),
    )

    try:
        account = await account_service.update_account(account_id, update_input)
    except AccountNotFoundError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="账号不存在") from exc

    await db.commit()
    return AccountResponse.model_validate(account)
