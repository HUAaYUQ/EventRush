package com.eventrush.domain;

import java.time.LocalDateTime;

public record EventCategory(
        Long id,
        String name,
        String iconKey,
        String contentProfile,
        int displayOrder,
        boolean enabled,
        LocalDateTime createdTime,
        LocalDateTime updatedTime
) {
}
