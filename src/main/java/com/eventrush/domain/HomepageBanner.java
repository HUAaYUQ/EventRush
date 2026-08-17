package com.eventrush.domain;

import java.time.LocalDateTime;

public record HomepageBanner(
        Long id,
        Long eventId,
        Long organizerId,
        String title,
        String subtitle,
        String imageUrl,
        String city,
        LocalDateTime displayStartTime,
        LocalDateTime displayEndTime,
        int displayOrder,
        String status,
        LocalDateTime createdTime,
        LocalDateTime updatedTime,
        LocalDateTime publishedTime
) {
}
