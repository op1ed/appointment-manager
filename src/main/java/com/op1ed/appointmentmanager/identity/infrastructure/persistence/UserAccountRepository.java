package com.op1ed.appointmentmanager.identity.infrastructure.persistence;

import java.util.Optional;

import com.op1ed.appointmentmanager.identity.domain.UserAccount;
import com.op1ed.appointmentmanager.identity.infrastructure.persistence.mapper.UserAccountMapper;
import com.op1ed.appointmentmanager.shared.persistence.MybatisRepository;

import org.springframework.stereotype.Repository;

/** 本模块的数据访问入口；Mapper 使用 MyBatis-Plus 执行 SQL。 */
@Repository
public class UserAccountRepository extends MybatisRepository<UserAccount> {

    private final UserAccountMapper mapper;

    public UserAccountRepository(UserAccountMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    public Optional<UserAccount> findByUsername(String username) {
        return mapper.findByUsername(username);
    }

    public boolean existsByUsername(String username) {
        return mapper.existsByUsername(username);
    }
}
