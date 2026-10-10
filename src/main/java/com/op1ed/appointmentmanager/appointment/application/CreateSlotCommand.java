package com.op1ed.appointmentmanager.appointment.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

public record CreateSlotCommand(Instant startTime, Instant endTime) {

    public CreateSlotCommand {
        if (startTime == null || endTime == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT, "时段开始和结束时间不能为空"
            );
        }

        startTime = startTime.truncatedTo(ChronoUnit.MICROS);
        endTime = endTime.truncatedTo(ChronoUnit.MICROS);

        if (!endTime.isAfter(startTime)) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT, "结束时间必须晚于开始时间"
            );
        }
    }
}
