"""add template assets table

Revision ID: 20251020_02
Revises: 20251020_01_add_device_name_to_script_job_targets
Create Date: 2025-10-20 05:05:00.000000
"""

from __future__ import annotations

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = "20251020_02"
down_revision = "20251020_01_add_device_name_to_script_job_targets"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "template_assets",
        sa.Column("id", sa.String(length=36), primary_key=True),
        sa.Column("owner_id", sa.String(length=36), nullable=False),
        sa.Column("file_name", sa.String(length=255), nullable=False),
        sa.Column("content_type", sa.String(length=100), nullable=True),
        sa.Column("size_bytes", sa.Integer(), nullable=False),
        sa.Column("checksum_sha256", sa.String(length=64), nullable=False),
        sa.Column("storage_path", sa.String(length=500), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now()),
        sa.ForeignKeyConstraint(["owner_id"], ["accounts.id"], ondelete="CASCADE"),
    )
    op.create_index("ix_template_assets_owner_id", "template_assets", ["owner_id"])


def downgrade() -> None:
    op.drop_index("ix_template_assets_owner_id", table_name="template_assets")
    op.drop_table("template_assets")
