package com.op1ed.appointmentmanager.appointment.application;

import com.op1ed.appointmentmanager.shared.error.BusinessInputs;

public record BookAppointmentCommand(String customerName, Long slotId) {

    public BookAppointmentCommand {
        customerName = BusinessInputs.requireName(customerName, "预约人姓名");
        slotId = BusinessInputs.requirePositiveId(slotId, "时段ID");
    }
}
