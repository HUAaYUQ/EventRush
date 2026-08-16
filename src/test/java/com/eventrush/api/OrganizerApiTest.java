package com.eventrush.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eventrush-organizer-test;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "eventrush.organizer.key=stage53-organizer-key",
        "eventrush.stock.redis-enabled=false"
})
@AutoConfigureMockMvc
class OrganizerApiTest {

    private static final String KEY = "stage53-organizer-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void rejectsOrganizerRequestWithoutKey() throws Exception {
        mockMvc.perform(get("/api/organizer/events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("主办方访问密钥无效"));
    }

    @Test
    void publishesDraftIntoPublicCatalog() throws Exception {
        Long eventId = dataId(mockMvc.perform(post("/api/organizer/events")
                        .header("X-Organizer-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Stage 53 发布演示",
                                  "location":"EventRush 一号馆",
                                  "description":"主办方发布闭环验收活动",
                                  "posterUrl":"/images/events/campus-music-night.jpg"
                                }
                                """))
                .andExpect(status().isOk()).andReturn());

        Long sessionId = dataId(mockMvc.perform(post(
                        "/api/organizer/events/%d/sessions".formatted(eventId))
                        .header("X-Organizer-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startTime":"2026-09-01T19:00:00","endTime":"2026-09-01T21:00:00"}
                                """))
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(post("/api/organizer/events/%d/sessions/%d/ticket-categories"
                        .formatted(eventId, sessionId))
                        .header("X-Organizer-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"预售票","priceCents":9900,"totalStock":120}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingStock").value(120));

        mockMvc.perform(post("/api/organizer/events/%d/publish".formatted(eventId))
                        .header("X-Organizer-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(get("/api/events/%d".formatted(eventId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Stage 53 发布演示"))
                .andExpect(jsonPath("$.data.sessions[0].ticketCategories[0].name").value("预售票"));
    }

    private Long dataId(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("id").longValue();
    }
}
