package com.eventrush.api;

import com.eventrush.domain.ElectronicTicket;
import com.eventrush.domain.TicketOrder;
import com.eventrush.service.TicketingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eventrush-api-test;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "eventrush.stock.redis-enabled=false",
        "eventrush.admin.key=stage18-test-admin-key"
})
@AutoConfigureMockMvc
@Sql(statements = {
        "DELETE FROM electronic_ticket",
        "DELETE FROM async_grab_request",
        "DELETE FROM ticket_order"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ApiResponseTest {

    private static final String ADMIN_KEY = "stage18-test-admin-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketingService ticketingService;

    @Test
    void wrapsSuccessResponse() throws Exception {
        mockMvc.perform(get("/api/events").header("X-Trace-Id", "trace-success"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "trace-success"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.traceId").value("trace-success"))
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void generatesTraceIdWhenMissing() throws Exception {
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void wrapsBusinessErrorResponse() throws Exception {
        mockMvc.perform(get("/api/events/999").header("X-Trace-Id", "trace-business"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Trace-Id", "trace-business"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("活动不存在"))
                .andExpect(jsonPath("$.traceId").value("trace-business"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void wrapsValidationErrorResponse() throws Exception {
        mockMvc.perform(post("/api/orders/grab")
                        .header("X-Trace-Id", "trace-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Trace-Id", "trace-validation"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("sessionId is required"))
                .andExpect(jsonPath("$.traceId").value("trace-validation"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void adminQueriesOrdersAndTickets() throws Exception {
        TicketOrder order = ticketingService.grabTicket(9800L, 101L, 1001L);
        ElectronicTicket ticket = ticketingService.payOrder(order.id());

        mockMvc.perform(get("/api/admin/users/9800/orders")
                        .header("X-Admin-Key", ADMIN_KEY)
                        .header("X-Trace-Id", "trace-admin-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.traceId").value("trace-admin-orders"))
                .andExpect(jsonPath("$.data[0].id").value(order.id()))
                .andExpect(jsonPath("$.data[0].status").value("PAID"));

        mockMvc.perform(get("/api/admin/orders/%s/ticket".formatted(order.id()))
                        .header("X-Admin-Key", ADMIN_KEY)
                        .header("X-Trace-Id", "trace-admin-order-ticket"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ticketCode").value(ticket.ticketCode()))
                .andExpect(jsonPath("$.data.status").value("VALID"));

        mockMvc.perform(get("/api/admin/tickets/%s".formatted(ticket.ticketCode()))
                        .header("X-Admin-Key", ADMIN_KEY)
                        .header("X-Trace-Id", "trace-admin-ticket"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value(order.id()))
                .andExpect(jsonPath("$.data.ticketCode").value(ticket.ticketCode()));
    }

    @Test
    void userCanRecoverOrdersAndTicketFromOrderList() throws Exception {
        TicketOrder order = ticketingService.grabTicket(9810L, 101L, 1001L);
        ElectronicTicket ticket = ticketingService.payOrder(order.id());

        mockMvc.perform(get("/api/users/9810/orders").header("X-Trace-Id", "trace-user-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(order.id()))
                .andExpect(jsonPath("$.data[0].amountCents").value(19900));

        mockMvc.perform(get("/api/users/9810/orders/%s/ticket".formatted(order.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticketCode").value(ticket.ticketCode()));

        mockMvc.perform(get("/api/users/9811/orders/%s/ticket".formatted(order.id())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        mockMvc.perform(get("/api/users/9810/tickets/%s".formatted(ticket.ticketCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(order.id()));

        mockMvc.perform(get("/api/users/9811/tickets/%s".formatted(ticket.ticketCode())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void rejectsAdminRequestWithoutAdminKey() throws Exception {
        mockMvc.perform(get("/api/admin/users/9800/orders").header("X-Trace-Id", "trace-admin-denied"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Trace-Id", "trace-admin-denied"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("admin key is invalid"))
                .andExpect(jsonPath("$.traceId").value("trace-admin-denied"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void rejectsAdminRequestWithDefaultKeyWhenConfiguredKeyOverrides() throws Exception {
        mockMvc.perform(get("/api/admin/users/9800/orders")
                        .header("X-Admin-Key", "eventrush-admin-key")
                        .header("X-Trace-Id", "trace-admin-old-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Trace-Id", "trace-admin-old-key"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("admin key is invalid"))
                .andExpect(jsonPath("$.traceId").value("trace-admin-old-key"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
