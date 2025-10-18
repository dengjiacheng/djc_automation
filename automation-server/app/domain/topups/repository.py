"""Repository interface for top-up orders."""

from __future__ import annotations

from typing import Protocol, Sequence

from app.db.models import WalletTopupOrder as TopupOrderModel


class TopupOrderRepository(Protocol):
    async def create(
        self,
        *,
        account_id: str,
        amount_cents: int,
        currency: str,
        payment_channel: str | None,
        reference_no: str | None,
    ) -> TopupOrderModel:
        ...

    async def update_status(
        self,
        order_id: str,
        *,
        status: str,
        confirmed_at,
    ) -> TopupOrderModel | None:
        ...

    async def get_order(self, order_id: str) -> TopupOrderModel | None:
        ...

    async def list_orders(self, account_id: str, limit: int, offset: int) -> Sequence[TopupOrderModel]:
        ...
