package com.eventrush.service;

import com.eventrush.domain.ElectronicTicket;
import com.eventrush.domain.OrderStatus;
import com.eventrush.domain.TicketOrder;
import com.eventrush.domain.TicketStatus;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketingServiceTest {

    @Test
    void completesGrabPayAndVerifyFlow() {
        EventCatalogService catalogService = new EventCatalogService();
        catalogService.seedData();
        TicketingService ticketingService = new TicketingService(catalogService);

        TicketOrder order = ticketingService.grabTicket(1L, 101L, 1001L);
        ElectronicTicket ticket = ticketingService.payOrder(order.id());
        ElectronicTicket verified = ticketingService.verifyTicket(ticket.ticketCode(), 99L);

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(ticket.status()).isEqualTo(TicketStatus.VALID);
        assertThat(verified.status()).isEqualTo(TicketStatus.VERIFIED);
        assertThat(verified.verifierId()).isEqualTo(99L);
    }

    @Test
    void rejectsDuplicateVerification() {
        EventCatalogService catalogService = new EventCatalogService();
        catalogService.seedData();
        TicketingService ticketingService = new TicketingService(catalogService);

        TicketOrder order = ticketingService.grabTicket(1L, 101L, 1001L);
        ElectronicTicket ticket = ticketingService.payOrder(order.id());
        ticketingService.verifyTicket(ticket.ticketCode(), 99L);

        assertThatThrownBy(() -> ticketingService.verifyTicket(ticket.ticketCode(), 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("ticket has already been verified");
    }

    @Test
    void repeatedPaymentReturnsSameTicket() {
        EventCatalogService catalogService = new EventCatalogService();
        catalogService.seedData();
        TicketingService ticketingService = new TicketingService(catalogService);
        TicketOrder order = ticketingService.grabTicket(1L, 101L, 1001L);

        ElectronicTicket first = ticketingService.payOrder(order.id());
        ElectronicTicket second = ticketingService.payOrder(order.id());

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.ticketCode()).isEqualTo(first.ticketCode());
    }

    @Test
    void rejectsDuplicateGrabForSameUserAndTicketCategory() {
        EventCatalogService catalogService = new EventCatalogService();
        catalogService.seedData();
        TicketingService ticketingService = new TicketingService(catalogService);

        ticketingService.grabTicket(1L, 101L, 1001L);

        assertThatThrownBy(() -> ticketingService.grabTicket(1L, 101L, 1001L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("user has already grabbed this ticket");
    }

    @Test
    void rejectsGrabWhenStockIsInsufficient() {
        EventCatalogService catalogService = new EventCatalogService();
        catalogService.seedData();
        TicketingService ticketingService = new TicketingService(catalogService);

        for (long userId = 1; userId <= 10; userId++) {
            ticketingService.grabTicket(userId, 101L, 1002L);
        }

        assertThatThrownBy(() -> ticketingService.grabTicket(11L, 101L, 1002L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("ticket stock is insufficient");
    }

    @Test
    void concurrentGrabDoesNotOversell() throws InterruptedException {
        EventCatalogService catalogService = new EventCatalogService();
        catalogService.seedData();
        TicketingService ticketingService = new TicketingService(catalogService);
        int users = 40;
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger insufficientCount = new AtomicInteger();
        CountDownLatch startLine = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(users);
        ExecutorService executor = Executors.newFixedThreadPool(8);

        try {
            for (long userId = 1; userId <= users; userId++) {
                long currentUserId = userId;
                executor.submit(() -> {
                    try {
                        startLine.await();
                        ticketingService.grabTicket(currentUserId, 101L, 1002L);
                        successCount.incrementAndGet();
                    } catch (BusinessException exception) {
                        if ("ticket stock is insufficient".equals(exception.getMessage())) {
                            insufficientCount.incrementAndGet();
                        } else {
                            throw exception;
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finishLine.countDown();
                    }
                });
            }

            startLine.countDown();

            assertThat(finishLine.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(successCount.get()).isEqualTo(10);
        assertThat(insufficientCount.get()).isEqualTo(30);
        assertThat(catalogService.getTicketCategory(101L, 1002L).remainingStock()).isZero();
    }
}
