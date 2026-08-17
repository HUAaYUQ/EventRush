package com.eventrush.service;

import com.eventrush.domain.EventCategory;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class EventCategoryService {

    private static final Set<String> CONTENT_PROFILES = Set.of(
            "PERFORMANCE", "SPORTS", "EXHIBITION", "FAMILY", "GENERAL");

    private final EventCategoryRepository repository;

    public EventCategoryService(EventCategoryRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    void initialize() {
        repository.ensureDefaults();
    }

    public List<EventCategory> listPublic() {
        return repository.list(true);
    }

    public List<EventCategory> listAll() {
        return repository.list(false);
    }

    public EventCategory create(
            String name, String iconKey, String contentProfile, int displayOrder, boolean enabled
    ) {
        return repository.create(name.trim(), normalizeIcon(iconKey), normalizeProfile(contentProfile),
                displayOrder, enabled);
    }

    public EventCategory update(
            Long id, String name, String iconKey, String contentProfile, int displayOrder, boolean enabled
    ) {
        return repository.update(id, name.trim(), normalizeIcon(iconKey), normalizeProfile(contentProfile),
                displayOrder, enabled);
    }

    public EventCategory requireEnabled(Long id) {
        return repository.requireEnabled(id);
    }

    private String normalizeIcon(String iconKey) {
        return iconKey == null || iconKey.isBlank() ? "ticket" : iconKey.trim();
    }

    private String normalizeProfile(String contentProfile) {
        String normalized = contentProfile == null ? "" : contentProfile.trim().toUpperCase();
        if (!CONTENT_PROFILES.contains(normalized)) {
            throw new BusinessException("INVALID_CONTENT_PROFILE", HttpStatus.BAD_REQUEST,
                    "活动内容模板不受支持");
        }
        return normalized;
    }
}
