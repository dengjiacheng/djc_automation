"""
初始化默认账号
创建device_android_001账号用于测试
"""
import asyncio
from app.db.session import get_db, init_db
from app.domain.accounts import AccountCreateInput, AccountService


async def create_default_account():
    """创建默认账号"""
    # 初始化数据库
    await init_db()

    # 获取数据库会话
    async for db in get_db():
        service = AccountService.with_session(db)

        existing = await service.get_by_username("device_android_001")
        if existing:
            print("默认账号已存在")
            return

        await service.create_account(
            AccountCreateInput(
                username="device_android_001",
                password="pass123",
                role="user",
                email=None,
                is_active=True,
            )
        )
        await db.commit()

        print("默认账号创建成功: device_android_001 / pass123")


if __name__ == "__main__":
    asyncio.run(create_default_account())
