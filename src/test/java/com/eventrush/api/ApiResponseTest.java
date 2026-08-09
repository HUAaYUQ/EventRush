package com.eventrush.api;

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
        "eventrush.stock.redis-enabled=false"
})
@AutoConfigureMockMvc
@Sql(statements = {
        "DELETE FROM electronic_ticket",
        "DELETE FROM async_grab_request",
        "DELETE FROM ticket_order"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ApiResponseTest {

    @Autowired
    private MockMvc mockMvc;

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
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Trace-Id", "trace-business"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("event not found"))
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
}
