package com.eventrush.service;

import com.eventrush.domain.ElectronicTicket;
import com.eventrush.domain.PassengerDocumentType;
import com.eventrush.domain.TicketStatus;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
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

    public ElectronicTicket create(Long orderId, Long passengerId, String ticketCode, LocalDateTime generatedTime) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO electronic_ticket
                            (order_id, passenger_id, ticket_code, ticket_status, generated_time)
                        VALUES (?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, orderId);
                statement.setLong(2, passengerId);
                statement.setString(3, ticketCode);
                statement.setString(4, TicketStatus.VALID.name());
                statement.setTimestamp(5, Timestamp.valueOf(generatedTime));
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            return findByPassengerId(passengerId)
                    .orElseThrow(() -> new BusinessException("ticket creation failed"));
        }
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException("ticket creation failed");
        }
        return findByCode(ticketCode).orElseThrow(() -> new BusinessException("ticket creation failed"));
    }

    public Optional<ElectronicTicket> findByCode(String ticketCode) {
        return jdbcTemplate.query("""
                        SELECT ticket.id, ticket.order_id, ticket.passenger_id, passenger.passenger_name,
                               passenger.passenger_document_type, passenger.passenger_document_last4,
                               ticket.ticket_code, ticket.ticket_status, ticket.generated_time,
                               ticket.verified_time, ticket.verifier_id
                        FROM electronic_ticket ticket
                        JOIN ticket_order_passenger passenger ON passenger.id = ticket.passenger_id
                        WHERE ticket.ticket_code = ?
                        """,
                (resultSet, rowNumber) -> mapTicket(resultSet),
                ticketCode
        ).stream().findFirst();
    }

    public List<ElectronicTicket> findByOrderId(Long orderId) {
        return jdbcTemplate.query("""
                        SELECT ticket.id, ticket.order_id, ticket.passenger_id, passenger.passenger_name,
                               passenger.passenger_document_type, passenger.passenger_document_last4,
                               ticket.ticket_code, ticket.ticket_status, ticket.generated_time,
                               ticket.verified_time, ticket.verifier_id
                        FROM electronic_ticket ticket
                        JOIN ticket_order_passenger passenger ON passenger.id = ticket.passenger_id
                        WHERE ticket.order_id = ?
                        ORDER BY passenger.passenger_sequence
                        """,
                (resultSet, rowNumber) -> mapTicket(resultSet),
                orderId
        );
    }

    private Optional<ElectronicTicket> findByPassengerId(Long passengerId) {
        return jdbcTemplate.query("""
                        SELECT ticket.id, ticket.order_id, ticket.passenger_id, passenger.passenger_name,
                               passenger.passenger_document_type, passenger.passenger_document_last4,
                               ticket.ticket_code, ticket.ticket_status, ticket.generated_time,
                               ticket.verified_time, ticket.verifier_id
                        FROM electronic_ticket ticket
                        JOIN ticket_order_passenger passenger ON passenger.id = ticket.passenger_id
                        WHERE ticket.passenger_id = ?
                        """,
                (resultSet, rowNumber) -> mapTicket(resultSet),
                passengerId
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

    private ElectronicTicket mapTicket(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new ElectronicTicket(
                resultSet.getLong("id"),
                resultSet.getLong("order_id"),
                resultSet.getLong("passenger_id"),
                resultSet.getString("passenger_name"),
                PassengerDocumentType.valueOf(resultSet.getString("passenger_document_type")),
                resultSet.getString("passenger_document_last4"),
                resultSet.getString("ticket_code"),
                TicketStatus.valueOf(resultSet.getString("ticket_status")),
                resultSet.getObject("generated_time", LocalDateTime.class),
                resultSet.getObject("verified_time", LocalDateTime.class),
                resultSet.getObject("verifier_id", Long.class)
        );
    }
}
