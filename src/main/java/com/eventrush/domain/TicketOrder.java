package com.eventrush.domain;

import java.time.LocalDateTime;

public record TicketOrder(
        Long id,
        Long userId,
        Long eventId,
        Long sessionId,
        Long ticketCategoryId,
        OrderStatus status,
        LocalDateTime createdTime,
        LocalDateTime payTime,
        LocalDateTime cancelTime,
        LocalDateTime expireTime
) {

    public TicketOrder paid(LocalDateTime payTime) {
        return new TicketOrder(id, userId, eventId, sessionId, ticketCategoryId, OrderStatus.PAID,
                createdTime, payTime, cancelTime, expireTime);
    }
}
