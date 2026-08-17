package com.eventrush.domain;

public record EventRule(
        Long id,
        Long eventId,
        String ruleGroup,
        String ruleCode,
        String title,
        String content,
        int displayOrder
) {
}
