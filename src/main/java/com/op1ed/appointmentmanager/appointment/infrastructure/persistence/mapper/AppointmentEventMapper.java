package com.op1ed.appointmentmanager.appointment.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentEvent;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 继承 BaseMapper 的通用增删改查，下面的方法声明本模块需要的 SQL。 */
@Mapper
public interface AppointmentEventMapper extends BaseMapper<AppointmentEvent> {
    @Select("SELECT * FROM appointment_events WHERE appointment_id = #{appointmentId} ORDER BY id ASC")
    IPage<AppointmentEvent> history(IPage<AppointmentEvent> page, @Param("appointmentId") Long appointmentId);
}
