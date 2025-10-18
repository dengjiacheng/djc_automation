# Database Migrations

The migrations directory is managed by **Alembic**. Use the provided
configuration to generate and apply schema migrations as the project evolves.

## Commands

```bash
# Generate a new migration after updating ORM models
alembic revision --autogenerate -m "describe change"

# Apply migrations to the current database
alembic upgrade head

# Downgrade one revision
alembic downgrade -1
```

The Alembic environment is configured to reuse the asynchronous SQLAlchemy
engine defined in `app.infrastructure.database.session`. For SQLite the
configuration automatically adapts to synchronous URLs when running in offline
mode.
