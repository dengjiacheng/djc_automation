package com.automation.bootstrap.cli;

/**
 * Minimal placeholder console entry point kept for compatibility with older tooling.
 * Prints basic usage instructions.
 */
public final class ConsoleEntry {

    private ConsoleEntry() {
    }

    public static void main(String[] args) {
        System.out.println("Automation console ready.");
        System.out.println("Overlay helpers available via FloatView; mock location via MockLocationProvider.");
        System.out.println("For text injection, enable the ADB Keyboard IME in system settings.");
    }
}
