package com.eventrush.api;

import com.eventrush.domain.EventSession;
import com.eventrush.domain.EventDetailSectionDraft;
import com.eventrush.domain.EventProductDraft;
import com.eventrush.domain.EventRuleDraft;
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
        return organizerEventService.createDraft(request.toProduct());
    }

    @PutMapping("/{eventId}")
    OrganizerEvent updateBasicInfo(
            @PathVariable Long eventId,
            @Valid @RequestBody EventRequest request
    ) {
        return organizerEventService.updateBasicInfo(eventId, request.toProduct());
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
            @NotNull(message = "活动类目不能为空") Long categoryId,
            @NotBlank(message = "活动城市不能为空")
            @Size(max = 80, message = "活动城市不能超过 80 个字") String city,
            @NotBlank(message = "场馆名称不能为空")
            @Size(max = 160, message = "场馆名称不能超过 160 个字") String venueName,
            @NotBlank(message = "场馆地址不能为空")
            @Size(max = 255, message = "场馆地址不能超过 255 个字") String venueAddress,
            @NotBlank(message = "活动介绍不能为空")
            @Size(max = 1000, message = "活动介绍不能超过 1000 个字")
            String description,
            @Size(max = 255, message = "海报地址不能超过 255 个字")
            String posterUrl,
            @NotNull(message = "演出时长不能为空")
            @Min(value = 1, message = "演出时长至少为 1 分钟")
            @Max(value = 1440, message = "演出时长不能超过 1440 分钟") Integer durationMinutes,
            @NotNull(message = "开售时间不能为空") LocalDateTime saleStartTime,
            @NotNull(message = "停售时间不能为空") LocalDateTime saleEndTime,
            @NotNull(message = "限购数量不能为空")
            @Min(value = 1, message = "每单至少可购买 1 张")
            @Max(value = 20, message = "每单限购不能超过 20 张") Integer purchaseLimit,
            @NotBlank(message = "实名规则不能为空")
            @Size(max = 32, message = "实名规则格式错误") String realNameRule,
            @NotBlank(message = "入场方式不能为空")
            @Size(max = 32, message = "入场方式格式错误") String entryMethod,
            @NotBlank(message = "退票规则不能为空")
            @Size(max = 1000, message = "退票规则不能超过 1000 个字") String refundRule,
            boolean waitlistEnabled,
            List<@Valid RuleRequest> rules,
            List<@Valid DetailSectionRequest> detailSections
    ) {
        EventProductDraft toProduct() {
            return new EventProductDraft(name, categoryId, city, venueName, venueAddress,
                    description, posterUrl, durationMinutes, saleStartTime, saleEndTime,
                    purchaseLimit, realNameRule, entryMethod, refundRule, waitlistEnabled,
                    rules == null ? List.of() : rules.stream().map(RuleRequest::toDraft).toList(),
                    detailSections == null ? List.of()
                            : detailSections.stream().map(DetailSectionRequest::toDraft).toList());
        }
    }

    record RuleRequest(
            @NotBlank(message = "规则分组不能为空")
            @Size(max = 32, message = "规则分组格式错误") String ruleGroup,
            @NotBlank(message = "规则标识不能为空")
            @Size(max = 64, message = "规则标识不能超过 64 个字") String ruleCode,
            @NotBlank(message = "规则标题不能为空")
            @Size(max = 80, message = "规则标题不能超过 80 个字") String title,
            @NotBlank(message = "规则内容不能为空")
            @Size(max = 2000, message = "规则内容不能超过 2000 个字") String content,
            @Min(value = 0, message = "规则顺序不能小于 0")
            @Max(value = 999, message = "规则顺序不能超过 999") int displayOrder
    ) {
        EventRuleDraft toDraft() {
            return new EventRuleDraft(ruleGroup, ruleCode, title, content, displayOrder);
        }
    }

    record DetailSectionRequest(
            @NotBlank(message = "详情模块类型不能为空")
            @Size(max = 32, message = "详情模块类型格式错误") String sectionType,
            @NotBlank(message = "详情模块标题不能为空")
            @Size(max = 100, message = "详情模块标题不能超过 100 个字") String title,
            @Size(max = 5000, message = "详情模块内容不能超过 5000 个字") String content,
            @Size(max = 255, message = "详情图片地址不能超过 255 个字") String imageUrl,
            @Min(value = 0, message = "详情模块顺序不能小于 0")
            @Max(value = 999, message = "详情模块顺序不能超过 999") int displayOrder
    ) {
        EventDetailSectionDraft toDraft() {
            return new EventDetailSectionDraft(sectionType, title, content, imageUrl, displayOrder);
        }
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
