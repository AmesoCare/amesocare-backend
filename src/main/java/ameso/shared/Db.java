package ameso.shared;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Thin Dapper-style helper: inline SQL with :named params (passed as name/value pairs),
 * rows mapped onto record constructors by column label (case-insensitive).
 */
@Component
public class Db {
    private final NamedParameterJdbcTemplate jdbc;

    public Db(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    public <T> List<T> query(Class<T> type, String sql, Object... params) {
        return jdbc.query(sql, params(params), mapper(type));
    }

    public <T> T one(Class<T> type, String sql, Object... params) {
        var rows = query(type, sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public <T> T scalar(Class<T> type, String sql, Object... params) {
        return jdbc.queryForObject(sql, params(params), type);
    }

    public int exec(String sql, Object... params) {
        return jdbc.update(sql, params(params));
    }

    private static MapSqlParameterSource params(Object... kv) {
        var p = new MapSqlParameterSource();
        for (int i = 0; i < kv.length; i += 2) p.addValue((String) kv[i], kv[i + 1]);
        return p;
    }

    private static <T> RowMapper<T> mapper(Class<T> type) {
        RecordComponent[] comps = type.getRecordComponents();
        Constructor<T> ctor;
        try {
            ctor = type.getDeclaredConstructor(java.util.Arrays.stream(comps).map(RecordComponent::getType).toArray(Class[]::new));
            ctor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(e);
        }
        return (rs, rowNum) -> {
            var args = new Object[comps.length];
            for (int i = 0; i < comps.length; i++) args[i] = read(rs, comps[i].getName(), comps[i].getType());
            try {
                return ctor.newInstance(args);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        };
    }

    private static Object read(ResultSet rs, String column, Class<?> type) throws SQLException {
        if (type == String[].class) {
            Array a = rs.getArray(column);
            return a == null ? null : (String[]) a.getArray();
        }
        if (type == int.class) return rs.getInt(column);
        if (type == double.class) return rs.getDouble(column);
        if (type == OffsetDateTime.class) return rs.getObject(column, OffsetDateTime.class);
        return rs.getObject(column, type);
    }
}
