"""WebSocket endpoints for devices and web console."""
import json
import logging
from dataclasses import dataclass, field
from typing import Any, Literal, Optional

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


SESSION_INIT = "session_init"
SESSION_READY = "session_ready"
MESSAGE_COMMAND = "command"
MESSAGE_RESULT = "result"
MESSAGE_PROGRESS = "progress"
MESSAGE_LOG = "log"
MESSAGE_HEARTBEAT = "heartbeat"
MESSAGE_ERROR = "error"
MESSAGE_COMMAND_ACK = "command_ack"


@dataclass(slots=True)
class DeviceSession:
    websocket: WebSocket
    username: str
    state: Literal["init", "ready"] = "init"
    device_id: Optional[str] = None
    capabilities: list[Any] = field(default_factory=list)

    def is_ready(self) -> bool:
        return self.state == "ready"

    def mark_ready(self, device_id: str, capabilities: list[Any]) -> None:
        self.state = "ready"
        self.device_id = device_id
        self.capabilities = capabilities


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
    device_service = DeviceService.with_session(db)
    command_service = CommandService.with_session(db)
    log_service = LogService.with_session(db)
    session: Optional[DeviceSession] = None
    try:
        token_data = decode_access_token(token)
        username = token_data.username

        await websocket.accept()
        session = DeviceSession(websocket=websocket, username=username)
        while True:
            message = await websocket.receive_text()
            await _handle_device_message(
                session=session,
                raw=message,
                device_service=device_service,
                command_service=command_service,
                log_service=log_service,
                db=db,
            )
    except WebSocketDisconnect:
        logger.info("Device %s disconnected", session.device_id if session and session.device_id else "unknown")
    except Exception as exc:  # pylint: disable=broad-except
        logger.error("Device websocket error: %s", exc)
    finally:
        if session and session.device_id:
            try:
                await manager.disconnect(session.device_id)
                await device_service.mark_offline(session.device_id)
                await db.commit()
            except Exception as cleanup_exc:  # pylint: disable=broad-except
                logger.error("Failed to cleanup session %s: %s", session.device_id, cleanup_exc)


def _parse_json(raw: str) -> dict:
    try:
        return json.loads(raw)
    except json.JSONDecodeError as exc:
        logger.error("Invalid JSON payload: %s", exc)
        return {}


async def _handle_device_message(
    *,
    session: DeviceSession,
    raw: str,
    device_service: DeviceService,
    command_service: CommandService,
    log_service: LogService,
    db: AsyncSession,
) -> None:
    data = _parse_json(raw)
    msg_type = data.get("type")

    if not msg_type:
        await _send_error(session, "message missing type")
        return

    if not session.is_ready():
        if msg_type != SESSION_INIT:
            await _send_error(session, "session not initialized")
            return
        await _process_session_init(
            session=session,
            payload=data.get("data", {}),
            device_service=device_service,
            log_service=log_service,
            db=db,
        )
        return

    if msg_type == SESSION_INIT:
        await session.websocket.send_text(
            WSMessage(type=SESSION_READY, data={"device_id": session.device_id}).model_dump_json()
        )
        return

    if session.device_id is None:
        logger.warning("Session ready but device_id missing for user %s", session.username)
        return

    if msg_type == MESSAGE_HEARTBEAT:
        manager.update_heartbeat(session.device_id)
        return

    if msg_type == MESSAGE_RESULT:
        await _handle_command_result(data.get("data", {}), command_service)
        await db.commit()
        return

    if msg_type == MESSAGE_PROGRESS:
        await _handle_command_progress(session.device_id, data.get("data", {}), log_service)
        await db.commit()
        return

    if msg_type == MESSAGE_LOG:
        await log_service.create_log(
            device_id=session.device_id,
            log_type=data.get("data", {}).get("type", "info"),
            message=data.get("data", {}).get("message", ""),
            data=data.get("data"),
        )
        await db.commit()
        return

    logger.warning("Unknown websocket message type: %s", msg_type)


async def _process_session_init(
    *,
    session: DeviceSession,
    payload: dict,
    device_service: DeviceService,
    log_service: LogService,
    db: AsyncSession,
) -> None:
    device_id = payload.get("device_id")
    if not device_id:
        await session.websocket.close(code=1003, reason="缺少 device_id")
        return

    try:
        await device_service.ensure_device_for_connection(
            device_id=device_id,
            username=session.username,
            device_name=payload.get("device_name"),
            device_model=payload.get("device_model"),
            android_version=payload.get("android_version"),
            local_ip=payload.get("local_ip"),
            public_ip=session.websocket.client.host if session.websocket.client else None,
        )
    except DeviceOwnershipError as exc:
        await session.websocket.close(code=1008, reason=str(exc))
        await db.rollback()
        return

    await db.commit()

    capabilities = payload.get("capabilities") or []

    manager.register(device_id, session.websocket)
    manager.update_capabilities(device_id, capabilities)
    await session.websocket.send_text(WSMessage(type=SESSION_READY, data={"device_id": device_id}).model_dump_json())

    await log_service.create_log(device_id=device_id, log_type="info", message="设备已连接", data=payload)
    if capabilities:
        await log_service.create_log(
            device_id=device_id,
            log_type="capabilities",
            message="上报指令能力",
            data={"capabilities": capabilities},
        )
    await db.commit()

    session.mark_ready(device_id, capabilities)


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

    device_id = payload.get("device_id")
    if device_id:
        await manager.send_message(
            device_id,
            {"type": MESSAGE_COMMAND_ACK, "data": {"command_id": result.command_id}},
        )


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


async def _send_error(session: DeviceSession, reason: str) -> None:
    try:
        await session.websocket.send_text(
            WSMessage(type=MESSAGE_ERROR, data={"reason": reason}).model_dump_json()
        )
    except Exception as exc:  # pylint: disable=broad-except
        logger.debug("Failed to send error to %s: %s", session.username, exc)
