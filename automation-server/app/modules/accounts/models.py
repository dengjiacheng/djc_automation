"""Domain models for accounts."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Optional


@dataclass(slots=True)
class Account:
    id: str
    username: str
    role: str
    is_active: bool
    password_hash: str = field(repr=False)
    email: Optional[str] = None
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None
    last_login_at: Optional[datetime] = None

    def is_admin(self) -> bool:
        return self.role in {"admin", "super_admin"}

    def is_super_admin(self) -> bool:
        return self.role == "super_admin"


@dataclass(slots=True)
class AccountCreateInput:
    username: str
    password: str
    role: str = "user"
    email: Optional[str] = None
    is_active: bool = True


# Sentinel used to differentiate between "not provided" and explicit None.
UNSET = object()


@dataclass(slots=True)
class AccountUpdateInput:
    email: Optional[str] | object = UNSET
    is_active: Optional[bool] | object = UNSET
    role: Optional[str] | object = UNSET
    password: Optional[str] | object = UNSET
