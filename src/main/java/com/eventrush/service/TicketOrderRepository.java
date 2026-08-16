package com.eventrush.service;

import com.eventrush.domain.OrderStatus;
import com.eventrush.domain.TicketOrder;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class TicketOrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public TicketOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TicketOrder createPending(
            Long userId,
            Long eventId,
            Long sessionId,
            Long ticketCategoryId,
            long unitPriceCents,
            LocalDateTime createdTime,
            LocalDateTime expireTime
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO ticket_order
                            (user_id, event_id, session_id, ticket_category_id, unit_price_cents, amount_cents,
                             active_grab_key, order_status, created_time, expire_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, userId);
                statement.setLong(2, eventId);
                statement.setLong(3, sessionId);
                statement.setLong(4, ticketCategoryId);
                statement.setLong(5, unitPriceCents);
                statement.setLong(6, unitPriceCents);
                statement.setString(7, activeGrabKey(userId, sessionId, ticketCategoryId));
                statement.setString(8, OrderStatus.PENDING_PAYMENT.name());
                statement.setTimestamp(9, Timestamp.valueOf(createdTime));
                statement.setTimestamp(10, Timestamp.valueOf(expireTime));
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("DUPLICATE_GRAB", HttpStatus.CONFLICT,
                    "你已有这个票档的有效订单，请前往我的电子票继续处理");
        }
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException("order creation failed");
        }
        return findById(key.longValue()).orElseThrow(() -> new BusinessException("order creation failed"));
    }

    public Optional<TicketOrder> findById(Long orderId) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, event_id, session_id, ticket_category_id, unit_price_cents, amount_cents, order_status,
                               created_time, pay_time, cancel_time, expire_time
                        FROM ticket_order
                        WHERE id = ?
                        """,
                (resultSet, rowNumber) -> new TicketOrder(
                        resultSet.getLong("id"),
                        resultSet.getLong("user_id"),
                        resultSet.getLong("event_id"),
                        resultSet.getLong("session_id"),
                        resultSet.getLong("ticket_category_id"),
                        resultSet.getLong("unit_price_cents"),
                        resultSet.getLong("amount_cents"),
                        OrderStatus.valueOf(resultSet.getString("order_status")),
                        resultSet.getObject("created_time", LocalDateTime.class),
                        resultSet.getObject("pay_time", LocalDateTime.class),
                        resultSet.getObject("cancel_time", LocalDateTime.class),
                        resultSet.getObject("expire_time", LocalDateTime.class)
                ),
                orderId
        ).stream().findFirst();
    }

    public List<TicketOrder> findExpiredPending(LocalDateTime now, int limit) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, event_id, session_id, ticket_category_id, unit_price_cents, amount_cents, order_status,
                               created_time, pay_time, cancel_time, expire_time
                        FROM ticket_order
                        WHERE order_status = ?
                          AND expire_time <= ?
                        ORDER BY expire_time
                        LIMIT ?
                        """,
                (resultSet, rowNumber) -> new TicketOrder(
                        resultSet.getLong("id"),
                        resultSet.getLong("user_id"),
                        resultSet.getLong("event_id"),
                        resultSet.getLong("session_id"),
                        resultSet.getLong("ticket_category_id"),
                        resultSet.getLong("unit_price_cents"),
                        resultSet.getLong("amount_cents"),
                        OrderStatus.valueOf(resultSet.getString("order_status")),
                        resultSet.getObject("created_time", LocalDateTime.class),
                        resultSet.getObject("pay_time", LocalDateTime.class),
                        resultSet.getObject("cancel_time", LocalDateTime.class),
                        resultSet.getObject("expire_time", LocalDateTime.class)
                ),
                OrderStatus.PENDING_PAYMENT.name(),
                Timestamp.valueOf(now),
                limit
        );
    }

    public List<TicketOrder> findByUserId(Long userId) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, event_id, session_id, ticket_category_id, unit_price_cents, amount_cents, order_status,
                               created_time, pay_time, cancel_time, expire_time
                        FROM ticket_order
                        WHERE user_id = ?
                        ORDER BY id DESC
                        """,
                (resultSet, rowNumber) -> new TicketOrder(
                        resultSet.getLong("id"),
                        resultSet.getLong("user_id"),
                        resultSet.getLong("event_id"),
                        resultSet.getLong("session_id"),
                        resultSet.getLong("ticket_category_id"),
                        resultSet.getLong("unit_price_cents"),
                        resultSet.getLong("amount_cents"),
                        OrderStatus.valueOf(resultSet.getString("order_status")),
                        resultSet.getObject("created_time", LocalDateTime.class),
                        resultSet.getObject("pay_time", LocalDateTime.class),
                        resultSet.getObject("cancel_time", LocalDateTime.class),
                        resultSet.getObject("expire_time", LocalDateTime.class)
                ),
                userId
        );
    }

    public boolean existsActiveGrab(Long userId, Long sessionId, Long ticketCategoryId) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM ticket_order
                        WHERE user_id = ?
                          AND session_id = ?
                          AND ticket_category_id = ?
                          AND order_status <> ?
                        """,
                Integer.class,
                userId,
                sessionId,
                ticketCategoryId,
                OrderStatus.CANCELED.name()
        );
        return count != null && count > 0;
    }

    public TicketOrder markPaid(Long orderId, LocalDateTime payTime) {
        int updated = jdbcTemplate.update("""
                        UPDATE ticket_order
                        SET order_status = ?, pay_time = ?
                        WHERE id = ? AND order_status = ?
                        """,
                OrderStatus.PAID.name(),
                Timestamp.valueOf(payTime),
                orderId,
                OrderStatus.PENDING_PAYMENT.name()
        );
        if (updated != 1) {
            throw new BusinessException("ORDER_NOT_PAYABLE", HttpStatus.CONFLICT,
                    "当前订单状态不能支付，请返回订单列表查看最新状态");
        }
        return findById(orderId).orElseThrow(() -> new BusinessException("order not found"));
    }

    public boolean markCanceledIfPending(Long orderId, LocalDateTime cancelTime) {
        int updated = jdbcTemplate.update("""
                        UPDATE ticket_order
                        SET order_status = ?, cancel_time = ?, active_grab_key = NULL
                        WHERE id = ? AND order_status = ?
                        """,
                OrderStatus.CANCELED.name(),
                Timestamp.valueOf(cancelTime),
                orderId,
                OrderStatus.PENDING_PAYMENT.name()
        );
        return updated == 1;
    }

    private String activeGrabKey(Long userId, Long sessionId, Long ticketCategoryId) {
        return "%d:%d:%d".formatted(userId, sessionId, ticketCategoryId);
    }
}
