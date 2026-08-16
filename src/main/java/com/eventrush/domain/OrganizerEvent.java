package com.eventrush.domain;

import java.time.LocalDateTime;
import java.util.List;

public record OrganizerEvent(
        Long id,
        Long organizerId,
        String name,
        String location,
        String description,
        String posterUrl,
        String status,
        LocalDateTime createdTime,
        LocalDateTime updatedTime,
        LocalDateTime publishedTime,
        List<EventSession> sessions,
        List<OrganizerNotice> notices
) {
}
