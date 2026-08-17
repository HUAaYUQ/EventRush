package com.eventrush.api;

import com.eventrush.domain.HomepageBanner;
import com.eventrush.service.HomepageContentService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
class HomepageContentController {

    private final HomepageContentService homepageContentService;

    HomepageContentController(HomepageContentService homepageContentService) {
        this.homepageContentService = homepageContentService;
    }

    @GetMapping("/api/homepage/banners")
    List<HomepageBanner> listPublicBanners() {
        return homepageContentService.listPublicBanners();
    }

    @GetMapping("/api/organizer/events/{eventId}/homepage-banner")
    List<HomepageBanner> getOrganizerBanner(@PathVariable Long eventId) {
        return homepageContentService.getOrganizerBanner(eventId).stream().toList();
    }

    @PutMapping("/api/organizer/events/{eventId}/homepage-banner")
    HomepageBanner saveDraft(
            @PathVariable Long eventId,
            @Valid @RequestBody BannerRequest request
    ) {
        return homepageContentService.saveDraft(eventId, request.title(), request.subtitle(),
                request.imageUrl(), request.city(), request.displayStartTime(),
                request.displayEndTime(), request.displayOrder());
    }

    @PostMapping("/api/organizer/events/{eventId}/homepage-banner/publish")
    HomepageBanner publish(@PathVariable Long eventId) {
        return homepageContentService.publish(eventId);
    }

    @PostMapping("/api/organizer/events/{eventId}/homepage-banner/unpublish")
    HomepageBanner unpublish(@PathVariable Long eventId) {
        return homepageContentService.unpublish(eventId);
    }

    record BannerRequest(
            @NotBlank(message = "主视觉标题不能为空")
            @Size(max = 100, message = "主视觉标题不能超过 100 个字")
            String title,
            @NotBlank(message = "主视觉说明不能为空")
            @Size(max = 200, message = "主视觉说明不能超过 200 个字")
            String subtitle,
            @NotBlank(message = "主视觉图片不能为空")
            @Size(max = 255, message = "图片地址不能超过 255 个字")
            String imageUrl,
            @NotBlank(message = "展示城市不能为空")
            @Size(max = 80, message = "展示城市不能超过 80 个字")
            String city,
            @NotNull(message = "展示开始时间不能为空") LocalDateTime displayStartTime,
            @NotNull(message = "展示结束时间不能为空") LocalDateTime displayEndTime,
            @Min(value = 0, message = "展示顺序不能小于 0")
            @Max(value = 999, message = "展示顺序不能超过 999")
            int displayOrder
    ) {
    }
}
