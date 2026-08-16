package com.eventrush.domain;

import java.util.List;

public record Event(
        Long id,
        String name,
        String location,
        String status,
        List<EventSession> sessions,
        String description,
        String posterUrl,
        List<OrganizerNotice> notices
) {
    public Event(Long id, String name, String location, String status, List<EventSession> sessions) {
        this(id, name, location, status, sessions, "", "/images/events/campus-music-night.jpg", List.of());
    }
}
