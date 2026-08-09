package com.eventrush.api;

import com.eventrush.domain.Event;
import com.eventrush.service.EventCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
class EventController {

    private final EventCatalogService eventCatalogService;

    EventController(EventCatalogService eventCatalogService) {
        this.eventCatalogService = eventCatalogService;
    }

    @GetMapping
    List<Event> listEvents() {
        return eventCatalogService.listEvents();
    }

    @GetMapping("/{eventId}")
    Event getEvent(@PathVariable Long eventId) {
        return eventCatalogService.getEvent(eventId);
    }
}
