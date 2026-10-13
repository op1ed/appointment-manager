package com.op1ed.appointmentmanager.appointment.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.BusinessInputs;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

public record AvailableSlotQuery(int page, int size, Instant fromTime, Instant toTime) {
    public AvailableSlotQuery {
        BusinessInputs.requirePageParameters(page, size);
        fromTime = fromTime == null ? null : fromTime.truncatedTo(ChronoUnit.MICROS);
        toTime = toTime == null ? null : toTime.truncatedTo(ChronoUnit.MICROS);
        if (fromTime != null && toTime != null && !toTime.isAfter(fromTime)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "查询结束时间必须晚于开始时间");
        }
    }
}
