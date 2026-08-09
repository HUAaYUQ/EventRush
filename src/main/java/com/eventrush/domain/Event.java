package com.eventrush.domain;

import java.util.List;

public record Event(
        Long id,
        String name,
        String location,
        String status,
        List<EventSession> sessions
) {
}
