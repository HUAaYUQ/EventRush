package com.eventrush.api;

import com.eventrush.domain.EventSession;
import com.eventrush.domain.OrganizerEvent;
import com.eventrush.domain.OrganizerNotice;
import com.eventrush.domain.OrganizerOrderSummary;
import com.eventrush.domain.TicketCategory;
import com.eventrush.service.OrganizerEventService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizer/events")
class OrganizerController {

    private final OrganizerEventService organizerEventService;

    OrganizerController(OrganizerEventService organizerEventService) {
        this.organizerEventService = organizerEventService;
    }

    @GetMapping
    List<OrganizerEvent> listEvents() {
        return organizerEventService.listEvents();
    }

    @GetMapping("/{eventId}")
    OrganizerEvent getEvent(@PathVariable Long eventId) {
        return organizerEventService.getEvent(eventId);
    }

    @GetMapping("/{eventId}/orders")
    List<OrganizerOrderSummary> listOrders(@PathVariable Long eventId) {
        return organizerEventService.listOrders(eventId);
    }

    @PostMapping
    OrganizerEvent createDraft(@Valid @RequestBody EventRequest request) {
        return organizerEventService.createDraft(
                request.name(), request.location(), request.description(), request.posterUrl());
    }

    @PutMapping("/{eventId}")
    OrganizerEvent updateBasicInfo(
            @PathVariable Long eventId,
            @Valid @RequestBody EventRequest request
    ) {
        return organizerEventService.updateBasicInfo(eventId, request.name(), request.location(),
                request.description(), request.posterUrl());
    }

    @PostMapping("/{eventId}/sessions")
    EventSession addSession(
            @PathVariable Long eventId,
            @Valid @RequestBody SessionRequest request
    ) {
        return organizerEventService.addSession(eventId, request.startTime(), request.endTime());
    }

    @PutMapping("/{eventId}/sessions/{sessionId}")
    EventSession updateSession(
            @PathVariable Long eventId,
            @PathVariable Long sessionId,
            @Valid @RequestBody SessionRequest request
    ) {
        return organizerEventService.updateSession(eventId, sessionId, request.startTime(), request.endTime());
    }

    @PostMapping("/{eventId}/sessions/{sessionId}/ticket-categories")
    TicketCategory addTicketCategory(
            @PathVariable Long eventId,
            @PathVariable Long sessionId,
            @Valid @RequestBody TicketCategoryRequest request
    ) {
        return organizerEventService.addTicketCategory(eventId, sessionId, request.name(),
                request.priceCents(), request.totalStock());
    }

    @PutMapping("/{eventId}/sessions/{sessionId}/ticket-categories/{categoryId}")
    TicketCategory updateTicketCategory(
            @PathVariable Long eventId,
            @PathVariable Long sessionId,
            @PathVariable Long categoryId,
            @Valid @RequestBody TicketCategoryRequest request
    ) {
        return organizerEventService.updateTicketCategory(eventId, sessionId, categoryId, request.name(),
                request.priceCents(), request.totalStock());
    }

    @PostMapping("/{eventId}/publish")
    OrganizerEvent publishEvent(@PathVariable Long eventId) {
        return organizerEventService.publishEvent(eventId);
    }

    @PostMapping("/{eventId}/notices")
    OrganizerNotice publishNotice(
            @PathVariable Long eventId,
            @Valid @RequestBody NoticeRequest request
    ) {
        return organizerEventService.publishNotice(eventId, request.title(), request.content());
    }

    record EventRequest(
            @NotBlank(message = "活动名称不能为空")
            @Size(max = 100, message = "活动名称不能超过 100 个字")
            String name,
            @NotBlank(message = "活动地点不能为空")
            @Size(max = 160, message = "活动地点不能超过 160 个字")
            String location,
            @NotNull(message = "活动介绍不能为空")
            @Size(max = 1000, message = "活动介绍不能超过 1000 个字")
            String description,
            @Size(max = 255, message = "海报地址不能超过 255 个字")
            String posterUrl
    ) {
    }

    record SessionRequest(
            @NotNull(message = "开始时间不能为空") LocalDateTime startTime,
            @NotNull(message = "结束时间不能为空") LocalDateTime endTime
    ) {
    }

    record TicketCategoryRequest(
            @NotBlank(message = "票档名称不能为空")
            @Size(max = 80, message = "票档名称不能超过 80 个字")
            String name,
            @Min(value = 0, message = "票价不能小于 0")
            long priceCents,
            @Min(value = 1, message = "票数至少为 1")
            @Max(value = 1000000, message = "票数不能超过 1000000")
            int totalStock
    ) {
    }

    record NoticeRequest(
            @NotBlank(message = "通知标题不能为空")
            @Size(max = 100, message = "通知标题不能超过 100 个字")
            String title,
            @NotBlank(message = "通知内容不能为空")
            @Size(max = 1000, message = "通知内容不能超过 1000 个字")
            String content
    ) {
    }
}
