"""SQLAlchemy implementation for wallet domain"""

from __future__ import annotations

from sqlalchemy import select, update, desc
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import Wallet, WalletTransaction


class SqlWalletRepository:
    def __init__(self, session: AsyncSession) -> None:
        self.session = session

    async def get_wallet(self, account_id: str) -> Wallet | None:
        stmt = select(Wallet).where(Wallet.account_id == account_id)
        result = await self.session.execute(stmt)
        return result.scalars().first()

    async def create_wallet(self, account_id: str, currency: str) -> Wallet:
        wallet = Wallet(account_id=account_id, currency=currency, balance_cents=0)
        self.session.add(wallet)
        try:
            await self.session.flush()
        except IntegrityError:
            await self.session.rollback()
            wallet = await self.get_wallet(account_id)
            if wallet is None:
                raise
        return wallet

    async def update_balance(self, account_id: str, delta_cents: int) -> Wallet:
        stmt = (
            update(Wallet)
            .where(Wallet.account_id == account_id)
            .values(balance_cents=Wallet.balance_cents + delta_cents)
            .execution_options(synchronize_session="fetch")
            .returning(Wallet)
        )
        result = await self.session.execute(stmt)
        wallet = result.scalars().first()
        if wallet is None:
            wallet = await self.create_wallet(account_id, "CNY")
            wallet.balance_cents += delta_cents
        return wallet

    async def add_transaction(
        self,
        *,
        account_id: str,
        job_id: str | None,
        amount_cents: int,
        currency: str,
        type: str,
        description: str | None,
    ) -> WalletTransaction:
        tx = WalletTransaction(
            account_id=account_id,
            job_id=job_id,
            amount_cents=amount_cents,
            currency=currency,
            type=type,
            description=description,
        )
        self.session.add(tx)
        await self.session.flush()
        return tx

    async def list_transactions(self, account_id: str, limit: int, offset: int) -> list[WalletTransaction]:
        stmt = (
            select(WalletTransaction)
            .where(WalletTransaction.account_id == account_id)
            .order_by(desc(WalletTransaction.created_at))
            .offset(offset)
            .limit(limit)
        )
        result = await self.session.execute(stmt)
        return result.scalars().all()
