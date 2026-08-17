package com.eventrush.domain;

public record EventDetailSection(
        Long id,
        Long eventId,
        String sectionType,
        String title,
        String content,
        String imageUrl,
        int displayOrder
) {
}
