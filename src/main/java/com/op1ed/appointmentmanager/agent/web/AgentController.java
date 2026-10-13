package com.op1ed.appointmentmanager.agent.web;

import java.util.List;

import com.op1ed.appointmentmanager.agent.application.AgentActionResult;
import com.op1ed.appointmentmanager.agent.application.AgentActionService;
import com.op1ed.appointmentmanager.agent.application.AgentToolCatalog;
import com.op1ed.appointmentmanager.agent.application.AgentToolDefinition;
import com.op1ed.appointmentmanager.agent.application.AgentToolResult;
import com.op1ed.appointmentmanager.agent.application.AgentToolService;
import com.op1ed.appointmentmanager.agent.web.dto.ExecuteAgentToolRequest;
import com.op1ed.appointmentmanager.identity.application.UserIdentity;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final AgentToolCatalog catalog;
    private final AgentToolService tools;
    private final AgentActionService actions;

    public AgentController(AgentToolCatalog catalog, AgentToolService tools, AgentActionService actions) {
        this.catalog = catalog;
        this.tools = tools;
        this.actions = actions;
    }

    @GetMapping("/tools")
    public List<AgentToolDefinition> listTools() {
        return catalog.definitions();
    }

    @PostMapping("/tools/execute")
    public AgentToolResult execute(@AuthenticationPrincipal UserIdentity caller,
            @Valid @RequestBody ExecuteAgentToolRequest request) {
        return tools.execute(caller, request.name(), request.arguments());
    }

    @GetMapping("/actions/{id}")
    public AgentActionResult action(@AuthenticationPrincipal UserIdentity caller, @PathVariable("id") String id) {
        return actions.find(caller, id);
    }

    // 确认不是模型工具。客户端展示服务器预览后，由当前登录用户调用。
    @PostMapping("/actions/{id}/confirm")
    public AgentActionResult confirm(@AuthenticationPrincipal UserIdentity caller, @PathVariable("id") String id) {
        return actions.confirm(caller, id);
    }
}
