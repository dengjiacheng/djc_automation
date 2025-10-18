"""Password hashing and JWT helpers."""
from datetime import datetime, timedelta
from typing import Optional

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jose import JWTError, jwt
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.domain.accounts import Account as AccountDomain
from app.domain.accounts.service import AccountService
from app.api.deps import get_db_session
from app.schemas import TokenData

settings = get_settings()
security = HTTPBearer()


def create_access_token(account_id: str, username: str, role: str, expires_delta: Optional[timedelta] = None) -> str:
    expire_delta = expires_delta or timedelta(
        minutes=1440 if role in {"admin", "super_admin"} else settings.access_token_expire_minutes
    )
    payload = {
        "sub": account_id,
        "username": username,
        "role": role,
        "exp": datetime.utcnow() + expire_delta,
    }
    return jwt.encode(payload, settings.secret_key, algorithm=settings.algorithm)


def decode_access_token(token: str) -> TokenData:
    try:
        payload = jwt.decode(token, settings.secret_key, algorithms=[settings.algorithm])
    except JWTError as exc:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="无法验证凭据") from exc

    account_id = payload.get("sub")
    username = payload.get("username")
    role = payload.get("role")
    if not all([account_id, username, role]):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="无法验证凭据")
    return TokenData(account_id=account_id, username=username, role=role)


async def get_current_account(
    credentials: HTTPAuthorizationCredentials = Depends(security),
    db: AsyncSession = Depends(get_db_session),
) -> AccountDomain:
    token_data = decode_access_token(credentials.credentials)
    service = AccountService.with_session(db)
    account = await service.get_by_id(token_data.account_id)
    if account is None or not account.is_active:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="账号不存在或已禁用")
    return account


async def get_current_admin(account: AccountDomain = Depends(get_current_account)) -> AccountDomain:
    if account.role not in {"admin", "super_admin"}:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="需要管理员权限")
    return account


async def get_super_admin(account: AccountDomain = Depends(get_current_account)) -> AccountDomain:
    if account.role != "super_admin":
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="需要超级管理员权限")
    return account
