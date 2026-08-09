package com.eventrush.service;

import com.eventrush.domain.OrderStatus;
import com.eventrush.domain.TicketOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eventrush-timeout-test;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "eventrush.stock.redis-enabled=false",
        "eventrush.order.expire-seconds=-1",
        "eventrush.order.timeout-scan-ms=600000"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Sql(statements = {
        "DELETE FROM electronic_ticket",
        "DELETE FROM ticket_order"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OrderTimeoutServiceTest {

    @Autowired
    private EventCatalogService eventCatalogService;

    @Autowired
    private TicketingService ticketingService;

    @Test
    void cancelsExpiredPendingOrderAndReleasesStock() {
        TicketOrder order = ticketingService.grabTicket(600L, 101L, 1001L);

        int canceled = ticketingService.cancelExpiredOrders();

        assertThat(canceled).isEqualTo(1);
        assertThat(ticketingService.getOrder(order.id()).status()).isEqualTo(OrderStatus.CANCELED);
        assertThat(eventCatalogService.getTicketCategory(101L, 1001L).remainingStock()).isEqualTo(50);
    }

    @Test
    void doesNotCancelPaidOrder() {
        TicketOrder order = ticketingService.grabTicket(601L, 101L, 1001L);
        ticketingService.payOrder(order.id());

        int canceled = ticketingService.cancelExpiredOrders();

        assertThat(canceled).isZero();
        assertThat(ticketingService.getOrder(order.id()).status()).isEqualTo(OrderStatus.PAID);
    }
}
