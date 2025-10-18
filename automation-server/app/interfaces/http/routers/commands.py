"""Command dispatch endpoints."""
from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException
from app.core.security import get_current_account
from app.modules.accounts import Account as AccountDomain
from app.db.models import generate_uuid
from app.schemas import CommandCreate, CommandResponse
from app.interfaces.ws.manager import manager

router = APIRouter()


@router.post("/{device_id}", response_model=CommandResponse, summary="向设备下发指令")
async def send_command(
    device_id: str,
    payload: CommandCreate,
    current_account: AccountDomain = Depends(get_current_account),
):
    if not manager.is_online(device_id):
        raise HTTPException(status_code=404, detail="设备离线")

    command = CommandResponse(
        command_id=generate_uuid(),
        device_id=device_id,
        user_id=current_account.id,
        action=payload.action,
        params=payload.params,
        status="sent",
        result=None,
        error_message=None,
        created_at=datetime.utcnow(),
        sent_at=datetime.utcnow(),
        completed_at=None,
    )

    success = await manager.send_command(device_id, command)
    if not success:
        raise HTTPException(status_code=500, detail="指令发送失败")
    return command
