package com.op1ed.appointmentmanager.doctor.application;

import java.util.List;
import java.util.Optional;

import com.op1ed.appointmentmanager.identity.application.UserIdentity;

/**
 * 医生模块对外提供的业务能力。Controller 和其他模块依赖这个接口。
 *
 * <p>先看这里有哪些方法，再到 impl/DoctorServiceImpl 阅读校验、事务和 Mapper 调用。
 * Spring 将对应实现的 Bean 注入调用方；本接口只声明方法，不注册组件。
 */
public interface DoctorService {

    /** 管理员创建医生。 */
    DoctorInfo create(UserIdentity caller, CreateDoctorCommand command);

    /** 按 id 升序查询所有医生。 */
    List<DoctorInfo> findAll();

    /** 按编号查询，不存在时返回空 Optional。 */
    Optional<DoctorInfo> findById(long id);

    /** 分页查询，可按姓名中的字面量筛选，页号从 0 开始。 */
    DoctorPageResult search(int page, int size, String name);

    /** 业务流程需要医生必须存在时使用，不存在则抛业务异常。 */
    DoctorInfo requireById(long id);

    /** 发布时段时锁定医生；调用方必须已开启事务，锁保持到该事务结束。 */
    DoctorInfo lockForScheduling(long id);
}
