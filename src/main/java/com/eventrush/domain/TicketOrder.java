package com.eventrush.domain;

import java.time.LocalDateTime;
import java.util.List;

public record TicketOrder(
        Long id,
        Long userId,
        Long eventId,
        Long sessionId,
        Long ticketCategoryId,
        long unitPriceCents,
        long amountCents,
        int quantity,
        List<TicketPassenger> passengers,
        OrderStatus status,
        LocalDateTime createdTime,
        LocalDateTime payTime,
        LocalDateTime cancelTime,
        LocalDateTime expireTime
) {

    public TicketOrder paid(LocalDateTime payTime) {
        return new TicketOrder(id, userId, eventId, sessionId, ticketCategoryId, unitPriceCents, amountCents,
                quantity, passengers, OrderStatus.PAID,
                createdTime, payTime, cancelTime, expireTime);
    }

    public TicketOrder canceled(LocalDateTime cancelTime) {
        return new TicketOrder(id, userId, eventId, sessionId, ticketCategoryId, unitPriceCents, amountCents,
                quantity, passengers, OrderStatus.CANCELED,
                createdTime, payTime, cancelTime, expireTime);
    }
}
