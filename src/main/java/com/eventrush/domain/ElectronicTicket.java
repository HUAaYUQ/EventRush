package com.eventrush.domain;

import java.time.LocalDateTime;

public record ElectronicTicket(
        Long id,
        Long orderId,
        Long passengerId,
        String passengerName,
        PassengerDocumentType passengerDocumentType,
        String passengerDocumentLast4,
        String ticketCode,
        TicketStatus status,
        LocalDateTime generatedTime,
        LocalDateTime verifiedTime,
        Long verifierId,
        LocalDateTime refundedTime
) {

    public ElectronicTicket verify(Long verifierId, LocalDateTime verifiedTime) {
        return new ElectronicTicket(id, orderId, passengerId, passengerName, passengerDocumentType,
                passengerDocumentLast4, ticketCode, TicketStatus.VERIFIED, generatedTime, verifiedTime, verifierId,
                refundedTime);
    }

    public ElectronicTicket refund(LocalDateTime refundedTime) {
        return new ElectronicTicket(id, orderId, passengerId, passengerName, passengerDocumentType,
                passengerDocumentLast4, ticketCode, TicketStatus.REFUNDED, generatedTime, verifiedTime, verifierId,
                refundedTime);
    }
}
