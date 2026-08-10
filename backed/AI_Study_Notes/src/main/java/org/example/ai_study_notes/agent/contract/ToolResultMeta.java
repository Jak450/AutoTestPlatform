package org.example.ai_study_notes.agent.contract;

/**
 * 工具结果结构化元数据，对应契约 toolResultMeta。
 * 前端按元数据渲染，不解析文本。
 */
public final class ToolResultMeta {

    public enum Status {
        SUCCESS("success"),
        ERROR("error"),
        PARTIAL_SUCCESS("partial_success");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public enum ErrorType {
        AUTH("auth"),
        RATE_LIMITED("rate_limited"),
        TRANSIENT("transient"),
        CONFIG("config"),
        PERMISSION("permission"),
        NO_RESULTS("no_results"),
        NOT_FOUND("not_found"),
        INTERNAL("internal"),
        TEST_FAILED("test_failed"),
        TEST_ERROR("test_error"),
        TEST_TIMEOUT("test_timeout"),
        TEST_FLAKY("test_flaky"),
        UNKNOWN("unknown");

        private final String value;

        ErrorType(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public enum RecommendedNextAction {
        CONTINUE("continue"),
        REWRITE_QUERY("rewrite_query"),
        TRY_ALTERNATIVE("try_alternative"),
        SUMMARIZE("summarize"),
        STOP("stop");

        private final String value;

        RecommendedNextAction(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public enum Source {
        EXCEPTION("exception"),
        TOOL_RETURN("tool_return"),
        CONTENT_ANALYSIS("content_analysis");

        private final String value;

        Source(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    private ToolResultMeta() {
    }
}
