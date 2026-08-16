package com.eventrush.domain;

import java.time.LocalDateTime;

public record OrganizerOrderSummary(
        Long id,
        Long userId,
        Long sessionId,
        Long ticketCategoryId,
        String ticketCategoryName,
        LocalDateTime sessionStartTime,
        int quantity,
        int refundedQuantity,
        long amountCents,
        OrderStatus status,
        LocalDateTime createdTime,
        LocalDateTime payTime,
        int issuedTicketCount,
        int refundedTicketCount
) {
}
