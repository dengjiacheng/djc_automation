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


class ScriptParameterSpec(BaseModel):
    name: str
    type: Optional[str] = None
    required: bool = False
    description: Optional[str] = None
    default: Optional[Any] = None


class ScriptCapabilityInfo(BaseModel):
    script_name: str
    script_title: Optional[str] = None
    description: Optional[str] = None
    version: Optional[str] = None
    schema_hash: str
    parameters: list[ScriptParameterSpec] = Field(default_factory=list)
    source_devices: list[str] = Field(default_factory=list)
    unit_price: Optional[int] = None
    currency: Optional[str] = None
    pricing: Optional[dict[str, Any]] = None


class ScriptCapabilityListResponse(BaseModel):
    scripts: list[ScriptCapabilityInfo] = Field(default_factory=list)


class ScriptTemplateCreate(BaseModel):
    script_name: str = Field(..., description="脚本唯一名称")
    script_title: Optional[str] = Field(None, description="模板显示名称，默认使用脚本名称")
    script_version: Optional[str] = Field(None, description="脚本版本标记，如果上报能力中存在")
    config: dict[str, Any] = Field(default_factory=dict, description="脚本配置项，例如 config.search_keyword 等")
    notes: Optional[str] = Field(None, description="模板备注")


class ScriptTemplateUpdate(BaseModel):
    config: Optional[dict[str, Any]] = None
    notes: Optional[str] = None


class ScriptTemplateSummary(BaseModel):
    id: str
    script_name: str
    script_title: Optional[str] = None
    script_version: Optional[str] = None
    status: str
    schema_hash: str
    compatibility: str
    created_at: datetime
    updated_at: Optional[datetime] = None


class ScriptTemplateDetail(ScriptTemplateSummary):
    schema: dict[str, Any] = Field(default_factory=dict)
    config: dict[str, Any] = Field(default_factory=dict)
    notes: Optional[str] = None


class ScriptTemplateListResponse(BaseModel):
    templates: list[ScriptTemplateSummary] = Field(default_factory=list)


class ScriptDeviceInfo(BaseModel):
    device_id: str
    device_name: Optional[str] = None
    device_model: Optional[str] = None
    is_online: bool = False
    compatibility: str


class ScriptDeviceListResponse(BaseModel):
    script_name: str
    devices: list[ScriptDeviceInfo] = Field(default_factory=list)


class ScriptJobTargetResponse(BaseModel):
    id: str
    device_id: str
    command_id: Optional[str] = None
    status: str
    sent_at: Optional[datetime] = None
    completed_at: Optional[datetime] = None
    result: Optional[str] = None
    error_message: Optional[str] = None


class ScriptJobResponse(BaseModel):
    id: str
    template_id: str
    script_name: str
    script_version: Optional[str] = None
    status: str
    total_targets: int
    unit_price: Optional[int] = None
    currency: Optional[str] = None
    total_price: Optional[int] = None
    created_at: datetime
    updated_at: Optional[datetime] = None
    targets: list[ScriptJobTargetResponse] = Field(default_factory=list)


class ScriptJobListResponse(BaseModel):
    jobs: list[ScriptJobResponse] = Field(default_factory=list)


class ScriptJobCreateRequest(BaseModel):
    template_id: str = Field(..., description="模板ID")
    device_ids: list[str] = Field(..., description="目标设备ID列表", min_length=1)


class WalletSnapshotResponse(BaseModel):
    balance_cents: int
    currency: str


class WalletTransactionResponse(BaseModel):
    id: str
    amount_cents: int
    currency: str
    type: str
    description: Optional[str] = None
    created_at: datetime
    job_id: Optional[str] = None


class WalletTransactionListResponse(BaseModel):
    transactions: list[WalletTransactionResponse] = Field(default_factory=list)


class WalletTopupRequest(BaseModel):
    amount_cents: int = Field(..., gt=0)
    payment_channel: Optional[str] = None
    reference_no: Optional[str] = None


class WalletTopupResponse(BaseModel):
    id: str
    amount_cents: int
    currency: str
    status: str
    payment_channel: Optional[str] = None
    reference_no: Optional[str] = None
    created_at: datetime
    confirmed_at: Optional[datetime] = None
