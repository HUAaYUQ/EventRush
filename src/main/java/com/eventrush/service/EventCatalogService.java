package com.eventrush.service;

import com.eventrush.domain.Event;
import com.eventrush.domain.EventSession;
import com.eventrush.domain.TicketCategory;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class EventCatalogService {

    private final Map<Long, Event> events = new ConcurrentHashMap<>();

    @PostConstruct
    void seedData() {
        TicketCategory standard = new TicketCategory(1001L, 101L, "Standard", 50, 50);
        TicketCategory vip = new TicketCategory(1002L, 101L, "VIP", 10, 10);
        EventSession session = new EventSession(
                101L,
                1L,
                LocalDateTime.now().plusDays(7),
                LocalDateTime.now().plusDays(7).plusHours(2),
                List.of(standard, vip)
        );
        events.put(1L, new Event(1L, "Campus Music Night", "Main Auditorium", "PUBLISHED", List.of(session)));
    }

    public List<Event> listEvents() {
        return new ArrayList<>(events.values());
    }

    public Event getEvent(Long eventId) {
        Event event = events.get(eventId);
        if (event == null) {
            throw new BusinessException("event not found");
        }
        return event;
    }

    public TicketCategory getTicketCategory(Long sessionId, Long ticketCategoryId) {
        return events.values().stream()
                .flatMap(event -> event.sessions().stream())
                .filter(session -> session.id().equals(sessionId))
                .flatMap(session -> session.ticketCategories().stream())
                .filter(category -> category.id().equals(ticketCategoryId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("ticket category not found"));
    }

    public synchronized TicketCategory deductStock(Long sessionId, Long ticketCategoryId) {
        Event event = findEventBySessionId(sessionId);
        List<EventSession> sessions = event.sessions().stream()
                .map(session -> session.id().equals(sessionId) ? deductFromSession(session, ticketCategoryId) : session)
                .toList();
        Event updated = new Event(event.id(), event.name(), event.location(), event.status(), sessions);
        events.put(updated.id(), updated);
        return getTicketCategory(sessionId, ticketCategoryId);
    }

    public Long getEventIdBySessionId(Long sessionId) {
        return findEventBySessionId(sessionId).id();
    }

    private Event findEventBySessionId(Long sessionId) {
        return events.values().stream()
                .filter(event -> event.sessions().stream().anyMatch(session -> session.id().equals(sessionId)))
                .findFirst()
                .orElseThrow(() -> new BusinessException("session not found"));
    }

    private EventSession deductFromSession(EventSession session, Long ticketCategoryId) {
        List<TicketCategory> categories = session.ticketCategories().stream()
                .map(category -> {
                    if (!category.id().equals(ticketCategoryId)) {
                        return category;
                    }
                    if (category.remainingStock() <= 0) {
                        throw new BusinessException("ticket stock is insufficient");
                    }
                    return category.deductOne();
                })
                .toList();
        return new EventSession(session.id(), session.eventId(), session.startTime(), session.endTime(), categories);
    }
}
