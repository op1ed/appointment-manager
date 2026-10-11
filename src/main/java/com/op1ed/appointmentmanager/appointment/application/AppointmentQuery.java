package com.op1ed.appointmentmanager.appointment.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.BusinessInputs;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

public record AppointmentQuery(
        int page,
        int size,
        AppointmentStatus status,
        String customerName,
        Instant fromTime,
        Instant toTime
) {
    public AppointmentQuery {
        if (page < 0) {
            throw invalidArgument("页号不能小于 0");
        }
        if (size < 1 || size > 100) {
            throw invalidArgument("每页条数必须在 1 到 100 之间");
        }
        if ((long) page * size > Integer.MAX_VALUE) {
            throw invalidArgument("分页偏移量过大");
        }

        if (customerName != null) {
            customerName = BusinessInputs.requireName(customerName, "预约人姓名");
        }

        fromTime = normalizeTime(fromTime);
        toTime = normalizeTime(toTime);
        if (fromTime != null && toTime != null && !toTime.isAfter(fromTime)) {
            throw invalidArgument("查询结束时间必须晚于开始时间");
        }
    }

    private static Instant normalizeTime(Instant time) {
        return time == null ? null : time.truncatedTo(ChronoUnit.MICROS);
    }

    private static BusinessException invalidArgument(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }
}
