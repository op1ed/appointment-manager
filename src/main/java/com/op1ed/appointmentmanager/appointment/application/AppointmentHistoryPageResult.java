package com.op1ed.appointmentmanager.appointment.application;

import java.util.List;

public record AppointmentHistoryPageResult(
        List<AppointmentHistoryEventResult> content, int page, int size,
        long totalElements, int totalPages, boolean hasNext
) {
}
