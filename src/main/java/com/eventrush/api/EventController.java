package com.eventrush.api;

import com.eventrush.domain.Event;
import com.eventrush.service.EventCacheService;
import com.eventrush.service.EventCatalogService;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
class EventController {

    private final EventCatalogService eventCatalogService;
    private final EventCacheService eventCacheService;
    private final boolean redisCacheEnabled;
    private final long eventDetailTtlSeconds;

    EventController(
            EventCatalogService eventCatalogService,
            ObjectProvider<EventCacheService> eventCacheService,
            @Value("${eventrush.cache.redis-enabled:false}") boolean redisCacheEnabled,
            @Value("${eventrush.cache.event-detail-ttl-seconds:300}") long eventDetailTtlSeconds
    ) {
        this.eventCatalogService = eventCatalogService;
        this.eventCacheService = eventCacheService.getIfAvailable();
        this.redisCacheEnabled = redisCacheEnabled;
        this.eventDetailTtlSeconds = eventDetailTtlSeconds;
    }

    @GetMapping
    List<Event> listEvents() {
        return eventCatalogService.listEvents();
    }

    @GetMapping("/{eventId}")
    Event getEvent(@PathVariable Long eventId) {
        if (!redisCacheEnabled) {
            return eventCatalogService.getEvent(eventId);
        }
        return eventCacheService.getEvent(eventId).orElseGet(() -> {
            Event event = eventCatalogService.getEvent(eventId);
            eventCacheService.putEvent(event, Duration.ofSeconds(eventDetailTtlSeconds));
            return event;
        });
    }
}
