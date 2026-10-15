package com.op1ed.appointmentmanager.shared.persistence;

import java.time.Instant;
import java.util.UUID;

import com.op1ed.appointmentmanager.appointment.domain.Appointment;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.AppointmentRepository;
import com.op1ed.appointmentmanager.doctor.domain.Doctor;
import com.op1ed.appointmentmanager.doctor.infrastructure.persistence.DoctorRepository;
import com.op1ed.appointmentmanager.support.AuthenticatedApiTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用 CI 的真实 MySQL，保护框架迁移中容易静默改变的数据语义。 */
class MybatisPersistenceTest extends AuthenticatedApiTest {

    @Autowired private AppointmentRepository appointments;
    @Autowired private DoctorRepository doctors;
    @Autowired private JdbcTemplate db;
    @Autowired private PlatformTransactionManager transactions;

    @Test
    void shouldPreserveUtcMicrosecondsEnumsAndExplicitNullUpdates() {
        Instant start = Instant.parse("2030-06-01T10:15:30.123456Z");
        // 无 slotId、ownerId 的记录模拟 V3/V4 之前的历史数据。
        var appointment = new Appointment("迁移测试-" + UUID.randomUUID(), null, "历史医生", start, start.plusSeconds(1800));
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> appointments.save(appointment));
        assertThat(appointment.getId()).isPositive();

        assertThat(db.queryForObject("SELECT DATE_FORMAT(start_time, '%Y-%m-%d %H:%i:%s.%f') FROM appointments WHERE id = ?",
                String.class, appointment.getId())).isEqualTo("2030-06-01 10:15:30.123456");
        tx.executeWithoutResult(status -> {
            var stored = appointments.findById(appointment.getId()).orElseThrow();
            assertThat(stored.getStartTime()).isEqualTo(start);
            assertThat(stored.getEndTime()).isEqualTo(start.plusSeconds(1800));
            assertThat(stored.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
            ReflectionTestUtils.setField(stored, "endTime", null);
            stored.cancel();
            appointments.update(stored);
        });

        var updated = appointments.findById(appointment.getId()).orElseThrow();
        assertThat(updated.getEndTime()).isNull();
        assertThat(updated.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(db.queryForObject("SELECT status FROM appointments WHERE id = ?", String.class, appointment.getId()))
                .isEqualTo("CANCELLED");
    }

    @Test
    void shouldExecuteRepeatedLockingReadsWithoutReusingCachedObjects() {
        var doctor = doctors.save(new Doctor("行锁测试-" + UUID.randomUUID()));
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var first = doctors.findByIdForUpdate(doctor.getId()).orElseThrow();
            // JDBC 写入与 Mapper 共用本事务，但不会替 MyBatis 清理一级缓存。
            db.update("UPDATE doctors SET name = ? WHERE id = ?", "更新后的姓名", doctor.getId());
            var latest = doctors.findByIdForUpdate(doctor.getId()).orElseThrow();
            assertThat(latest.getName()).isEqualTo("更新后的姓名");
            assertThat(latest).isNotSameAs(first);
        });
    }
}
