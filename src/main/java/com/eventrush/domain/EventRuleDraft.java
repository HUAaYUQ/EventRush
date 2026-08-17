package com.eventrush.domain;

public record EventRuleDraft(
        String ruleGroup,
        String ruleCode,
        String title,
        String content,
        int displayOrder
) {
}
