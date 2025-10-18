"""Repository protocol for wallet operations."""

from __future__ import annotations

from typing import Protocol, Sequence

from app.db.models import Wallet as WalletModel, WalletTransaction as WalletTransactionModel


class WalletRepository(Protocol):
    async def get_wallet(self, account_id: str) -> WalletModel | None:
        ...

    async def create_wallet(self, account_id: str, currency: str) -> WalletModel:
        ...

    async def update_balance(self, account_id: str, delta_cents: int) -> WalletModel:
        ...

    async def add_transaction(
        self,
        *,
        account_id: str,
        job_id: str | None,
        amount_cents: int,
        currency: str,
        type: str,
        description: str | None,
    ) -> WalletTransactionModel:
        ...

    async def list_transactions(self, account_id: str, limit: int, offset: int) -> Sequence[WalletTransactionModel]:
        ...
