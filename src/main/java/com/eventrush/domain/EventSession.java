package com.eventrush.domain;

import java.time.LocalDateTime;
import java.util.List;

public record EventSession(
        Long id,
        Long eventId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        List<TicketCategory> ticketCategories
) {
}
