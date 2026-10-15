package com.op1ed.appointmentmanager.identity.infrastructure.persistence.mapper;

import java.util.Optional;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.op1ed.appointmentmanager.identity.domain.UserAccount;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 继承 BaseMapper 的通用增删改查，下面的方法声明本模块需要的 SQL。 */
@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {
    @Select("SELECT * FROM user_accounts WHERE username = #{username}")
    Optional<UserAccount> findByUsername(@Param("username") String username);

    @Select("SELECT EXISTS(SELECT 1 FROM user_accounts WHERE username = #{username})")
    boolean existsByUsername(@Param("username") String username);
}
