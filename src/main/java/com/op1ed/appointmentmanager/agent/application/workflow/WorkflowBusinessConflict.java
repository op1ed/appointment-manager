package com.op1ed.appointmentmanager.agent.application.workflow;

/** 只标记业务准备或执行节点的冲突，不能把权限、修订号等错误当成可自动恢复的冲突。 */
public class WorkflowBusinessConflict extends RuntimeException {
    public WorkflowBusinessConflict(String detail, Throwable cause) { super(detail, cause); }
}
