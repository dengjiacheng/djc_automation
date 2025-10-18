"""Administrative endpoints for managing accounts and devices."""
from datetime import datetime
from typing import Iterable, List, Optional

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import delete, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.interfaces.http.deps import get_db_session
from app.core.security import create_access_token, get_current_admin, get_super_admin
from app.db.models import Device, Command
from app.modules.accounts import (
    Account as AccountDomain,
    AccountAlreadyExistsError,
    AccountCreateInput,
    AccountNotFoundError,
    AccountService,
    AccountUpdateInput,
    UNSET,
)
from app.modules.devices import DeviceService, DeviceAlreadyExistsError as DeviceCreationError
from app.modules.commands import CommandService
from app.modules.script_jobs import ScriptJob, ScriptJobService, ScriptJobTarget
from app.modules.wallets import WalletService
from app.modules.topups import TopupService
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
    ScriptJobListResponse,
    ScriptJobResponse,
    WalletTransactionListResponse,
    WalletTransactionResponse,
    WalletTopupListResponse,
    WalletTopupReviewRequest,
    WalletTopupResponse,
    WalletSnapshotResponse,
)
from app.interfaces.ws.manager import manager

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
        response_payload = command.to_response(
            user_id=admin.id,
            params=payload.params,
            status_override="sent",
            sent_at=sent_at,
        )
        success = await manager.send_command(device_id, response_payload)
        if not success:
            await db.rollback()
            raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="指令发送失败")

        updated_command = await command_service.mark_sent(command.id, sent_at)
        await db.commit()

        if updated_command:
            response_payload = updated_command.to_response()
        return response_payload
    except HTTPException:
        raise
    except Exception as exc:  # pylint: disable=broad-except
        await db.rollback()
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="指令发送失败") from exc


@router.get("/script-jobs", response_model=ScriptJobListResponse)
async def admin_list_script_jobs(
    status_filter: Optional[str] = None,
    limit: int = 50,
    offset: int = 0,
    _: AccountDomain = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db_session),
) -> ScriptJobListResponse:
    service = ScriptJobService.with_session(db)
    jobs = await service.list_jobs_admin(status=status_filter, limit=limit, offset=offset)
    responses: list[ScriptJobResponse] = []
    for job in jobs:
        targets = await service.get_targets(job.id)
        responses.append(_job_to_response(job, targets))
    return ScriptJobListResponse(jobs=responses)


@router.get("/script-jobs/{job_id}", response_model=ScriptJobResponse)
async def admin_get_script_job(
    job_id: str,
    _: AccountDomain = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db_session),
) -> ScriptJobResponse:
    service = ScriptJobService.with_session(db)
    job = await service.get_job(job_id)
    if job is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="任务不存在")
    targets = await service.get_targets(job.id)
    return _job_to_response(job, targets)


@router.get("/wallet/balance", response_model=WalletSnapshotResponse)
async def admin_wallet_balance(
    account_id: str,
    _: AccountDomain = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db_session),
) -> WalletSnapshotResponse:
    wallet_service = WalletService.with_session(db)
    snapshot = await wallet_service.ensure_wallet(account_id)
    return WalletSnapshotResponse(balance_cents=snapshot.balance_cents, currency=snapshot.currency)


@router.get("/wallet/transactions", response_model=WalletTransactionListResponse)
async def admin_wallet_transactions(
    account_id: str,
    limit: int = 50,
    offset: int = 0,
    _: AccountDomain = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db_session),
) -> WalletTransactionListResponse:
    wallet_service = WalletService.with_session(db)
    rows = await wallet_service.list_transactions(account_id, limit, offset)
    transactions = [
        WalletTransactionResponse(
            id=row.id,
            amount_cents=row.amount_cents,
            currency=row.currency,
            type=row.type,
            description=row.description,
            created_at=row.created_at,
            job_id=row.job_id,
        )
        for row in rows
    ]
    return WalletTransactionListResponse(transactions=transactions)


@router.get("/wallet/topups", response_model=WalletTopupListResponse)
async def admin_list_topups(
    status_filter: Optional[str] = None,
    limit: int = 50,
    offset: int = 0,
    _: AccountDomain = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db_session),
) -> WalletTopupListResponse:
    topup_service = TopupService.with_session(db)
    orders = await topup_service.list_orders_admin(status=status_filter, limit=limit, offset=offset)
    return WalletTopupListResponse(orders=[_topup_to_response(order) for order in orders])


@router.post("/wallet/topups/{order_id}/review", response_model=WalletTopupResponse)
async def admin_review_topup(
    order_id: str,
    payload: WalletTopupReviewRequest,
    _: AccountDomain = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db_session),
) -> WalletTopupResponse:
    topup_service = TopupService.with_session(db)
    wallet_service = WalletService.with_session(db)

    order = await topup_service.get_order(order_id)
    if order is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="充值单不存在")
    if order.status != "pending":
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="充值单已经处理")

    if payload.action == "approve":
        order = await topup_service.mark_success(order_id)
        await wallet_service.ensure_wallet(order.account_id, order.currency)
        await wallet_service.credit_amount(
            account_id=order.account_id,
            amount_cents=order.amount_cents,
            currency=order.currency,
            description="人工审核充值",
        )
    else:
        order = await topup_service.mark_failed(order_id)

    await db.commit()
    return _topup_to_response(order)
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


def _job_to_response(job: ScriptJob, targets: Iterable[ScriptJobTarget]) -> ScriptJobResponse:
    return ScriptJobResponse(
        id=job.id,
        template_id=job.template_id,
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


def _topup_to_response(order) -> WalletTopupResponse:
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
