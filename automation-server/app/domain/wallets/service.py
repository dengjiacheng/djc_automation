"""Wallet domain service"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Optional

from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import Wallet as WalletModel, WalletTransaction as WalletTransactionModel
from app.infrastructure.database.repositories.wallet_repository import SqlWalletRepository

from .models import WalletSnapshot, WalletTransactionRecord
from .repository import WalletRepository


@dataclass(slots=True)
class WalletService:
    repository: WalletRepository

    @classmethod
    def with_session(cls, session: AsyncSession) -> "WalletService":
        return cls(SqlWalletRepository(session))

    async def ensure_wallet(self, account_id: str, currency: str = "CNY") -> WalletSnapshot:
        wallet = await self.repository.get_wallet(account_id)
        if wallet is None:
            wallet = await self.repository.create_wallet(account_id, currency)
        return self._to_snapshot(wallet)

    async def freeze_amount(
        self,
        *,
        account_id: str,
        job_id: str,
        amount_cents: int,
        currency: str = "CNY",
        description: Optional[str] = None,
    ) -> WalletSnapshot:
        wallet = await self.repository.get_wallet(account_id)
        if wallet is None:
            wallet = await self.repository.create_wallet(account_id, currency)
        if wallet.balance_cents < amount_cents:
            raise ValueError("余额不足，无法执行脚本")
        wallet = await self.repository.update_balance(account_id, -amount_cents)
        await self.repository.add_transaction(
            account_id=account_id,
            job_id=job_id,
            amount_cents=-amount_cents,
            currency=currency,
            type="freeze",
            description=description or "脚本执行冻结",
        )
        return self._to_snapshot(wallet)

    async def capture_amount(
        self,
        *,
        account_id: str,
        job_id: str,
        amount_cents: int,
        currency: str = "CNY",
        description: Optional[str] = None,
    ) -> WalletSnapshot:
        await self.repository.add_transaction(
            account_id=account_id,
            job_id=job_id,
            amount_cents=amount_cents,
            currency=currency,
            type="capture",
            description=description or "脚本执行扣费",
        )
        wallet = await self.repository.get_wallet(account_id)
        return self._to_snapshot(wallet)

    async def refund_amount(
        self,
        *,
        account_id: str,
        job_id: str,
        amount_cents: int,
        currency: str = "CNY",
        description: Optional[str] = None,
    ) -> WalletSnapshot:
        wallet = await self.repository.update_balance(account_id, amount_cents)
        await self.repository.add_transaction(
            account_id=account_id,
            job_id=job_id,
            amount_cents=amount_cents,
            currency=currency,
            type="refund",
            description=description or "脚本执行退款",
        )
        return self._to_snapshot(wallet)

    async def list_transactions(self, account_id: str, limit: int = 20, offset: int = 0) -> list[WalletTransactionRecord]:
        rows = await self.repository.list_transactions(account_id, limit, offset)
        return [self._to_transaction(row) for row in rows]

    @staticmethod
    def _to_snapshot(model: WalletModel) -> WalletSnapshot:
        return WalletSnapshot(
            account_id=model.account_id,
            balance_cents=model.balance_cents,
            currency=model.currency,
            updated_at=model.updated_at,
        )

    @staticmethod
    def _to_transaction(model: WalletTransactionModel) -> WalletTransactionRecord:
        return WalletTransactionRecord(
            id=model.id,
            account_id=model.account_id,
            job_id=model.job_id,
            amount_cents=model.amount_cents,
            currency=model.currency,
            type=model.type,
            description=model.description,
            created_at=model.created_at,
        )
