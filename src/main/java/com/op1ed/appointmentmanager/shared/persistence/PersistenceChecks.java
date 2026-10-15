package com.op1ed.appointmentmanager.shared.persistence;

/** 检查已经执行的 SQL 结果；不持有 Mapper，也不发起数据库访问。 */
public final class PersistenceChecks {

    private PersistenceChecks() {
    }

    /** 已锁定并读取的记录应更新一行，异常时让外层业务事务整体回滚。 */
    public static void requireUpdatedRow(int affectedRows) {
        if (affectedRows != 1) {
            throw new IllegalStateException("更新的记录不存在");
        }
    }
}
