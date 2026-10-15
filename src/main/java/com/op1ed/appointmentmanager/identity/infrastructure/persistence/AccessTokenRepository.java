package com.op1ed.appointmentmanager.identity.infrastructure.persistence;

import java.util.Optional;

import com.op1ed.appointmentmanager.identity.domain.AccessToken;
import com.op1ed.appointmentmanager.identity.infrastructure.persistence.mapper.AccessTokenMapper;
import com.op1ed.appointmentmanager.shared.persistence.MybatisRepository;

import org.springframework.stereotype.Repository;

/** 本模块的数据访问入口；Mapper 使用 MyBatis-Plus 执行 SQL。 */
@Repository
public class AccessTokenRepository extends MybatisRepository<AccessToken> {

    private final AccessTokenMapper mapper;

    public AccessTokenRepository(AccessTokenMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    public Optional<AccessToken> findByTokenHash(String tokenHash) {
        return mapper.findByTokenHash(tokenHash);
    }

    public void revokeByTokenHash(String tokenHash) { mapper.revokeByTokenHash(tokenHash); }
}
