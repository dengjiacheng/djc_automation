"""WebSocket endpoints for devices and web console."""
import json
import logging
from typing import Optional

from fastapi import APIRouter, Depends, Query, WebSocket, WebSocketDisconnect
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_db_session
from app.core.security import decode_access_token
from app.domain.commands import CommandService
from app.domain.devices import DeviceService, DeviceOwnershipError
from app.domain.logs import LogService
from app.schemas import CommandResultUpdate, WSMessage
from app.websocket.manager import manager

logger = logging.getLogger(__name__)

router = APIRouter()


@router.websocket("/ws/web")
async def web_console_socket(websocket: WebSocket, token: str = Query(...)):
    user_id = None
    try:
        try:
            token_data = decode_access_token(token)
            user_id = token_data.account_id
        except Exception as exc:  # pylint: disable=broad-except
            logger.error("WebSocket token invalid: %s", exc)
            await websocket.close(code=1008, reason="Token验证失败")
            return

        await manager.connect_web(user_id, websocket)
        while True:
            await websocket.receive_text()
    except WebSocketDisconnect:
        logger.info("Web user %s disconnected", user_id)
    finally:
        if user_id:
            await manager.disconnect_web(user_id)


@router.websocket("/ws")
async def device_socket(websocket: WebSocket, token: str = Query(...), db: AsyncSession = Depends(get_db_session)):
    device_id = None
    device_service = DeviceService.with_session(db)
    command_service = CommandService.with_session(db)
    log_service = LogService.with_session(db)
    try:
        token_data = decode_access_token(token)
        username = token_data.username

        await websocket.accept()
        first_message = await websocket.receive_text()
        payload = json.loads(first_message)
        if payload.get("type") != "device_info":
            await websocket.close(code=1003, reason="首条消息必须是device_info")
            return

        info = payload.get("data", {})
        device_id = info.get("device_id")
        if not device_id:
            await websocket.close(code=1003, reason="缺少device_id")
            return

        try:
            await device_service.ensure_device_for_connection(
                device_id=device_id,
                username=username,
                device_name=info.get("device_name"),
                device_model=info.get("device_model"),
                android_version=info.get("android_version"),
                local_ip=info.get("local_ip"),
                public_ip=websocket.client.host if websocket.client else None,
            )
        except DeviceOwnershipError as exc:
            await websocket.close(code=1008, reason=str(exc))
            await db.rollback()
            return

        await db.commit()

        capabilities = info.get("capabilities") or []

        manager.register(device_id, websocket)
        manager.update_capabilities(device_id, capabilities)
        await websocket.send_text(WSMessage(type="welcome", data={}).model_dump_json())

        await log_service.create_log(device_id=device_id, log_type="info", message="设备已连接", data=info)
        if capabilities:
            await log_service.create_log(
                device_id=device_id,
                log_type="capabilities",
                message="上报指令能力",
                data={"capabilities": capabilities},
            )
        await db.commit()

        while True:
            message = await websocket.receive_text()
            await _handle_message(
                device_id=device_id,
                raw=message,
                command_service=command_service,
                log_service=log_service,
            )
            await db.commit()
    except WebSocketDisconnect:
        logger.info("Device %s disconnected", device_id)
    except Exception as exc:  # pylint: disable=broad-except
        logger.error("Device websocket error: %s", exc)
    finally:
        if device_id:
            await manager.disconnect(device_id)
            await device_service.mark_offline(device_id)
            await db.commit()


def _parse_json(raw: str) -> dict:
    try:
        return json.loads(raw)
    except json.JSONDecodeError as exc:
        logger.error("Invalid JSON payload: %s", exc)
        return {}


async def _handle_message(
    *,
    device_id: str,
    raw: str,
    command_service: CommandService,
    log_service: LogService,
) -> None:
    data = _parse_json(raw)
    msg_type = data.get("type")
    if msg_type == "heartbeat":
        manager.update_heartbeat(device_id)
    elif msg_type == "result":
        await _handle_command_result(data.get("data", {}), command_service)
    elif msg_type == "progress":
        await _handle_command_progress(device_id, data.get("data", {}), log_service)
    elif msg_type == "log":
        await log_service.create_log(
            device_id=device_id,
            log_type=data.get("data", {}).get("type", "info"),
            message=data.get("data", {}).get("message", ""),
            data=data.get("data"),
        )
    else:
        logger.warning("Unknown websocket message type: %s", msg_type)


async def _handle_command_result(payload: dict, command_service: CommandService) -> None:
    try:
        result = CommandResultUpdate(**payload)
    except Exception as exc:  # pylint: disable=broad-except
        logger.error("Invalid command result payload: %s", exc)
        return

    await command_service.update_result(
        result.command_id,
        status=result.status,
        result=result.result,
        error_message=result.error_message,
    )

    user_id = payload.get("user_id")
    if user_id:
        await manager.send_to_web(user_id, {"type": "command_result", "data": payload})


async def _handle_command_progress(device_id: str, payload: dict, log_service: LogService) -> None:
    command_id = payload.get("command_id")
    if not command_id:
        logger.error("Progress payload missing command_id: %s", payload)
        return

    payload["device_id"] = device_id

    await log_service.create_log(
        device_id=device_id,
        log_type=payload.get("stage", "info"),
        message=payload.get("message", ""),
        data=payload,
    )

    user_id = payload.get("user_id")
    if user_id:
        await manager.send_to_web(user_id, {"type": "command_progress", "data": payload})
