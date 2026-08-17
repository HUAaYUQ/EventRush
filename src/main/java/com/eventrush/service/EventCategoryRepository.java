package com.eventrush.service;

import com.eventrush.domain.EventCategory;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class EventCategoryRepository {

    private static final List<String[]> DEFAULT_CATEGORIES = List.of(
            new String[]{"演唱会", "mic"},
            new String[]{"话剧歌剧", "masks"},
            new String[]{"体育赛事", "trophy"},
            new String[]{"展览休闲", "image"},
            new String[]{"儿童亲子", "baby"},
            new String[]{"音乐会", "music"},
            new String[]{"舞蹈芭蕾", "sparkles"},
            new String[]{"曲艺杂谈", "theater"},
            new String[]{"动漫展会", "gamepad"}
    );

    private final JdbcTemplate jdbcTemplate;

    public EventCategoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void ensureDefaults() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM event_category", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < DEFAULT_CATEGORIES.size(); index++) {
            String[] category = DEFAULT_CATEGORIES.get(index);
            jdbcTemplate.update("""
                    INSERT INTO event_category
                        (name, icon_key, display_order, enabled, created_time, updated_time)
                    VALUES (?, ?, ?, TRUE, ?, ?)
                    """, category[0], category[1], index * 10,
                    Timestamp.valueOf(now), Timestamp.valueOf(now));
        }
    }

    public List<EventCategory> list(boolean enabledOnly) {
        String where = enabledOnly ? " WHERE enabled = TRUE" : "";
        return jdbcTemplate.query("""
                        SELECT id, name, icon_key, display_order, enabled, created_time, updated_time
                        FROM event_category
                        """ + where + " ORDER BY display_order, id",
                (resultSet, rowNumber) -> map(resultSet));
    }

    public Optional<EventCategory> find(Long id) {
        return jdbcTemplate.query("""
                        SELECT id, name, icon_key, display_order, enabled, created_time, updated_time
                        FROM event_category WHERE id = ?
                        """, (resultSet, rowNumber) -> map(resultSet), id).stream().findFirst();
    }

    public EventCategory create(String name, String iconKey, int displayOrder, boolean enabled) {
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO event_category
                        (name, icon_key, display_order, enabled, created_time, updated_time)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, name);
            statement.setString(2, iconKey);
            statement.setInt(3, displayOrder);
            statement.setBoolean(4, enabled);
            statement.setTimestamp(5, Timestamp.valueOf(now));
            statement.setTimestamp(6, Timestamp.valueOf(now));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException("CATEGORY_CREATE_FAILED", HttpStatus.INTERNAL_SERVER_ERROR,
                    "活动类目创建失败");
        }
        return require(key.longValue());
    }

    public EventCategory update(Long id, String name, String iconKey, int displayOrder, boolean enabled) {
        int updated = jdbcTemplate.update("""
                        UPDATE event_category
                        SET name = ?, icon_key = ?, display_order = ?, enabled = ?, updated_time = ?
                        WHERE id = ?
                        """, name, iconKey, displayOrder, enabled,
                Timestamp.valueOf(LocalDateTime.now()), id);
        if (updated != 1) {
            throw new BusinessException("CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND, "活动类目不存在");
        }
        return require(id);
    }

    public EventCategory requireEnabled(Long id) {
        EventCategory category = require(id);
        if (!category.enabled()) {
            throw new BusinessException("CATEGORY_DISABLED", HttpStatus.CONFLICT,
                    "所选活动类目已停用，请重新选择");
        }
        return category;
    }

    private EventCategory require(Long id) {
        return find(id).orElseThrow(() -> new BusinessException(
                "CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND, "活动类目不存在"));
    }

    private EventCategory map(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new EventCategory(
                resultSet.getLong("id"), resultSet.getString("name"),
                resultSet.getString("icon_key"), resultSet.getInt("display_order"),
                resultSet.getBoolean("enabled"),
                resultSet.getObject("created_time", LocalDateTime.class),
                resultSet.getObject("updated_time", LocalDateTime.class));
    }
}
