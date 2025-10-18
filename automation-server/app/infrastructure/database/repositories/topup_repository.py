"""SQLAlchemy implementation for top-up repository"""

from __future__ import annotations

from datetime import datetime
from typing import Sequence

from sqlalchemy import select, update, desc
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import WalletTopupOrder


class SqlTopupRepository:
    def __init__(self, session: AsyncSession) -> None:
        self.session = session

    async def create(
        self,
        *,
        account_id: str,
        amount_cents: int,
        currency: str,
        payment_channel: str | None,
        reference_no: str | None,
    ) -> WalletTopupOrder:
        order = WalletTopupOrder(
            account_id=account_id,
            amount_cents=amount_cents,
            currency=currency,
            payment_channel=payment_channel,
            reference_no=reference_no,
        )
        self.session.add(order)
        await self.session.flush()
        await self.session.refresh(order)
        return order

    async def update_status(
        self,
        order_id: str,
        *,
        status: str,
        confirmed_at: datetime,
    ) -> WalletTopupOrder | None:
        stmt = (
            update(WalletTopupOrder)
            .where(WalletTopupOrder.id == order_id)
            .values(status=status, confirmed_at=confirmed_at)
            .execution_options(synchronize_session="fetch")
            .returning(WalletTopupOrder)
        )
        result = await self.session.execute(stmt)
        return result.scalars().first()

    async def get_order(self, order_id: str) -> WalletTopupOrder | None:
        stmt = select(WalletTopupOrder).where(WalletTopupOrder.id == order_id)
        result = await self.session.execute(stmt)
        return result.scalars().first()

    async def list_orders(
        self,
        account_id: str,
        limit: int,
        offset: int,
        status: str | None = None,
    ) -> Sequence[WalletTopupOrder]:
        stmt = select(WalletTopupOrder).where(WalletTopupOrder.account_id == account_id)
        if status and status != "all":
            stmt = stmt.where(WalletTopupOrder.status == status)
        stmt = stmt.order_by(desc(WalletTopupOrder.created_at)).offset(offset).limit(limit)
        result = await self.session.execute(stmt)
        return result.scalars().all()

    async def list_orders_all(
        self,
        *,
        status: str | None,
        limit: int,
        offset: int,
    ) -> Sequence[WalletTopupOrder]:
        stmt = select(WalletTopupOrder)
        if status and status != "all":
            stmt = stmt.where(WalletTopupOrder.status == status)
        stmt = stmt.order_by(desc(WalletTopupOrder.created_at)).offset(offset).limit(limit)
        result = await self.session.execute(stmt)
        return result.scalars().all()
