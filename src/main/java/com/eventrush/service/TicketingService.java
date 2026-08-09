package com.eventrush.service;

import com.eventrush.domain.ElectronicTicket;
import com.eventrush.domain.OrderStatus;
import com.eventrush.domain.TicketOrder;
import com.eventrush.domain.TicketStatus;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TicketingService {

    private final EventCatalogService eventCatalogService;
    private final AtomicLong orderIdGenerator = new AtomicLong(1);
    private final AtomicLong ticketIdGenerator = new AtomicLong(1);
    private final Map<Long, TicketOrder> orders = new ConcurrentHashMap<>();
    private final Map<String, ElectronicTicket> tickets = new ConcurrentHashMap<>();
    private final boolean redisStockEnabled;
    private final RedisTicketStockService redisTicketStockService;

    @Autowired
    public TicketingService(
            EventCatalogService eventCatalogService,
            @Value("${eventrush.stock.redis-enabled:false}") boolean redisStockEnabled,
            ObjectProvider<RedisTicketStockService> redisTicketStockService
    ) {
        this.eventCatalogService = eventCatalogService;
        this.redisStockEnabled = redisStockEnabled;
        this.redisTicketStockService = redisTicketStockService.getIfAvailable();
    }

    public TicketingService(EventCatalogService eventCatalogService) {
        this.eventCatalogService = eventCatalogService;
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

    // ponytail: in-memory orders need one JVM lock; replace with DB unique indexes when MySQL is introduced.
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
        TicketOrder order = new TicketOrder(
                orderIdGenerator.getAndIncrement(),
                userId,
                eventCatalogService.getEventIdBySessionId(sessionId),
                sessionId,
                ticketCategoryId,
                OrderStatus.PENDING_PAYMENT,
                now,
                null,
                null,
                now.plusMinutes(15)
        );
        orders.put(order.id(), order);
        return order;
    }

    private boolean hasGrabbed(Long userId, Long sessionId, Long ticketCategoryId) {
        return orders.values().stream()
                .anyMatch(order -> order.userId().equals(userId)
                        && order.sessionId().equals(sessionId)
                        && order.ticketCategoryId().equals(ticketCategoryId)
                        && order.status() != OrderStatus.CANCELED);
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
        TicketOrder order = orders.get(orderId);
        if (order == null) {
            throw new BusinessException("order not found");
        }
        return order;
    }

    public ElectronicTicket payOrder(Long orderId) {
        TicketOrder order = getOrder(orderId);
        if (order.status() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException("only pending payment orders can be paid");
        }
        TicketOrder paidOrder = order.paid(LocalDateTime.now());
        orders.put(orderId, paidOrder);

        ElectronicTicket ticket = new ElectronicTicket(
                ticketIdGenerator.getAndIncrement(),
                orderId,
                "ER-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(),
                TicketStatus.VALID,
                LocalDateTime.now(),
                null,
                null
        );
        tickets.put(ticket.ticketCode(), ticket);
        return ticket;
    }

    public ElectronicTicket getTicket(String ticketCode) {
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
        ElectronicTicket verified = ticket.verify(verifierId, LocalDateTime.now());
        tickets.put(ticketCode, verified);
        return verified;
    }
}
