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
    void timeoutMessageCancelsExpiredPendingOrder() {
        TicketOrder order = ticketingService.grabTicket(602L, 101L, 1001L);

        boolean canceled = ticketingService.cancelExpiredOrder(order.id());

        assertThat(canceled).isTrue();
        assertThat(ticketingService.getOrder(order.id()).status()).isEqualTo(OrderStatus.CANCELED);
        assertThat(eventCatalogService.getTicketCategory(101L, 1001L).remainingStock()).isEqualTo(50);
    }

    @Test
    void rejectsPaymentAfterExpiryAndReleasesPurchaseEligibility() {
        TicketOrder order = ticketingService.grabTicket(601L, 101L, 1001L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ticketingService.payOrder(order.id()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).code()).isEqualTo("ORDER_EXPIRED"));

        assertThat(ticketingService.getOrder(order.id()).status()).isEqualTo(OrderStatus.CANCELED);
        assertThat(eventCatalogService.getTicketCategory(101L, 1001L).remainingStock()).isEqualTo(50);
        TicketOrder replacement = ticketingService.grabTicket(601L, 101L, 1001L);
        assertThat(replacement.id()).isNotEqualTo(order.id());
        assertThat(replacement.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }
}
