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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class EventCatalogService {

    private final Map<Long, Event> events = new ConcurrentHashMap<>();
    private final EventCatalogRepository repository;

    public EventCatalogService() {
        this.repository = null;
    }

    @Autowired
    public EventCatalogService(EventCatalogRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    void seedData() {
        if (repository != null) {
            repository.seedDefaultIfEmpty();
            return;
        }
        TicketCategory standard = new TicketCategory(1001L, 101L, "标准票", 19900, 50, 50);
        TicketCategory vip = new TicketCategory(1002L, 101L, "VIP 票", 39900, 10, 10);
        EventSession session = new EventSession(
                101L,
                1L,
                LocalDateTime.now().plusDays(7),
                LocalDateTime.now().plusDays(7).plusHours(2),
                List.of(standard, vip)
        );
        events.put(1L, new Event(1L, "校园音乐之夜", "大学生活动中心", "PUBLISHED", List.of(session)));
    }

    public List<Event> listEvents() {
        return repository == null ? new ArrayList<>(events.values()) : repository.listPublishedEvents();
    }

    public Event getEvent(Long eventId) {
        if (repository != null) {
            return repository.findPublicEvent(eventId)
                    .orElseThrow(() -> new BusinessException(
                            "EVENT_NOT_FOUND", HttpStatus.NOT_FOUND, "活动不存在"));
        }
        Event event = events.get(eventId);
        if (event == null) {
            throw new BusinessException("EVENT_NOT_FOUND", HttpStatus.NOT_FOUND, "活动不存在");
        }
        return event;
    }

    public TicketCategory getTicketCategory(Long sessionId, Long ticketCategoryId) {
        if (repository != null) {
            return repository.findTicketCategory(sessionId, ticketCategoryId)
                    .orElseThrow(() -> new BusinessException(
                            "TICKET_CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND, "票档不存在"));
        }
        return events.values().stream()
                .flatMap(event -> event.sessions().stream())
                .filter(session -> session.id().equals(sessionId))
                .flatMap(session -> session.ticketCategories().stream())
                .filter(category -> category.id().equals(ticketCategoryId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "TICKET_CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND, "票档不存在"));
    }

    public List<TicketCategory> listTicketCategories() {
        if (repository != null) {
            return repository.listTicketCategories();
        }
        return events.values().stream()
                .flatMap(event -> event.sessions().stream())
                .flatMap(session -> session.ticketCategories().stream())
                .toList();
    }

    public synchronized TicketCategory deductStock(Long sessionId, Long ticketCategoryId, int quantity) {
        if (repository != null) {
            return repository.deductStock(sessionId, ticketCategoryId, quantity);
        }
        Event event = findEventBySessionId(sessionId);
        List<EventSession> sessions = event.sessions().stream()
                .map(session -> session.id().equals(sessionId)
                        ? deductFromSession(session, ticketCategoryId, quantity) : session)
                .toList();
        Event updated = new Event(event.id(), event.name(), event.location(), event.status(), sessions);
        events.put(updated.id(), updated);
        return getTicketCategory(sessionId, ticketCategoryId);
    }

    public synchronized TicketCategory releaseStock(Long sessionId, Long ticketCategoryId, int quantity) {
        if (repository != null) {
            return repository.releaseStock(sessionId, ticketCategoryId, quantity);
        }
        Event event = findEventBySessionId(sessionId);
        List<EventSession> sessions = event.sessions().stream()
                .map(session -> session.id().equals(sessionId)
                        ? releaseToSession(session, ticketCategoryId, quantity) : session)
                .toList();
        Event updated = new Event(event.id(), event.name(), event.location(), event.status(), sessions);
        events.put(updated.id(), updated);
        return getTicketCategory(sessionId, ticketCategoryId);
    }

    public Long getEventIdBySessionId(Long sessionId) {
        return findEventBySessionId(sessionId).id();
    }

    public EventSession getSession(Long sessionId) {
        return findEventBySessionId(sessionId).sessions().stream()
                .filter(session -> session.id().equals(sessionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "SESSION_NOT_FOUND", HttpStatus.NOT_FOUND, "场次不存在"));
    }

    private Event findEventBySessionId(Long sessionId) {
        if (repository != null) {
            return repository.findEventBySessionId(sessionId);
        }
        return events.values().stream()
                .filter(event -> event.sessions().stream().anyMatch(session -> session.id().equals(sessionId)))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "SESSION_NOT_FOUND", HttpStatus.NOT_FOUND, "场次不存在"));
    }

    private EventSession deductFromSession(EventSession session, Long ticketCategoryId, int quantity) {
        List<TicketCategory> categories = session.ticketCategories().stream()
                .map(category -> {
                    if (!category.id().equals(ticketCategoryId)) {
                        return category;
                    }
                    if (category.remainingStock() < quantity) {
                        throw new BusinessException("TICKET_SOLD_OUT", HttpStatus.CONFLICT,
                                "当前票档库存不足，请刷新后重新选择");
                    }
                    return category.deduct(quantity);
                })
                .toList();
        return new EventSession(session.id(), session.eventId(), session.startTime(), session.endTime(), categories);
    }

    private EventSession releaseToSession(EventSession session, Long ticketCategoryId, int quantity) {
        List<TicketCategory> categories = session.ticketCategories().stream()
                .map(category -> category.id().equals(ticketCategoryId) ? category.release(quantity) : category)
                .toList();
        return new EventSession(session.id(), session.eventId(), session.startTime(), session.endTime(), categories);
    }
}
