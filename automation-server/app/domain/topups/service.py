"""Top-up domain service."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Optional

from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import WalletTopupOrder as TopupOrderModel
from app.infrastructure.database.repositories.topup_repository import SqlTopupRepository

from .models import TopupOrder
from .repository import TopupOrderRepository


@dataclass(slots=True)
class TopupService:
    repository: TopupOrderRepository

    @classmethod
    def with_session(cls, session: AsyncSession) -> "TopupService":
        return cls(SqlTopupRepository(session))

    async def create_order(
        self,
        *,
        account_id: str,
        amount_cents: int,
        currency: str = "CNY",
        payment_channel: str | None = None,
        reference_no: str | None = None,
    ) -> TopupOrder:
        order = await self.repository.create(
            account_id=account_id,
            amount_cents=amount_cents,
            currency=currency,
            payment_channel=payment_channel,
            reference_no=reference_no,
        )
        return self._to_domain(order)

    async def mark_success(self, order_id: str) -> TopupOrder | None:
        order = await self.repository.update_status(
            order_id,
            status="success",
            confirmed_at=datetime.utcnow(),
        )
        return self._to_domain(order) if order else None

    async def mark_failed(self, order_id: str) -> TopupOrder | None:
        order = await self.repository.update_status(
            order_id,
            status="failed",
            confirmed_at=datetime.utcnow(),
        )
        return self._to_domain(order) if order else None

    async def get_order(self, order_id: str) -> TopupOrder | None:
        order = await self.repository.get_order(order_id)
        return self._to_domain(order) if order else None

    async def list_orders(self, account_id: str, limit: int = 20, offset: int = 0, status: str | None = None) -> list[TopupOrder]:
        rows = await self.repository.list_orders(account_id, limit, offset, status)
        return [self._to_domain(row) for row in rows]

    async def list_orders_admin(self, status: str | None = None, limit: int = 20, offset: int = 0) -> list[TopupOrder]:
        rows = await self.repository.list_orders_all(status=status, limit=limit, offset=offset)
        return [self._to_domain(row) for row in rows]

    @staticmethod
    def _to_domain(model: TopupOrderModel) -> TopupOrder:
        return TopupOrder(
            id=model.id,
            account_id=model.account_id,
            amount_cents=model.amount_cents,
            currency=model.currency,
            status=model.status,
            payment_channel=model.payment_channel,
            reference_no=model.reference_no,
            created_at=model.created_at,
            confirmed_at=model.confirmed_at,
        )
