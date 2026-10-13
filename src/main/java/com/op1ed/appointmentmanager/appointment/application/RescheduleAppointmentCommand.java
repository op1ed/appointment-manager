package com.op1ed.appointmentmanager.appointment.application;

import com.op1ed.appointmentmanager.shared.error.BusinessInputs;

public record RescheduleAppointmentCommand(Long expectedSlotId, Long targetSlotId) {
    public RescheduleAppointmentCommand {
        expectedSlotId = BusinessInputs.requirePositiveId(expectedSlotId, "当前时段 ID");
        targetSlotId = BusinessInputs.requirePositiveId(targetSlotId, "目标时段 ID");
    }
}
