package com.eventrush.service;

import com.eventrush.domain.EventSession;
import com.eventrush.domain.EventProductDraft;
import com.eventrush.domain.OrganizerEvent;
import com.eventrush.domain.OrganizerNotice;
import com.eventrush.domain.OrganizerOrderSummary;
import com.eventrush.domain.TicketCategory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class OrganizerEventService {

    private static final Logger log = LoggerFactory.getLogger(OrganizerEventService.class);
    public static final Long CURRENT_ORGANIZER_ID = 9001L;
    private static final Set<String> REAL_NAME_RULES = Set.of("REQUIRED", "NOT_REQUIRED");
    private static final Set<String> ENTRY_METHODS = Set.of("E_TICKET", "ID_CARD", "PAPER_TICKET");

    private final EventCatalogRepository repository;
    private final EventCategoryService eventCategoryService;
    private final ObjectProvider<EventCacheService> eventCacheService;
    private final boolean redisCacheEnabled;

    public OrganizerEventService(
            EventCatalogRepository repository,
            EventCategoryService eventCategoryService,
            ObjectProvider<EventCacheService> eventCacheService,
            @Value("${eventrush.cache.redis-enabled:false}") boolean redisCacheEnabled
    ) {
        this.repository = repository;
        this.eventCategoryService = eventCategoryService;
        this.eventCacheService = eventCacheService;
        this.redisCacheEnabled = redisCacheEnabled;
    }

    public List<OrganizerEvent> listEvents() {
        return repository.listOrganizerEvents(CURRENT_ORGANIZER_ID);
    }

    public OrganizerEvent getEvent(Long eventId) {
        return repository.findOrganizerEvent(eventId, CURRENT_ORGANIZER_ID)
                .orElseThrow(() -> new BusinessException(
                        "EVENT_NOT_FOUND", HttpStatus.NOT_FOUND, "活动不存在或不属于当前主办方"));
    }

    public List<OrganizerOrderSummary> listOrders(Long eventId) {
        return repository.listOrganizerOrders(eventId, CURRENT_ORGANIZER_ID);
    }

    public OrganizerEvent createDraft(EventProductDraft product) {
        EventProductDraft normalized = normalizeProduct(product);
        return repository.createDraft(CURRENT_ORGANIZER_ID, normalized, LocalDateTime.now());
    }

    public OrganizerEvent updateBasicInfo(
            Long eventId,
            EventProductDraft product
    ) {
        repository.updateBasicInfo(eventId, CURRENT_ORGANIZER_ID, normalizeProduct(product),
                LocalDateTime.now());
        return getEvent(eventId);
    }

    public EventSession addSession(Long eventId, LocalDateTime startTime, LocalDateTime endTime) {
        requireValidRange(startTime, endTime);
        return repository.addSession(eventId, CURRENT_ORGANIZER_ID, startTime, endTime, LocalDateTime.now());
    }

    public EventSession updateSession(
            Long eventId,
            Long sessionId,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        requireValidRange(startTime, endTime);
        repository.updateSession(eventId, CURRENT_ORGANIZER_ID, sessionId, startTime, endTime,
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
        return repository.addTicketCategory(eventId, CURRENT_ORGANIZER_ID, sessionId, name.trim(),
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
        repository.updateTicketCategory(eventId, CURRENT_ORGANIZER_ID, sessionId, categoryId,
                name.trim(), priceCents, totalStock, LocalDateTime.now());
        return repository.findTicketCategory(sessionId, categoryId)
                .orElseThrow(() -> new BusinessException(
                        "TICKET_CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND, "票档不存在"));
    }

    @Transactional
    public OrganizerEvent publishEvent(Long eventId) {
        OrganizerEvent event = getEvent(eventId);
        if (event.name().isBlank() || event.location().isBlank() || event.city().isBlank()
                || event.venueAddress().isBlank() || event.description().isBlank()
                || event.posterUrl().isBlank() || event.refundRule().isBlank()) {
            throw new BusinessException("EVENT_INFO_REQUIRED", HttpStatus.CONFLICT,
                    "发布前请补全活动、场馆、图片和票务规则");
        }
        eventCategoryService.requireEnabled(event.categoryId());
        repository.publishEvent(eventId, CURRENT_ORGANIZER_ID, LocalDateTime.now());
        evictPublishedEvent(eventId);
        return getEvent(eventId);
    }

    public OrganizerNotice publishNotice(Long eventId, String title, String content) {
        return repository.addNotice(eventId, CURRENT_ORGANIZER_ID, title.trim(), content.trim(),
                LocalDateTime.now());
    }

    private void requireValidRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException("INVALID_SESSION_TIME", HttpStatus.BAD_REQUEST,
                    "结束时间必须晚于开始时间");
        }
    }

    private EventProductDraft normalizeProduct(EventProductDraft product) {
        eventCategoryService.requireEnabled(product.categoryId());
        requireValidRange(product.saleStartTime(), product.saleEndTime());
        if (!REAL_NAME_RULES.contains(product.realNameRule())) {
            throw new BusinessException("INVALID_REAL_NAME_RULE", HttpStatus.BAD_REQUEST,
                    "实名规则不受支持");
        }
        if (!ENTRY_METHODS.contains(product.entryMethod())) {
            throw new BusinessException("INVALID_ENTRY_METHOD", HttpStatus.BAD_REQUEST,
                    "入场方式不受支持");
        }
        return new EventProductDraft(
                product.name().trim(), product.categoryId(), product.city().trim(),
                product.venueName().trim(), product.venueAddress().trim(),
                product.description().trim(), product.posterUrl() == null ? "" : product.posterUrl().trim(),
                product.durationMinutes(), product.saleStartTime(), product.saleEndTime(),
                product.purchaseLimit(), product.realNameRule().trim(), product.entryMethod().trim(),
                product.refundRule().trim(), product.waitlistEnabled());
    }

    private void evictPublishedEvent(Long eventId) {
        if (!redisCacheEnabled) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doEvictPublishedEvent(eventId);
                }
            });
            return;
        }
        doEvictPublishedEvent(eventId);
    }

    private void doEvictPublishedEvent(Long eventId) {
        try {
            eventCacheService.ifAvailable(cache -> cache.evictEvent(eventId));
        } catch (RuntimeException exception) {
            log.warn("event cache eviction failed after publish, eventId={}", eventId, exception);
        }
    }
}
