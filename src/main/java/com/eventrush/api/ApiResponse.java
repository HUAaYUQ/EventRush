package com.eventrush.api;

public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        String traceId
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", "success", data, TraceContext.getTraceId());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, code, message, null, TraceContext.getTraceId());
    }
}
