package com.automation.domain.scenario;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 脚本互斥锁，确保同一时间仅有一个任务执行。
 */
public final class ScriptRunGuard {

    private final AtomicReference<String> activeScript = new AtomicReference<>();

    /**
     * 尝试占用执行权。
     *
     * @param scriptId 唯一脚本标识
     * @return 成功占用返回 true
     */
    public boolean tryAcquire(String scriptId) {
        return activeScript.compareAndSet(null, scriptId);
    }

    /**
     * 释放执行权。
     */
    public void release(String scriptId) {
        activeScript.compareAndSet(scriptId, null);
    }

    public boolean isBusy() {
        return activeScript.get() != null;
    }

    public String currentScript() {
        return activeScript.get();
    }
}
