package com.eventrush.service;

import com.eventrush.domain.ElectronicTicket;
import com.eventrush.domain.OrderStatus;
import com.eventrush.domain.PassengerDocumentType;
import com.eventrush.domain.TicketOrder;
import com.eventrush.domain.TicketPassenger;
import com.eventrush.domain.TicketStatus;
import java.util.List;
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

        TicketOrder order = ticketingService.grabTicket(1L, 101L, 1001L, List.of(
                passenger(" 张三 ", PassengerDocumentType.ID_CARD, "a123"),
                passenger("李四", PassengerDocumentType.PASSPORT, "9x8p")
        ));
        List<ElectronicTicket> tickets = ticketingService.payOrder(order.id());
        ElectronicTicket verified = ticketingService.verifyTicket(tickets.get(0).ticketCode(), 99L);

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.unitPriceCents()).isEqualTo(19900);
        assertThat(order.amountCents()).isEqualTo(39800);
        assertThat(order.quantity()).isEqualTo(2);
        assertThat(order.passengers()).extracting(TicketPassenger::name).containsExactly("张三", "李四");
        assertThat(order.passengers()).extracting(TicketPassenger::documentLast4).containsExactly("A123", "9X8P");
        assertThat(tickets).hasSize(2).allMatch(ticket -> ticket.status() == TicketStatus.VALID);
        assertThat(tickets).extracting(ElectronicTicket::ticketCode).doesNotHaveDuplicates();
        assertThat(verified.status()).isEqualTo(TicketStatus.VERIFIED);
        assertThat(verified.verifierId()).isEqualTo(99L);
        assertThat(ticketingService.getTicket(tickets.get(1).ticketCode()).status()).isEqualTo(TicketStatus.VALID);
    }

    @Test
    void rejectsMoreThanFivePassengers() {
        EventCatalogService catalogService = new EventCatalogService();
        catalogService.seedData();
        TicketingService ticketingService = new TicketingService(catalogService);

        List<TicketPassenger> passengers = java.util.stream.IntStream.rangeClosed(1, 6)
                .mapToObj(index -> passenger("购票人" + index, PassengerDocumentType.OTHER, "%04d".formatted(index)))
                .toList();

        assertThatThrownBy(() -> ticketingService.grabTicket(1L, 101L, 1001L, passengers))
                .isInstanceOf(BusinessException.class)
                .hasMessage("每笔订单请选择 1 到 5 位购票人");
    }

    @Test
    void rejectsDuplicateVerification() {
        EventCatalogService catalogService = new EventCatalogService();
        catalogService.seedData();
        TicketingService ticketingService = new TicketingService(catalogService);

        TicketOrder order = ticketingService.grabTicket(1L, 101L, 1001L);
        ElectronicTicket ticket = ticketingService.payOrder(order.id()).get(0);
        ticketingService.verifyTicket(ticket.ticketCode(), 99L);

        assertThatThrownBy(() -> ticketingService.verifyTicket(ticket.ticketCode(), 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("这张电子票已经核验，不能重复入场");
    }

    @Test
    void repeatedPaymentReturnsSameTicket() {
        EventCatalogService catalogService = new EventCatalogService();
        catalogService.seedData();
        TicketingService ticketingService = new TicketingService(catalogService);
        TicketOrder order = ticketingService.grabTicket(1L, 101L, 1001L);

        List<ElectronicTicket> first = ticketingService.payOrder(order.id());
        List<ElectronicTicket> second = ticketingService.payOrder(order.id());

        assertThat(second).extracting(ElectronicTicket::id)
                .containsExactlyElementsOf(first.stream().map(ElectronicTicket::id).toList());
        assertThat(second).extracting(ElectronicTicket::ticketCode)
                .containsExactlyElementsOf(first.stream().map(ElectronicTicket::ticketCode).toList());
    }

    @Test
    void rejectsDuplicateGrabForSameUserAndTicketCategory() {
        EventCatalogService catalogService = new EventCatalogService();
        catalogService.seedData();
        TicketingService ticketingService = new TicketingService(catalogService);

        ticketingService.grabTicket(1L, 101L, 1001L);

        assertThatThrownBy(() -> ticketingService.grabTicket(1L, 101L, 1001L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("你已有这个票档的有效订单，请前往我的电子票继续处理");
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
                .hasMessage("当前票档库存不足，请刷新后重新选择");
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
                        if ("TICKET_SOLD_OUT".equals(exception.code())) {
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

    private TicketPassenger passenger(
            String name,
            PassengerDocumentType documentType,
            String documentLast4
    ) {
        return new TicketPassenger(null, null, 0, name, documentType, documentLast4);
    }
}
