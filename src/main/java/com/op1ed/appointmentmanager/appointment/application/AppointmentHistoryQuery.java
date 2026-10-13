package com.op1ed.appointmentmanager.appointment.application;

import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

public record AppointmentHistoryQuery(int page, int size) {
    public AppointmentHistoryQuery {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "页码或每页数量不合法");
        }
    }
}
