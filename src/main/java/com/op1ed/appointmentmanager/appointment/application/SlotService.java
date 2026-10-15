package com.op1ed.appointmentmanager.appointment.application;

import java.util.List;
import java.util.Optional;

import com.op1ed.appointmentmanager.identity.application.UserIdentity;

/** 时段业务接口；锁、时间重叠检查与数据库访问见 impl/SlotServiceImpl。 */
public interface SlotService {

    /** 管理员为医生发布未来时段，拒绝与已有时段重叠。 */
    SlotResult create(UserIdentity caller, long doctorId, CreateSlotCommand command);

    /** 查询医生的全部时段及各时段当前的可用状态。 */
    List<SlotResult> findByDoctorId(long doctorId);

    /** 查询一个时段及当前可用状态，不存在时返回空 Optional。 */
    Optional<SlotResult> findById(long id);

    /** 按时间范围分页查询未来且未占用的时段，页号从 0 开始。 */
    SlotPageResult searchAvailable(long doctorId, AvailableSlotQuery query);
}
