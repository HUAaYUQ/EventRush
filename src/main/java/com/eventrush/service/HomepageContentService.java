package com.eventrush.service;

import com.eventrush.domain.HomepageBanner;
import com.eventrush.domain.OrganizerEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HomepageContentService {

    private final HomepageContentRepository repository;
    private final OrganizerEventService organizerEventService;

    public HomepageContentService(
            HomepageContentRepository repository,
            OrganizerEventService organizerEventService
    ) {
        this.repository = repository;
        this.organizerEventService = organizerEventService;
    }

    public List<HomepageBanner> listPublicBanners() {
        return repository.listActive(LocalDateTime.now());
    }

    public Optional<HomepageBanner> getOrganizerBanner(Long eventId) {
        organizerEventService.getEvent(eventId);
        return repository.findByEvent(eventId, OrganizerEventService.CURRENT_ORGANIZER_ID);
    }

    public List<HomepageBanner> listOrganizerBanners() {
        return repository.listByOrganizer(OrganizerEventService.CURRENT_ORGANIZER_ID);
    }

    public HomepageBanner saveDraft(
            Long eventId,
            String title,
            String subtitle,
            String imageUrl,
            String city,
            LocalDateTime displayStartTime,
            LocalDateTime displayEndTime,
            int displayOrder
    ) {
        organizerEventService.getEvent(eventId);
        requireValidRange(displayStartTime, displayEndTime);
        return repository.saveDraft(eventId, OrganizerEventService.CURRENT_ORGANIZER_ID,
                title.trim(), subtitle.trim(), imageUrl.trim(), city.trim(), displayStartTime,
                displayEndTime, displayOrder, LocalDateTime.now());
    }

    @Transactional
    public HomepageBanner publish(Long eventId) {
        OrganizerEvent event = organizerEventService.getEvent(eventId);
        if (!"PUBLISHED".equals(event.status())) {
            throw new BusinessException("EVENT_NOT_PUBLISHED", HttpStatus.CONFLICT,
                    "活动发布后才能进入购票首页");
        }
        HomepageBanner banner = repository.findByEvent(
                        eventId, OrganizerEventService.CURRENT_ORGANIZER_ID)
                .orElseThrow(() -> new BusinessException("HOMEPAGE_BANNER_NOT_FOUND",
                        HttpStatus.NOT_FOUND, "请先保存首页主视觉草稿"));
        requireValidRange(banner.displayStartTime(), banner.displayEndTime());
        if (banner.displayEndTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("HOMEPAGE_BANNER_EXPIRED", HttpStatus.CONFLICT,
                    "展示结束时间已经过去，请调整后再发布");
        }
        return repository.publish(eventId, OrganizerEventService.CURRENT_ORGANIZER_ID,
                LocalDateTime.now());
    }

    @Transactional
    public HomepageBanner unpublish(Long eventId) {
        organizerEventService.getEvent(eventId);
        return repository.unpublish(eventId, OrganizerEventService.CURRENT_ORGANIZER_ID,
                LocalDateTime.now());
    }

    private void requireValidRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException("INVALID_DISPLAY_TIME", HttpStatus.BAD_REQUEST,
                    "展示结束时间必须晚于开始时间");
        }
    }
}
