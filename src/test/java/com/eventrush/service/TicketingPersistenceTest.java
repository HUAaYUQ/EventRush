package com.eventrush.service;

import com.eventrush.domain.ElectronicTicket;
import com.eventrush.domain.OrderStatus;
import com.eventrush.domain.PassengerDocumentType;
import com.eventrush.domain.TicketOrder;
import com.eventrush.domain.TicketPassenger;
import com.eventrush.domain.TicketStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eventrush-test;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "eventrush.stock.redis-enabled=false",
        "eventrush.catalog.seed-enabled=true"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Sql(statements = {
        "DELETE FROM electronic_ticket",
        "DELETE FROM ticket_order_passenger",
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
        TicketOrder order = ticketingService.grabTicket(300L, 101L, 1001L, List.of(
                passenger("李雷", PassengerDocumentType.PASSPORT, "8X2P"),
                passenger("韩梅梅", PassengerDocumentType.ID_CARD, "1024")
        ));

        assertThat(ticketOrderRepository.findById(order.id()))
                .get()
                .satisfies(persisted -> {
                    assertThat(persisted.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
                    assertThat(persisted.amountCents()).isEqualTo(39800);
                    assertThat(persisted.quantity()).isEqualTo(2);
                    assertThat(persisted.passengers()).extracting(TicketPassenger::name)
                            .containsExactly("李雷", "韩梅梅");
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
        ElectronicTicket ticket = ticketingService.payOrder(order.id()).get(0);

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
        List<ElectronicTicket> first = ticketingService.payOrder(order.id());

        List<ElectronicTicket> second = ticketingService.payOrder(order.id());

        assertThat(second).extracting(ElectronicTicket::id)
                .containsExactlyElementsOf(first.stream().map(ElectronicTicket::id).toList());
        assertThat(electronicTicketRepository.findByOrderId(order.id()))
                .extracting(ElectronicTicket::ticketCode)
                .containsExactlyElementsOf(first.stream().map(ElectronicTicket::ticketCode).toList());
    }

    @Test
    void persistsPartialRefundAndKeepsRepeatedRequestIdempotent() {
        TicketOrder order = ticketingService.grabTicket(304L, 101L, 1001L, List.of(
                passenger("李雷", PassengerDocumentType.PASSPORT, "8X2P"),
                passenger("韩梅梅", PassengerDocumentType.ID_CARD, "1024")
        ));
        List<ElectronicTicket> tickets = ticketingService.payOrder(order.id());

        ticketingService.refundTicketsForUser(304L, order.id(), List.of(tickets.get(0).ticketCode()));
        var repeated = ticketingService.refundTicketsForUser(
                304L, order.id(), List.of(tickets.get(0).ticketCode()));

        assertThat(repeated.newlyRefundedQuantity()).isZero();
        assertThat(ticketOrderRepository.findById(order.id()))
                .get()
                .satisfies(refunded -> {
                    assertThat(refunded.status()).isEqualTo(OrderStatus.PARTIALLY_REFUNDED);
                    assertThat(refunded.refundedQuantity()).isEqualTo(1);
                    assertThat(refunded.refundedAmountCents()).isEqualTo(19900);
                    assertThat(refunded.refundTime()).isNotNull();
                });
        assertThat(electronicTicketRepository.findByOrderId(order.id()).get(0))
                .satisfies(refunded -> {
                    assertThat(refunded.status()).isEqualTo(TicketStatus.REFUNDED);
                    assertThat(refunded.refundedTime()).isNotNull();
                });

        ticketingService.refundTicketsForUser(304L, order.id(), List.of(tickets.get(1).ticketCode()));

        assertThat(ticketOrderRepository.findById(order.id()))
                .get()
                .satisfies(refunded -> {
                    assertThat(refunded.status()).isEqualTo(OrderStatus.REFUNDED);
                    assertThat(refunded.refundedQuantity()).isEqualTo(2);
                    assertThat(refunded.refundedAmountCents()).isEqualTo(39800);
                });
    }

    private TicketPassenger passenger(
            String name,
            PassengerDocumentType documentType,
            String documentLast4
    ) {
        return new TicketPassenger(null, null, 0, name, documentType, documentLast4);
    }
}
