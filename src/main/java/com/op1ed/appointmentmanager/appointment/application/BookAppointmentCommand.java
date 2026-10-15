package com.op1ed.appointmentmanager.appointment.application;

import java.util.UUID;

import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.BusinessInputs;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

/** requestId 标识一次创建意图；重试沿用它，重新预约则生成新值。 */
public record BookAppointmentCommand(String customerName, Long slotId, String requestId) {

    /** 原有调用方可以不提供请求 ID，例如已有动作确认机制的 Agent。 */
    public BookAppointmentCommand(String customerName, Long slotId) {
        this(customerName, slotId, null);
    }

    public BookAppointmentCommand {
        customerName = BusinessInputs.requireName(customerName, "预约人姓名");
        slotId = BusinessInputs.requirePositiveId(slotId, "时段ID");
        if (requestId != null) {
            try {
                String canonical = UUID.fromString(requestId).toString();
                // UUID.fromString 也接受某些缩写；这里仅接受完整的 36 位 UUID。
                if (!canonical.equalsIgnoreCase(requestId)) {
                    throw new IllegalArgumentException("Non-canonical UUID");
                }
                requestId = canonical;
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "预约请求 ID 必须是完整的 UUID");
            }
        }
    }
}
