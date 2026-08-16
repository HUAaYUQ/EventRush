package com.eventrush.service;

import com.eventrush.domain.EventSession;
import com.eventrush.domain.OrganizerEvent;
import com.eventrush.domain.OrganizerNotice;
import com.eventrush.domain.OrganizerOrderSummary;
import com.eventrush.domain.TicketCategory;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OrganizerEventService {

    public static final Long DEMO_ORGANIZER_ID = 9001L;

    private final EventCatalogRepository repository;

    public OrganizerEventService(EventCatalogRepository repository) {
        this.repository = repository;
    }

    public List<OrganizerEvent> listEvents() {
        return repository.listOrganizerEvents(DEMO_ORGANIZER_ID);
    }

    public OrganizerEvent getEvent(Long eventId) {
        return repository.findOrganizerEvent(eventId, DEMO_ORGANIZER_ID)
                .orElseThrow(() -> new BusinessException(
                        "EVENT_NOT_FOUND", HttpStatus.NOT_FOUND, "活动不存在或不属于当前主办方"));
    }

    public List<OrganizerOrderSummary> listOrders(Long eventId) {
        return repository.listOrganizerOrders(eventId, DEMO_ORGANIZER_ID);
    }

    public OrganizerEvent createDraft(
            String name,
            String location,
            String description,
            String posterUrl
    ) {
        return repository.createDraft(DEMO_ORGANIZER_ID, name.trim(), location.trim(),
                description.trim(), normalizePoster(posterUrl), LocalDateTime.now());
    }

    public OrganizerEvent updateBasicInfo(
            Long eventId,
            String name,
            String location,
            String description,
            String posterUrl
    ) {
        repository.updateBasicInfo(eventId, DEMO_ORGANIZER_ID, name.trim(), location.trim(),
                description.trim(), normalizePoster(posterUrl), LocalDateTime.now());
        return getEvent(eventId);
    }

    public EventSession addSession(Long eventId, LocalDateTime startTime, LocalDateTime endTime) {
        requireValidRange(startTime, endTime);
        return repository.addSession(eventId, DEMO_ORGANIZER_ID, startTime, endTime, LocalDateTime.now());
    }

    public EventSession updateSession(
            Long eventId,
            Long sessionId,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        requireValidRange(startTime, endTime);
        repository.updateSession(eventId, DEMO_ORGANIZER_ID, sessionId, startTime, endTime,
                LocalDateTime.now());
        return getEvent(eventId).sessions().stream()
                .filter(session -> session.id().equals(sessionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "SESSION_NOT_FOUND", HttpStatus.NOT_FOUND, "场次不存在"));
    }

    public TicketCategory addTicketCategory(
            Long eventId,
            Long sessionId,
            String name,
            long priceCents,
            int totalStock
    ) {
        return repository.addTicketCategory(eventId, DEMO_ORGANIZER_ID, sessionId, name.trim(),
                priceCents, totalStock, LocalDateTime.now());
    }

    public TicketCategory updateTicketCategory(
            Long eventId,
            Long sessionId,
            Long categoryId,
            String name,
            long priceCents,
            int totalStock
    ) {
        repository.updateTicketCategory(eventId, DEMO_ORGANIZER_ID, sessionId, categoryId,
                name.trim(), priceCents, totalStock, LocalDateTime.now());
        return repository.findTicketCategory(sessionId, categoryId)
                .orElseThrow(() -> new BusinessException(
                        "TICKET_CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND, "票档不存在"));
    }

    public OrganizerEvent publishEvent(Long eventId) {
        OrganizerEvent event = getEvent(eventId);
        if (event.name().isBlank() || event.location().isBlank()) {
            throw new BusinessException("EVENT_INFO_REQUIRED", HttpStatus.CONFLICT,
                    "发布前请补全活动名称和地点");
        }
        repository.publishEvent(eventId, DEMO_ORGANIZER_ID, LocalDateTime.now());
        return getEvent(eventId);
    }

    public OrganizerNotice publishNotice(Long eventId, String title, String content) {
        return repository.addNotice(eventId, DEMO_ORGANIZER_ID, title.trim(), content.trim(),
                LocalDateTime.now());
    }

    private void requireValidRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException("INVALID_SESSION_TIME", HttpStatus.BAD_REQUEST,
                    "结束时间必须晚于开始时间");
        }
    }

    private String normalizePoster(String posterUrl) {
        return posterUrl == null || posterUrl.isBlank()
                ? "/images/events/campus-music-night.jpg"
                : posterUrl.trim();
    }
}
