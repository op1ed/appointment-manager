package com.op1ed.appointmentmanager.agent.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import com.op1ed.appointmentmanager.agent.application.workflow.ReschedulingWorkflowService;
import com.op1ed.appointmentmanager.agent.application.workflow.WorkflowCommands;
import com.op1ed.appointmentmanager.appointment.application.AppointmentHistoryQuery;
import com.op1ed.appointmentmanager.appointment.application.AppointmentQuery;
import com.op1ed.appointmentmanager.appointment.application.AppointmentService;
import com.op1ed.appointmentmanager.appointment.application.AvailableSlotQuery;
import com.op1ed.appointmentmanager.appointment.application.BookAppointmentCommand;
import com.op1ed.appointmentmanager.appointment.application.RescheduleAppointmentCommand;
import com.op1ed.appointmentmanager.appointment.application.SlotService;
import com.op1ed.appointmentmanager.doctor.application.DoctorService;
import com.op1ed.appointmentmanager.identity.application.UserIdentity;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

import org.springframework.stereotype.Service;

@Service
public class AgentToolService {
    private final AgentToolCatalog catalog;
    private final DoctorService doctors;
    private final SlotService slots;
    private final AppointmentService appointments;
    private final AgentActionService actions;
    private final ReschedulingWorkflowService workflows;

    public AgentToolService(AgentToolCatalog catalog, DoctorService doctors, SlotService slots,
            AppointmentService appointments, AgentActionService actions, ReschedulingWorkflowService workflows) {
        this.catalog = catalog;
        this.doctors = doctors;
        this.slots = slots;
        this.appointments = appointments;
        this.actions = actions;
        this.workflows = workflows;
    }

    public AgentToolResult execute(UserIdentity caller, String name, JsonNode arguments) {
        UserIdentity user = UserIdentity.requireAuthenticated(caller);
        AgentToolArguments input = new AgentToolArguments(arguments, catalog.require(name));
        Object result = switch (name) {
            case "search_doctors" -> doctors.search(input.page(), input.size(), input.text("name"));
            case "get_doctor" -> doctors.requireById(input.id("doctorId"));
            case "search_available_slots" -> slots.searchAvailable(input.id("doctorId"),
                    new AvailableSlotQuery(input.page(), input.size(), input.time("from"), input.time("to")));
            case "search_appointments" -> appointments.search(user, AppointmentQuery.withStatusName(
                    input.page(), input.size(), input.text("status"), input.text("customerName"), input.time("from"), input.time("to")));
            case "get_appointment" -> appointments.findById(user, input.id("appointmentId"))
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "预约不存在"));
            case "get_appointment_history" -> appointments.history(user, input.id("appointmentId"),
                    new AppointmentHistoryQuery(input.page(), input.size()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "预约不存在"));
            case "prepare_booking" -> actions.prepareBooking(user,
                    new BookAppointmentCommand(input.text("customerName"), input.id("slotId")));
            case "prepare_cancellation" -> actions.prepareCancellation(user, input.id("appointmentId"));
            case "prepare_rescheduling" -> actions.prepareRescheduling(user, input.id("appointmentId"),
                    new RescheduleAppointmentCommand(input.id("expectedSlotId"), input.id("targetSlotId")));
            case "start_rescheduling_workflow" -> workflows.start(user, new WorkflowCommands.Start(UUID.randomUUID().toString(),
                    input.id("appointmentId"), input.optionalId("doctorId"), input.time("from"), input.time("to")));
            default -> throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "工具名称不存在");
        };
        return new AgentToolResult(name, result);
    }
}
