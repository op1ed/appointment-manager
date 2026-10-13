package com.op1ed.appointmentmanager.agent.infrastructure.springai;

import java.util.List;
import java.util.function.Function;

import com.op1ed.appointmentmanager.agent.application.AgentMessage;
import com.op1ed.appointmentmanager.agent.application.AgentModelReply;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import reactor.core.publisher.Flux;

/** 把现有 DeepSeek 传输端口适配为 Spring AI ChatModel，保留正文大小与网络超时保护。 */
final class DeepSeekChatModelAdapter implements ChatModel {
    private final Function<List<AgentMessage>, AgentModelReply> complete;

    DeepSeekChatModelAdapter(Function<List<AgentMessage>, AgentModelReply> complete) {
        this.complete = complete;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        var reply = complete.apply(SpringAiMessages.fromSpring(prompt.getInstructions()));
        return new ChatResponse(List.of(new Generation(SpringAiMessages.assistant(reply))));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        // 工具由 ReactAgent 的工具节点执行，模型适配器只返回工具调用。
        return ToolCallingChatOptions.builder().internalToolExecutionEnabled(false).build();
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.defer(() -> Flux.just(call(prompt)));
    }
}
