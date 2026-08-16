package com.eventrush.domain;

public record TicketCategory(
        Long id,
        Long sessionId,
        String name,
        long priceCents,
        int totalStock,
        int remainingStock
) {

    public TicketCategory deduct(int quantity) {
        return new TicketCategory(id, sessionId, name, priceCents, totalStock, remainingStock - quantity);
    }

    public TicketCategory release(int quantity) {
        return new TicketCategory(id, sessionId, name, priceCents, totalStock,
                Math.min(totalStock, remainingStock + quantity));
    }
}
