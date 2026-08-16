package com.eventrush.domain;

import java.time.LocalDateTime;
import java.util.List;

public record TicketWaitlistRequest(
        Long id,
        Long userId,
        Long eventId,
        Long sessionId,
        Long ticketCategoryId,
        long unitPriceCents,
        int quantity,
        List<TicketPassenger> passengers,
        WaitlistStatus status,
        int waitingAhead,
        Long orderId,
        LocalDateTime createdTime,
        LocalDateTime updatedTime,
        LocalDateTime fulfilledTime,
        LocalDateTime canceledTime,
        LocalDateTime expiredTime,
        LocalDateTime paymentExpireTime
) {
}
