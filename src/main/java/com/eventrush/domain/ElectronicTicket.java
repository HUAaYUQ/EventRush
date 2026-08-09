package com.eventrush.domain;

import java.time.LocalDateTime;

public record ElectronicTicket(
        Long id,
        Long orderId,
        String ticketCode,
        TicketStatus status,
        LocalDateTime generatedTime,
        LocalDateTime verifiedTime,
        Long verifierId
) {

    public ElectronicTicket verify(Long verifierId, LocalDateTime verifiedTime) {
        return new ElectronicTicket(id, orderId, ticketCode, TicketStatus.VERIFIED, generatedTime, verifiedTime, verifierId);
    }
}
