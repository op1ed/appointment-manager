package com.op1ed.appointmentmanager.appointment.web.dto;

import java.util.List;

import com.op1ed.appointmentmanager.appointment.application.AppointmentPageResult;

public record AppointmentPageResponse(
        List<AppointmentResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public AppointmentPageResponse {
        content = List.copyOf(content);
    }

    public static AppointmentPageResponse from(AppointmentPageResult result) {
        return new AppointmentPageResponse(
                result.content().stream().map(AppointmentResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.hasNext()
        );
    }
}
