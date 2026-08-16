package com.eventrush.domain;

public record TicketCategory(
        Long id,
        Long sessionId,
        String name,
        long priceCents,
        int totalStock,
        int remainingStock
) {

    public TicketCategory deductOne() {
        return new TicketCategory(id, sessionId, name, priceCents, totalStock, remainingStock - 1);
    }

    public TicketCategory releaseOne() {
        return new TicketCategory(id, sessionId, name, priceCents, totalStock, Math.min(totalStock, remainingStock + 1));
    }
}
