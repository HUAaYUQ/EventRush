package com.eventrush.domain;

import java.time.LocalDateTime;

public record OrganizerNotice(
        Long id,
        Long eventId,
        String title,
        String content,
        String status,
        LocalDateTime createdTime,
        LocalDateTime publishedTime
) {
}
