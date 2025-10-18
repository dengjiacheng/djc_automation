"""
初始化管理员账号
创建默认的超级管理员账号用于首次登录
"""
import asyncio
from sqlalchemy import select

from app.db.session import get_db, init_db
from app.db.models import Account
from app.modules.accounts import AccountCreateInput, AccountService


async def create_default_admin():
    """创建默认管理员账号"""
    await init_db()

    async for db in get_db():
        # 检查是否已存在管理员
        stmt = select(Account).where(Account.role.in_(["admin", "super_admin"]))
        result = await db.execute(stmt)
        existing_admin = result.scalar_one_or_none()

        if existing_admin:
            print("管理员账号已存在,无需初始化")
            return

        service = AccountService.with_session(db)

        await service.create_account(
            AccountCreateInput(
                username="admin",
                password="admin123",
                role="super_admin",
                email="admin@example.com",
                is_active=True,
            )
        )
        await db.commit()

        print("=" * 50)
        print("默认管理员账号创建成功!")
        print("=" * 50)
        print(f"用户名: admin")
        print(f"密码: admin123")
        print("=" * 50)
        print("请登录后立即修改密码!")
        print("=" * 50)


if __name__ == "__main__":
    asyncio.run(create_default_admin())
