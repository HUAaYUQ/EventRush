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
import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eventrush-organizer-test;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "eventrush.organizer.key=stage53-organizer-key",
        "eventrush.media.upload-dir=target/test-media",
        "eventrush.stock.redis-enabled=false",
        "eventrush.catalog.seed-enabled=true"
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
    void uploadsHomepageImageForOrganizer() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "stage56.png", "image/png", "stage56-image".getBytes());

        mockMvc.perform(multipart("/api/organizer/media/images")
                        .file(file)
                        .header("X-Organizer-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value(org.hamcrest.Matchers.startsWith("/media/")))
                .andExpect(jsonPath("$.data.contentType").value("image/png"));
    }

    @Test
    void managesPublicEventCategories() throws Exception {
        Long categoryId = dataId(mockMvc.perform(post("/api/organizer/catalog/categories")
                        .header("X-Organizer-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"现场娱乐","iconKey":"ticket","contentProfile":"EXHIBITION","displayOrder":5,"enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("现场娱乐"))
                .andExpect(jsonPath("$.data.contentProfile").value("EXHIBITION"))
                .andReturn());

        mockMvc.perform(get("/api/catalog/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id")
                        .value(org.hamcrest.Matchers.hasItem(categoryId.intValue())));

        mockMvc.perform(put("/api/organizer/catalog/categories/%d".formatted(categoryId))
                        .header("X-Organizer-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"现场娱乐","iconKey":"ticket","contentProfile":"SPORTS","displayOrder":5,"enabled":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.contentProfile").value("SPORTS"));

        mockMvc.perform(get("/api/catalog/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.hasItem(categoryId.intValue()))));
    }

    @Test
    void publishesDraftIntoPublicCatalog() throws Exception {
        Long eventId = dataId(mockMvc.perform(post("/api/organizer/events")
                        .header("X-Organizer-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"春季音乐现场",
                                  "categoryId":1,
                                  "city":"上海",
                                  "venueName":"EventRush 一号馆",
                                  "venueAddress":"浦东新区海岸路 18 号",
                                  "description":"主办方发布闭环验收活动",
                                  "posterUrl":"/media/test-project.jpg",
                                  "durationMinutes":120,
                                  "saleStartTime":"2026-08-20T10:00:00",
                                  "saleEndTime":"2026-08-31T23:00:00",
                                  "purchaseLimit":4,
                                  "realNameRule":"REQUIRED",
                                  "entryMethod":"E_TICKET",
                                  "refundRule":"开演前 48 小时可申请退票。",
                                  "waitlistEnabled":true,
                                  "rules":[
                                    {"ruleGroup":"PURCHASE","ruleCode":"CHILD_POLICY","title":"儿童购票","content":"儿童也需要实名购票。","displayOrder":0},
                                    {"ruleGroup":"ATTENDANCE","ruleCode":"ENTRY_TIME","title":"入场时间","content":"请提前 30 分钟到场。","displayOrder":10}
                                  ],
                                  "detailSections":[
                                    {"sectionType":"RICH_TEXT","title":"演出介绍","content":"首版演出详情。","imageUrl":"","displayOrder":0},
                                    {"sectionType":"IMAGE","title":"演出现场","content":"","imageUrl":"/media/detail-first.jpg","displayOrder":10}
                                  ]
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

        Long categoryId = dataId(mockMvc.perform(post("/api/organizer/events/%d/sessions/%d/ticket-categories"
                        .formatted(eventId, sessionId))
                        .header("X-Organizer-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"预售票","priceCents":9900,"totalStock":120}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingStock").value(120))
                .andReturn());

        mockMvc.perform(post("/api/organizer/events/%d/publish".formatted(eventId))
                        .header("X-Organizer-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(put("/api/organizer/events/%d/sessions/%d".formatted(eventId, sessionId))
                        .header("X-Organizer-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startTime":"2026-09-01T20:00:00","endTime":"2026-09-01T22:00:00"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/organizer/events/%d/sessions/%d/ticket-categories/%d"
                        .formatted(eventId, sessionId, categoryId))
                        .header("X-Organizer-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"正式票","priceCents":12900,"totalStock":120}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/organizer/events/%d".formatted(eventId))
                        .header("X-Organizer-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"春季音乐现场·加场",
                                  "categoryId":1,
                                  "city":"上海",
                                  "venueName":"EventRush 一号馆",
                                  "venueAddress":"浦东新区海岸路 18 号",
                                  "description":"主办方发布闭环验收活动",
                                  "posterUrl":"/media/test-project.jpg",
                                  "durationMinutes":120,
                                  "saleStartTime":"2026-08-20T10:00:00",
                                  "saleEndTime":"2026-08-31T23:00:00",
                                  "purchaseLimit":4,
                                  "realNameRule":"REQUIRED",
                                  "entryMethod":"E_TICKET",
                                  "refundRule":"开演前 48 小时可申请退票。",
                                  "waitlistEnabled":true,
                                  "rules":[
                                    {"ruleGroup":"PURCHASE","ruleCode":"CHILD_POLICY","title":"儿童购票","content":"加场版本要求儿童实名购票。","displayOrder":0},
                                    {"ruleGroup":"ATTENDANCE","ruleCode":"ENTRY_TIME","title":"入场时间","content":"加场版本请提前 45 分钟到场。","displayOrder":10}
                                  ],
                                  "detailSections":[
                                    {"sectionType":"RICH_TEXT","title":"演出介绍","content":"加场版本演出详情。","imageUrl":"","displayOrder":0},
                                    {"sectionType":"IMPORTANT_NOTICE","title":"重要说明","content":"请以现场公告为准。","imageUrl":"","displayOrder":10}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasUnpublishedChanges").value(true));

        mockMvc.perform(get("/api/events/%d".formatted(eventId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("春季音乐现场"))
                .andExpect(jsonPath("$.data.sessions[0].startTime").value("2026-09-01T19:00:00"))
                .andExpect(jsonPath("$.data.sessions[0].ticketCategories[0].name").value("预售票"))
                .andExpect(jsonPath("$.data.sessions[0].ticketCategories[0].priceCents").value(9900))
                .andExpect(jsonPath("$.data.rules[?(@.ruleCode == 'CHILD_POLICY')].content")
                        .value(org.hamcrest.Matchers.hasItem("儿童也需要实名购票。")))
                .andExpect(jsonPath("$.data.detailSections[0].content").value("首版演出详情。"))
                .andExpect(jsonPath("$.data.detailSections[1].imageUrl").value("/media/detail-first.jpg"));

        mockMvc.perform(post("/api/organizer/events/%d/publish".formatted(eventId))
                        .header("X-Organizer-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasUnpublishedChanges").value(false));

        mockMvc.perform(put("/api/organizer/events/%d/homepage-banner".formatted(eventId))
                        .header("X-Organizer-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Stage 56 首页主视觉",
                                  "subtitle":"主办方配置，购票首页只读取已发布版本",
                                  "imageUrl":"/media/test-project.jpg",
                                  "city":"北京",
                                  "displayStartTime":"2026-01-01T00:00:00",
                                  "displayEndTime":"2030-01-01T00:00:00",
                                  "displayOrder":1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(get("/api/homepage/banners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(post("/api/organizer/events/%d/homepage-banner/publish".formatted(eventId))
                        .header("X-Organizer-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(get("/api/homepage/banners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].eventId").value(eventId))
                .andExpect(jsonPath("$.data[0].title").value("Stage 56 首页主视觉"))
                .andExpect(jsonPath("$.data[0].city").value("北京"));

        mockMvc.perform(put("/api/organizer/events/%d/homepage-banner".formatted(eventId))
                        .header("X-Organizer-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Stage 56 待发布修改",
                                  "subtitle":"保存草稿不会直接替换购票端正在展示的版本",
                                  "imageUrl":"/media/test-project.jpg",
                                  "city":"上海",
                                  "displayStartTime":"2026-01-01T00:00:00",
                                  "displayEndTime":"2030-01-01T00:00:00",
                                  "displayOrder":2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.publishedTime").isNotEmpty());

        mockMvc.perform(get("/api/homepage/banners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Stage 56 首页主视觉"))
                .andExpect(jsonPath("$.data[0].city").value("北京"));

        mockMvc.perform(post("/api/organizer/events/%d/homepage-banner/publish".formatted(eventId))
                        .header("X-Organizer-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(get("/api/homepage/banners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Stage 56 待发布修改"))
                .andExpect(jsonPath("$.data[0].city").value("上海"));

        mockMvc.perform(post("/api/organizer/events/%d/homepage-banner/unpublish".formatted(eventId))
                        .header("X-Organizer-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.publishedTime").isEmpty());

        mockMvc.perform(get("/api/homepage/banners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(post("/api/organizer/events/%d/notices".formatted(eventId))
                        .header("X-Organizer-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"入场提醒","content":"请提前 30 分钟到场。"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("入场提醒"));

        mockMvc.perform(post("/api/orders/grab")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId":88001,
                                  "sessionId":%d,
                                  "ticketCategoryId":%d,
                                  "passengers":[{"name":"测试购票人","documentType":"OTHER","documentLast4":"8001"}]
                                }
                                """.formatted(sessionId, categoryId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/organizer/events/%d/orders".formatted(eventId))
                        .header("X-Organizer-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(88001))
                .andExpect(jsonPath("$.data[0].ticketCategoryName").value("正式票"))
                .andExpect(jsonPath("$.data[0].amountCents").value(12900))
                .andExpect(jsonPath("$.data[0].quantity").value(1))
                .andExpect(jsonPath("$.data[0].status").value("PENDING_PAYMENT"));

        mockMvc.perform(get("/api/events/%d".formatted(eventId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("春季音乐现场·加场"))
                .andExpect(jsonPath("$.data.categoryName").value("演唱会"))
                .andExpect(jsonPath("$.data.contentProfile").value("PERFORMANCE"))
                .andExpect(jsonPath("$.data.city").value("上海"))
                .andExpect(jsonPath("$.data.purchaseLimit").value(4))
                .andExpect(jsonPath("$.data.waitlistEnabled").value(true))
                .andExpect(jsonPath("$.data.description").value("主办方发布闭环验收活动"))
                .andExpect(jsonPath("$.data.posterUrl").value("/media/test-project.jpg"))
                .andExpect(jsonPath("$.data.notices[0].title").value("入场提醒"))
                .andExpect(jsonPath("$.data.rules[?(@.ruleCode == 'CHILD_POLICY')].content")
                        .value(org.hamcrest.Matchers.hasItem("加场版本要求儿童实名购票。")))
                .andExpect(jsonPath("$.data.rules[?(@.ruleCode == 'ENTRY_TIME')].content")
                        .value(org.hamcrest.Matchers.hasItem("加场版本请提前 45 分钟到场。")))
                .andExpect(jsonPath("$.data.detailSections[0].content").value("加场版本演出详情。"))
                .andExpect(jsonPath("$.data.detailSections[1].title").value("重要说明"))
                .andExpect(jsonPath("$.data.sessions[0].startTime").value("2026-09-01T20:00:00"))
                .andExpect(jsonPath("$.data.sessions[0].ticketCategories[0].name").value("正式票"))
                .andExpect(jsonPath("$.data.sessions[0].ticketCategories[0].priceCents").value(12900));
    }

    private Long dataId(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("id").longValue();
    }
}
