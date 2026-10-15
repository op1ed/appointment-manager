package com.op1ed.appointmentmanager.agent.application;

import java.util.List;
import com.op1ed.appointmentmanager.agent.application.workflow.ReschedulingWorkflowView;

/**
 * Agent 部分的输出对象：AgentRuntime 执行完一轮后，交给 AgentChatService 的结果。
 *
 * <p>它把“给用户展示的内容”和“需要保存的执行信息”一起带回来。
 * AgentChatService 将这些数据整理为接口响应，并保存到会话中。
 *
 * <p>generatedMessages 保存工具调用及其结果，最终文字单独放在 reply 中。
 * 保存历史时，AgentChatService 会把本轮用户消息、generatedMessages、reply 转成的
 * assistant 消息按顺序拼起来。这样下一轮既能看到工具过程，也能看到最终回复。
 *
 * <p>几个常见状态：
 * COMPLETED 表示本轮对话结束；ACTION_PENDING 表示返回了操作预览；
 * WORKFLOW_PENDING 表示返回了改期流程，具体进度看流程自身的 status；
 * LIMIT_REACHED 表示到达调用或上下文预算；FAILED 表示本轮执行失败。
 * 特别注意：COMPLETED 说的是对话结束，不能据此认定预约已经创建或改期。
 *
 * @param status 本轮 Agent 的执行状态，含义见上面的说明
 * @param reply 给用户展示的最终文字；得到预览或流程时，由服务器生成确认提示
 * @param actions 本轮生成的操作预览列表，供用户核对；生成预览本身不会创建、取消或移动预约
 * @param toolExecutions 工具调用摘要，每项记录调用 id、工具名、成功与否、错误码；用于展示和排查
 * @param modelCalls 本轮实际请求模型的次数，一次 run 可以包含多次模型请求
 * @param failureCode 本轮失败原因，如 MODEL_TIMEOUT；没有整轮失败时通常为 null，单个工具错误另看摘要
 * @param generatedMessages 本轮新增的工具调用消息和对应结果，用于保存历史；不包含传入 run 的旧历史
 * @param workflows 本轮新启动的改期流程列表，包含流程 id、候选时段和当前状态
 */
public record AgentRunResult(String status, String reply, List<AgentActionResult> actions,
        List<AgentToolExecution> toolExecutions, int modelCalls, String failureCode,
        List<AgentMessage> generatedMessages, List<ReschedulingWorkflowView> workflows) {
    public AgentRunResult {
        // 保存不可增删的列表副本，避免运行器的临时列表继续变化，影响已经返回的结果。
        // 这保护的是列表容器；不是对元素内部数据做深拷贝。
        actions = List.copyOf(actions);
        toolExecutions = List.copyOf(toolExecutions);
        generatedMessages = List.copyOf(generatedMessages);
        workflows = List.copyOf(workflows);
    }
}
