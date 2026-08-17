package com.eventrush.service;

import com.eventrush.domain.Event;
import com.eventrush.domain.EventDetailSection;
import com.eventrush.domain.EventDetailSectionDraft;
import com.eventrush.domain.EventProductDraft;
import com.eventrush.domain.EventRule;
import com.eventrush.domain.EventRuleDraft;
import com.eventrush.domain.EventSession;
import com.eventrush.domain.OrganizerEvent;
import com.eventrush.domain.OrganizerNotice;
import com.eventrush.domain.OrganizerOrderSummary;
import com.eventrush.domain.OrderStatus;
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
    private final boolean seedEnabled;

    public EventCatalogRepository(
            JdbcTemplate jdbcTemplate,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${eventrush.catalog.seed-enabled:false}") boolean seedEnabled
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.inMemoryDatabase = datasourceUrl.startsWith("jdbc:h2:mem:");
        this.seedEnabled = seedEnabled;
    }

    public void seedDefaultIfEmpty() {
        if (!seedEnabled) {
            return;
        }
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
                    (id, organizer_id, name, location, category_id, city, venue_address,
                     description, poster_url, duration_minutes, sale_start_time, sale_end_time,
                     purchase_limit, real_name_rule, entry_method, refund_rule, waitlist_enabled,
                     status, created_time, updated_time, published_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, 1L, 9001L, "测试演出项目", "测试场馆", 1L, "测试城市", "测试地址",
                "自动化测试使用的票务项目。", "", 120,
                Timestamp.valueOf(now.minusHours(1)), Timestamp.valueOf(now.plusDays(30)),
                5, "REQUIRED", "E_TICKET", "测试退票规则", true, "PUBLISHED",
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
        jdbcTemplate.update("""
                INSERT INTO event_session_publication (event_id, session_id, start_time, end_time)
                VALUES (?, ?, ?, ?)
                """, 1L, 101L, Timestamp.valueOf(startTime), Timestamp.valueOf(startTime.plusHours(2)));
        jdbcTemplate.update("""
                INSERT INTO ticket_category_publication
                    (event_id, session_id, ticket_category_id, name, price_cents)
                VALUES (?, ?, ?, ?, ?), (?, ?, ?, ?, ?)
                """, 1L, 101L, 1001L, "标准票", 19900L,
                1L, 101L, 1002L, "VIP 票", 39900L);
        jdbcTemplate.update("""
                INSERT INTO event_publication
                    (event_id, organizer_id, category_id, category_name, content_profile, name, city,
                     venue_name, venue_address, description, poster_url, duration_minutes,
                     sale_start_time, sale_end_time, purchase_limit, real_name_rule,
                     entry_method, refund_rule, waitlist_enabled, published_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, 1L, 9001L, 1L, "演唱会", "PERFORMANCE", "测试演出项目", "测试城市",
                "测试场馆", "测试地址", "自动化测试使用的票务项目。", "", 120,
                Timestamp.valueOf(now.minusHours(1)), Timestamp.valueOf(now.plusDays(30)),
                5, "REQUIRED", "E_TICKET", "测试退票规则", true, Timestamp.valueOf(now));
    }

    public List<Event> listPublishedEvents() {
        return jdbcTemplate.query("""
                        SELECT publication.event_id
                        FROM event_publication publication
                        JOIN event_catalog event ON event.id = publication.event_id
                        WHERE event.status = 'PUBLISHED'
                        ORDER BY publication.published_time DESC, publication.event_id DESC
                        """,
                (resultSet, rowNumber) -> findPublicEvent(resultSet.getLong("event_id"))
                        .orElseThrow(() -> new BusinessException("event loading failed"))
        );
    }

    public Optional<Event> findPublicEvent(Long eventId) {
        return jdbcTemplate.query("""
                        SELECT publication.*, event.status
                        FROM event_publication publication
                        JOIN event_catalog event ON event.id = publication.event_id
                        WHERE publication.event_id = ? AND event.status = 'PUBLISHED'
                        """,
                (resultSet, rowNumber) -> {
                    List<EventSession> sessions = findPublishedSessions(eventId);
                    return new Event(
                        resultSet.getLong("event_id"),
                        resultSet.getString("name"),
                        resultSet.getString("venue_name"),
                        resultSet.getLong("category_id"),
                        resultSet.getString("category_name"),
                        resultSet.getString("content_profile"),
                        resultSet.getString("city"),
                        resultSet.getString("venue_address"),
                        resultSet.getString("status"),
                        resolveSaleStatus(resultSet.getObject("sale_start_time", LocalDateTime.class),
                                resultSet.getObject("sale_end_time", LocalDateTime.class), sessions),
                        resultSet.getObject("sale_start_time", LocalDateTime.class),
                        resultSet.getObject("sale_end_time", LocalDateTime.class),
                        resultSet.getInt("duration_minutes"),
                        resultSet.getInt("purchase_limit"),
                        resultSet.getString("real_name_rule"),
                        resultSet.getString("entry_method"),
                        resultSet.getString("refund_rule"),
                        resultSet.getBoolean("waitlist_enabled"),
                        sessions,
                        resultSet.getString("description"),
                        resultSet.getString("poster_url"),
                        findPublishedRules(eventId),
                        findPublishedDetailSections(eventId),
                        findNotices(eventId));
                }, eventId).stream().findFirst();
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

    public List<OrganizerOrderSummary> listOrganizerOrders(Long eventId, Long organizerId) {
        requireOwnedEvent(eventId, organizerId);
        return jdbcTemplate.query("""
                        SELECT ticket_order.id, ticket_order.user_id, ticket_order.session_id,
                               ticket_order.ticket_category_id, ticket_category_catalog.name AS category_name,
                               event_session_catalog.start_time, ticket_order.quantity,
                               ticket_order.refunded_quantity, ticket_order.amount_cents,
                               ticket_order.order_status, ticket_order.created_time, ticket_order.pay_time,
                               COALESCE(ticket_counts.issued_count, 0) AS issued_count,
                               COALESCE(ticket_counts.refunded_count, 0) AS refunded_count
                        FROM ticket_order
                        JOIN event_session_catalog
                          ON event_session_catalog.id = ticket_order.session_id
                        JOIN ticket_category_catalog
                          ON ticket_category_catalog.id = ticket_order.ticket_category_id
                        LEFT JOIN (
                            SELECT order_id,
                                   COUNT(*) AS issued_count,
                                   SUM(CASE WHEN ticket_status = 'REFUNDED' THEN 1 ELSE 0 END) AS refunded_count
                            FROM electronic_ticket
                            GROUP BY order_id
                        ) ticket_counts ON ticket_counts.order_id = ticket_order.id
                        WHERE ticket_order.event_id = ?
                        ORDER BY ticket_order.created_time DESC, ticket_order.id DESC
                        """,
                (resultSet, rowNumber) -> new OrganizerOrderSummary(
                        resultSet.getLong("id"),
                        resultSet.getLong("user_id"),
                        resultSet.getLong("session_id"),
                        resultSet.getLong("ticket_category_id"),
                        resultSet.getString("category_name"),
                        resultSet.getObject("start_time", LocalDateTime.class),
                        resultSet.getInt("quantity"),
                        resultSet.getInt("refunded_quantity"),
                        resultSet.getLong("amount_cents"),
                        OrderStatus.valueOf(resultSet.getString("order_status")),
                        resultSet.getObject("created_time", LocalDateTime.class),
                        resultSet.getObject("pay_time", LocalDateTime.class),
                        resultSet.getInt("issued_count"),
                        resultSet.getInt("refunded_count")
                ), eventId);
    }

    public Optional<OrganizerEvent> findOrganizerEvent(Long eventId, Long organizerId) {
        return jdbcTemplate.query("""
                        SELECT event.*, category.name AS category_name,
                               category.content_profile AS content_profile,
                               publication.published_time AS public_version_time
                        FROM event_catalog event
                        LEFT JOIN event_category category ON category.id = event.category_id
                        LEFT JOIN event_publication publication ON publication.event_id = event.id
                        WHERE event.id = ? AND event.organizer_id = ?
                        """,
                (resultSet, rowNumber) -> new OrganizerEvent(
                        resultSet.getLong("id"),
                        resultSet.getLong("organizer_id"),
                        resultSet.getString("name"),
                        resultSet.getString("location"),
                        resultSet.getObject("category_id", Long.class),
                        resultSet.getString("category_name"),
                        resultSet.getString("content_profile"),
                        resultSet.getString("city"),
                        resultSet.getString("venue_address"),
                        resultSet.getString("description"),
                        resultSet.getString("poster_url"),
                        resultSet.getInt("duration_minutes"),
                        resultSet.getObject("sale_start_time", LocalDateTime.class),
                        resultSet.getObject("sale_end_time", LocalDateTime.class),
                        resultSet.getInt("purchase_limit"),
                        resultSet.getString("real_name_rule"),
                        resultSet.getString("entry_method"),
                        resultSet.getString("refund_rule"),
                        resultSet.getBoolean("waitlist_enabled"),
                        resultSet.getString("status"),
                        resultSet.getObject("public_version_time", LocalDateTime.class) == null
                                || resultSet.getObject("updated_time", LocalDateTime.class)
                                .isAfter(resultSet.getObject("public_version_time", LocalDateTime.class)),
                        resultSet.getObject("created_time", LocalDateTime.class),
                        resultSet.getObject("updated_time", LocalDateTime.class),
                        resultSet.getObject("published_time", LocalDateTime.class),
                        findSessions(eventId),
                        findRules(eventId),
                        findDetailSections(eventId),
                        findNotices(eventId)
                ), eventId, organizerId).stream().findFirst();
    }

    public OrganizerEvent createDraft(
            Long organizerId,
            EventProductDraft product,
            LocalDateTime now
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO event_catalog
                        (organizer_id, name, location, category_id, city, venue_address,
                         description, poster_url, duration_minutes, sale_start_time, sale_end_time,
                         purchase_limit, real_name_rule, entry_method, refund_rule, waitlist_enabled,
                         status, created_time, updated_time, published_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, NULL)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, organizerId);
            setProduct(statement, product, 2);
            statement.setTimestamp(17, Timestamp.valueOf(now));
            statement.setTimestamp(18, Timestamp.valueOf(now));
            return statement;
        }, keyHolder);
        Long eventId = requireKey(keyHolder, "activity");
        replaceProductContent(eventId, product);
        return findOrganizerEvent(eventId, organizerId)
                .orElseThrow(() -> new BusinessException("event creation failed"));
    }

    public void updateBasicInfo(
            Long eventId,
            Long organizerId,
            EventProductDraft product,
            LocalDateTime now
    ) {
        int updated = jdbcTemplate.update("""
                        UPDATE event_catalog
                        SET name = ?, location = ?, category_id = ?, city = ?, venue_address = ?,
                            description = ?, poster_url = ?, duration_minutes = ?,
                            sale_start_time = ?, sale_end_time = ?, purchase_limit = ?,
                            real_name_rule = ?, entry_method = ?, refund_rule = ?,
                            waitlist_enabled = ?, updated_time = ?
                        WHERE id = ? AND organizer_id = ?
                        """,
                product.name(), product.venueName(), product.categoryId(), product.city(),
                product.venueAddress(), product.description(), product.posterUrl(),
                product.durationMinutes(), Timestamp.valueOf(product.saleStartTime()),
                Timestamp.valueOf(product.saleEndTime()), product.purchaseLimit(),
                product.realNameRule(), product.entryMethod(), product.refundRule(),
                product.waitlistEnabled(), Timestamp.valueOf(now), eventId, organizerId);
        requireOwnedUpdate(updated);
        replaceProductContent(eventId, product);
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
        OrganizerEvent event = requireOwnedEvent(eventId, organizerId);
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
        Integer publicationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_publication WHERE event_id = ?", Integer.class, eventId);
        Object[] values = publicationValues(event, now);
        if (publicationCount != null && publicationCount > 0) {
            jdbcTemplate.update("""
                    UPDATE event_publication
                    SET organizer_id = ?, category_id = ?, category_name = ?, content_profile = ?, name = ?, city = ?,
                        venue_name = ?, venue_address = ?, description = ?, poster_url = ?,
                        duration_minutes = ?, sale_start_time = ?, sale_end_time = ?, purchase_limit = ?,
                        real_name_rule = ?, entry_method = ?, refund_rule = ?, waitlist_enabled = ?,
                        published_time = ?
                    WHERE event_id = ?
                    """, append(values, eventId));
        } else {
            jdbcTemplate.update("""
                    INSERT INTO event_publication
                        (organizer_id, category_id, category_name, content_profile, name, city, venue_name,
                         venue_address, description, poster_url, duration_minutes, sale_start_time,
                         sale_end_time, purchase_limit, real_name_rule, entry_method, refund_rule,
                         waitlist_enabled, published_time, event_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, append(values, eventId));
        }
        jdbcTemplate.update("DELETE FROM ticket_category_publication WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM event_session_publication WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM event_rule_publication WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM event_detail_section_publication WHERE event_id = ?", eventId);
        jdbcTemplate.update("""
                INSERT INTO event_session_publication (event_id, session_id, start_time, end_time)
                SELECT event_id, id, start_time, end_time
                FROM event_session_catalog
                WHERE event_id = ?
                """, eventId);
        jdbcTemplate.update("""
                INSERT INTO ticket_category_publication
                    (event_id, session_id, ticket_category_id, name, price_cents)
                SELECT session.event_id, ticket.session_id, ticket.id, ticket.name, ticket.price_cents
                FROM ticket_category_catalog ticket
                JOIN event_session_catalog session ON session.id = ticket.session_id
                WHERE session.event_id = ?
                """, eventId);
        jdbcTemplate.update("""
                INSERT INTO event_rule_publication
                    (event_id, rule_group, rule_code, title, content, display_order)
                SELECT event_id, rule_group, rule_code, title, content, display_order
                FROM event_rule_catalog
                WHERE event_id = ?
                """, eventId);
        jdbcTemplate.update("""
                INSERT INTO event_detail_section_publication
                    (event_id, section_id, section_type, title, content, image_url, display_order)
                SELECT event_id, id, section_type, title, content, image_url, display_order
                FROM event_detail_section_catalog
                WHERE event_id = ?
                """, eventId);
        jdbcTemplate.update("""
                        UPDATE event_catalog
                        SET status = 'PUBLISHED', updated_time = ?, published_time = COALESCE(published_time, ?)
                        WHERE id = ? AND organizer_id = ?
                        """,
                Timestamp.valueOf(now), Timestamp.valueOf(now), eventId, organizerId);
    }

    public boolean isWaitlistEnabledForSession(Long sessionId) {
        Boolean enabled = jdbcTemplate.queryForObject("""
                SELECT publication.waitlist_enabled
                FROM event_session_publication session
                JOIN event_publication publication ON publication.event_id = session.event_id
                WHERE session.session_id = ?
                """, Boolean.class, sessionId);
        return Boolean.TRUE.equals(enabled);
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

    public Optional<TicketCategory> findPublishedTicketCategory(Long sessionId, Long ticketCategoryId) {
        return jdbcTemplate.query("""
                        SELECT snapshot.ticket_category_id AS id, snapshot.session_id,
                               snapshot.name, snapshot.price_cents,
                               live.total_stock, live.remaining_stock
                        FROM ticket_category_publication snapshot
                        JOIN ticket_category_catalog live
                          ON live.id = snapshot.ticket_category_id
                         AND live.session_id = snapshot.session_id
                        WHERE snapshot.ticket_category_id = ? AND snapshot.session_id = ?
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
                        SELECT event_id FROM event_session_publication WHERE session_id = ?
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

    private List<EventSession> findPublishedSessions(Long eventId) {
        return jdbcTemplate.query("""
                        SELECT event_id, session_id, start_time, end_time
                        FROM event_session_publication
                        WHERE event_id = ?
                        ORDER BY start_time, session_id
                        """,
                (resultSet, rowNumber) -> new EventSession(
                        resultSet.getLong("session_id"),
                        resultSet.getLong("event_id"),
                        resultSet.getObject("start_time", LocalDateTime.class),
                        resultSet.getObject("end_time", LocalDateTime.class),
                        findPublishedCategories(resultSet.getLong("session_id"))
                ), eventId);
    }

    private List<TicketCategory> findPublishedCategories(Long sessionId) {
        return jdbcTemplate.query("""
                        SELECT snapshot.ticket_category_id AS id, snapshot.session_id,
                               snapshot.name, snapshot.price_cents,
                               live.total_stock, live.remaining_stock
                        FROM ticket_category_publication snapshot
                        JOIN ticket_category_catalog live
                          ON live.id = snapshot.ticket_category_id
                         AND live.session_id = snapshot.session_id
                        WHERE snapshot.session_id = ?
                        ORDER BY snapshot.price_cents, snapshot.ticket_category_id
                        """,
                (resultSet, rowNumber) -> new TicketCategory(
                        resultSet.getLong("id"), resultSet.getLong("session_id"),
                        resultSet.getString("name"), resultSet.getLong("price_cents"),
                        resultSet.getInt("total_stock"), resultSet.getInt("remaining_stock")
                ), sessionId);
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

    private void replaceProductContent(Long eventId, EventProductDraft product) {
        jdbcTemplate.update("DELETE FROM event_rule_catalog WHERE event_id = ?", eventId);
        for (EventRuleDraft rule : product.rules()) {
            jdbcTemplate.update("""
                    INSERT INTO event_rule_catalog
                        (event_id, rule_group, rule_code, title, content, display_order)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, eventId, rule.ruleGroup(), rule.ruleCode(), rule.title(), rule.content(),
                    rule.displayOrder());
        }
        jdbcTemplate.update("DELETE FROM event_detail_section_catalog WHERE event_id = ?", eventId);
        for (EventDetailSectionDraft section : product.detailSections()) {
            jdbcTemplate.update("""
                    INSERT INTO event_detail_section_catalog
                        (event_id, section_type, title, content, image_url, display_order)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, eventId, section.sectionType(), section.title(), section.content(),
                    section.imageUrl(), section.displayOrder());
        }
    }

    private List<EventRule> findRules(Long eventId) {
        return jdbcTemplate.query("""
                        SELECT id, event_id, rule_group, rule_code, title, content, display_order
                        FROM event_rule_catalog
                        WHERE event_id = ?
                        ORDER BY CASE rule_group WHEN 'PURCHASE' THEN 0 ELSE 1 END,
                                 display_order, id
                        """,
                (resultSet, rowNumber) -> new EventRule(
                        resultSet.getLong("id"), resultSet.getLong("event_id"),
                        resultSet.getString("rule_group"), resultSet.getString("rule_code"),
                        resultSet.getString("title"), resultSet.getString("content"),
                        resultSet.getInt("display_order")), eventId);
    }

    private List<EventRule> findPublishedRules(Long eventId) {
        return jdbcTemplate.query("""
                        SELECT event_id, rule_group, rule_code, title, content, display_order
                        FROM event_rule_publication
                        WHERE event_id = ?
                        ORDER BY CASE rule_group WHEN 'PURCHASE' THEN 0 ELSE 1 END,
                                 display_order, rule_code
                        """,
                (resultSet, rowNumber) -> new EventRule(
                        null, resultSet.getLong("event_id"), resultSet.getString("rule_group"),
                        resultSet.getString("rule_code"), resultSet.getString("title"),
                        resultSet.getString("content"), resultSet.getInt("display_order")), eventId);
    }

    private List<EventDetailSection> findDetailSections(Long eventId) {
        return jdbcTemplate.query("""
                        SELECT id, event_id, section_type, title, content, image_url, display_order
                        FROM event_detail_section_catalog
                        WHERE event_id = ?
                        ORDER BY display_order, id
                        """,
                (resultSet, rowNumber) -> new EventDetailSection(
                        resultSet.getLong("id"), resultSet.getLong("event_id"),
                        resultSet.getString("section_type"), resultSet.getString("title"),
                        resultSet.getString("content"), resultSet.getString("image_url"),
                        resultSet.getInt("display_order")), eventId);
    }

    private List<EventDetailSection> findPublishedDetailSections(Long eventId) {
        return jdbcTemplate.query("""
                        SELECT section_id, event_id, section_type, title, content, image_url, display_order
                        FROM event_detail_section_publication
                        WHERE event_id = ?
                        ORDER BY display_order, section_id
                        """,
                (resultSet, rowNumber) -> new EventDetailSection(
                        resultSet.getLong("section_id"), resultSet.getLong("event_id"),
                        resultSet.getString("section_type"), resultSet.getString("title"),
                        resultSet.getString("content"), resultSet.getString("image_url"),
                        resultSet.getInt("display_order")), eventId);
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

    private void setProduct(PreparedStatement statement, EventProductDraft product, int start)
            throws java.sql.SQLException {
        statement.setString(start, product.name());
        statement.setString(start + 1, product.venueName());
        statement.setLong(start + 2, product.categoryId());
        statement.setString(start + 3, product.city());
        statement.setString(start + 4, product.venueAddress());
        statement.setString(start + 5, product.description());
        statement.setString(start + 6, product.posterUrl());
        statement.setInt(start + 7, product.durationMinutes());
        statement.setTimestamp(start + 8, Timestamp.valueOf(product.saleStartTime()));
        statement.setTimestamp(start + 9, Timestamp.valueOf(product.saleEndTime()));
        statement.setInt(start + 10, product.purchaseLimit());
        statement.setString(start + 11, product.realNameRule());
        statement.setString(start + 12, product.entryMethod());
        statement.setString(start + 13, product.refundRule());
        statement.setBoolean(start + 14, product.waitlistEnabled());
    }

    private Object[] publicationValues(OrganizerEvent event, LocalDateTime publishedTime) {
        return new Object[]{
                event.organizerId(), event.categoryId(), event.categoryName(), event.contentProfile(),
                event.name(), event.city(),
                event.location(), event.venueAddress(), event.description(), event.posterUrl(),
                event.durationMinutes(), Timestamp.valueOf(event.saleStartTime()),
                Timestamp.valueOf(event.saleEndTime()), event.purchaseLimit(), event.realNameRule(),
                event.entryMethod(), event.refundRule(), event.waitlistEnabled(),
                Timestamp.valueOf(publishedTime)
        };
    }

    private Object[] append(Object[] values, Object tail) {
        Object[] result = java.util.Arrays.copyOf(values, values.length + 1);
        result[result.length - 1] = tail;
        return result;
    }

    private String resolveSaleStatus(
            LocalDateTime saleStartTime,
            LocalDateTime saleEndTime,
            List<EventSession> sessions
    ) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(saleStartTime)) {
            return "UPCOMING";
        }
        if (!now.isBefore(saleEndTime)) {
            return "ENDED";
        }
        boolean available = sessions.stream()
                .flatMap(session -> session.ticketCategories().stream())
                .anyMatch(category -> category.remainingStock() > 0);
        return available ? "ON_SALE" : "SOLD_OUT";
    }

    private Long requireKey(KeyHolder keyHolder, String resource) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException(resource + " creation failed");
        }
        return key.longValue();
    }
}
