package com.op1ed.appointmentmanager.doctor.application;

import java.util.List;

public record DoctorPageResult(List<DoctorInfo> content, int page, int size,
        long totalElements, int totalPages, boolean hasNext) {
    public DoctorPageResult {
        content = List.copyOf(content);
    }
}
