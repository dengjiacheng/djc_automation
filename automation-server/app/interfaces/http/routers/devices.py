"""Device management endpoints."""

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession

from app.interfaces.http.deps import get_db_session
from app.modules.devices import DeviceService
from app.schemas import DeviceListResponse, DeviceResponse

router = APIRouter()


def _to_schema(device) -> DeviceResponse:
    return DeviceResponse.model_validate(device)


@router.get("/", response_model=DeviceListResponse, summary="获取设备列表")
async def list_devices(
    skip: int = 0,
    limit: int = 100,
    online_only: bool = False,
    db: AsyncSession = Depends(get_db_session),
):
    service = DeviceService.with_session(db)
    summary = await service.list_devices(skip=skip, limit=limit, online_only=online_only)
    return DeviceListResponse(
        total=summary.total,
        devices=[_to_schema(device) for device in summary.devices],
    )


@router.get("/{device_id}", response_model=DeviceResponse, summary="获取设备详情")
async def get_device(device_id: str, db: AsyncSession = Depends(get_db_session)):
    service = DeviceService.with_session(db)
    device = await service.get_device(device_id)
    if device is None:
        raise HTTPException(status_code=404, detail="设备不存在")
    return _to_schema(device)
