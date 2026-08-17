package com.eventrush.service;

import com.eventrush.domain.HomepageBanner;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class HomepageContentRepository {

    private final JdbcTemplate jdbcTemplate;

    public HomepageContentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<HomepageBanner> listActive(LocalDateTime now) {
        return jdbcTemplate.query("""
                        SELECT banner.id, banner.event_id, banner.organizer_id,
                               banner.title, banner.subtitle, banner.image_url, banner.city,
                               banner.display_start_time, banner.display_end_time,
                               banner.display_order, 'PUBLISHED' AS status,
                               banner.published_time AS created_time,
                               banner.published_time AS updated_time,
                               banner.published_time
                        FROM homepage_banner_publication banner
                        JOIN event_catalog event ON event.id = banner.event_id
                        JOIN event_publication publication ON publication.event_id = banner.event_id
                        WHERE event.status = 'PUBLISHED'
                          AND banner.display_start_time <= ?
                          AND banner.display_end_time > ?
                        ORDER BY banner.display_order, banner.published_time DESC, banner.id DESC
                        """,
                (resultSet, rowNumber) -> map(resultSet), Timestamp.valueOf(now), Timestamp.valueOf(now));
    }

    public Optional<HomepageBanner> findByEvent(Long eventId, Long organizerId) {
        return jdbcTemplate.query("""
                        SELECT * FROM homepage_banner
                        WHERE event_id = ? AND organizer_id = ?
                        """,
                (resultSet, rowNumber) -> map(resultSet), eventId, organizerId).stream().findFirst();
    }

    public List<HomepageBanner> listByOrganizer(Long organizerId) {
        return jdbcTemplate.query("""
                        SELECT * FROM homepage_banner
                        WHERE organizer_id = ?
                        ORDER BY display_order, updated_time DESC, id DESC
                        """, (resultSet, rowNumber) -> map(resultSet), organizerId);
    }

    public HomepageBanner saveDraft(
            Long eventId,
            Long organizerId,
            String title,
            String subtitle,
            String imageUrl,
            String city,
            LocalDateTime displayStartTime,
            LocalDateTime displayEndTime,
            int displayOrder,
            LocalDateTime now
    ) {
        Optional<HomepageBanner> current = findByEvent(eventId, organizerId);
        if (current.isPresent()) {
            jdbcTemplate.update("""
                            UPDATE homepage_banner
                            SET title = ?, subtitle = ?, image_url = ?, city = ?,
                                display_start_time = ?, display_end_time = ?, display_order = ?,
                                status = 'DRAFT', updated_time = ?
                            WHERE event_id = ? AND organizer_id = ?
                            """,
                    title, subtitle, imageUrl, city, Timestamp.valueOf(displayStartTime),
                    Timestamp.valueOf(displayEndTime), displayOrder, Timestamp.valueOf(now),
                    eventId, organizerId);
            return requireByEvent(eventId, organizerId);
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO homepage_banner
                        (event_id, organizer_id, title, subtitle, image_url, city,
                         display_start_time, display_end_time, display_order, status,
                         created_time, updated_time, published_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, NULL)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, eventId);
            statement.setLong(2, organizerId);
            statement.setString(3, title);
            statement.setString(4, subtitle);
            statement.setString(5, imageUrl);
            statement.setString(6, city);
            statement.setTimestamp(7, Timestamp.valueOf(displayStartTime));
            statement.setTimestamp(8, Timestamp.valueOf(displayEndTime));
            statement.setInt(9, displayOrder);
            statement.setTimestamp(10, Timestamp.valueOf(now));
            statement.setTimestamp(11, Timestamp.valueOf(now));
            return statement;
        }, keyHolder);
        return requireByEvent(eventId, organizerId);
    }

    public HomepageBanner publish(Long eventId, Long organizerId, LocalDateTime now) {
        HomepageBanner draft = requireByEvent(eventId, organizerId);
        int updated = jdbcTemplate.update("""
                        UPDATE homepage_banner
                        SET status = 'PUBLISHED', updated_time = ?, published_time = ?
                        WHERE event_id = ? AND organizer_id = ?
                        """,
                Timestamp.valueOf(now), Timestamp.valueOf(now), eventId, organizerId);
        if (updated != 1) {
            throw new BusinessException("HOMEPAGE_BANNER_NOT_FOUND",
                    org.springframework.http.HttpStatus.NOT_FOUND, "请先保存首页主视觉草稿");
        }
        savePublication(draft, now);
        return requireByEvent(eventId, organizerId);
    }

    public HomepageBanner unpublish(Long eventId, Long organizerId, LocalDateTime now) {
        requireByEvent(eventId, organizerId);
        jdbcTemplate.update("""
                DELETE FROM homepage_banner_publication
                WHERE event_id = ? AND organizer_id = ?
                """, eventId, organizerId);
        jdbcTemplate.update("""
                UPDATE homepage_banner
                SET status = 'DRAFT', updated_time = ?, published_time = NULL
                WHERE event_id = ? AND organizer_id = ?
                """, Timestamp.valueOf(now), eventId, organizerId);
        return requireByEvent(eventId, organizerId);
    }

    private void savePublication(HomepageBanner draft, LocalDateTime now) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM homepage_banner_publication
                WHERE event_id = ? AND organizer_id = ?
                """, Integer.class, draft.eventId(), draft.organizerId());
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                            UPDATE homepage_banner_publication
                            SET title = ?, subtitle = ?, image_url = ?, city = ?,
                                display_start_time = ?, display_end_time = ?, display_order = ?,
                                published_time = ?
                            WHERE event_id = ? AND organizer_id = ?
                            """,
                    draft.title(), draft.subtitle(), draft.imageUrl(), draft.city(),
                    Timestamp.valueOf(draft.displayStartTime()), Timestamp.valueOf(draft.displayEndTime()),
                    draft.displayOrder(), Timestamp.valueOf(now), draft.eventId(), draft.organizerId());
            return;
        }
        jdbcTemplate.update("""
                        INSERT INTO homepage_banner_publication
                            (event_id, organizer_id, title, subtitle, image_url, city,
                             display_start_time, display_end_time, display_order, published_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                draft.eventId(), draft.organizerId(), draft.title(), draft.subtitle(), draft.imageUrl(),
                draft.city(), Timestamp.valueOf(draft.displayStartTime()),
                Timestamp.valueOf(draft.displayEndTime()), draft.displayOrder(), Timestamp.valueOf(now));
    }

    private HomepageBanner requireByEvent(Long eventId, Long organizerId) {
        return findByEvent(eventId, organizerId)
                .orElseThrow(() -> new BusinessException("homepage banner loading failed"));
    }

    private HomepageBanner map(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new HomepageBanner(
                resultSet.getLong("id"),
                resultSet.getLong("event_id"),
                resultSet.getLong("organizer_id"),
                resultSet.getString("title"),
                resultSet.getString("subtitle"),
                resultSet.getString("image_url"),
                resultSet.getString("city"),
                resultSet.getObject("display_start_time", LocalDateTime.class),
                resultSet.getObject("display_end_time", LocalDateTime.class),
                resultSet.getInt("display_order"),
                resultSet.getString("status"),
                resultSet.getObject("created_time", LocalDateTime.class),
                resultSet.getObject("updated_time", LocalDateTime.class),
                resultSet.getObject("published_time", LocalDateTime.class)
        );
    }
}
