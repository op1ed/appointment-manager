package com.op1ed.appointmentmanager.appointment.infrastructure.persistence.mapper;

import java.util.Optional;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentBookingRequest;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AppointmentBookingRequestMapper extends BaseMapper<AppointmentBookingRequest> {

    /**
     * 首次请求插入；同账号、同请求 ID 再来时，只锁住原记录，保留首次输入与结果。
     * InnoDB 的唯一约束和更新锁使并发重试等待前一个事务；不使用“先查再插”。
     * 不依据影响行数判断首次请求，因为不改变值的 UPDATE 受 JDBC 选项影响。
     */
    @Insert("""
            INSERT INTO appointment_booking_requests (owner_id, request_id, customer_name, slot_id, created_at)
            VALUES (#{request.ownerId}, #{request.requestId}, #{request.customerName}, #{request.slotId}, #{request.createdAt})
            ON DUPLICATE KEY UPDATE id = id
            """)
    void reserve(@Param("request") AppointmentBookingRequest request);

    /** 锁定读取取到等待后的最新结果；此查询不会提前建立普通查询的事务快照。 */
    @Select("""
            SELECT * FROM appointment_booking_requests
            WHERE owner_id = #{ownerId} AND request_id = #{requestId}
            FOR UPDATE
            """)
    Optional<AppointmentBookingRequest> lockByRequest(@Param("ownerId") Long ownerId, @Param("requestId") String requestId);

    /** 成功结果只写一次；与预约创建和操作记录共用事务。 */
    @Update("UPDATE appointment_booking_requests SET response_json = #{responseJson} WHERE id = #{id} AND response_json IS NULL")
    int complete(@Param("id") Long id, @Param("responseJson") String responseJson);
}
