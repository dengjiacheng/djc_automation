"""Pydantic schemas used across the project."""
from datetime import datetime
from typing import Any, Optional

from pydantic import BaseModel, ConfigDict, Field


class LoginRequest(BaseModel):
    username: str = Field(..., min_length=3, max_length=50)
    password: str = Field(..., min_length=6)
    device_id: Optional[str] = None


class LoginResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    device_id: str
    ws_url: str


class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"


class TokenData(BaseModel):
    account_id: str
    username: str
    role: str


class DeviceBase(BaseModel):
    username: str
    device_name: Optional[str] = None
    device_model: Optional[str] = None
    android_version: Optional[str] = None
    local_ip: Optional[str] = None
    public_ip: Optional[str] = None


class DeviceCreate(DeviceBase):
    password: str = Field(..., min_length=6)


class DeviceResponse(DeviceBase):
    id: str
    is_online: bool
    last_online_at: Optional[datetime] = None
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


class DeviceListResponse(BaseModel):
    total: int
    devices: list[DeviceResponse]


class CapabilityParam(BaseModel):
    name: str
    type: str
    required: bool = False
    description: Optional[str] = None
    default: Optional[Any] = None


class DeviceCapability(BaseModel):
    action: str
    description: Optional[str] = None
    params: list[CapabilityParam] = Field(default_factory=list)


class DeviceCapabilitiesResponse(BaseModel):
    device_id: str
    capabilities: list[DeviceCapability] = Field(default_factory=list)


class DeviceStatusUpdate(BaseModel):
    battery: Optional[int] = None
    network_type: Optional[str] = None
    current_task: Optional[str] = None
    cpu_usage: Optional[float] = None
    memory_usage: Optional[float] = None


class CommandCreate(BaseModel):
    action: str = Field(..., description="操作类型: click, swipe, screenshot, start_task")
    params: Optional[dict[str, Any]] = None


class CommandResponse(BaseModel):
    command_id: str
    device_id: str
    action: str
    params: Optional[dict[str, Any]] = None
    user_id: Optional[str] = None
    status: str
    result: Optional[str] = None
    error_message: Optional[str] = None
    created_at: datetime
    sent_at: Optional[datetime] = None
    completed_at: Optional[datetime] = None

    model_config = ConfigDict(from_attributes=True)


class CommandResultUpdate(BaseModel):
    command_id: str
    status: str = Field(..., description="success 或 failed")
    result: Optional[str] = None
    error_message: Optional[str] = None
    user_id: Optional[str] = None
    device_id: Optional[str] = None
    action: Optional[str] = None


class WSMessage(BaseModel):
    type: str
    data: Optional[dict[str, Any]] = None


class WSHeartbeat(WSMessage):
    type: str = "heartbeat"
    data: Optional[DeviceStatusUpdate] = None


class WSCommand(WSMessage):
    type: str = "command"
    data: CommandResponse


class WSResult(WSMessage):
    type: str = "result"
    data: CommandResultUpdate


class AdminLoginRequest(BaseModel):
    username: str = Field(..., min_length=3, max_length=50)
    password: str = Field(..., min_length=6)


class AccountLoginResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    account_id: str
    username: str
    role: str
    is_super_admin: bool = False


class AccountCreate(BaseModel):
    username: str = Field(..., min_length=3, max_length=50)
    password: str = Field(..., min_length=6)
    email: Optional[str] = None
    role: str = "user"


class AccountResponse(BaseModel):
    id: str
    username: str
    email: Optional[str] = None
    role: str
    is_active: bool
    created_at: datetime
    last_login_at: Optional[datetime] = None

    model_config = ConfigDict(from_attributes=True)


class AccountUpdate(BaseModel):
    email: Optional[str] = None
    is_active: Optional[bool] = None
    role: Optional[str] = None
    password: Optional[str] = Field(None, min_length=6)


class SuccessResponse(BaseModel):
    success: bool = True
    message: str = "操作成功"
    data: Optional[Any] = None


class AdminStatsResponse(BaseModel):
    device_total: int = 0
    device_online: int = 0
    device_offline: int = 0
    today_commands: int = 0


class AdminCommandRequest(CommandCreate):
    device_id: str = Field(..., min_length=1)


class TestApkAssetInfo(BaseModel):
    file_name: str
    download_url: str
    size_bytes: int
    checksum_sha256: Optional[str] = None
    package_name: Optional[str] = None
    version_code: Optional[int] = None
    version_name: Optional[str] = None


class TestApkInfo(BaseModel):
    version: str
    version_code: int
    app: TestApkAssetInfo
    test: TestApkAssetInfo
    created_at: Optional[str] = None
