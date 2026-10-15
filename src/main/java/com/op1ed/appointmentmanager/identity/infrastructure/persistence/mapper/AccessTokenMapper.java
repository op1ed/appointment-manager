package com.op1ed.appointmentmanager.identity.infrastructure.persistence.mapper;

import java.util.Optional;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.op1ed.appointmentmanager.identity.domain.AccessToken;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 继承 BaseMapper 的通用增删改查，下面的方法声明本模块需要的 SQL。 */
@Mapper
public interface AccessTokenMapper extends BaseMapper<AccessToken> {
    @Select("SELECT * FROM user_access_tokens WHERE token_hash = #{tokenHash}")
    Optional<AccessToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    @Delete("DELETE FROM user_access_tokens WHERE token_hash = #{tokenHash}")
    int revokeByTokenHash(@Param("tokenHash") String tokenHash);
}
