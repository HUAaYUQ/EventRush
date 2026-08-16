package com.eventrush.service;

import com.eventrush.domain.ElectronicTicket;
import com.eventrush.domain.OrderStatus;
import com.eventrush.domain.PassengerDocumentType;
import com.eventrush.domain.TicketCategory;
import com.eventrush.domain.TicketOrder;
import com.eventrush.domain.TicketPassenger;
import com.eventrush.domain.TicketRefundResult;
import com.eventrush.domain.TicketStatus;
import com.eventrush.domain.TicketWaitlistRequest;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class TicketingService {

    private static final Logger log = LoggerFactory.getLogger(TicketingService.class);

    private final EventCatalogService eventCatalogService;
    private final AtomicLong orderIdGenerator = new AtomicLong(1);
    private final AtomicLong passengerIdGenerator = new AtomicLong(1);
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
    private final ObjectProvider<TicketWaitlistService> ticketWaitlistService;

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
            ObjectProvider<OrderTimeoutMessagePublisher> orderTimeoutMessagePublisher,
            ObjectProvider<TicketWaitlistService> ticketWaitlistService
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
        this.ticketWaitlistService = ticketWaitlistService;
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
        this.ticketWaitlistService = null;
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
        return grabTicket(
                userId,
                sessionId,
                ticketCategoryId,
                List.of(new TicketPassenger(
                        null,
                        null,
                        1,
                        "压测用户 " + userId,
                        PassengerDocumentType.OTHER,
                        "%04d".formatted(Math.floorMod(userId, 10_000))
                ))
        );
    }

    @Transactional
    public synchronized TicketOrder grabTicket(
            Long userId,
            Long sessionId,
            Long ticketCategoryId,
            int quantity,
            String passengerName,
            PassengerDocumentType passengerDocumentType,
            String passengerDocumentLast4
    ) {
        if (quantity != 1) {
            throw new BusinessException("PASSENGER_COUNT_MISMATCH", HttpStatus.BAD_REQUEST,
                    "订单数量必须与购票人数一致");
        }
        return grabTicket(userId, sessionId, ticketCategoryId, List.of(new TicketPassenger(
                null, null, 1, passengerName, passengerDocumentType, passengerDocumentLast4)));
    }

    @Transactional
    public synchronized TicketOrder grabTicket(
            Long userId,
            Long sessionId,
            Long ticketCategoryId,
            List<TicketPassenger> requestedPassengers
    ) {
        List<TicketPassenger> passengers = validatePassengers(requestedPassengers);
        int quantity = passengers.size();
        checkGrabRateLimit(userId);
        TicketCategory ticketCategory = eventCatalogService.getTicketCategory(sessionId, ticketCategoryId);
        if (hasWaitingQueue(sessionId, ticketCategoryId)) {
            throw new BusinessException("WAITLIST_QUEUE_ACTIVE", HttpStatus.CONFLICT,
                    "该票档正在按候补顺序分配，请加入候补队列");
        }
        if (hasGrabbed(userId, sessionId, ticketCategoryId)) {
            throw new BusinessException("DUPLICATE_GRAB", HttpStatus.CONFLICT,
                    "你已有这个票档的有效订单，请前往我的电子票继续处理");
        }
        if (redisStockEnabled) {
            deductRedisStock(userId, sessionId, ticketCategoryId, quantity);
        }
        eventCatalogService.deductStock(sessionId, ticketCategoryId, quantity);
        LocalDateTime now = LocalDateTime.now();
        TicketOrder order = createPendingOrder(
                userId,
                eventCatalogService.getEventIdBySessionId(sessionId),
                sessionId,
                ticketCategoryId,
                ticketCategory.priceCents(),
                passengers,
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
                        && order.status() != OrderStatus.CANCELED
                        && order.status() != OrderStatus.REFUNDED);
    }

    public boolean hasActiveOrder(Long userId, Long sessionId, Long ticketCategoryId) {
        return hasGrabbed(userId, sessionId, ticketCategoryId);
    }

    private boolean hasWaitingQueue(Long sessionId, Long ticketCategoryId) {
        if (ticketWaitlistService == null) {
            return false;
        }
        TicketWaitlistService service = ticketWaitlistService.getIfAvailable();
        return service != null && service.hasWaiting(sessionId, ticketCategoryId);
    }

    private TicketOrder createPendingOrder(
            Long userId,
            Long eventId,
            Long sessionId,
            Long ticketCategoryId,
            long unitPriceCents,
            List<TicketPassenger> passengers,
            LocalDateTime createdTime,
            LocalDateTime expireTime
    ) {
        int quantity = passengers.size();
        if (ticketOrderRepository != null) {
            return ticketOrderRepository.createPending(
                    userId, eventId, sessionId, ticketCategoryId, unitPriceCents,
                    passengers,
                    createdTime, expireTime);
        }
        long orderId = orderIdGenerator.getAndIncrement();
        List<TicketPassenger> savedPassengers = passengers.stream()
                .map(passenger -> new TicketPassenger(
                        passengerIdGenerator.getAndIncrement(), orderId, passenger.sequence(), passenger.name(),
                        passenger.documentType(), passenger.documentLast4()))
                .toList();
        // ponytail: only used by small unit tests; app runtime writes orders through TicketOrderRepository.
        TicketOrder order = new TicketOrder(
                orderId,
                userId,
                eventId,
                sessionId,
                ticketCategoryId,
                unitPriceCents,
                unitPriceCents * quantity,
                quantity,
                0,
                0,
                savedPassengers,
                OrderStatus.PENDING_PAYMENT,
                createdTime,
                null,
                null,
                null,
                expireTime
        );
        orders.put(order.id(), order);
        return order;
    }

    public List<TicketPassenger> normalizePassengers(List<TicketPassenger> requestedPassengers) {
        return validatePassengers(requestedPassengers);
    }

    private List<TicketPassenger> validatePassengers(List<TicketPassenger> requestedPassengers) {
        if (requestedPassengers == null || requestedPassengers.isEmpty() || requestedPassengers.size() > 5) {
            throw new BusinessException("INVALID_PASSENGER_COUNT", HttpStatus.BAD_REQUEST,
                    "每笔订单请选择 1 到 5 位购票人");
        }
        return IntStream.range(0, requestedPassengers.size())
                .mapToObj(index -> validatePassenger(requestedPassengers.get(index), index + 1))
                .toList();
    }

    @Transactional
    public synchronized TicketOrder createOrderFromWaitlist(TicketWaitlistRequest request) {
        if (hasGrabbed(request.userId(), request.sessionId(), request.ticketCategoryId())) {
            throw new BusinessException("DUPLICATE_GRAB", HttpStatus.CONFLICT,
                    "你已有这个票档的有效订单，请前往我的电子票继续处理");
        }
        boolean redisDeducted = false;
        boolean catalogDeducted = false;
        try {
            if (redisStockEnabled) {
                deductRedisStock(request.userId(), request.sessionId(), request.ticketCategoryId(), request.quantity());
                redisDeducted = true;
            }
            eventCatalogService.deductStock(request.sessionId(), request.ticketCategoryId(), request.quantity());
            catalogDeducted = true;
            LocalDateTime now = LocalDateTime.now();
            TicketOrder order = createPendingOrder(
                    request.userId(), request.eventId(), request.sessionId(), request.ticketCategoryId(),
                    request.unitPriceCents(), request.passengers(), now, now.plusSeconds(orderExpireSeconds));
            publishOrderTimeout(order);
            return order;
        } catch (RuntimeException exception) {
            compensateWaitlistDeduction(request, catalogDeducted, redisDeducted);
            throw exception;
        }
    }

    public synchronized void compensateWaitlistOrderCreation(TicketWaitlistRequest request) {
        compensateWaitlistDeduction(request, true, redisStockEnabled);
    }

    private void compensateWaitlistDeduction(
            TicketWaitlistRequest request,
            boolean catalogDeducted,
            boolean redisDeducted
    ) {
        if (catalogDeducted) {
            eventCatalogService.releaseStock(request.sessionId(), request.ticketCategoryId(), request.quantity());
        }
        if (redisDeducted && redisTicketStockService != null) {
            redisTicketStockService.release(
                    request.userId(), request.sessionId(), request.ticketCategoryId(), request.quantity());
        }
    }

    private TicketPassenger validatePassenger(TicketPassenger passenger, int sequence) {
        String normalizedName = passenger == null || passenger.name() == null ? "" : passenger.name().trim();
        String normalizedLast4 = passenger == null || passenger.documentLast4() == null
                ? "" : passenger.documentLast4().trim().toUpperCase();
        PassengerDocumentType documentType = passenger == null ? null : passenger.documentType();
        if (normalizedName.length() < 2 || normalizedName.length() > 30) {
            throw new BusinessException("INVALID_PASSENGER", HttpStatus.BAD_REQUEST,
                    "第 %d 位购票人姓名长度应为 2 到 30 个字符".formatted(sequence));
        }
        if (documentType == null || !normalizedLast4.matches("[A-Z0-9]{4}")) {
            throw new BusinessException("INVALID_PASSENGER", HttpStatus.BAD_REQUEST,
                    "第 %d 位购票人需要选择证件类型并填写证件号码后四位".formatted(sequence));
        }
        return new TicketPassenger(null, null, sequence, normalizedName, documentType, normalizedLast4);
    }

    private void deductRedisStock(Long userId, Long sessionId, Long ticketCategoryId, int quantity) {
        long result = redisTicketStockService.tryDeduct(userId, sessionId, ticketCategoryId, quantity);
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
    public List<ElectronicTicket> payOrder(Long orderId) {
        TicketOrder order = getOrder(orderId);
        if (order.status() == OrderStatus.PAID) {
            return getTicketsByOrderId(orderId);
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

        return createElectronicTickets(order);
    }

    @Transactional(noRollbackFor = OrderExpiredException.class)
    public List<ElectronicTicket> payOrderForUser(Long userId, Long orderId) {
        assertOrderOwner(userId, getOrder(orderId));
        return payOrder(orderId);
    }

    public List<ElectronicTicket> getTicketsByOrderId(Long orderId) {
        if (electronicTicketRepository != null) {
            List<ElectronicTicket> found = electronicTicketRepository.findByOrderId(orderId);
            if (found.isEmpty()) {
                throw new BusinessException("TICKET_NOT_FOUND", HttpStatus.NOT_FOUND, "电子票尚未生成");
            }
            return found;
        }
        List<ElectronicTicket> found = tickets.values().stream()
                .filter(ticket -> ticket.orderId().equals(orderId))
                .sorted((left, right) -> Long.compare(left.passengerId(), right.passengerId()))
                .toList();
        if (found.isEmpty()) {
            throw new BusinessException("TICKET_NOT_FOUND", HttpStatus.NOT_FOUND, "电子票尚未生成");
        }
        return found;
    }

    public List<ElectronicTicket> getTicketsByOrderIdForUser(Long userId, Long orderId) {
        assertOrderOwner(userId, getOrder(orderId));
        return getTicketsByOrderId(orderId);
    }

    private List<ElectronicTicket> createElectronicTickets(TicketOrder order) {
        return order.passengers().stream()
                .map(passenger -> createElectronicTicket(order.id(), passenger))
                .toList();
    }

    private ElectronicTicket createElectronicTicket(Long orderId, TicketPassenger passenger) {
        String ticketCode = "ER-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        LocalDateTime generatedTime = LocalDateTime.now();
        if (electronicTicketRepository != null) {
            return electronicTicketRepository.create(orderId, passenger.id(), ticketCode, generatedTime);
        }
        ElectronicTicket ticket = new ElectronicTicket(
                ticketIdGenerator.getAndIncrement(),
                orderId,
                passenger.id(),
                passenger.name(),
                passenger.documentType(),
                passenger.documentLast4(),
                ticketCode,
                TicketStatus.VALID,
                generatedTime,
                null,
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
        if (ticket.status() == TicketStatus.REFUNDED) {
            throw new BusinessException("TICKET_REFUNDED", HttpStatus.CONFLICT,
                    "这张电子票已经退票，不能核验入场");
        }
        if (electronicTicketRepository != null) {
            return electronicTicketRepository.markVerified(ticketCode, verifierId, LocalDateTime.now());
        }
        ElectronicTicket verified = ticket.verify(verifierId, LocalDateTime.now());
        tickets.put(ticketCode, verified);
        return verified;
    }

    @Transactional
    public synchronized TicketRefundResult refundTicketsForUser(
            Long userId,
            Long orderId,
            List<String> requestedTicketCodes
    ) {
        TicketOrder order = getOrderForUser(userId, orderId);
        if (order.status() != OrderStatus.PAID
                && order.status() != OrderStatus.PARTIALLY_REFUNDED
                && order.status() != OrderStatus.REFUNDED) {
            throw new BusinessException("ORDER_NOT_REFUNDABLE", HttpStatus.CONFLICT,
                    "当前订单状态不能退票，请刷新订单后重试");
        }

        Set<String> ticketCodes = normalizeRefundTicketCodes(requestedTicketCodes);
        Map<String, ElectronicTicket> orderTickets = getTicketsByOrderId(orderId).stream()
                .collect(java.util.stream.Collectors.toMap(ElectronicTicket::ticketCode, ticket -> ticket));
        List<ElectronicTicket> selectedTickets = ticketCodes.stream()
                .map(ticketCode -> {
                    ElectronicTicket selected = orderTickets.get(ticketCode);
                    if (selected == null) {
                        throw new BusinessException("TICKET_NOT_IN_ORDER", HttpStatus.BAD_REQUEST,
                                "所选电子票不属于当前订单");
                    }
                    return selected;
                })
                .toList();

        if (selectedTickets.stream().anyMatch(ticket -> ticket.status() == TicketStatus.VERIFIED)) {
            throw new BusinessException("TICKET_NOT_REFUNDABLE", HttpStatus.CONFLICT,
                    "已核验入场的电子票不能退票");
        }
        boolean hasValidTicket = selectedTickets.stream()
                .anyMatch(ticket -> ticket.status() == TicketStatus.VALID);
        LocalDateTime refundTime = LocalDateTime.now();
        if (hasValidTicket && !eventCatalogService.getSession(order.sessionId()).startTime().isAfter(refundTime)) {
            throw new BusinessException("REFUND_WINDOW_CLOSED", HttpStatus.CONFLICT,
                    "场次已经开始，当前电子票不能在线退票");
        }

        int newlyRefundedQuantity = 0;
        for (ElectronicTicket selected : selectedTickets) {
            if (selected.status() == TicketStatus.REFUNDED) {
                continue;
            }
            if (electronicTicketRepository != null) {
                if (electronicTicketRepository.markRefunded(selected.ticketCode(), refundTime)) {
                    newlyRefundedQuantity++;
                } else if (getTicket(selected.ticketCode()).status() == TicketStatus.VERIFIED) {
                    throw new BusinessException("TICKET_NOT_REFUNDABLE", HttpStatus.CONFLICT,
                            "已核验入场的电子票不能退票");
                }
            } else {
                tickets.put(selected.ticketCode(), selected.refund(refundTime));
                newlyRefundedQuantity++;
            }
        }

        long newlyRefundedAmountCents = order.unitPriceCents() * newlyRefundedQuantity;
        if (newlyRefundedQuantity > 0) {
            TicketOrder updatedOrder;
            if (ticketOrderRepository != null) {
                updatedOrder = ticketOrderRepository.recordRefund(
                        orderId, newlyRefundedQuantity, newlyRefundedAmountCents, refundTime);
            } else {
                updatedOrder = order.refunded(newlyRefundedQuantity, refundTime);
                orders.put(orderId, updatedOrder);
            }
            releaseRefundedStock(updatedOrder, newlyRefundedQuantity);
        }

        return new TicketRefundResult(
                getOrder(orderId),
                getTicketsByOrderId(orderId),
                newlyRefundedQuantity,
                newlyRefundedAmountCents
        );
    }

    private Set<String> normalizeRefundTicketCodes(List<String> requestedTicketCodes) {
        if (requestedTicketCodes == null || requestedTicketCodes.isEmpty() || requestedTicketCodes.size() > 5) {
            throw new BusinessException("INVALID_REFUND_TICKETS", HttpStatus.BAD_REQUEST,
                    "每次请选择 1 到 5 张电子票");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String ticketCode : requestedTicketCodes) {
            if (ticketCode == null || ticketCode.isBlank()) {
                throw new BusinessException("INVALID_REFUND_TICKETS", HttpStatus.BAD_REQUEST,
                        "退票请求中不能包含空票码");
            }
            normalized.add(ticketCode.trim().toUpperCase());
        }
        return normalized;
    }

    private void releaseRefundedStock(TicketOrder order, int quantity) {
        eventCatalogService.releaseStock(order.sessionId(), order.ticketCategoryId(), quantity);
        if (redisStockEnabled && redisTicketStockService != null) {
            redisTicketStockService.releaseStock(order.sessionId(), order.ticketCategoryId(), quantity);
            if (order.status() == OrderStatus.REFUNDED) {
                redisTicketStockService.closeGrab(order.userId(), order.sessionId(), order.ticketCategoryId());
            }
        }
        notifyReleasedStock(order.sessionId(), order.ticketCategoryId());
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
        eventCatalogService.releaseStock(order.sessionId(), order.ticketCategoryId(), order.quantity());
        if (redisStockEnabled && redisTicketStockService != null) {
            redisTicketStockService.release(
                    order.userId(), order.sessionId(), order.ticketCategoryId(), order.quantity());
        }
        notifyReleasedStock(order.sessionId(), order.ticketCategoryId());
    }

    private void notifyReleasedStock(Long sessionId, Long ticketCategoryId) {
        if (ticketWaitlistService == null) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fulfillReleasedStock(sessionId, ticketCategoryId);
                }
            });
            return;
        }
        fulfillReleasedStock(sessionId, ticketCategoryId);
    }

    private void fulfillReleasedStock(Long sessionId, Long ticketCategoryId) {
        try {
            ticketWaitlistService.ifAvailable(service -> service.fulfillAvailable(sessionId, ticketCategoryId));
        } catch (RuntimeException exception) {
            log.warn("waitlist fulfillment failed after stock release, sessionId={}, ticketCategoryId={}",
                    sessionId, ticketCategoryId, exception);
        }
    }

    private void assertOrderOwner(Long userId, TicketOrder order) {
        if (!order.userId().equals(userId)) {
            throw new BusinessException("ORDER_NOT_FOUND", HttpStatus.NOT_FOUND, "订单不存在");
        }
    }
}
