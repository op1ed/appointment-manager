package com.op1ed.appointmentmanager.doctor.infrastructure.persistence.mapper;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.op1ed.appointmentmanager.doctor.domain.Doctor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 继承 BaseMapper 的通用增删改查，下面的方法声明本模块需要的 SQL。 */
@Mapper
public interface DoctorMapper extends BaseMapper<Doctor> {
    @Select("SELECT * FROM doctors ORDER BY id ASC")
    List<Doctor> listOrdered();

    @Select("SELECT * FROM doctors WHERE name LIKE #{pattern} ESCAPE '=' ORDER BY id ASC")
    IPage<Doctor> search(IPage<Doctor> page, @Param("pattern") String pattern);

    // 锁属于当前数据库事务，外层服务提交或回滚后才释放。
    @Select("SELECT * FROM doctors WHERE id = #{id} FOR UPDATE")
    Optional<Doctor> lockById(@Param("id") Long id);
}
