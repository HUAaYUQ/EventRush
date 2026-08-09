package com.eventrush.service;

import com.eventrush.service.AsyncGrabService.GrabResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eventrush-async-test;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "eventrush.stock.redis-enabled=false",
        "eventrush.queue.redis-enabled=false",
        "eventrush.queue.consumer-scan-ms=600000"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Sql(statements = {
        "DELETE FROM electronic_ticket",
        "DELETE FROM async_grab_request",
        "DELETE FROM ticket_order"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AsyncGrabServiceTest {

    @Autowired
    private AsyncGrabService asyncGrabService;

    @Test
    void queuedGrabRequestsAreConsumedWithoutOverselling() {
        List<String> requestIds = new ArrayList<>();
        for (long userId = 700; userId <= 710; userId++) {
            GrabResult result = asyncGrabService.submitGrab(userId, 101L, 1002L);
            assertThat(result.status()).isEqualTo(AsyncGrabService.PENDING);
            requestIds.add(result.requestId());
        }

        for (int i = 0; i < requestIds.size(); i++) {
            asyncGrabService.consumeOne();
        }

        List<GrabResult> results = requestIds.stream()
                .map(asyncGrabService::getResult)
                .toList();

        assertThat(results).filteredOn(result -> result.status().equals(AsyncGrabService.SUCCESS)).hasSize(10);
        assertThat(results).filteredOn(result -> result.status().equals(AsyncGrabService.FAILED))
                .singleElement()
                .extracting(GrabResult::errorMessage)
                .isEqualTo("ticket stock is insufficient");
    }

    @Test
    void duplicateMessageDoesNotProcessAgain() {
        GrabResult submitted = asyncGrabService.submitGrab(720L, 101L, 1001L);

        asyncGrabService.consumeOne();
        GrabResult success = asyncGrabService.getResult(submitted.requestId());
        asyncGrabService.consumeRocket("""
                {
                  "requestId": "%s",
                  "userId": 720,
                  "sessionId": 101,
                  "ticketCategoryId": 1001
                }
                """.formatted(submitted.requestId()));

        GrabResult afterDuplicate = asyncGrabService.getResult(submitted.requestId());
        assertThat(afterDuplicate.status()).isEqualTo(AsyncGrabService.SUCCESS);
        assertThat(afterDuplicate.orderId()).isEqualTo(success.orderId());
        assertThat(afterDuplicate.errorMessage()).isNull();
    }
}
