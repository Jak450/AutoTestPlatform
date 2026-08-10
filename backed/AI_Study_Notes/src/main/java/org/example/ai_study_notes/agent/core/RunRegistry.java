package org.example.ai_study_notes.agent.core;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;

/**
 * 会话运行状态注册表：取消信号与运行标记。
 */
@Component
public class RunRegistry {

    private final ConcurrentMap<Long, RunHandle> runs = new ConcurrentHashMap<>();

    public RunHandle start(Long conversationId) {
        RunHandle handle = new RunHandle();
        runs.put(conversationId, handle);
        return handle;
    }

    public RunHandle get(Long conversationId) {
        return runs.get(conversationId);
    }

    public void cancel(Long conversationId) {
        RunHandle handle = runs.get(conversationId);
        if (handle != null) {
            handle.cancel();
        }
    }

    public boolean isCancelled(Long conversationId) {
        RunHandle handle = runs.get(conversationId);
        return handle != null && handle.isCancelled();
    }

    public boolean isRunning(Long conversationId) {
        return runs.containsKey(conversationId);
    }

    public void finish(Long conversationId) {
        runs.remove(conversationId);
    }

    public static class RunHandle {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final String traceId = UUID.randomUUID().toString();

        public void cancel() {
            cancelled.set(true);
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        public String traceId() {
            return traceId;
        }
    }
}
