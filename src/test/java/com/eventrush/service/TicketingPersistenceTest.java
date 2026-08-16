package com.eventrush.service;

import com.eventrush.domain.ElectronicTicket;
import com.eventrush.domain.OrderStatus;
import com.eventrush.domain.TicketOrder;
import com.eventrush.domain.TicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eventrush-test;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "eventrush.stock.redis-enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Sql(statements = {
        "DELETE FROM electronic_ticket",
        "DELETE FROM ticket_order"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class TicketingPersistenceTest {

    @Autowired
    private TicketingService ticketingService;

    @Autowired
    private TicketOrderRepository ticketOrderRepository;

    @Autowired
    private ElectronicTicketRepository electronicTicketRepository;

    @Test
    void persistsOrderAndPaymentStatus() {
        TicketOrder order = ticketingService.grabTicket(300L, 101L, 1001L);

        assertThat(ticketOrderRepository.findById(order.id()))
                .get()
                .satisfies(persisted -> {
                    assertThat(persisted.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
                    assertThat(persisted.amountCents()).isEqualTo(19900);
                });

        ticketingService.payOrder(order.id());

        assertThat(ticketOrderRepository.findById(order.id()))
                .get()
                .extracting(TicketOrder::status)
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void databaseRejectsDuplicateGrab() {
        ticketingService.grabTicket(301L, 101L, 1001L);

        assertThatThrownBy(() -> ticketingService.grabTicket(301L, 101L, 1001L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("你已有这个票档的有效订单，请前往我的电子票继续处理");
    }

    @Test
    void persistsTicketAndVerificationStatus() {
        TicketOrder order = ticketingService.grabTicket(302L, 101L, 1001L);
        ElectronicTicket ticket = ticketingService.payOrder(order.id());

        assertThat(electronicTicketRepository.findByCode(ticket.ticketCode()))
                .get()
                .extracting(ElectronicTicket::status)
                .isEqualTo(TicketStatus.VALID);

        ticketingService.verifyTicket(ticket.ticketCode(), 99L);

        assertThat(electronicTicketRepository.findByCode(ticket.ticketCode()))
                .get()
                .satisfies(verified -> {
                    assertThat(verified.status()).isEqualTo(TicketStatus.VERIFIED);
                    assertThat(verified.verifierId()).isEqualTo(99L);
                });

        assertThatThrownBy(() -> ticketingService.verifyTicket(ticket.ticketCode(), 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("这张电子票已经核验，不能重复入场");
    }

    @Test
    void repeatedPaymentReturnsExistingTicket() {
        TicketOrder order = ticketingService.grabTicket(303L, 101L, 1001L);
        ElectronicTicket first = ticketingService.payOrder(order.id());

        ElectronicTicket second = ticketingService.payOrder(order.id());

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.ticketCode()).isEqualTo(first.ticketCode());
        assertThat(electronicTicketRepository.findByOrderId(order.id())).get()
                .extracting(ElectronicTicket::ticketCode)
                .isEqualTo(first.ticketCode());
    }
}
