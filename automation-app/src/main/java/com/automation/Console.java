package com.automation;

import com.automation.bootstrap.cli.ConsoleEntry;

/**
 * 保留旧入口，内部委托给新的 cli 包实现。
 */
public final class Console {

    private Console() {
    }

    public static void main(String[] args) {
        ConsoleEntry.main(args);
    }
}
