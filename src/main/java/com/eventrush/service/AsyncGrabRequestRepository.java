package com.eventrush.service;

import com.eventrush.service.AsyncGrabService.GrabResult;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AsyncGrabRequestRepository {

    private static final String PROCESSING = "PROCESSING";

    private final JdbcTemplate jdbcTemplate;

    public AsyncGrabRequestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public GrabResult createPending(String requestId, Long userId, Long sessionId, Long ticketCategoryId) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                        INSERT INTO async_grab_request
                            (request_id, user_id, session_id, ticket_category_id, request_status, created_time, updated_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                requestId,
                userId,
                sessionId,
                ticketCategoryId,
                AsyncGrabService.PENDING,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
        );
        return findResult(requestId).orElseThrow(() -> new BusinessException("grab request creation failed"));
    }

    public Optional<GrabResult> findResult(String requestId) {
        return jdbcTemplate.query("""
                        SELECT request_id, request_status, order_id, error_message
                        FROM async_grab_request
                        WHERE request_id = ?
                        """,
                (resultSet, rowNumber) -> new GrabResult(
                        resultSet.getString("request_id"),
                        visibleStatus(resultSet.getString("request_status")),
                        resultSet.getObject("order_id", Long.class),
                        resultSet.getString("error_message")
                ),
                requestId
        ).stream().findFirst();
    }

    public boolean markProcessingIfPending(String requestId) {
        int updated = jdbcTemplate.update("""
                        UPDATE async_grab_request
                        SET request_status = ?, updated_time = ?
                        WHERE request_id = ? AND request_status = ?
                        """,
                PROCESSING,
                Timestamp.valueOf(LocalDateTime.now()),
                requestId,
                AsyncGrabService.PENDING
        );
        return updated == 1;
    }

    public void markSuccess(String requestId, Long orderId) {
        jdbcTemplate.update("""
                        UPDATE async_grab_request
                        SET request_status = ?, order_id = ?, error_message = NULL, updated_time = ?
                        WHERE request_id = ?
                        """,
                AsyncGrabService.SUCCESS,
                orderId,
                Timestamp.valueOf(LocalDateTime.now()),
                requestId
        );
    }

    public void markFailed(String requestId, String errorMessage) {
        jdbcTemplate.update("""
                        UPDATE async_grab_request
                        SET request_status = ?, error_message = ?, updated_time = ?
                        WHERE request_id = ?
                        """,
                AsyncGrabService.FAILED,
                errorMessage,
                Timestamp.valueOf(LocalDateTime.now()),
                requestId
        );
    }

    private static String visibleStatus(String status) {
        if (PROCESSING.equals(status)) {
            return AsyncGrabService.PENDING;
        }
        return status;
    }
}
