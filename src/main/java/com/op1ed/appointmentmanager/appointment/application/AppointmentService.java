package com.op1ed.appointmentmanager.appointment.application;

import java.util.Optional;

import com.op1ed.appointmentmanager.identity.application.UserIdentity;

/**
 * 预约业务接口，由 HTTP 和 Agent 共用，调用身份由服务端传入。
 *
 * <p>接口描述一次业务操作；权限、行锁、事务和操作记录的实现见 impl/AppointmentServiceImpl。
 * 当前用户无权访问的预约与不存在的预约，都返回空 Optional。
 */
public interface AppointmentService {

    /** 为当前登录账号创建预约；提供 requestId 时，相同账号与输入的重试返回首次成功结果。 */
    AppointmentResult create(UserIdentity caller, BookAppointmentCommand command);

    /** 按身份可见范围与筛选条件分页查询预约。 */
    AppointmentPageResult search(UserIdentity caller, AppointmentQuery query);

    /** 查询有权访问的预约。 */
    Optional<AppointmentResult> findById(UserIdentity caller, long id);

    /** 取消预约并保存记录；重复取消直接返回原结果。 */
    Optional<AppointmentResult> cancel(UserIdentity caller, long id);

    /** Agent 确认取消时，还要检查预约时段是否与预览一致。 */
    Optional<AppointmentResult> cancelIfUnchanged(UserIdentity caller, long id, Long expectedSlotId);

    /** 改到目标时段，同时检查原时段未发生变化并保存操作记录。 */
    Optional<AppointmentResult> reschedule(UserIdentity caller, long id, RescheduleAppointmentCommand command);

    /** 分页查询有权访问的预约的操作记录。 */
    Optional<AppointmentHistoryPageResult> history(UserIdentity caller, long id, AppointmentHistoryQuery query);
}
