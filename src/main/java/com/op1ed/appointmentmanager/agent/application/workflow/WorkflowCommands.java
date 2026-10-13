package com.op1ed.appointmentmanager.agent.application.workflow;

import java.time.Duration;
import java.time.Instant;

import com.op1ed.appointmentmanager.agent.application.AgentIds;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.BusinessInputs;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

public final class WorkflowCommands {
    private WorkflowCommands() { }

    public record Start(String requestId, Long appointmentId, Long doctorId, Instant from, Instant to) {
        public Start {
            requestId = AgentIds.requireUuid(requestId, "请求 ID");
            appointmentId = BusinessInputs.requirePositiveId(appointmentId, "预约 ID");
            if (doctorId != null) { doctorId = BusinessInputs.requirePositiveId(doctorId, "医生 ID"); }
            if (from == null || to == null || !to.isAfter(from) || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "请提供不超过 31 天的有效查询时间范围");
            }
        }
    }

    public record Step(String requestId, long expectedRevision, Long targetSlotId) {
        public Step {
            requestId = AgentIds.requireUuid(requestId, "请求 ID");
            if (expectedRevision < 0) { throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "流程修订号不能为负数"); }
            if (targetSlotId != null) { targetSlotId = BusinessInputs.requirePositiveId(targetSlotId, "目标时段 ID"); }
        }
    }

    public enum Signal { SELECT, CONFIRM, REFRESH, REJECT }
}
