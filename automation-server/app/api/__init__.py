from fastapi import APIRouter

from app.api.routers import admin, apk, auth, commands, customer, devices, websocket


def create_api_router(prefix: str = "") -> APIRouter:
    router = APIRouter(prefix=prefix)
    router.include_router(auth.router, prefix="/auth", tags=["认证"])
    router.include_router(devices.router, prefix="/devices", tags=["设备"])
    router.include_router(commands.router, prefix="/commands", tags=["指令"])
    router.include_router(admin.router, prefix="/admin", tags=["后台管理"])
    router.include_router(customer.router, prefix="/customer", tags=["客户"])
    router.include_router(apk.router)
    return router


__all__ = [
    "create_api_router",
]
