package com.op1ed.appointmentmanager.appointment.infrastructure.persistence.mapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.op1ed.appointmentmanager.appointment.domain.Appointment;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 继承 BaseMapper 的通用增删改查，下面的方法声明本模块需要的 SQL。 */
@Mapper
public interface AppointmentMapper extends BaseMapper<Appointment> {
    @Select("SELECT * FROM appointments WHERE id = #{id} AND owner_id = #{ownerId}")
    Optional<Appointment> findOwned(@Param("id") Long id, @Param("ownerId") Long ownerId);

    @Select("""
            <script>
            SELECT * FROM appointments WHERE id = #{id}
            <if test="ownerId != null">AND owner_id = #{ownerId}</if>
            FOR UPDATE
            </script>
            """)
    Optional<Appointment> lockVisible(@Param("id") Long id, @Param("ownerId") Long ownerId);

    @Select("SELECT EXISTS(SELECT 1 FROM appointments WHERE slot_id = #{slotId} AND status = #{status})")
    boolean existsOccupied(@Param("slotId") Long slotId, @Param("status") AppointmentStatus status);

    @Select("""
            <script>
            SELECT * FROM appointments WHERE status = #{status} AND slot_id IN
            <foreach collection="slotIds" item="slotId" open="(" separator="," close=")">#{slotId}</foreach>
            </script>
            """)
    List<Appointment> findBySlots(@Param("slotIds") List<Long> slotIds, @Param("status") AppointmentStatus status);

    @Select("""
            <script>
            SELECT * FROM appointments WHERE 1 = 1
            <if test="ownerId != null">AND owner_id = #{ownerId}</if>
            <if test="status != null">AND status = #{status}</if>
            <if test="customerName != null">AND customer_name = #{customerName}</if>
            <if test="fromTime != null">AND start_time &gt;= #{fromTime}</if>
            <if test="toTime != null">AND start_time &lt; #{toTime}</if>
            ORDER BY id ASC
            </script>
            """)
    IPage<Appointment> search(IPage<Appointment> page, @Param("ownerId") Long ownerId, @Param("status") AppointmentStatus status,
            @Param("customerName") String customerName, @Param("fromTime") Instant fromTime, @Param("toTime") Instant toTime);
}
