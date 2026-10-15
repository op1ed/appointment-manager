package com.op1ed.appointmentmanager.shared.persistence;

import java.sql.*;
import java.time.Instant;
import java.util.Calendar;
import com.op1ed.appointmentmanager.shared.persistence.typehandler.UtcInstantTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UtcInstantTypeHandlerTest {
    private final UtcInstantTypeHandler handler = new UtcInstantTypeHandler();
    private final Instant value = Instant.parse("2030-01-01T00:00:00.123456Z");

    @Test void shouldBindTimestampWithExplicitUtcCalendarAndPreserveMicroseconds() throws Exception {
        var statement = mock(PreparedStatement.class);
        handler.setNonNullParameter(statement, 1, value, JdbcType.TIMESTAMP);
        var timestamp = ArgumentCaptor.forClass(Timestamp.class);
        var calendar = ArgumentCaptor.forClass(Calendar.class);
        verify(statement).setTimestamp(eq(1), timestamp.capture(), calendar.capture());
        assertThat(timestamp.getValue().toInstant()).isEqualTo(value);
        assertThat(calendar.getValue().getTimeZone().getID()).isEqualTo("UTC");
    }
    @Test void shouldReadNamedTimestampUsingUtcAndPreserveNull() throws Exception {
        var result = mock(ResultSet.class);
        when(result.getTimestamp(eq("created_at"), any(Calendar.class))).thenReturn(Timestamp.from(value), (Timestamp) null);
        assertThat(handler.getNullableResult(result, "created_at")).isEqualTo(value);
        assertThat(handler.getNullableResult(result, "created_at")).isNull();
        var calendar = ArgumentCaptor.forClass(Calendar.class);
        verify(result, times(2)).getTimestamp(eq("created_at"), calendar.capture());
        assertThat(calendar.getAllValues()).allSatisfy(item -> assertThat(item.getTimeZone().getID()).isEqualTo("UTC"));
    }
    @Test void shouldReadIndexedAndCallableTimestampUsingUtc() throws Exception {
        var result = mock(ResultSet.class);
        var statement = mock(CallableStatement.class);
        when(result.getTimestamp(eq(2), any(Calendar.class))).thenReturn(Timestamp.from(value));
        when(statement.getTimestamp(eq(3), any(Calendar.class))).thenReturn(Timestamp.from(value));
        assertThat(handler.getNullableResult(result, 2)).isEqualTo(value);
        assertThat(handler.getNullableResult(statement, 3)).isEqualTo(value);
        var calendar = ArgumentCaptor.forClass(Calendar.class);
        verify(result).getTimestamp(eq(2), calendar.capture());
        verify(statement).getTimestamp(eq(3), calendar.capture());
        assertThat(calendar.getAllValues()).allSatisfy(item -> assertThat(item.getTimeZone().getID()).isEqualTo("UTC"));
    }
}
