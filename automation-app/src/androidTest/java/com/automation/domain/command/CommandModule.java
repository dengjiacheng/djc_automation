package com.automation.domain.command;

/**
 * 指令模块，实现类负责将一组相关指令注册到注册表。
 */
public interface CommandModule {
    void register(CommandRegistry registry);
}
