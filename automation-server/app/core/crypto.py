"""Utilities for password hashing and verification."""

from __future__ import annotations

import bcrypt


def hash_password(password: str) -> str:
    """Hash plain text password using bcrypt."""
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def verify_password(plain_password: str, hashed_password: str) -> bool:
    """Verify a plain text password against a stored bcrypt hash."""
    try:
        return bcrypt.checkpw(plain_password.encode("utf-8"), hashed_password.encode("utf-8"))
    except ValueError:
        return False


__all__ = ["hash_password", "verify_password"]
