"""Customer-facing endpoints for viewing own devices."""
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_db_session
from app.core.security import get_current_account
from app.domain.accounts import Account as AccountDomain
from app.domain.devices import DeviceService
from app.schemas import AccountResponse, DeviceListResponse, DeviceResponse

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
