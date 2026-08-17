package com.eventrush.domain;

import java.time.LocalDateTime;
import java.util.List;

public record EventProductDraft(
        String name,
        Long categoryId,
        String city,
        String venueName,
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
        List<EventRuleDraft> rules,
        List<EventDetailSectionDraft> detailSections
) {
}
