package com.op1ed.appointmentmanager.appointment.web.dto;

import java.util.List;

import com.op1ed.appointmentmanager.appointment.application.AppointmentHistoryPageResult;

public record AppointmentHistoryPageResponse(
        List<AppointmentHistoryEventResponse> content, int page, int size,
        long totalElements, int totalPages, boolean hasNext
) {
    public static AppointmentHistoryPageResponse from(AppointmentHistoryPageResult result) {
        return new AppointmentHistoryPageResponse(
                result.content().stream().map(AppointmentHistoryEventResponse::from).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages(), result.hasNext());
    }
}
