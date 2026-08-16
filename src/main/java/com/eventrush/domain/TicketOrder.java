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
        int refundedQuantity,
        long refundedAmountCents,
        List<TicketPassenger> passengers,
        OrderStatus status,
        LocalDateTime createdTime,
        LocalDateTime payTime,
        LocalDateTime cancelTime,
        LocalDateTime refundTime,
        LocalDateTime expireTime
) {

    public TicketOrder paid(LocalDateTime payTime) {
        return new TicketOrder(id, userId, eventId, sessionId, ticketCategoryId, unitPriceCents, amountCents,
                quantity, refundedQuantity, refundedAmountCents, passengers, OrderStatus.PAID,
                createdTime, payTime, cancelTime, refundTime, expireTime);
    }

    public TicketOrder canceled(LocalDateTime cancelTime) {
        return new TicketOrder(id, userId, eventId, sessionId, ticketCategoryId, unitPriceCents, amountCents,
                quantity, refundedQuantity, refundedAmountCents, passengers, OrderStatus.CANCELED,
                createdTime, payTime, cancelTime, refundTime, expireTime);
    }

    public TicketOrder refunded(int newlyRefundedQuantity, LocalDateTime refundedTime) {
        int nextRefundedQuantity = Math.min(quantity, refundedQuantity + newlyRefundedQuantity);
        OrderStatus nextStatus = nextRefundedQuantity == quantity
                ? OrderStatus.REFUNDED : OrderStatus.PARTIALLY_REFUNDED;
        return new TicketOrder(id, userId, eventId, sessionId, ticketCategoryId, unitPriceCents, amountCents,
                quantity, nextRefundedQuantity, unitPriceCents * nextRefundedQuantity, passengers, nextStatus,
                createdTime, payTime, cancelTime, refundedTime, expireTime);
    }
}
