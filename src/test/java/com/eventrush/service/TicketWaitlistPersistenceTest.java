package com.eventrush.service;

import com.eventrush.domain.ElectronicTicket;
import com.eventrush.domain.OrderStatus;
import com.eventrush.domain.PassengerDocumentType;
import com.eventrush.domain.TicketOrder;
import com.eventrush.domain.TicketPassenger;
import com.eventrush.domain.TicketWaitlistRequest;
import com.eventrush.domain.WaitlistStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eventrush-waitlist-test;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "eventrush.stock.redis-enabled=false",
        "eventrush.catalog.seed-enabled=true"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Sql(statements = {
        "DELETE FROM ticket_waitlist_passenger",
        "DELETE FROM ticket_waitlist",
        "DELETE FROM electronic_ticket",
        "DELETE FROM ticket_order_passenger",
        "DELETE FROM ticket_order"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class TicketWaitlistPersistenceTest {

    @Autowired
    private TicketingService ticketingService;

    @Autowired
    private TicketWaitlistService waitlistService;

    @Autowired
    private TicketWaitlistRepository waitlistRepository;

    @Autowired
    private EventCatalogService eventCatalogService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void waitlistApiUsesUnifiedResponseAndTraceId() throws Exception {
        exhaustVipStock();

        mockMvc.perform(post("/api/users/750/waitlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": 101,
                                  "ticketCategoryId": 1002,
                                  "passengers": [
                                    {"name": "接口候补", "documentType": "ID_CARD", "documentLast4": "0750"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.waitingAhead").value(0));
    }

    @Test
    void onlyAllowsWaitlistWhenRequestedQuantityExceedsStock() {
        assertThatThrownBy(() -> waitlistService.join(
                700L, 101L, 1002L, List.of(passenger("候补甲", "7001"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).code())
                        .isEqualTo("WAITLIST_NOT_AVAILABLE"));

        exhaustVipStock();
        TicketWaitlistRequest request = waitlistService.join(
                700L, 101L, 1002L, List.of(passenger("候补甲", "7001")));

        assertThat(request.status()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(request.waitingAhead()).isZero();
        assertThat(request.passengers()).extracting(TicketPassenger::name).containsExactly("候补甲");
        assertThat(waitlistRepository.findById(request.id())).isPresent();
        assertThatThrownBy(() -> waitlistService.join(
                700L, 101L, 1002L, List.of(passenger("候补甲", "7001"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).code())
                        .isEqualTo("DUPLICATE_WAITLIST"));
    }

    @Test
    void fulfillsOldestGroupWithoutSplittingAndCreatesPendingOrder() {
        List<ElectronicTicket> refundableTickets = exhaustVipStockWithTwoPaidOrders();
        TicketWaitlistRequest first = waitlistService.join(801L, 101L, 1002L, List.of(
                passenger("候补甲", "8001"),
                passenger("候补乙", "8002")
        ));
        TicketWaitlistRequest second = waitlistService.join(
                802L, 101L, 1002L, List.of(passenger("候补丙", "8003")));

        assertThat(waitlistService.getForUser(802L, second.id()).waitingAhead()).isEqualTo(1);

        ticketingService.refundTicketsForUser(
                ticketingService.getOrder(refundableTickets.get(0).orderId()).userId(),
                refundableTickets.get(0).orderId(),
                List.of(refundableTickets.get(0).ticketCode())
        );

        assertThat(waitlistService.getForUser(801L, first.id()).status()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(waitlistService.getForUser(802L, second.id()).status()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(eventCatalogService.getTicketCategory(101L, 1002L).remainingStock()).isEqualTo(1);
        assertThatThrownBy(() -> ticketingService.grabTicket(803L, 101L, 1002L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).code())
                        .isEqualTo("WAITLIST_QUEUE_ACTIVE"));

        ticketingService.refundTicketsForUser(
                ticketingService.getOrder(refundableTickets.get(1).orderId()).userId(),
                refundableTickets.get(1).orderId(),
                List.of(refundableTickets.get(1).ticketCode())
        );

        TicketWaitlistRequest fulfilled = waitlistService.getForUser(801L, first.id());
        TicketWaitlistRequest stillWaiting = waitlistService.getForUser(802L, second.id());
        TicketOrder fulfilledOrder = ticketingService.getOrderForUser(801L, fulfilled.orderId());
        assertThat(fulfilled.status()).isEqualTo(WaitlistStatus.FULFILLED);
        assertThat(fulfilled.paymentExpireTime()).isEqualTo(fulfilledOrder.expireTime());
        assertThat(fulfilledOrder.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(fulfilledOrder.quantity()).isEqualTo(2);
        assertThat(stillWaiting.status()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(eventCatalogService.getTicketCategory(101L, 1002L).remainingStock()).isZero();

        assertThat(waitlistService.fulfillAvailable(101L, 1002L)).isZero();
        assertThat(ticketingService.listOrdersByUser(801L)).hasSize(1);
    }

    @Test
    void allowsOwnerToCancelAndHidesWaitlistFromOtherUsers() {
        exhaustVipStock();
        TicketWaitlistRequest request = waitlistService.join(
                901L, 101L, 1002L, List.of(passenger("候补用户", "9001")));

        assertThatThrownBy(() -> waitlistService.getForUser(902L, request.id()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).code())
                        .isEqualTo("WAITLIST_NOT_FOUND"));

        TicketWaitlistRequest canceled = waitlistService.cancelForUser(901L, request.id());
        assertThat(canceled.status()).isEqualTo(WaitlistStatus.CANCELED);
        assertThat(canceled.canceledTime()).isNotNull();
        assertThatThrownBy(() -> waitlistService.cancelForUser(901L, request.id()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).code())
                        .isEqualTo("WAITLIST_NOT_CANCELABLE"));
    }

    private void exhaustVipStock() {
        for (long userId = 10_000L; userId < 10_010L; userId++) {
            ticketingService.grabTicket(userId, 101L, 1002L);
        }
    }

    private List<ElectronicTicket> exhaustVipStockWithTwoPaidOrders() {
        List<ElectronicTicket> refundableTickets = new ArrayList<>();
        for (long userId = 20_000L; userId < 20_010L; userId++) {
            TicketOrder order = ticketingService.grabTicket(userId, 101L, 1002L);
            if (refundableTickets.size() < 2) {
                refundableTickets.add(ticketingService.payOrder(order.id()).get(0));
            }
        }
        return refundableTickets;
    }

    private TicketPassenger passenger(String name, String documentLast4) {
        return new TicketPassenger(null, null, 0, name, PassengerDocumentType.ID_CARD, documentLast4);
    }
}
