"""Domain model for wallet top-up orders."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Optional


@dataclass(slots=True)
class TopupOrder:
    id: str
    account_id: str
    amount_cents: int
    currency: str
    status: str
    payment_channel: Optional[str]
    reference_no: Optional[str]
    created_at: datetime
    confirmed_at: Optional[datetime]
