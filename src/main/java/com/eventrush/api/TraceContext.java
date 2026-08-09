package com.eventrush.api;

final class TraceContext {

    static final String HEADER_NAME = "X-Trace-Id";

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private TraceContext() {
    }

    static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
    }

    static String getTraceId() {
        return TRACE_ID.get();
    }

    static void clear() {
        TRACE_ID.remove();
    }
}
