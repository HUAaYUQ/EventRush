package com.eventrush.service;

import com.eventrush.domain.Event;
import com.eventrush.domain.EventSession;
import com.eventrush.domain.OrganizerEvent;
import com.eventrush.domain.OrganizerNotice;
import com.eventrush.domain.TicketCategory;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class EventCatalogRepository {

    private final JdbcTemplate jdbcTemplate;
    private final boolean inMemoryDatabase;

    public EventCatalogRepository(
            JdbcTemplate jdbcTemplate,
            @Value("${spring.datasource.url:}") String datasourceUrl
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.inMemoryDatabase = datasourceUrl.startsWith("jdbc:h2:mem:");
    }

    public void seedDefaultIfEmpty() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM event_catalog", Integer.class);
        if (count != null && count > 0) {
            if (inMemoryDatabase) {
                jdbcTemplate.update("""
                        UPDATE ticket_category_catalog
                        SET remaining_stock = total_stock
                        WHERE session_id = 101
                        """);
            }
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.plusDays(7);
        jdbcTemplate.update("""
                INSERT INTO event_catalog
                    (id, organizer_id, name, location, description, poster_url, status,
                     created_time, updated_time, published_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, 1L, 9001L, "校园音乐之夜", "大学生活动中心",
                "在校园中央舞台听见乐队、民谣与夏夜。",
                "/images/events/campus-music-night.jpg", "PUBLISHED",
                Timestamp.valueOf(now), Timestamp.valueOf(now), Timestamp.valueOf(now));
        jdbcTemplate.update("""
                INSERT INTO event_session_catalog (id, event_id, start_time, end_time)
                VALUES (?, ?, ?, ?)
                """, 101L, 1L, Timestamp.valueOf(startTime), Timestamp.valueOf(startTime.plusHours(2)));
        jdbcTemplate.update("""
                INSERT INTO ticket_category_catalog
                    (id, session_id, name, price_cents, total_stock, remaining_stock)
                VALUES (?, ?, ?, ?, ?, ?)
                """, 1001L, 101L, "标准票", 19900L, 50, 50);
        jdbcTemplate.update("""
                INSERT INTO ticket_category_catalog
                    (id, session_id, name, price_cents, total_stock, remaining_stock)
                VALUES (?, ?, ?, ?, ?, ?)
                """, 1002L, 101L, "VIP 票", 39900L, 10, 10);
    }

    public List<Event> listPublishedEvents() {
        return jdbcTemplate.query("""
                        SELECT id FROM event_catalog
                        WHERE status = 'PUBLISHED'
                        ORDER BY published_time DESC, id DESC
                        """,
                (resultSet, rowNumber) -> findPublicEvent(resultSet.getLong("id"))
                        .orElseThrow(() -> new BusinessException("event loading failed"))
        );
    }

    public Optional<Event> findPublicEvent(Long eventId) {
        return jdbcTemplate.query("""
                        SELECT id, name, location, status
                        FROM event_catalog
                        WHERE id = ? AND status = 'PUBLISHED'
                        """,
                (resultSet, rowNumber) -> new Event(
                        resultSet.getLong("id"),
                        resultSet.getString("name"),
                        resultSet.getString("location"),
                        resultSet.getString("status"),
                        findSessions(resultSet.getLong("id"))
                ), eventId).stream().findFirst();
    }

    public List<OrganizerEvent> listOrganizerEvents(Long organizerId) {
        return jdbcTemplate.query("""
                        SELECT id FROM event_catalog
                        WHERE organizer_id = ?
                        ORDER BY updated_time DESC, id DESC
                        """,
                (resultSet, rowNumber) -> findOrganizerEvent(resultSet.getLong("id"), organizerId)
                        .orElseThrow(() -> new BusinessException("event loading failed")),
                organizerId
        );
    }

    public Optional<OrganizerEvent> findOrganizerEvent(Long eventId, Long organizerId) {
        return jdbcTemplate.query("""
                        SELECT id, organizer_id, name, location, description, poster_url, status,
                               created_time, updated_time, published_time
                        FROM event_catalog
                        WHERE id = ? AND organizer_id = ?
                        """,
                (resultSet, rowNumber) -> new OrganizerEvent(
                        resultSet.getLong("id"),
                        resultSet.getLong("organizer_id"),
                        resultSet.getString("name"),
                        resultSet.getString("location"),
                        resultSet.getString("description"),
                        resultSet.getString("poster_url"),
                        resultSet.getString("status"),
                        resultSet.getObject("created_time", LocalDateTime.class),
                        resultSet.getObject("updated_time", LocalDateTime.class),
                        resultSet.getObject("published_time", LocalDateTime.class),
                        findSessions(eventId),
                        findNotices(eventId)
                ), eventId, organizerId).stream().findFirst();
    }

    public OrganizerEvent createDraft(
            Long organizerId,
            String name,
            String location,
            String description,
            String posterUrl,
            LocalDateTime now
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO event_catalog
                        (organizer_id, name, location, description, poster_url, status,
                         created_time, updated_time, published_time)
                    VALUES (?, ?, ?, ?, ?, 'DRAFT', ?, ?, NULL)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, organizerId);
            statement.setString(2, name);
            statement.setString(3, location);
            statement.setString(4, description);
            statement.setString(5, posterUrl);
            statement.setTimestamp(6, Timestamp.valueOf(now));
            statement.setTimestamp(7, Timestamp.valueOf(now));
            return statement;
        }, keyHolder);
        return findOrganizerEvent(requireKey(keyHolder, "activity"), organizerId)
                .orElseThrow(() -> new BusinessException("event creation failed"));
    }

    public void updateBasicInfo(
            Long eventId,
            Long organizerId,
            String name,
            String location,
            String description,
            String posterUrl,
            LocalDateTime now
    ) {
        int updated = jdbcTemplate.update("""
                        UPDATE event_catalog
                        SET name = ?, location = ?, description = ?, poster_url = ?, updated_time = ?
                        WHERE id = ? AND organizer_id = ?
                        """,
                name, location, description, posterUrl, Timestamp.valueOf(now), eventId, organizerId);
        requireOwnedUpdate(updated);
    }

    public EventSession addSession(
            Long eventId,
            Long organizerId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            LocalDateTime now
    ) {
        requireOwnedEvent(eventId, organizerId);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO event_session_catalog (event_id, start_time, end_time)
                    VALUES (?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, eventId);
            statement.setTimestamp(2, Timestamp.valueOf(startTime));
            statement.setTimestamp(3, Timestamp.valueOf(endTime));
            return statement;
        }, keyHolder);
        touchEvent(eventId, now);
        Long sessionId = requireKey(keyHolder, "session");
        return new EventSession(sessionId, eventId, startTime, endTime, List.of());
    }

    public void updateSession(
            Long eventId,
            Long organizerId,
            Long sessionId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            LocalDateTime now
    ) {
        requireOwnedEvent(eventId, organizerId);
        int updated = jdbcTemplate.update("""
                        UPDATE event_session_catalog
                        SET start_time = ?, end_time = ?
                        WHERE id = ? AND event_id = ?
                        """,
                Timestamp.valueOf(startTime), Timestamp.valueOf(endTime), sessionId, eventId);
        if (updated != 1) {
            throw new BusinessException("SESSION_NOT_FOUND", HttpStatus.NOT_FOUND, "场次不存在");
        }
        touchEvent(eventId, now);
    }

    public TicketCategory addTicketCategory(
            Long eventId,
            Long organizerId,
            Long sessionId,
            String name,
            long priceCents,
            int totalStock,
            LocalDateTime now
    ) {
        requireOwnedSession(eventId, organizerId, sessionId);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO ticket_category_catalog
                        (session_id, name, price_cents, total_stock, remaining_stock)
                    VALUES (?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, sessionId);
            statement.setString(2, name);
            statement.setLong(3, priceCents);
            statement.setInt(4, totalStock);
            statement.setInt(5, totalStock);
            return statement;
        }, keyHolder);
        touchEvent(eventId, now);
        return new TicketCategory(requireKey(keyHolder, "ticket category"), sessionId, name,
                priceCents, totalStock, totalStock);
    }

    public void updateTicketCategory(
            Long eventId,
            Long organizerId,
            Long sessionId,
            Long categoryId,
            String name,
            long priceCents,
            int totalStock,
            LocalDateTime now
    ) {
        requireOwnedSession(eventId, organizerId, sessionId);
        TicketCategory current = findTicketCategory(sessionId, categoryId)
                .orElseThrow(() -> new BusinessException(
                        "TICKET_CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND, "票档不存在"));
        int sold = current.totalStock() - current.remainingStock();
        if (totalStock < sold) {
            throw new BusinessException("STOCK_BELOW_SOLD", HttpStatus.CONFLICT,
                    "总票数不能小于已售票数");
        }
        jdbcTemplate.update("""
                        UPDATE ticket_category_catalog
                        SET name = ?, price_cents = ?, total_stock = ?, remaining_stock = ?
                        WHERE id = ? AND session_id = ?
                        """,
                name, priceCents, totalStock, totalStock - sold, categoryId, sessionId);
        touchEvent(eventId, now);
    }

    public void publishEvent(Long eventId, Long organizerId, LocalDateTime now) {
        requireOwnedEvent(eventId, organizerId);
        Integer sessionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_session_catalog WHERE event_id = ?", Integer.class, eventId);
        Integer ticketCount = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM ticket_category_catalog ticket
                        JOIN event_session_catalog session ON session.id = ticket.session_id
                        WHERE session.event_id = ?
                        """, Integer.class, eventId);
        if (sessionCount == null || sessionCount == 0) {
            throw new BusinessException("SESSION_REQUIRED", HttpStatus.CONFLICT, "发布前至少配置一个场次");
        }
        if (ticketCount == null || ticketCount == 0) {
            throw new BusinessException("TICKET_CATEGORY_REQUIRED", HttpStatus.CONFLICT,
                    "发布前至少配置一个票档");
        }
        jdbcTemplate.update("""
                        UPDATE event_catalog
                        SET status = 'PUBLISHED', updated_time = ?, published_time = COALESCE(published_time, ?)
                        WHERE id = ? AND organizer_id = ?
                        """,
                Timestamp.valueOf(now), Timestamp.valueOf(now), eventId, organizerId);
    }

    public OrganizerNotice addNotice(
            Long eventId,
            Long organizerId,
            String title,
            String content,
            LocalDateTime now
    ) {
        OrganizerEvent event = requireOwnedEvent(eventId, organizerId);
        if (!"PUBLISHED".equals(event.status())) {
            throw new BusinessException("EVENT_NOT_PUBLISHED", HttpStatus.CONFLICT,
                    "活动发布后才能发布通知");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO event_notice
                        (event_id, title, content, status, created_time, published_time)
                    VALUES (?, ?, ?, 'PUBLISHED', ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, eventId);
            statement.setString(2, title);
            statement.setString(3, content);
            statement.setTimestamp(4, Timestamp.valueOf(now));
            statement.setTimestamp(5, Timestamp.valueOf(now));
            return statement;
        }, keyHolder);
        return new OrganizerNotice(requireKey(keyHolder, "notice"), eventId, title, content,
                "PUBLISHED", now, now);
    }

    public Optional<TicketCategory> findTicketCategory(Long sessionId, Long ticketCategoryId) {
        return jdbcTemplate.query("""
                        SELECT id, session_id, name, price_cents, total_stock, remaining_stock
                        FROM ticket_category_catalog
                        WHERE id = ? AND session_id = ?
                        """,
                (resultSet, rowNumber) -> new TicketCategory(
                        resultSet.getLong("id"), resultSet.getLong("session_id"),
                        resultSet.getString("name"), resultSet.getLong("price_cents"),
                        resultSet.getInt("total_stock"), resultSet.getInt("remaining_stock")
                ), ticketCategoryId, sessionId).stream().findFirst();
    }

    public List<TicketCategory> listTicketCategories() {
        return jdbcTemplate.query("""
                        SELECT id, session_id, name, price_cents, total_stock, remaining_stock
                        FROM ticket_category_catalog
                        ORDER BY id
                        """,
                (resultSet, rowNumber) -> new TicketCategory(
                        resultSet.getLong("id"), resultSet.getLong("session_id"),
                        resultSet.getString("name"), resultSet.getLong("price_cents"),
                        resultSet.getInt("total_stock"), resultSet.getInt("remaining_stock")
                ));
    }

    public Event findEventBySessionId(Long sessionId) {
        return jdbcTemplate.query("""
                        SELECT event_id FROM event_session_catalog WHERE id = ?
                        """,
                (resultSet, rowNumber) -> findPublicEvent(resultSet.getLong("event_id"))
                        .orElseThrow(() -> new BusinessException(
                                "EVENT_NOT_FOUND", HttpStatus.NOT_FOUND, "活动不存在")),
                sessionId).stream().findFirst()
                .orElseThrow(() -> new BusinessException(
                        "SESSION_NOT_FOUND", HttpStatus.NOT_FOUND, "场次不存在"));
    }

    public TicketCategory deductStock(Long sessionId, Long ticketCategoryId, int quantity) {
        int updated = jdbcTemplate.update("""
                        UPDATE ticket_category_catalog
                        SET remaining_stock = remaining_stock - ?
                        WHERE id = ? AND session_id = ? AND remaining_stock >= ?
                        """,
                quantity, ticketCategoryId, sessionId, quantity);
        if (updated != 1) {
            if (findTicketCategory(sessionId, ticketCategoryId).isEmpty()) {
                throw new BusinessException("TICKET_CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND, "票档不存在");
            }
            throw new BusinessException("TICKET_SOLD_OUT", HttpStatus.CONFLICT,
                    "当前票档库存不足，请刷新后重新选择");
        }
        return findTicketCategory(sessionId, ticketCategoryId)
                .orElseThrow(() -> new BusinessException("ticket category loading failed"));
    }

    public TicketCategory releaseStock(Long sessionId, Long ticketCategoryId, int quantity) {
        TicketCategory category = findTicketCategory(sessionId, ticketCategoryId)
                .orElseThrow(() -> new BusinessException(
                        "TICKET_CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND, "票档不存在"));
        int remaining = Math.min(category.totalStock(), category.remainingStock() + quantity);
        jdbcTemplate.update("""
                        UPDATE ticket_category_catalog SET remaining_stock = ?
                        WHERE id = ? AND session_id = ?
                        """, remaining, ticketCategoryId, sessionId);
        return findTicketCategory(sessionId, ticketCategoryId)
                .orElseThrow(() -> new BusinessException("ticket category loading failed"));
    }

    private List<EventSession> findSessions(Long eventId) {
        return jdbcTemplate.query("""
                        SELECT id, event_id, start_time, end_time
                        FROM event_session_catalog
                        WHERE event_id = ?
                        ORDER BY start_time, id
                        """,
                (resultSet, rowNumber) -> new EventSession(
                        resultSet.getLong("id"),
                        resultSet.getLong("event_id"),
                        resultSet.getObject("start_time", LocalDateTime.class),
                        resultSet.getObject("end_time", LocalDateTime.class),
                        findCategories(resultSet.getLong("id"))
                ), eventId);
    }

    private List<TicketCategory> findCategories(Long sessionId) {
        return jdbcTemplate.query("""
                        SELECT id, session_id, name, price_cents, total_stock, remaining_stock
                        FROM ticket_category_catalog
                        WHERE session_id = ?
                        ORDER BY price_cents, id
                        """,
                (resultSet, rowNumber) -> new TicketCategory(
                        resultSet.getLong("id"), resultSet.getLong("session_id"),
                        resultSet.getString("name"), resultSet.getLong("price_cents"),
                        resultSet.getInt("total_stock"), resultSet.getInt("remaining_stock")
                ), sessionId);
    }

    private List<OrganizerNotice> findNotices(Long eventId) {
        return jdbcTemplate.query("""
                        SELECT id, event_id, title, content, status, created_time, published_time
                        FROM event_notice
                        WHERE event_id = ?
                        ORDER BY published_time DESC, id DESC
                        """,
                (resultSet, rowNumber) -> new OrganizerNotice(
                        resultSet.getLong("id"), resultSet.getLong("event_id"),
                        resultSet.getString("title"), resultSet.getString("content"),
                        resultSet.getString("status"),
                        resultSet.getObject("created_time", LocalDateTime.class),
                        resultSet.getObject("published_time", LocalDateTime.class)
                ), eventId);
    }

    private OrganizerEvent requireOwnedEvent(Long eventId, Long organizerId) {
        return findOrganizerEvent(eventId, organizerId)
                .orElseThrow(() -> new BusinessException(
                        "EVENT_NOT_FOUND", HttpStatus.NOT_FOUND, "活动不存在或不属于当前主办方"));
    }

    private void requireOwnedSession(Long eventId, Long organizerId, Long sessionId) {
        requireOwnedEvent(eventId, organizerId);
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM event_session_catalog WHERE id = ? AND event_id = ?
                        """, Integer.class, sessionId, eventId);
        if (count == null || count == 0) {
            throw new BusinessException("SESSION_NOT_FOUND", HttpStatus.NOT_FOUND, "场次不存在");
        }
    }

    private void requireOwnedUpdate(int updated) {
        if (updated != 1) {
            throw new BusinessException("EVENT_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "活动不存在或不属于当前主办方");
        }
    }

    private void touchEvent(Long eventId, LocalDateTime now) {
        jdbcTemplate.update("UPDATE event_catalog SET updated_time = ? WHERE id = ?",
                Timestamp.valueOf(now), eventId);
    }

    private Long requireKey(KeyHolder keyHolder, String resource) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException(resource + " creation failed");
        }
        return key.longValue();
    }
}
