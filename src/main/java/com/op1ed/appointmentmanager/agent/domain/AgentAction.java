package com.op1ed.appointmentmanager.agent.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

@TableName("agent_actions")
public class AgentAction {
    @TableId(type = IdType.INPUT)
    private String id;

    private Long ownerId;

    private AgentActionKind kind;

    private AgentActionStatus status;

    private Long appointmentId;
    private String customerName;
    private Long expectedSlotId;
    private Long targetSlotId;
    private String workflowId;
    private String previewJson;
    private String resultJson;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant confirmedAt;

    protected AgentAction() {
    }

    public AgentAction(String id, Long ownerId, AgentActionKind kind, Long appointmentId,
            String customerName, Long expectedSlotId, Long targetSlotId, String previewJson,
            Instant createdAt, Instant expiresAt) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.kind = Objects.requireNonNull(kind);
        this.appointmentId = appointmentId;
        this.customerName = customerName;
        this.expectedSlotId = expectedSlotId;
        this.targetSlotId = targetSlotId;
        this.previewJson = Objects.requireNonNull(previewJson);
        this.createdAt = Objects.requireNonNull(createdAt).truncatedTo(ChronoUnit.MICROS);
        this.expiresAt = Objects.requireNonNull(expiresAt).truncatedTo(ChronoUnit.MICROS);
        if (!this.expiresAt.isAfter(this.createdAt)) {
            throw new IllegalArgumentException("动作有效期必须为正数");
        }
        this.status = AgentActionStatus.PENDING;
    }

    public boolean isExpiredAt(Instant now) {
        return status == AgentActionStatus.PENDING && !expiresAt.isAfter(now);
    }

    public void assignWorkflow(String workflowId) {
        if (kind != AgentActionKind.RESCHEDULE || status != AgentActionStatus.PENDING) {
            throw new IllegalStateException("只有待确认的改期预览可以关联流程");
        }
        this.workflowId = Objects.requireNonNull(workflowId);
    }

    public void revoke() {
        if (status == AgentActionStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.CONFLICT, "已确认的动作不能撤回");
        }
        this.status = AgentActionStatus.REVOKED;
    }

    public void confirm(String resultJson, Instant now) {
        if (status != AgentActionStatus.PENDING || isExpiredAt(now)) {
            throw new BusinessException(ErrorCode.CONFLICT, "动作已确认或已过期");
        }
        this.resultJson = Objects.requireNonNull(resultJson);
        this.confirmedAt = now.truncatedTo(ChronoUnit.MICROS);
        this.status = AgentActionStatus.CONFIRMED;
    }

    public String getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public AgentActionKind getKind() { return kind; }
    public AgentActionStatus getStatus() { return status; }
    public Long getAppointmentId() { return appointmentId; }
    public String getCustomerName() { return customerName; }
    public Long getExpectedSlotId() { return expectedSlotId; }
    public Long getTargetSlotId() { return targetSlotId; }
    public String getWorkflowId() { return workflowId; }
    public String getPreviewJson() { return previewJson; }
    public String getResultJson() { return resultJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
}
