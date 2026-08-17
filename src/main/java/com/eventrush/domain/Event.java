package com.eventrush.domain;

import java.time.LocalDateTime;
import java.util.List;

public record Event(
        Long id,
        String name,
        String location,
        Long categoryId,
        String categoryName,
        String city,
        String venueAddress,
        String status,
        String saleStatus,
        LocalDateTime saleStartTime,
        LocalDateTime saleEndTime,
        int durationMinutes,
        int purchaseLimit,
        String realNameRule,
        String entryMethod,
        String refundRule,
        boolean waitlistEnabled,
        List<EventSession> sessions,
        String description,
        String posterUrl,
        List<OrganizerNotice> notices
) {
    public Event(Long id, String name, String location, String status, List<EventSession> sessions) {
        this(id, name, location, 1L, "其他", "", "", status, "ON_SALE",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30), 0, 5,
                "REQUIRED", "E_TICKET", "以活动公布的退票规则为准", true,
                sessions, "", "", List.of());
    }

    public Event(
            Long id,
            String name,
            String location,
            String status,
            List<EventSession> sessions,
            String description,
            String posterUrl,
            List<OrganizerNotice> notices
    ) {
        this(id, name, location, 1L, "其他", "", "", status, "ON_SALE",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30), 0, 5,
                "REQUIRED", "E_TICKET", "以活动公布的退票规则为准", true,
                sessions, description, posterUrl, notices);
    }
}
