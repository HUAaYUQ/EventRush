package com.eventrush.service;

import com.eventrush.domain.EventSession;
import com.eventrush.domain.TicketCategory;
import com.eventrush.domain.TicketOrder;
import com.eventrush.domain.TicketPassenger;
import com.eventrush.domain.TicketWaitlistRequest;
import com.eventrush.domain.WaitlistStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketWaitlistService {

    private final EventCatalogService eventCatalogService;
    private final TicketingService ticketingService;
    private final TicketWaitlistRepository repository;

    public TicketWaitlistService(
            EventCatalogService eventCatalogService,
            TicketingService ticketingService,
            TicketWaitlistRepository repository
    ) {
        this.eventCatalogService = eventCatalogService;
        this.ticketingService = ticketingService;
        this.repository = repository;
    }

    @Transactional
    public synchronized TicketWaitlistRequest join(
            Long userId,
            Long sessionId,
            Long ticketCategoryId,
            List<TicketPassenger> requestedPassengers
    ) {
        LocalDateTime now = LocalDateTime.now();
        EventSession session = eventCatalogService.getSession(sessionId);
        if (!session.startTime().isAfter(now)) {
            throw new BusinessException("WAITLIST_CLOSED", HttpStatus.CONFLICT,
                    "场次已经开始，不能再提交候补");
        }
        List<TicketPassenger> passengers = ticketingService.normalizePassengers(requestedPassengers);
        TicketCategory category = eventCatalogService.getTicketCategory(sessionId, ticketCategoryId);
        if (category.remainingStock() >= passengers.size()
                && !repository.existsWaiting(sessionId, ticketCategoryId)) {
            throw new BusinessException("WAITLIST_NOT_AVAILABLE", HttpStatus.CONFLICT,
                    "当前库存足够，请直接提交购票订单");
        }
        if (ticketingService.hasActiveOrder(userId, sessionId, ticketCategoryId)) {
            throw new BusinessException("DUPLICATE_GRAB", HttpStatus.CONFLICT,
                    "你已有这个票档的有效订单，请前往我的电子票继续处理");
        }
        if (repository.existsActive(userId, sessionId, ticketCategoryId)) {
            throw new BusinessException("DUPLICATE_WAITLIST", HttpStatus.CONFLICT,
                    "你已在这个票档的候补队列中，请前往我的候补查看进度");
        }
        return repository.create(
                userId,
                eventCatalogService.getEventIdBySessionId(sessionId),
                sessionId,
                ticketCategoryId,
                category.priceCents(),
                passengers,
                now
        );
    }

    public boolean hasWaiting(Long sessionId, Long ticketCategoryId) {
        return repository.existsWaiting(sessionId, ticketCategoryId);
    }

    public TicketWaitlistRequest getForUser(Long userId, Long waitlistId) {
        TicketWaitlistRequest request = refreshExpired(get(waitlistId));
        assertOwner(userId, request);
        return request;
    }

    public List<TicketWaitlistRequest> listForUser(Long userId) {
        for (TicketWaitlistRequest request : repository.findByUserId(userId)) {
            expireIfStarted(request);
        }
        return repository.findByUserId(userId);
    }

    @Transactional
    public synchronized TicketWaitlistRequest cancelForUser(Long userId, Long waitlistId) {
        TicketWaitlistRequest request = refreshExpired(get(waitlistId));
        assertOwner(userId, request);
        if (request.status() != WaitlistStatus.WAITING) {
            throw new BusinessException("WAITLIST_NOT_CANCELABLE", HttpStatus.CONFLICT,
                    "当前候补状态不能取消，请刷新后查看最新结果");
        }
        if (!repository.markCanceledIfWaiting(waitlistId, LocalDateTime.now())) {
            throw new BusinessException("WAITLIST_NOT_CANCELABLE", HttpStatus.CONFLICT,
                    "当前候补状态不能取消，请刷新后查看最新结果");
        }
        return get(waitlistId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized int fulfillAvailable(Long sessionId, Long ticketCategoryId) {
        int fulfilled = 0;
        while (true) {
            TicketWaitlistRequest request = repository
                    .findFirstWaitingForUpdate(sessionId, ticketCategoryId)
                    .orElse(null);
            if (request == null) {
                return fulfilled;
            }
            if (expireIfStarted(request)) {
                continue;
            }
            if (ticketingService.hasActiveOrder(request.userId(), sessionId, ticketCategoryId)) {
                repository.markExpiredIfWaiting(request.id(), LocalDateTime.now());
                continue;
            }
            TicketCategory category = eventCatalogService.getTicketCategory(sessionId, ticketCategoryId);
            if (category.remainingStock() < request.quantity()) {
                return fulfilled;
            }

            TicketOrder order = null;
            try {
                order = ticketingService.createOrderFromWaitlist(request);
                if (!repository.markFulfilledIfWaiting(
                        request.id(), order.id(), LocalDateTime.now(), order.expireTime())) {
                    throw new BusinessException("WAITLIST_FULFILL_CONFLICT", HttpStatus.CONFLICT,
                            "候补状态已经变化，请刷新后重试");
                }
                fulfilled++;
            } catch (RuntimeException exception) {
                if (order != null) {
                    ticketingService.compensateWaitlistOrderCreation(request);
                }
                throw exception;
            }
        }
    }

    private TicketWaitlistRequest get(Long waitlistId) {
        return repository.findById(waitlistId)
                .orElseThrow(() -> new BusinessException("WAITLIST_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "候补记录不存在"));
    }

    private TicketWaitlistRequest refreshExpired(TicketWaitlistRequest request) {
        if (expireIfStarted(request)) {
            return get(request.id());
        }
        return request;
    }

    private boolean expireIfStarted(TicketWaitlistRequest request) {
        if (request.status() != WaitlistStatus.WAITING) {
            return false;
        }
        if (eventCatalogService.getSession(request.sessionId()).startTime().isAfter(LocalDateTime.now())) {
            return false;
        }
        return repository.markExpiredIfWaiting(request.id(), LocalDateTime.now());
    }

    private void assertOwner(Long userId, TicketWaitlistRequest request) {
        if (!request.userId().equals(userId)) {
            throw new BusinessException("WAITLIST_NOT_FOUND", HttpStatus.NOT_FOUND, "候补记录不存在");
        }
    }
}
