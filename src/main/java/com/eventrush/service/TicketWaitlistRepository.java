package com.eventrush.service;

import com.eventrush.domain.PassengerDocumentType;
import com.eventrush.domain.TicketPassenger;
import com.eventrush.domain.TicketWaitlistRequest;
import com.eventrush.domain.WaitlistStatus;
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
public class TicketWaitlistRepository {

    private static final String SELECT_COLUMNS = """
            id, user_id, event_id, session_id, ticket_category_id, unit_price_cents, quantity,
            waitlist_status, order_id, created_time, updated_time, fulfilled_time, canceled_time,
            expired_time, payment_expire_time
            """;

    private final JdbcTemplate jdbcTemplate;

    public TicketWaitlistRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TicketWaitlistRequest create(
            Long userId,
            Long eventId,
            Long sessionId,
            Long ticketCategoryId,
            long unitPriceCents,
            List<TicketPassenger> passengers,
            LocalDateTime createdTime
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO ticket_waitlist
                            (user_id, event_id, session_id, ticket_category_id, unit_price_cents, quantity,
                             active_waitlist_key, waitlist_status, created_time, updated_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, userId);
                statement.setLong(2, eventId);
                statement.setLong(3, sessionId);
                statement.setLong(4, ticketCategoryId);
                statement.setLong(5, unitPriceCents);
                statement.setInt(6, passengers.size());
                statement.setString(7, activeWaitlistKey(userId, sessionId, ticketCategoryId));
                statement.setString(8, WaitlistStatus.WAITING.name());
                statement.setTimestamp(9, Timestamp.valueOf(createdTime));
                statement.setTimestamp(10, Timestamp.valueOf(createdTime));
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("DUPLICATE_WAITLIST", HttpStatus.CONFLICT,
                    "你已在这个票档的候补队列中，请前往我的候补查看进度");
        }
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException("WAITLIST_CREATE_FAILED", HttpStatus.INTERNAL_SERVER_ERROR,
                    "候补提交失败，请稍后重试");
        }
        insertPassengers(key.longValue(), passengers);
        return findById(key.longValue()).orElseThrow(() -> new BusinessException("候补记录创建失败"));
    }

    private void insertPassengers(Long waitlistId, List<TicketPassenger> passengers) {
        for (TicketPassenger passenger : passengers) {
            jdbcTemplate.update("""
                            INSERT INTO ticket_waitlist_passenger
                                (waitlist_id, passenger_sequence, passenger_name, passenger_document_type,
                                 passenger_document_last4)
                            VALUES (?, ?, ?, ?, ?)
                            """,
                    waitlistId,
                    passenger.sequence(),
                    passenger.name(),
                    passenger.documentType().name(),
                    passenger.documentLast4()
            );
        }
    }

    public Optional<TicketWaitlistRequest> findById(Long waitlistId) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM ticket_waitlist WHERE id = ?",
                (resultSet, rowNumber) -> map(resultSet),
                waitlistId
        ).stream().findFirst().map(this::withDetails);
    }

    public List<TicketWaitlistRequest> findByUserId(Long userId) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM ticket_waitlist WHERE user_id = ? ORDER BY id DESC",
                (resultSet, rowNumber) -> map(resultSet),
                userId
        ).stream().map(this::withDetails).toList();
    }

    public Optional<TicketWaitlistRequest> findFirstWaitingForUpdate(Long sessionId, Long ticketCategoryId) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM ticket_waitlist "
                        + "WHERE session_id = ? AND ticket_category_id = ? AND waitlist_status = ? "
                        + "ORDER BY created_time, id LIMIT 1 FOR UPDATE",
                (resultSet, rowNumber) -> map(resultSet),
                sessionId,
                ticketCategoryId,
                WaitlistStatus.WAITING.name()
        ).stream().findFirst().map(this::withDetails);
    }

    public boolean existsActive(Long userId, Long sessionId, Long ticketCategoryId) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM ticket_waitlist
                        WHERE user_id = ? AND session_id = ? AND ticket_category_id = ?
                          AND active_waitlist_key IS NOT NULL
                        """,
                Integer.class,
                userId,
                sessionId,
                ticketCategoryId
        );
        return count != null && count > 0;
    }

    public boolean existsWaiting(Long sessionId, Long ticketCategoryId) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM ticket_waitlist
                        WHERE session_id = ? AND ticket_category_id = ? AND waitlist_status = ?
                        """,
                Integer.class,
                sessionId,
                ticketCategoryId,
                WaitlistStatus.WAITING.name()
        );
        return count != null && count > 0;
    }

    public boolean markFulfilledIfWaiting(
            Long waitlistId,
            Long orderId,
            LocalDateTime fulfilledTime,
            LocalDateTime paymentExpireTime
    ) {
        return jdbcTemplate.update("""
                        UPDATE ticket_waitlist
                        SET waitlist_status = ?, order_id = ?, fulfilled_time = ?, payment_expire_time = ?,
                            updated_time = ?, active_waitlist_key = NULL
                        WHERE id = ? AND waitlist_status = ?
                        """,
                WaitlistStatus.FULFILLED.name(),
                orderId,
                Timestamp.valueOf(fulfilledTime),
                Timestamp.valueOf(paymentExpireTime),
                Timestamp.valueOf(fulfilledTime),
                waitlistId,
                WaitlistStatus.WAITING.name()
        ) == 1;
    }

    public boolean markCanceledIfWaiting(Long waitlistId, LocalDateTime canceledTime) {
        return jdbcTemplate.update("""
                        UPDATE ticket_waitlist
                        SET waitlist_status = ?, canceled_time = ?, updated_time = ?, active_waitlist_key = NULL
                        WHERE id = ? AND waitlist_status = ?
                        """,
                WaitlistStatus.CANCELED.name(),
                Timestamp.valueOf(canceledTime),
                Timestamp.valueOf(canceledTime),
                waitlistId,
                WaitlistStatus.WAITING.name()
        ) == 1;
    }

    public boolean markExpiredIfWaiting(Long waitlistId, LocalDateTime expiredTime) {
        return jdbcTemplate.update("""
                        UPDATE ticket_waitlist
                        SET waitlist_status = ?, expired_time = ?, updated_time = ?, active_waitlist_key = NULL
                        WHERE id = ? AND waitlist_status = ?
                        """,
                WaitlistStatus.EXPIRED.name(),
                Timestamp.valueOf(expiredTime),
                Timestamp.valueOf(expiredTime),
                waitlistId,
                WaitlistStatus.WAITING.name()
        ) == 1;
    }

    private TicketWaitlistRequest map(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new TicketWaitlistRequest(
                resultSet.getLong("id"),
                resultSet.getLong("user_id"),
                resultSet.getLong("event_id"),
                resultSet.getLong("session_id"),
                resultSet.getLong("ticket_category_id"),
                resultSet.getLong("unit_price_cents"),
                resultSet.getInt("quantity"),
                List.of(),
                WaitlistStatus.valueOf(resultSet.getString("waitlist_status")),
                0,
                nullableLong(resultSet, "order_id"),
                resultSet.getObject("created_time", LocalDateTime.class),
                resultSet.getObject("updated_time", LocalDateTime.class),
                resultSet.getObject("fulfilled_time", LocalDateTime.class),
                resultSet.getObject("canceled_time", LocalDateTime.class),
                resultSet.getObject("expired_time", LocalDateTime.class),
                resultSet.getObject("payment_expire_time", LocalDateTime.class)
        );
    }

    private TicketWaitlistRequest withDetails(TicketWaitlistRequest request) {
        return new TicketWaitlistRequest(
                request.id(), request.userId(), request.eventId(), request.sessionId(), request.ticketCategoryId(),
                request.unitPriceCents(), request.quantity(), findPassengers(request.id()), request.status(),
                countWaitingAhead(request), request.orderId(), request.createdTime(), request.updatedTime(),
                request.fulfilledTime(), request.canceledTime(), request.expiredTime(), request.paymentExpireTime()
        );
    }

    private List<TicketPassenger> findPassengers(Long waitlistId) {
        return jdbcTemplate.query("""
                        SELECT id, waitlist_id, passenger_sequence, passenger_name, passenger_document_type,
                               passenger_document_last4
                        FROM ticket_waitlist_passenger
                        WHERE waitlist_id = ?
                        ORDER BY passenger_sequence
                        """,
                (resultSet, rowNumber) -> new TicketPassenger(
                        resultSet.getLong("id"),
                        resultSet.getLong("waitlist_id"),
                        resultSet.getInt("passenger_sequence"),
                        resultSet.getString("passenger_name"),
                        PassengerDocumentType.valueOf(resultSet.getString("passenger_document_type")),
                        resultSet.getString("passenger_document_last4")
                ),
                waitlistId
        );
    }

    private int countWaitingAhead(TicketWaitlistRequest request) {
        if (request.status() != WaitlistStatus.WAITING) {
            return 0;
        }
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM ticket_waitlist
                        WHERE session_id = ? AND ticket_category_id = ? AND waitlist_status = ?
                          AND (created_time < ? OR (created_time = ? AND id < ?))
                        """,
                Integer.class,
                request.sessionId(),
                request.ticketCategoryId(),
                WaitlistStatus.WAITING.name(),
                Timestamp.valueOf(request.createdTime()),
                Timestamp.valueOf(request.createdTime()),
                request.id()
        );
        return count == null ? 0 : count;
    }

    private Long nullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private String activeWaitlistKey(Long userId, Long sessionId, Long ticketCategoryId) {
        return "%d:%d:%d".formatted(userId, sessionId, ticketCategoryId);
    }
}
