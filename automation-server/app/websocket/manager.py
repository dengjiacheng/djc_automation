"""Connection manager for device and web websocket clients."""
import asyncio
import json
import logging
from datetime import datetime, timedelta
from typing import Dict, Optional

from fastapi import WebSocket

logger = logging.getLogger(__name__)


class ConnectionManager:
    def __init__(self, timeout: int = 300, check_interval: int = 30) -> None:
        self.device_connections: Dict[str, WebSocket] = {}
        self.web_connections: Dict[str, WebSocket] = {}
        self.device_capabilities: Dict[str, list] = {}
        self.heartbeat_tasks: Dict[str, asyncio.Task] = {}
        self.last_heartbeat: Dict[str, datetime] = {}
        self.timeout = timedelta(seconds=timeout)
        self.check_interval = check_interval

    async def connect(self, device_id: str, websocket: WebSocket) -> None:
        await websocket.accept()
        self.register(device_id, websocket)

    def register(self, device_id: str, websocket: WebSocket) -> None:
        self.device_connections[device_id] = websocket
        self.last_heartbeat[device_id] = datetime.utcnow()
        self._start_heartbeat_monitor(device_id)
        logger.info("设备 %s 已注册到连接管理器", device_id)

    async def disconnect(self, device_id: str) -> None:
        self.device_connections.pop(device_id, None)
        self.last_heartbeat.pop(device_id, None)
        task = self.heartbeat_tasks.pop(device_id, None)
        if task:
            task.cancel()
        self.device_capabilities.pop(device_id, None)
        logger.info("设备 %s 已断开 WebSocket", device_id)

    async def send_message(self, device_id: str, message: dict) -> bool:
        websocket = self.device_connections.get(device_id)
        if websocket is None:
            logger.warning("设备 %s 不在线", device_id)
            return False
        try:
            await websocket.send_text(json.dumps(message))
            return True
        except Exception as exc:  # pylint: disable=broad-except
            logger.error("发送消息到设备 %s 失败: %s", device_id, exc)
            await self.disconnect(device_id)
            return False

    async def send_command(self, device_id: str, command) -> bool:
        return await self.send_message(
            device_id,
            {"type": "command", "data": command.model_dump(mode="json")},
        )

    async def broadcast(self, message: dict, exclude: Optional[str] = None) -> None:
        for device_id in list(self.device_connections.keys()):
            if device_id != exclude:
                await self.send_message(device_id, message)

    def is_online(self, device_id: str) -> bool:
        return device_id in self.device_connections

    def update_capabilities(self, device_id: str, capabilities: list) -> None:
        self.device_capabilities[device_id] = capabilities

    def get_capabilities(self, device_id: str) -> Optional[list]:
        return self.device_capabilities.get(device_id)

    def get_online_count(self) -> int:
        return len(self.device_connections)

    def update_heartbeat(self, device_id: str) -> None:
        self.last_heartbeat[device_id] = datetime.utcnow()

    def _start_heartbeat_monitor(self, device_id: str) -> None:
        task = self.heartbeat_tasks.get(device_id)
        if task:
            task.cancel()
        self.heartbeat_tasks[device_id] = asyncio.create_task(self._heartbeat_monitor(device_id))

    async def _heartbeat_monitor(self, device_id: str) -> None:
        try:
            while True:
                await asyncio.sleep(self.check_interval)
                last = self.last_heartbeat.get(device_id)
                if last and datetime.utcnow() - last > self.timeout:
                    logger.warning("设备 %s 心跳超时,断开连接", device_id)
                    await self.disconnect(device_id)
                    break
        except asyncio.CancelledError:
            logger.debug("设备 %s 心跳监听任务已取消", device_id)

    async def connect_web(self, user_id: str, websocket: WebSocket) -> None:
        await websocket.accept()
        self.web_connections[user_id] = websocket
        logger.info("Web用户 %s 已连接", user_id)

    async def disconnect_web(self, user_id: str) -> None:
        self.web_connections.pop(user_id, None)
        logger.info("Web用户 %s 已断开", user_id)

    async def send_to_web(self, user_id: str, message: dict) -> bool:
        websocket = self.web_connections.get(user_id)
        if websocket is None:
            logger.warning("Web用户 %s 不在线", user_id)
            return False
        try:
            await websocket.send_text(json.dumps(message))
            return True
        except Exception as exc:  # pylint: disable=broad-except
            logger.error("向 Web 用户 %s 发送消息失败: %s", user_id, exc)
            await self.disconnect_web(user_id)
            return False


manager = ConnectionManager()
