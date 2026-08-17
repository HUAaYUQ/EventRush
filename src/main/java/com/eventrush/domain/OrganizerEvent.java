package com.eventrush.domain;

import java.time.LocalDateTime;
import java.util.List;

public record OrganizerEvent(
        Long id,
        Long organizerId,
        String name,
        String location,
        Long categoryId,
        String categoryName,
        String contentProfile,
        String city,
        String venueAddress,
        String description,
        String posterUrl,
        int durationMinutes,
        LocalDateTime saleStartTime,
        LocalDateTime saleEndTime,
        int purchaseLimit,
        String realNameRule,
        String entryMethod,
        String refundRule,
        boolean waitlistEnabled,
        String status,
        boolean hasUnpublishedChanges,
        LocalDateTime createdTime,
        LocalDateTime updatedTime,
        LocalDateTime publishedTime,
        List<EventSession> sessions,
        List<EventRule> rules,
        List<EventDetailSection> detailSections,
        List<OrganizerNotice> notices
) {
}
