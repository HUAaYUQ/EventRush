package com.eventrush.domain;

public record TicketCategory(
        Long id,
        Long sessionId,
        String name,
        int totalStock,
        int remainingStock
) {

    public TicketCategory deductOne() {
        return new TicketCategory(id, sessionId, name, totalStock, remainingStock - 1);
    }
}
