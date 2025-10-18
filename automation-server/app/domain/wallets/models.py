"""Domain models for wallet operations."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Optional


@dataclass(slots=True)
class WalletSnapshot:
    account_id: str
    balance_cents: int
    currency: str
    updated_at: Optional[datetime]


@dataclass(slots=True)
class WalletTransactionRecord:
    id: str
    account_id: str
    job_id: Optional[str]
    amount_cents: int
    currency: str
    type: str
    description: Optional[str]
    created_at: datetime
