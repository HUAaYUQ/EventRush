package com.eventrush.service;

import com.eventrush.domain.ElectronicTicket;
import com.eventrush.domain.OrderStatus;
import com.eventrush.domain.TicketOrder;
import com.eventrush.domain.TicketStatus;
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
}
