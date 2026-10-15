package com.op1ed.appointmentmanager.shared.persistence;

import java.io.Serializable;
import java.util.Optional;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** 共用最小的新增、更新和按主键查询；各模块自己的查询留在其 Repository 中。 */
public abstract class MybatisRepository<T> {

    private final BaseMapper<T> mapper;

    protected MybatisRepository(BaseMapper<T> mapper) {
        this.mapper = mapper;
    }

    /** 新增立即执行 INSERT，数据库自增主键会回填到对象中；事务仍由调用服务控制。 */
    public T save(T entity) {
        mapper.insert(entity);
        return entity;
    }

    /** 显式执行 UPDATE。修改 Java 对象后必须调用它，MyBatis 不会自动检测对象变化。 */
    public T update(T entity) {
        if (mapper.updateById(entity) != 1) {
            throw new IllegalStateException("更新的记录不存在");
        }
        return entity;
    }

    public Optional<T> findById(Serializable id) {
        return Optional.ofNullable(mapper.selectById(id));
    }
}
