package com.eventrush.service;

import com.eventrush.domain.ElectronicTicket;
import com.eventrush.domain.OrderStatus;
import com.eventrush.domain.TicketCategory;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketingService {

    private static final Logger log = LoggerFactory.getLogger(TicketingService.class);

    private final EventCatalogService eventCatalogService;
    private final AtomicLong orderIdGenerator = new AtomicLong(1);
    private final AtomicLong ticketIdGenerator = new AtomicLong(1);
    private final Map<Long, TicketOrder> orders = new ConcurrentHashMap<>();
    private final Map<String, ElectronicTicket> tickets = new ConcurrentHashMap<>();
    private final TicketOrderRepository ticketOrderRepository;
    private final ElectronicTicketRepository electronicTicketRepository;
    private final RateLimitService rateLimitService;
    private final long orderExpireSeconds;
    private final boolean redisStockEnabled;
    private final boolean redisRateLimitEnabled;
    private final int grabRateLimit;
    private final int grabRateLimitWindowSeconds;
    private final RedisTicketStockService redisTicketStockService;
    private final ObjectProvider<OrderTimeoutMessagePublisher> orderTimeoutMessagePublisher;

    @Autowired
    public TicketingService(
            EventCatalogService eventCatalogService,
            TicketOrderRepository ticketOrderRepository,
            ElectronicTicketRepository electronicTicketRepository,
            ObjectProvider<RateLimitService> rateLimitService,
            @Value("${eventrush.order.expire-seconds:900}") long orderExpireSeconds,
            @Value("${eventrush.stock.redis-enabled:false}") boolean redisStockEnabled,
            @Value("${eventrush.rate-limit.redis-enabled:false}") boolean redisRateLimitEnabled,
            @Value("${eventrush.rate-limit.grab-limit:3}") int grabRateLimit,
            @Value("${eventrush.rate-limit.grab-window-seconds:10}") int grabRateLimitWindowSeconds,
            ObjectProvider<RedisTicketStockService> redisTicketStockService,
            ObjectProvider<OrderTimeoutMessagePublisher> orderTimeoutMessagePublisher
    ) {
        this.eventCatalogService = eventCatalogService;
        this.ticketOrderRepository = ticketOrderRepository;
        this.electronicTicketRepository = electronicTicketRepository;
        this.rateLimitService = rateLimitService.getIfAvailable();
        this.orderExpireSeconds = orderExpireSeconds;
        this.redisStockEnabled = redisStockEnabled;
        this.redisRateLimitEnabled = redisRateLimitEnabled;
        this.grabRateLimit = grabRateLimit;
        this.grabRateLimitWindowSeconds = grabRateLimitWindowSeconds;
        this.redisTicketStockService = redisTicketStockService.getIfAvailable();
        this.orderTimeoutMessagePublisher = orderTimeoutMessagePublisher;
    }

    public TicketingService(EventCatalogService eventCatalogService) {
        this.eventCatalogService = eventCatalogService;
        this.ticketOrderRepository = null;
        this.electronicTicketRepository = null;
        this.rateLimitService = null;
        this.orderExpireSeconds = 900;
        this.redisStockEnabled = false;
        this.redisRateLimitEnabled = false;
        this.grabRateLimit = 3;
        this.grabRateLimitWindowSeconds = 10;
        this.redisTicketStockService = null;
        this.orderTimeoutMessagePublisher = null;
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
        checkGrabRateLimit(userId);
        TicketCategory ticketCategory = eventCatalogService.getTicketCategory(sessionId, ticketCategoryId);
        if (hasGrabbed(userId, sessionId, ticketCategoryId)) {
            throw new BusinessException("DUPLICATE_GRAB", HttpStatus.CONFLICT,
                    "你已有这个票档的有效订单，请前往我的电子票继续处理");
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
                ticketCategory.priceCents(),
                now,
                now.plusSeconds(orderExpireSeconds)
        );
        publishOrderTimeout(order);
        return order;
    }

    private void publishOrderTimeout(TicketOrder order) {
        if (orderTimeoutMessagePublisher != null) {
            try {
                orderTimeoutMessagePublisher.ifAvailable(publisher -> publisher.publish(order.id()));
            } catch (RuntimeException exception) {
                // The timeout scanner remains the fallback if RocketMQ is temporarily unavailable.
                log.warn("order timeout message publish failed, orderId={}", order.id(), exception);
            }
        }
    }

    private void checkGrabRateLimit(Long userId) {
        if (!redisRateLimitEnabled) {
            return;
        }
        if (!rateLimitService.allowGrab(userId, grabRateLimit, grabRateLimitWindowSeconds)) {
            throw new BusinessException("GRAB_RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS,
                    "请求过于频繁，请稍后重试");
        }
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
            long unitPriceCents,
            LocalDateTime createdTime,
            LocalDateTime expireTime
    ) {
        if (ticketOrderRepository != null) {
            return ticketOrderRepository.createPending(
                    userId, eventId, sessionId, ticketCategoryId, unitPriceCents, createdTime, expireTime);
        }
        // ponytail: only used by small unit tests; app runtime writes orders through TicketOrderRepository.
        TicketOrder order = new TicketOrder(
                orderIdGenerator.getAndIncrement(),
                userId,
                eventId,
                sessionId,
                ticketCategoryId,
                unitPriceCents,
                unitPriceCents,
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
            throw new BusinessException("DUPLICATE_GRAB", HttpStatus.CONFLICT,
                    "你已有这个票档的有效订单，请前往我的电子票继续处理");
        }
        if (result == RedisTicketStockService.STOCK_NOT_INITIALIZED) {
            throw new BusinessException("STOCK_SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                    "库存服务尚未就绪，请稍后重试");
        }
        if (result == RedisTicketStockService.STOCK_NOT_ENOUGH) {
            throw new BusinessException("TICKET_SOLD_OUT", HttpStatus.CONFLICT,
                    "当前票档库存不足，请刷新后重新选择");
        }
    }

    public TicketOrder getOrder(Long orderId) {
        if (ticketOrderRepository != null) {
            return ticketOrderRepository.findById(orderId)
                    .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", HttpStatus.NOT_FOUND, "订单不存在"));
        }
        TicketOrder order = orders.get(orderId);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", HttpStatus.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    public TicketOrder getOrderForUser(Long userId, Long orderId) {
        TicketOrder order = getOrder(orderId);
        assertOrderOwner(userId, order);
        return order;
    }

    public List<TicketOrder> listOrdersByUser(Long userId) {
        if (ticketOrderRepository != null) {
            return ticketOrderRepository.findByUserId(userId);
        }
        return orders.values().stream()
                .filter(order -> order.userId().equals(userId))
                .toList();
    }

    @Transactional(noRollbackFor = OrderExpiredException.class)
    public ElectronicTicket payOrder(Long orderId) {
        TicketOrder order = getOrder(orderId);
        if (order.status() == OrderStatus.PAID) {
            return getTicketByOrderId(orderId);
        }
        if (order.status() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException("ORDER_NOT_PAYABLE", HttpStatus.CONFLICT,
                    "当前订单状态不能支付，请返回订单列表查看最新状态");
        }
        LocalDateTime payTime = LocalDateTime.now();
        if (!order.expireTime().isAfter(payTime)) {
            cancelExpiredOrder(orderId);
            throw new OrderExpiredException();
        }
        if (ticketOrderRepository != null) {
            ticketOrderRepository.markPaid(orderId, payTime);
        } else {
            TicketOrder paidOrder = order.paid(payTime);
            orders.put(orderId, paidOrder);
        }

        ElectronicTicket ticket = createElectronicTicket(orderId);
        return ticket;
    }

    @Transactional(noRollbackFor = OrderExpiredException.class)
    public ElectronicTicket payOrderForUser(Long userId, Long orderId) {
        assertOrderOwner(userId, getOrder(orderId));
        return payOrder(orderId);
    }

    public ElectronicTicket getTicketByOrderId(Long orderId) {
        if (electronicTicketRepository != null) {
            return electronicTicketRepository.findByOrderId(orderId)
                    .orElseThrow(() -> new BusinessException("TICKET_NOT_FOUND", HttpStatus.NOT_FOUND, "电子票尚未生成"));
        }
        return tickets.values().stream()
                .filter(ticket -> ticket.orderId().equals(orderId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("TICKET_NOT_FOUND", HttpStatus.NOT_FOUND, "电子票尚未生成"));
    }

    public ElectronicTicket getTicketByOrderIdForUser(Long userId, Long orderId) {
        assertOrderOwner(userId, getOrder(orderId));
        return getTicketByOrderId(orderId);
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
                    .orElseThrow(() -> new BusinessException("TICKET_NOT_FOUND", HttpStatus.NOT_FOUND, "电子票不存在"));
        }
        ElectronicTicket ticket = tickets.get(ticketCode);
        if (ticket == null) {
            throw new BusinessException("TICKET_NOT_FOUND", HttpStatus.NOT_FOUND, "电子票不存在");
        }
        return ticket;
    }

    public ElectronicTicket getTicketForUser(Long userId, String ticketCode) {
        ElectronicTicket ticket = getTicket(ticketCode);
        assertOrderOwner(userId, getOrder(ticket.orderId()));
        return ticket;
    }

    public ElectronicTicket verifyTicket(String ticketCode, Long verifierId) {
        ElectronicTicket ticket = getTicket(ticketCode);
        if (ticket.status() == TicketStatus.VERIFIED) {
            throw new BusinessException("TICKET_ALREADY_VERIFIED", HttpStatus.CONFLICT,
                    "这张电子票已经核验，不能重复入场");
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
            if (cancelExpiredOrder(order.id())) {
                canceled++;
            }
        }
        return canceled;
    }

    @Transactional
    public boolean cancelExpiredOrder(Long orderId) {
        TicketOrder order = getOrder(orderId);
        if (order.status() != OrderStatus.PENDING_PAYMENT || order.expireTime().isAfter(LocalDateTime.now())) {
            return false;
        }
        if (ticketOrderRepository != null) {
            if (!ticketOrderRepository.markCanceledIfPending(order.id(), LocalDateTime.now())) {
                return false;
            }
        } else {
            orders.put(order.id(), order.canceled(LocalDateTime.now()));
        }
        releaseStock(order);
        return true;
    }

    private int cancelExpiredMemoryOrders() {
        LocalDateTime now = LocalDateTime.now();
        int canceled = 0;
        for (TicketOrder order : List.copyOf(orders.values())) {
            if (order.status() == OrderStatus.PENDING_PAYMENT
                    && !order.expireTime().isAfter(now)
                    && cancelExpiredOrder(order.id())) {
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

    private void assertOrderOwner(Long userId, TicketOrder order) {
        if (!order.userId().equals(userId)) {
            throw new BusinessException("ORDER_NOT_FOUND", HttpStatus.NOT_FOUND, "订单不存在");
        }
    }
}
