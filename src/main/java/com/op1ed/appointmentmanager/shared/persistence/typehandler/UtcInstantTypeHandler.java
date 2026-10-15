package com.op1ed.appointmentmanager.shared.persistence.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

/** 现有 DATETIME(6) 继续按 UTC 读写，保持服务端时区变化前后的 Instant 一致。 */
@MappedTypes(Instant.class)
@MappedJdbcTypes(value = JdbcType.TIMESTAMP, includeNullJdbcType = true)
public class UtcInstantTypeHandler extends BaseTypeHandler<Instant> {

    private Calendar utc() {
        // Calendar 可变，每次创建，避免多个请求共用它。
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, Instant value, JdbcType type) throws SQLException {
        statement.setTimestamp(index, Timestamp.from(value), utc());
    }

    @Override
    public Instant getNullableResult(ResultSet result, String column) throws SQLException {
        return instant(result.getTimestamp(column, utc()));
    }

    @Override
    public Instant getNullableResult(ResultSet result, int column) throws SQLException {
        return instant(result.getTimestamp(column, utc()));
    }

    @Override
    public Instant getNullableResult(CallableStatement statement, int column) throws SQLException {
        return instant(statement.getTimestamp(column, utc()));
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
