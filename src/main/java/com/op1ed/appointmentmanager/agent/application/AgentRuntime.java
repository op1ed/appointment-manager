package com.op1ed.appointmentmanager.agent.application;

import java.util.List;

import com.op1ed.appointmentmanager.identity.application.UserIdentity;

/**
 * Agent 部分的执行入口：规定“执行一轮 Agent 对话”需要什么输入、返回什么结果。
 * 整个项目从 docs/project-reading-guide.md 开始，读到 Agent 批次时再进入这里。
 *
 * <p>先区分两个概念：模型负责根据消息生成文字或工具调用请求；Agent 还负责执行工具、
 * 把工具结果交回模型，并在生成预览或达到调用上限时停止。因此，一轮 Agent 对话可能请求模型多次。
 *
 * <p>调用关系：AgentChatService（整理历史）→ AgentRuntime（本接口）
 * → SpringAiAgentRuntime（用阿里框架执行）。接口只声明能力，具体执行代码在实现类里。
 * Spring 将 SpringAiAgentRuntime 注入 AgentChatService，调用方使用本接口的方法。
 *
 * <p>为什么加这一层接口？会话服务负责权限、历史和保存结果，框架适配器负责运行 Agent。
 * 两者通过本项目的输入、输出对象交换数据，框架的 Message、Graph、Hook 类型留在适配器中。
 *
 * <p>在 Agent 部分，接着看 AgentMessage（输入消息）和 AgentRunResult（输出结果），
 * 再进入 SpringAiAgentRuntime，看接口如何被实现。
 */
public interface AgentRuntime {

    /**
     * 判断当前配置是否允许调用模型，例如是否启用 DeepSeek、是否配置格式合法的 Key。
     * 当前实现只检查配置，不发网络请求探测模型；返回 true 也不代表后续请求一定成功。
     * AgentChatService 在开始执行一轮新对话前检查它。
     */
    boolean available();

    /**
     * 执行一轮对话：接收已经整理好的消息，运行模型与工具，返回本轮结果。
     *
     * <p>例如用户问“明天有没有空闲时段”：模型可以先请求查询医生，再请求查询时段，
     * 最后生成回复。这些步骤属于同一次 run 调用。
     *
     * <p>执行结束后，AgentChatService 把本轮新增消息、最终回复和结果保存到会话中。
     * 保存历史的职责在会话服务；这个接口描述的是执行能力。
     *
     * @param caller 服务端从登录凭证取得的身份，用于工具执行时检查权限；不能让模型生成身份
     * @param messages 会话服务整理的消息，依次包含系统提示、保留的历史和本次用户消息
     * @return 本轮最终回复、预览或流程、工具调用摘要，以及需要保存的新增消息
     */
    AgentRunResult run(UserIdentity caller, List<AgentMessage> messages);
}
