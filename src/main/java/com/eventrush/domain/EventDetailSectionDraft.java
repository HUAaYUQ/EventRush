package com.eventrush.domain;

public record EventDetailSectionDraft(
        String sectionType,
        String title,
        String content,
        String imageUrl,
        int displayOrder
) {
}
