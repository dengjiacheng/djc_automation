"""SQLAlchemy ORM models."""
import uuid
from sqlalchemy import Boolean, Column, DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func

from app.db.session import Base


def generate_uuid() -> str:
    return str(uuid.uuid4())


class Account(Base):
    __tablename__ = "accounts"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    username = Column(String(50), unique=True, nullable=False, index=True)
    password_hash = Column(String(255), nullable=False)
    role = Column(String(20), default="user")
    is_active = Column(Boolean, default=True)
    email = Column(String(100), unique=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())
    last_login_at = Column(DateTime(timezone=True))


class Device(Base):
    __tablename__ = "devices"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    username = Column(String(50), nullable=False, index=True)
    device_name = Column(String(100))
    device_model = Column(String(100))
    android_version = Column(String(20))
    local_ip = Column(String(45))
    public_ip = Column(String(45))
    is_online = Column(Boolean, default=False)
    last_online_at = Column(DateTime(timezone=True), onupdate=func.now())
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())

    commands = relationship("Command", back_populates="device", cascade="all, delete-orphan")
    logs = relationship("DeviceLog", back_populates="device", cascade="all, delete-orphan")


class Command(Base):
    __tablename__ = "commands"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    device_id = Column(String(36), ForeignKey("devices.id"), nullable=False, index=True)
    user_id = Column(String(36))
    action = Column(String(50), nullable=False)
    params = Column(Text)
    status = Column(String(20), default="pending")
    result = Column(Text)
    error_message = Column(Text)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    sent_at = Column(DateTime(timezone=True))
    completed_at = Column(DateTime(timezone=True))

    device = relationship("Device", back_populates="commands")


class DeviceLog(Base):
    __tablename__ = "device_logs"

    id = Column(Integer, primary_key=True, autoincrement=True)
    device_id = Column(String(36), ForeignKey("devices.id"), nullable=False, index=True)
    log_type = Column(String(20), nullable=False)
    message = Column(Text)
    data = Column(Text)
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    device = relationship("Device", back_populates="logs")


class ScriptTemplate(Base):
    __tablename__ = "script_templates"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    owner_id = Column(String(36), ForeignKey("accounts.id"), nullable=False, index=True)
    script_name = Column(String(150), nullable=False, index=True)
    script_title = Column(String(255))
    script_version = Column(String(50))
    schema_hash = Column(String(64), nullable=False)
    schema = Column(Text, nullable=False)
    defaults = Column(Text, nullable=False)
    notes = Column(Text)
    status = Column(String(20), nullable=False, default="active")
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())

    owner = relationship("Account")


class ScriptJob(Base):
    __tablename__ = "script_jobs"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    owner_id = Column(String(36), ForeignKey("accounts.id"), nullable=False, index=True)
    template_id = Column(String(36), ForeignKey("script_templates.id"), nullable=False, index=True)
    script_name = Column(String(150), nullable=False, index=True)
    script_version = Column(String(50))
    schema_hash = Column(String(64), nullable=False)
    total_targets = Column(Integer, nullable=False, default=0)
    status = Column(String(20), nullable=False, default="pending")
    unit_price = Column(Integer, nullable=True)
    currency = Column(String(10), nullable=True)
    total_price = Column(Integer, nullable=True)
    meta = Column(Text)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())

    owner = relationship("Account")
    template = relationship("ScriptTemplate")
    targets = relationship(
        "ScriptJobTarget",
        back_populates="job",
        cascade="all, delete-orphan",
    )


class ScriptJobTarget(Base):
    __tablename__ = "script_job_targets"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    job_id = Column(String(36), ForeignKey("script_jobs.id"), nullable=False, index=True)
    device_id = Column(String(36), ForeignKey("devices.id"), nullable=False)
    command_id = Column(String(36), nullable=True, index=True)
    status = Column(String(20), nullable=False, default="pending")
    result = Column(Text)
    error_message = Column(Text)
    sent_at = Column(DateTime(timezone=True))
    completed_at = Column(DateTime(timezone=True))

    job = relationship("ScriptJob", back_populates="targets")
    device = relationship("Device")


class Wallet(Base):
    __tablename__ = "wallets"

    account_id = Column(String(36), ForeignKey("accounts.id"), primary_key=True)
    balance_cents = Column(Integer, nullable=False, default=0)
    currency = Column(String(10), nullable=False, default="CNY")
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    account = relationship("Account")
    transactions = relationship("WalletTransaction", back_populates="wallet", cascade="all, delete-orphan")


class WalletTopupOrder(Base):
    __tablename__ = "wallet_topup_orders"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    account_id = Column(String(36), ForeignKey("accounts.id"), nullable=False, index=True)
    amount_cents = Column(Integer, nullable=False)
    currency = Column(String(10), nullable=False, default="CNY")
    status = Column(String(20), nullable=False, default="pending")  # pending, success, failed
    payment_channel = Column(String(50))
    reference_no = Column(String(100))
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    confirmed_at = Column(DateTime(timezone=True))

    account = relationship("Account")


class WalletTransaction(Base):
    __tablename__ = "wallet_transactions"

    id = Column(String(36), primary_key=True, default=generate_uuid)
    account_id = Column(String(36), ForeignKey("accounts.id"), nullable=False, index=True)
    job_id = Column(String(36), ForeignKey("script_jobs.id"), nullable=True)
    amount_cents = Column(Integer, nullable=False)
    currency = Column(String(10), nullable=False, default="CNY")
    type = Column(String(20), nullable=False, default="freeze")  # freeze, capture, refund
    description = Column(String(255))
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    wallet = relationship("Wallet", back_populates="transactions")
    job = relationship("ScriptJob")
