package com.op1ed.appointmentmanager.appointment.infrastructure.persistence.mapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentSlot;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 继承 BaseMapper 的通用增删改查，下面的方法声明本模块需要的 SQL。 */
@Mapper
public interface AppointmentSlotMapper extends BaseMapper<AppointmentSlot> {
    @Select("SELECT * FROM appointment_slots WHERE id = #{id} FOR UPDATE")
    Optional<AppointmentSlot> lockById(@Param("id") Long id);

    @Select("SELECT * FROM appointment_slots WHERE doctor_id = #{doctorId} ORDER BY start_time ASC, id ASC")
    List<AppointmentSlot> listByDoctor(@Param("doctorId") Long doctorId);

    @Select("SELECT EXISTS(SELECT 1 FROM appointment_slots WHERE doctor_id = #{doctorId} AND start_time < #{endTime} AND end_time > #{startTime})")
    boolean overlaps(@Param("doctorId") Long doctorId, @Param("endTime") Instant endTime, @Param("startTime") Instant startTime);

    @Select("""
            <script>
            SELECT s.* FROM appointment_slots s
            WHERE s.doctor_id = #{doctorId} AND s.start_time &gt; #{now}
            <if test="fromTime != null">AND s.start_time &gt;= #{fromTime}</if>
            <if test="toTime != null">AND s.start_time &lt; #{toTime}</if>
            AND NOT EXISTS (SELECT 1 FROM appointments a WHERE a.slot_id = s.id AND a.status = 'BOOKED')
            ORDER BY s.start_time ASC, s.id ASC
            </script>
            """)
    IPage<AppointmentSlot> searchAvailable(IPage<AppointmentSlot> page, @Param("doctorId") Long doctorId,
            @Param("now") Instant now, @Param("fromTime") Instant fromTime, @Param("toTime") Instant toTime);
}
