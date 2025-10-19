"""add device_name to script_job_targets

Revision ID: 20251020_01
Revises: 20251019_01_add_wallet_and_jobs
Create Date: 2025-10-20 04:45:00.000000
"""

from __future__ import annotations

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = "20251020_01"
down_revision = "20251019_01_add_wallet_and_jobs"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("script_job_targets", sa.Column("device_name", sa.String(length=100), nullable=True))


def downgrade() -> None:
    op.drop_column("script_job_targets", "device_name")
