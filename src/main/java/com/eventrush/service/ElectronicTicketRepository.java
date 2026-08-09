package com.eventrush.service;

import com.eventrush.domain.ElectronicTicket;
import com.eventrush.domain.TicketStatus;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ElectronicTicketRepository {

    private final JdbcTemplate jdbcTemplate;

    public ElectronicTicketRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ElectronicTicket create(Long orderId, String ticketCode, LocalDateTime generatedTime) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO electronic_ticket
                            (order_id, ticket_code, ticket_status, generated_time)
                        VALUES (?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, orderId);
                statement.setString(2, ticketCode);
                statement.setString(3, TicketStatus.VALID.name());
                statement.setTimestamp(4, Timestamp.valueOf(generatedTime));
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            return findByOrderId(orderId).orElseThrow(() -> new BusinessException("ticket creation failed"));
        }
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException("ticket creation failed");
        }
        return findByCode(ticketCode).orElseThrow(() -> new BusinessException("ticket creation failed"));
    }

    public Optional<ElectronicTicket> findByCode(String ticketCode) {
        return jdbcTemplate.query("""
                        SELECT id, order_id, ticket_code, ticket_status, generated_time, verified_time, verifier_id
                        FROM electronic_ticket
                        WHERE ticket_code = ?
                        """,
                (resultSet, rowNumber) -> new ElectronicTicket(
                        resultSet.getLong("id"),
                        resultSet.getLong("order_id"),
                        resultSet.getString("ticket_code"),
                        TicketStatus.valueOf(resultSet.getString("ticket_status")),
                        resultSet.getObject("generated_time", LocalDateTime.class),
                        resultSet.getObject("verified_time", LocalDateTime.class),
                        resultSet.getObject("verifier_id", Long.class)
                ),
                ticketCode
        ).stream().findFirst();
    }

    public Optional<ElectronicTicket> findByOrderId(Long orderId) {
        return jdbcTemplate.query("""
                        SELECT id, order_id, ticket_code, ticket_status, generated_time, verified_time, verifier_id
                        FROM electronic_ticket
                        WHERE order_id = ?
                        """,
                (resultSet, rowNumber) -> new ElectronicTicket(
                        resultSet.getLong("id"),
                        resultSet.getLong("order_id"),
                        resultSet.getString("ticket_code"),
                        TicketStatus.valueOf(resultSet.getString("ticket_status")),
                        resultSet.getObject("generated_time", LocalDateTime.class),
                        resultSet.getObject("verified_time", LocalDateTime.class),
                        resultSet.getObject("verifier_id", Long.class)
                ),
                orderId
        ).stream().findFirst();
    }

    public ElectronicTicket markVerified(String ticketCode, Long verifierId, LocalDateTime verifiedTime) {
        int updated = jdbcTemplate.update("""
                        UPDATE electronic_ticket
                        SET ticket_status = ?, verified_time = ?, verifier_id = ?
                        WHERE ticket_code = ? AND ticket_status = ?
                        """,
                TicketStatus.VERIFIED.name(),
                Timestamp.valueOf(verifiedTime),
                verifierId,
                ticketCode,
                TicketStatus.VALID.name()
        );
        if (updated != 1) {
            throw new BusinessException("ticket has already been verified");
        }
        return findByCode(ticketCode).orElseThrow(() -> new BusinessException("ticket not found"));
    }
}
