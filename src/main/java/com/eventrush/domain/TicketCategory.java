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

    public TicketCategory releaseOne() {
        return new TicketCategory(id, sessionId, name, totalStock, Math.min(totalStock, remainingStock + 1));
    }
}
