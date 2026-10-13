package com.op1ed.appointmentmanager.appointment.application;

import java.util.List;

public record SlotPageResult(List<SlotResult> content, int page, int size,
        long totalElements, int totalPages, boolean hasNext) {
    public SlotPageResult {
        content = List.copyOf(content);
    }
}
