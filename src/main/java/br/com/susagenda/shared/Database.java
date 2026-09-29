package br.com.susagenda.shared;

import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Converte nomes SQL para camelCase e datas para ISO-8601 na API. */
@Component
public class Database {
    public final JdbcTemplate jdbc;
    public Database(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Map<String,Object>> rows(String sql, Object... args) {
        return jdbc.query(sql, (rs, row) -> {
            Map<String,Object> result = new LinkedHashMap<>();
            var meta = rs.getMetaData();
            for (int i=1; i<=meta.getColumnCount(); i++) {
                String name = meta.getColumnLabel(i).toLowerCase(Locale.ROOT);
                StringBuilder key = new StringBuilder(); boolean upper = false;
                for (char c : name.toCharArray()) {
                    if (c=='_') { upper=true; continue; }
                    key.append(upper ? Character.toUpperCase(c) : c); upper=false;
                }
                Object value = rs.getObject(i);
                if (value instanceof OffsetDateTime d) value = d.toInstant().toString();
                if (value instanceof Timestamp t) value = t.toInstant().toString();
                result.put(key.toString(), value);
            }
            return result;
        }, args);
    }
    public Map<String,Object> one(String sql, Object... args) {
        var results = rows(sql, args);
        if (results.isEmpty()) throw BusinessException.missing();
        return results.get(0);
    }
    public long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    public static String text(Map<String,Object> row, String key) { return (String)row.get(key); }
    public static OffsetDateTime utc(Instant instant) { return instant.atOffset(ZoneOffset.UTC); }
}
