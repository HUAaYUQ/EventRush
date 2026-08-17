package com.eventrush.service;

import com.eventrush.domain.EventCategory;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EventCategoryService {

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

    public EventCategory create(String name, String iconKey, int displayOrder, boolean enabled) {
        return repository.create(name.trim(), normalizeIcon(iconKey), displayOrder, enabled);
    }

    public EventCategory update(Long id, String name, String iconKey, int displayOrder, boolean enabled) {
        return repository.update(id, name.trim(), normalizeIcon(iconKey), displayOrder, enabled);
    }

    public EventCategory requireEnabled(Long id) {
        return repository.requireEnabled(id);
    }

    private String normalizeIcon(String iconKey) {
        return iconKey == null || iconKey.isBlank() ? "ticket" : iconKey.trim();
    }
}
