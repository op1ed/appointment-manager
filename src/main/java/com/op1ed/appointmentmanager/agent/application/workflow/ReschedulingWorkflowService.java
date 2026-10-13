package com.op1ed.appointmentmanager.agent.application.workflow;

import com.op1ed.appointmentmanager.agent.application.AgentIds;
import com.op1ed.appointmentmanager.identity.application.UserIdentity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** 位于事务外，确保业务冲突事务先完整回滚，再开始恢复事务。 */
@Service
public class ReschedulingWorkflowService {
    private final ReschedulingWorkflowTransactions transactions;
    public ReschedulingWorkflowService(ReschedulingWorkflowTransactions transactions) { this.transactions = transactions; }
    public ReschedulingWorkflowView start(UserIdentity caller, WorkflowCommands.Start command) {
        UserIdentity user = UserIdentity.requireAuthenticated(caller);
        try { return transactions.start(user, command); }
        catch (DataIntegrityViolationException exception) {
            // 相同 requestId 的并发创建：失败事务已经回滚，再读取胜出请求。
            return transactions.replayStart(user, command).orElseThrow(() -> exception);
        }
    }
    public ReschedulingWorkflowView advance(UserIdentity caller, String id, WorkflowCommands.Signal signal, WorkflowCommands.Step command) {
        UserIdentity user = UserIdentity.requireAuthenticated(caller);
        String workflowId = AgentIds.requireUuid(id, "流程 ID");
        try { return transactions.advance(user, workflowId, signal, command); }
        catch (WorkflowBusinessConflict conflict) {
            return transactions.recover(user, workflowId, signal, command, conflict.getMessage());
        }
    }
    public ReschedulingWorkflowView find(UserIdentity caller, String id) {
        return transactions.find(UserIdentity.requireAuthenticated(caller), AgentIds.requireUuid(id, "流程 ID"));
    }
}
