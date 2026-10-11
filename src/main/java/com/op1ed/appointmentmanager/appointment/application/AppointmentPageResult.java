package com.op1ed.appointmentmanager.appointment.application;

import java.util.List;

public record AppointmentPageResult(
        List<AppointmentResult> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public AppointmentPageResult {
        content = List.copyOf(content);
    }
}
