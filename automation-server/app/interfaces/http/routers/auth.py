"""Authentication endpoints used by devices and web clients."""
from fastapi import APIRouter, Depends, HTTPException, Request, status

from app.interfaces.http.deps import get_account_service
from app.core.config import get_settings
from app.core.security import create_access_token
from app.db.models import generate_uuid
from app.modules.accounts import (
    AccountAlreadyExistsError,
    AccountCreateInput,
    AccountService,
)
from app.schemas import AccountCreate, AccountLoginResponse, AdminLoginRequest, LoginRequest, LoginResponse

router = APIRouter()
settings = get_settings()


def _ws_url(request: Request, token: str) -> str:
    host_header = request.headers.get("host", f"localhost:{settings.port}")
    scheme = "ws"
    if request.headers.get("x-forwarded-proto") == "https":
        scheme = "wss"
    return f"{scheme}://{host_header}/ws?token={token}"


@router.post("/device/login", response_model=LoginResponse, summary="设备登录（返回 WebSocket 信息）")
async def device_login(
    payload: LoginRequest,
    request: Request,
    account_service: AccountService = Depends(get_account_service),
) -> LoginResponse:
    account = await account_service.authenticate(payload.username, payload.password)
    if not account:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="用户名或密码错误")

    access_token = create_access_token(account.id, account.username, account.role)
    ws_url = _ws_url(request, access_token)

    device_id = payload.device_id or generate_uuid()
    return LoginResponse(access_token=access_token, device_id=device_id, ws_url=ws_url)


@router.post("/register", response_model=LoginResponse, summary="账号注册（设备用）")
async def register(
    payload: AccountCreate,
    request: Request,
    account_service: AccountService = Depends(get_account_service),
):
    try:
        role = payload.role if payload.role in {"user", "customer"} else "user"
        account = await account_service.create_account(
            AccountCreateInput(
                username=payload.username,
                password=payload.password,
                role=role,
                email=payload.email,
            )
        )
    except AccountAlreadyExistsError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="用户名已存在") from exc

    access_token = create_access_token(account.id, account.username, account.role)
    ws_url = _ws_url(request, access_token)
    return LoginResponse(access_token=access_token, device_id="", ws_url=ws_url)


@router.post("/web/login", response_model=AccountLoginResponse, summary="Web 登录（管理员/普通用户）")
async def web_login(
    payload: LoginRequest,
    account_service: AccountService = Depends(get_account_service),
):
    account = await account_service.authenticate(payload.username, payload.password)
    if not account:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="用户名或密码错误")

    access_token = create_access_token(account.id, account.username, account.role)
    return AccountLoginResponse(
        access_token=access_token,
        account_id=account.id,
        username=account.username,
        role=account.role,
        is_super_admin=account.role == "super_admin",
    )


@router.post("/admin/login", response_model=AccountLoginResponse, summary="管理员登录")
async def admin_login(
    payload: AdminLoginRequest,
    account_service: AccountService = Depends(get_account_service),
):
    account = await account_service.authenticate(payload.username, payload.password)
    if not account or account.role not in {"admin", "super_admin"}:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="无效的管理员凭据")

    await account_service.set_last_login(account.id)

    access_token = create_access_token(account.id, account.username, account.role)
    return AccountLoginResponse(
        access_token=access_token,
        account_id=account.id,
        username=account.username,
        role=account.role,
        is_super_admin=account.role == "super_admin",
    )
