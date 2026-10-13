package com.op1ed.appointmentmanager.agent.web;

import java.net.URI;
import java.time.OffsetDateTime;

import com.op1ed.appointmentmanager.agent.application.workflow.ReschedulingWorkflowService;
import com.op1ed.appointmentmanager.agent.application.workflow.ReschedulingWorkflowView;
import com.op1ed.appointmentmanager.agent.application.workflow.WorkflowCommands;
import com.op1ed.appointmentmanager.identity.application.UserIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent/rescheduling-workflows")
public class ReschedulingWorkflowController {
    private final ReschedulingWorkflowService workflows;
    public ReschedulingWorkflowController(ReschedulingWorkflowService workflows) { this.workflows = workflows; }

    public record StartRequest(@NotBlank String requestId, @NotNull @Positive Long appointmentId, @Positive Long doctorId,
            @NotNull OffsetDateTime from, @NotNull OffsetDateTime to) { }
    public record SelectionRequest(@NotBlank String requestId, @NotNull @Min(0) Long expectedRevision,
            @NotNull @Positive Long targetSlotId) { }
    public record StepRequest(@NotBlank String requestId, @NotNull @Min(0) Long expectedRevision) { }

    @PostMapping
    public ResponseEntity<ReschedulingWorkflowView> start(@AuthenticationPrincipal UserIdentity caller,
            @Valid @RequestBody StartRequest request) {
        var workflow = workflows.start(caller, new WorkflowCommands.Start(request.requestId(), request.appointmentId(),
                request.doctorId(), request.from().toInstant(), request.to().toInstant()));
        return ResponseEntity.created(URI.create("/api/agent/rescheduling-workflows/" + workflow.id())).body(workflow);
    }
    @GetMapping("/{id}")
    public ReschedulingWorkflowView find(@AuthenticationPrincipal UserIdentity caller, @PathVariable String id) {
        return workflows.find(caller, id);
    }
    @PostMapping("/{id}/selection")
    public ReschedulingWorkflowView select(@AuthenticationPrincipal UserIdentity caller, @PathVariable String id,
            @Valid @RequestBody SelectionRequest request) {
        return workflows.advance(caller, id, WorkflowCommands.Signal.SELECT,
                new WorkflowCommands.Step(request.requestId(), request.expectedRevision(), request.targetSlotId()));
    }
    @PostMapping("/{id}/confirm")
    public ReschedulingWorkflowView confirm(@AuthenticationPrincipal UserIdentity caller, @PathVariable String id,
            @Valid @RequestBody StepRequest request) { return step(caller, id, WorkflowCommands.Signal.CONFIRM, request); }
    @PostMapping("/{id}/refresh")
    public ReschedulingWorkflowView refresh(@AuthenticationPrincipal UserIdentity caller, @PathVariable String id,
            @Valid @RequestBody StepRequest request) { return step(caller, id, WorkflowCommands.Signal.REFRESH, request); }
    @PostMapping("/{id}/reject")
    public ReschedulingWorkflowView reject(@AuthenticationPrincipal UserIdentity caller, @PathVariable String id,
            @Valid @RequestBody StepRequest request) { return step(caller, id, WorkflowCommands.Signal.REJECT, request); }
    private ReschedulingWorkflowView step(UserIdentity caller, String id, WorkflowCommands.Signal signal, StepRequest request) {
        return workflows.advance(caller, id, signal, new WorkflowCommands.Step(request.requestId(), request.expectedRevision(), null));
    }
}
