package com.eventrush.service;

import com.eventrush.domain.ElectronicTicket;
import com.eventrush.domain.OrderStatus;
import com.eventrush.domain.TicketOrder;
import com.eventrush.domain.TicketStatus;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketingService {

    private final EventCatalogService eventCatalogService;
    private final AtomicLong orderIdGenerator = new AtomicLong(1);
    private final AtomicLong ticketIdGenerator = new AtomicLong(1);
    private final Map<Long, TicketOrder> orders = new ConcurrentHashMap<>();
    private final Map<String, ElectronicTicket> tickets = new ConcurrentHashMap<>();
    private final TicketOrderRepository ticketOrderRepository;
    private final ElectronicTicketRepository electronicTicketRepository;
    private final long orderExpireSeconds;
    private final boolean redisStockEnabled;
    private final RedisTicketStockService redisTicketStockService;

    @Autowired
    public TicketingService(
            EventCatalogService eventCatalogService,
            TicketOrderRepository ticketOrderRepository,
            ElectronicTicketRepository electronicTicketRepository,
            @Value("${eventrush.order.expire-seconds:900}") long orderExpireSeconds,
            @Value("${eventrush.stock.redis-enabled:false}") boolean redisStockEnabled,
            ObjectProvider<RedisTicketStockService> redisTicketStockService
    ) {
        this.eventCatalogService = eventCatalogService;
        this.ticketOrderRepository = ticketOrderRepository;
        this.electronicTicketRepository = electronicTicketRepository;
        this.orderExpireSeconds = orderExpireSeconds;
        this.redisStockEnabled = redisStockEnabled;
        this.redisTicketStockService = redisTicketStockService.getIfAvailable();
    }

    public TicketingService(EventCatalogService eventCatalogService) {
        this.eventCatalogService = eventCatalogService;
        this.ticketOrderRepository = null;
        this.electronicTicketRepository = null;
        this.orderExpireSeconds = 900;
        this.redisStockEnabled = false;
        this.redisTicketStockService = null;
    }

    @PostConstruct
    void preloadRedisStock() {
        if (!redisStockEnabled) {
            return;
        }
        eventCatalogService.listTicketCategories()
                .forEach(category -> redisTicketStockService.preloadStock(
                        category.sessionId(),
                        category.id(),
                        category.remainingStock()
                ));
    }

    @Transactional
    public synchronized TicketOrder grabTicket(Long userId, Long sessionId, Long ticketCategoryId) {
        eventCatalogService.getTicketCategory(sessionId, ticketCategoryId);
        if (hasGrabbed(userId, sessionId, ticketCategoryId)) {
            throw new BusinessException("user has already grabbed this ticket");
        }
        if (redisStockEnabled) {
            deductRedisStock(userId, sessionId, ticketCategoryId);
        }
        eventCatalogService.deductStock(sessionId, ticketCategoryId);
        LocalDateTime now = LocalDateTime.now();
        TicketOrder order = createPendingOrder(
                userId,
                eventCatalogService.getEventIdBySessionId(sessionId),
                sessionId,
                ticketCategoryId,
                now,
                now.plusSeconds(orderExpireSeconds)
        );
        return order;
    }

    private boolean hasGrabbed(Long userId, Long sessionId, Long ticketCategoryId) {
        if (ticketOrderRepository != null) {
            return ticketOrderRepository.existsActiveGrab(userId, sessionId, ticketCategoryId);
        }
        return orders.values().stream()
                .anyMatch(order -> order.userId().equals(userId)
                        && order.sessionId().equals(sessionId)
                        && order.ticketCategoryId().equals(ticketCategoryId)
                        && order.status() != OrderStatus.CANCELED);
    }

    private TicketOrder createPendingOrder(
            Long userId,
            Long eventId,
            Long sessionId,
            Long ticketCategoryId,
            LocalDateTime createdTime,
            LocalDateTime expireTime
    ) {
        if (ticketOrderRepository != null) {
            return ticketOrderRepository.createPending(userId, eventId, sessionId, ticketCategoryId, createdTime, expireTime);
        }
        // ponytail: only used by small unit tests; app runtime writes orders through TicketOrderRepository.
        TicketOrder order = new TicketOrder(
                orderIdGenerator.getAndIncrement(),
                userId,
                eventId,
                sessionId,
                ticketCategoryId,
                OrderStatus.PENDING_PAYMENT,
                createdTime,
                null,
                null,
                expireTime
        );
        orders.put(order.id(), order);
        return order;
    }

    private void deductRedisStock(Long userId, Long sessionId, Long ticketCategoryId) {
        long result = redisTicketStockService.tryDeduct(userId, sessionId, ticketCategoryId);
        if (result == RedisTicketStockService.DUPLICATE_GRAB) {
            throw new BusinessException("user has already grabbed this ticket");
        }
        if (result == RedisTicketStockService.STOCK_NOT_INITIALIZED) {
            throw new BusinessException("redis stock is not initialized");
        }
        if (result == RedisTicketStockService.STOCK_NOT_ENOUGH) {
            throw new BusinessException("ticket stock is insufficient");
        }
    }

    public TicketOrder getOrder(Long orderId) {
        if (ticketOrderRepository != null) {
            return ticketOrderRepository.findById(orderId)
                    .orElseThrow(() -> new BusinessException("order not found"));
        }
        TicketOrder order = orders.get(orderId);
        if (order == null) {
            throw new BusinessException("order not found");
        }
        return order;
    }

    @Transactional
    public ElectronicTicket payOrder(Long orderId) {
        TicketOrder order = getOrder(orderId);
        if (order.status() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException("only pending payment orders can be paid");
        }
        LocalDateTime payTime = LocalDateTime.now();
        if (ticketOrderRepository != null) {
            ticketOrderRepository.markPaid(orderId, payTime);
        } else {
            TicketOrder paidOrder = order.paid(payTime);
            orders.put(orderId, paidOrder);
        }

        ElectronicTicket ticket = createElectronicTicket(orderId);
        return ticket;
    }

    private ElectronicTicket createElectronicTicket(Long orderId) {
        String ticketCode = "ER-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        LocalDateTime generatedTime = LocalDateTime.now();
        if (electronicTicketRepository != null) {
            return electronicTicketRepository.create(orderId, ticketCode, generatedTime);
        }
        ElectronicTicket ticket = new ElectronicTicket(
                ticketIdGenerator.getAndIncrement(),
                orderId,
                ticketCode,
                TicketStatus.VALID,
                generatedTime,
                null,
                null
        );
        tickets.put(ticket.ticketCode(), ticket);
        return ticket;
    }

    public ElectronicTicket getTicket(String ticketCode) {
        if (electronicTicketRepository != null) {
            return electronicTicketRepository.findByCode(ticketCode)
                    .orElseThrow(() -> new BusinessException("ticket not found"));
        }
        ElectronicTicket ticket = tickets.get(ticketCode);
        if (ticket == null) {
            throw new BusinessException("ticket not found");
        }
        return ticket;
    }

    public ElectronicTicket verifyTicket(String ticketCode, Long verifierId) {
        ElectronicTicket ticket = getTicket(ticketCode);
        if (ticket.status() == TicketStatus.VERIFIED) {
            throw new BusinessException("ticket has already been verified");
        }
        if (electronicTicketRepository != null) {
            return electronicTicketRepository.markVerified(ticketCode, verifierId, LocalDateTime.now());
        }
        ElectronicTicket verified = ticket.verify(verifierId, LocalDateTime.now());
        tickets.put(ticketCode, verified);
        return verified;
    }

    @Transactional
    public int cancelExpiredOrders() {
        if (ticketOrderRepository == null) {
            return cancelExpiredMemoryOrders();
        }
        List<TicketOrder> expiredOrders = ticketOrderRepository.findExpiredPending(LocalDateTime.now(), 100);
        int canceled = 0;
        for (TicketOrder order : expiredOrders) {
            if (ticketOrderRepository.markCanceledIfPending(order.id(), LocalDateTime.now())) {
                releaseStock(order);
                canceled++;
            }
        }
        return canceled;
    }

    private int cancelExpiredMemoryOrders() {
        LocalDateTime now = LocalDateTime.now();
        int canceled = 0;
        for (TicketOrder order : List.copyOf(orders.values())) {
            if (order.status() == OrderStatus.PENDING_PAYMENT && !order.expireTime().isAfter(now)) {
                orders.put(order.id(), order.canceled(now));
                releaseStock(order);
                canceled++;
            }
        }
        return canceled;
    }

    private void releaseStock(TicketOrder order) {
        eventCatalogService.releaseStock(order.sessionId(), order.ticketCategoryId());
        if (redisStockEnabled && redisTicketStockService != null) {
            redisTicketStockService.release(order.userId(), order.sessionId(), order.ticketCategoryId());
        }
    }
}
