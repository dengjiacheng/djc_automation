"""add wallet and job related tables

Revision ID: a1b2c3d4e5f6
Revises: 
Create Date: 2025-10-19 02:05:00.000000
"""

from __future__ import annotations

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = "a1b2c3d4e5f6"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "script_templates",
        sa.Column("id", sa.String(length=36), primary_key=True),
        sa.Column("owner_id", sa.String(length=36), sa.ForeignKey("accounts.id"), nullable=False),
        sa.Column("script_name", sa.String(length=150), nullable=False),
        sa.Column("script_title", sa.String(length=255)),
        sa.Column("script_version", sa.String(length=50)),
        sa.Column("schema_hash", sa.String(length=64), nullable=False),
        sa.Column("schema", sa.Text(), nullable=False),
        sa.Column("defaults", sa.Text(), nullable=False),
        sa.Column("notes", sa.Text()),
        sa.Column("status", sa.String(length=20), nullable=False, server_default="active"),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime(timezone=True)),
    )
    op.create_index("ix_script_templates_owner_id", "script_templates", ["owner_id"])
    op.create_index("ix_script_templates_script_name", "script_templates", ["script_name"])

    op.create_table(
        "script_jobs",
        sa.Column("id", sa.String(length=36), primary_key=True),
        sa.Column("owner_id", sa.String(length=36), sa.ForeignKey("accounts.id"), nullable=False),
        sa.Column("template_id", sa.String(length=36), sa.ForeignKey("script_templates.id"), nullable=False),
        sa.Column("script_name", sa.String(length=150), nullable=False),
        sa.Column("script_version", sa.String(length=50)),
        sa.Column("schema_hash", sa.String(length=64), nullable=False),
        sa.Column("total_targets", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("status", sa.String(length=20), nullable=False, server_default="pending"),
        sa.Column("unit_price", sa.Integer()),
        sa.Column("currency", sa.String(length=10)),
        sa.Column("total_price", sa.Integer()),
        sa.Column("meta", sa.Text()),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime(timezone=True)),
    )
    op.create_index("ix_script_jobs_owner_id", "script_jobs", ["owner_id"])
    op.create_index("ix_script_jobs_template_id", "script_jobs", ["template_id"])
    op.create_index("ix_script_jobs_script_name", "script_jobs", ["script_name"])

    op.create_table(
        "wallets",
        sa.Column("account_id", sa.String(length=36), sa.ForeignKey("accounts.id"), primary_key=True),
        sa.Column("balance_cents", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("currency", sa.String(length=10), nullable=False, server_default="CNY"),
        sa.Column("updated_at", sa.DateTime(timezone=True)),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now()),
    )

    op.create_table(
        "wallet_topup_orders",
        sa.Column("id", sa.String(length=36), primary_key=True),
        sa.Column("account_id", sa.String(length=36), sa.ForeignKey("accounts.id"), nullable=False),
        sa.Column("amount_cents", sa.Integer(), nullable=False),
        sa.Column("currency", sa.String(length=10), nullable=False, server_default="CNY"),
        sa.Column("status", sa.String(length=20), nullable=False, server_default="pending"),
        sa.Column("payment_channel", sa.String(length=50)),
        sa.Column("reference_no", sa.String(length=100)),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now()),
        sa.Column("confirmed_at", sa.DateTime(timezone=True)),
    )
    op.create_index("ix_wallet_topup_orders_account_id", "wallet_topup_orders", ["account_id"])

    op.create_table(
        "script_job_targets",
        sa.Column("id", sa.String(length=36), primary_key=True),
        sa.Column("job_id", sa.String(length=36), sa.ForeignKey("script_jobs.id"), nullable=False),
        sa.Column("device_id", sa.String(length=36), sa.ForeignKey("devices.id"), nullable=False),
        sa.Column("command_id", sa.String(length=36)),
        sa.Column("status", sa.String(length=20), nullable=False, server_default="pending"),
        sa.Column("result", sa.Text()),
        sa.Column("error_message", sa.Text()),
        sa.Column("sent_at", sa.DateTime(timezone=True)),
        sa.Column("completed_at", sa.DateTime(timezone=True)),
    )
    op.create_index("ix_script_job_targets_job_id", "script_job_targets", ["job_id"])
    op.create_index("ix_script_job_targets_command_id", "script_job_targets", ["command_id"])

    op.create_table(
        "wallet_transactions",
        sa.Column("id", sa.String(length=36), primary_key=True),
        sa.Column("account_id", sa.String(length=36), sa.ForeignKey("wallets.account_id"), nullable=False),
        sa.Column("job_id", sa.String(length=36), sa.ForeignKey("script_jobs.id")),
        sa.Column("amount_cents", sa.Integer(), nullable=False),
        sa.Column("currency", sa.String(length=10), nullable=False, server_default="CNY"),
        sa.Column("type", sa.String(length=20), nullable=False, server_default="freeze"),
        sa.Column("description", sa.String(length=255)),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now()),
    )
    op.create_index("ix_wallet_transactions_account_id", "wallet_transactions", ["account_id"])
    op.create_index("ix_wallet_transactions_job_id", "wallet_transactions", ["job_id"])


def downgrade() -> None:
    op.drop_index("ix_wallet_transactions_job_id", table_name="wallet_transactions")
    op.drop_index("ix_wallet_transactions_account_id", table_name="wallet_transactions")
    op.drop_table("wallet_transactions")

    op.drop_index("ix_script_job_targets_command_id", table_name="script_job_targets")
    op.drop_index("ix_script_job_targets_job_id", table_name="script_job_targets")
    op.drop_table("script_job_targets")

    op.drop_index("ix_wallet_topup_orders_account_id", table_name="wallet_topup_orders")
    op.drop_table("wallet_topup_orders")

    op.drop_table("wallets")

    op.drop_index("ix_script_jobs_script_name", table_name="script_jobs")
    op.drop_index("ix_script_jobs_template_id", table_name="script_jobs")
    op.drop_index("ix_script_jobs_owner_id", table_name="script_jobs")
    op.drop_table("script_jobs")

    op.drop_index("ix_script_templates_script_name", table_name="script_templates")
    op.drop_index("ix_script_templates_owner_id", table_name="script_templates")
    op.drop_table("script_templates")
