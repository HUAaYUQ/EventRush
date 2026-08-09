package com.eventrush.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderTimeoutScheduler {

    private final TicketingService ticketingService;

    public OrderTimeoutScheduler(TicketingService ticketingService) {
        this.ticketingService = ticketingService;
    }

    @Scheduled(fixedDelayString = "${eventrush.order.timeout-scan-ms:5000}")
    void cancelExpiredOrders() {
        ticketingService.cancelExpiredOrders();
    }
}
