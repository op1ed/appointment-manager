package com.op1ed.appointmentmanager.agent.application;

import java.util.List;

/**
 * Agent 部分的输入对象：表示“发给模型或保存在会话中的一条消息”。
 *
 * <p>record 是 Java 的数据类型写法。括号中的参数就是这条消息携带的数据，
 * Java 自动生成构造方法和同名访问方法，例如 message.role()、message.content()。
 *
 * <p>一条消息的 role 说明它来自谁：
 * system 是服务器的系统提示；user 是用户输入；assistant 是模型或服务器的助手回复；
 * tool 是服务器执行某个工具后返回的结果。
 *
 * <p>重点看工具调用与工具结果如何配对：
 * 模型先产生一条 assistant 消息，toolCalls 中包含“调用 get_doctor，参数 doctorId=7”，
 * 并给这次调用分配 id，例如 call_1。服务器执行后产生一条 tool 消息，
 * 用 toolCallId=call_1 关联这次调用，content 保存查询结果。
 * 下一次请求模型时，需要把调用和对应结果一起传入，模型才能知道查询到了什么。
 *
 * @param role 消息角色：system、user、assistant 或 tool
 * @param content 消息正文；普通消息是文字，工具结果通常是包含成功结果或错误信息的 JSON 字符串
 * @param toolCalls 模型请求执行的工具列表；每项包含调用 id、工具名 name 和 JSON 参数字符串 arguments
 * @param toolCallId 当前 tool 消息对应哪次调用；普通文字消息和 assistant 消息通常为 null
 */
public record AgentMessage(String role, String content, List<AgentModelToolCall> toolCalls, String toolCallId) {

    // 这是 record 的“紧凑构造方法”：在自动赋值给字段前，先整理参数。
    // 把 null 统一为空列表，调用方就能直接遍历；copyOf 使列表不能再增删元素。
    public AgentMessage {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    /** 创建普通文字消息，例如用户提问、系统提示或本轮最终回复。 */
    public static AgentMessage text(String role, String content) {
        // 普通文字不请求工具，也不需要关联工具调用，所以后两个参数是空列表和 null。
        return new AgentMessage(role, content, List.of(), null);
    }

    /** 将一次模型回复变成 assistant 消息，保留模型请求执行的工具。 */
    public static AgentMessage assistant(AgentModelReply reply) {
        // 这里记录“模型想执行什么”；真正执行工具的代码在下一批的运行器中。
        return new AgentMessage("assistant", reply.content(), reply.toolCalls(), null);
    }

    /** 创建工具结果消息；callId 必须与前面 assistant 消息中的某个调用 id 对应。 */
    public static AgentMessage tool(String callId, String content) {
        // 工具结果只是告诉模型“这次调用得到了什么”，本身不再请求执行新工具。
        return new AgentMessage("tool", content, List.of(), callId);
    }
}
