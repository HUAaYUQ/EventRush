package com.eventrush.domain;

import java.time.LocalDateTime;

public record TicketOrder(
        Long id,
        Long userId,
        Long eventId,
        Long sessionId,
        Long ticketCategoryId,
        long unitPriceCents,
        long amountCents,
        int quantity,
        String passengerName,
        PassengerDocumentType passengerDocumentType,
        String passengerDocumentLast4,
        OrderStatus status,
        LocalDateTime createdTime,
        LocalDateTime payTime,
        LocalDateTime cancelTime,
        LocalDateTime expireTime
) {

    public TicketOrder paid(LocalDateTime payTime) {
        return new TicketOrder(id, userId, eventId, sessionId, ticketCategoryId, unitPriceCents, amountCents,
                quantity, passengerName, passengerDocumentType, passengerDocumentLast4, OrderStatus.PAID,
                createdTime, payTime, cancelTime, expireTime);
    }

    public TicketOrder canceled(LocalDateTime cancelTime) {
        return new TicketOrder(id, userId, eventId, sessionId, ticketCategoryId, unitPriceCents, amountCents,
                quantity, passengerName, passengerDocumentType, passengerDocumentLast4, OrderStatus.CANCELED,
                createdTime, payTime, cancelTime, expireTime);
    }
}
