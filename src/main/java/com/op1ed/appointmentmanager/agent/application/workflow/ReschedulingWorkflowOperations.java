package com.op1ed.appointmentmanager.agent.application.workflow;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.op1ed.appointmentmanager.agent.application.AgentActionService;
import com.op1ed.appointmentmanager.appointment.application.AppointmentService;
import com.op1ed.appointmentmanager.appointment.application.AvailableSlotQuery;
import com.op1ed.appointmentmanager.appointment.application.RescheduleAppointmentCommand;
import com.op1ed.appointmentmanager.appointment.application.SlotResult;
import com.op1ed.appointmentmanager.appointment.application.SlotService;
import com.op1ed.appointmentmanager.doctor.application.DoctorService;
import com.op1ed.appointmentmanager.identity.application.UserIdentity;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;
import org.springframework.stereotype.Service;

import static com.op1ed.appointmentmanager.agent.application.workflow.WorkflowJson.*;

@Service
public class ReschedulingWorkflowOperations {
    private final AppointmentService appointments;
    private final SlotService slots;
    private final DoctorService doctors;
    private final AgentActionService actions;
    private final WorkflowJson json;

    public ReschedulingWorkflowOperations(AppointmentService appointments, SlotService slots, DoctorService doctors,
            AgentActionService actions, WorkflowJson json) {
        this.appointments = appointments; this.slots = slots; this.doctors = doctors;
        this.actions = actions; this.json = json;
    }

    public void validateStart(UserIdentity caller, WorkflowCommands.Start command) {
        appointments.findById(caller, command.appointmentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "预约不存在"));
        if (command.doctorId() != null) { doctors.requireById(command.doctorId()); }
    }

    public Map<String, Object> initial(String workflowId, WorkflowCommands.Start command) {
        var state = new LinkedHashMap<String, Object>();
        state.put("workflowId", workflowId); state.put("appointmentId", command.appointmentId());
        state.put("doctorId", command.doctorId() == null ? 0L : command.doctorId());
        state.put("from", command.from().toString()); state.put("to", command.to().toString());
        state.put("status", "CREATED"); state.put("note", ""); state.put("actionId", "");
        state.put("candidatesJson", "[]"); state.put("selectedSlotId", 0L);
        return state;
    }

    public Map<String, Object> readCurrent(UserIdentity caller, Map<String, Object> state) {
        revokePreview(caller, state);
        var appointment = appointments.findById(caller, id(state, "appointmentId"))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "预约不存在"));
        var update = new LinkedHashMap<String, Object>();
        update.put("appointmentJson", json.write(appointment)); update.put("actionId", "");
        update.put("selectedSlotId", 0L); update.put("candidatesJson", "[]");
        update.put("expectedSlotId", appointment.slotId() == null ? 0L : appointment.slotId());
        if (!appointment.booked() || !appointment.startTime().isAfter(Instant.now())) {
            update.put("status", "ABORTED"); update.put("note", "预约已取消或已开始，流程结束");
            return update;
        }
        Long doctorId = id(state, "doctorId");
        if (doctorId == null) {
            doctorId = slots.findById(appointment.slotId()).orElseThrow().doctorId();
        }
        update.put("doctorId", doctorId); update.put("doctorName", doctors.requireById(doctorId).name());
        update.put("status", "SEARCHING");
        return update;
    }

    public Map<String, Object> queryCandidates(Map<String, Object> state) {
        var candidates = slots.searchAvailable(id(state, "doctorId"), new AvailableSlotQuery(0, 20,
                Instant.parse(text(state, "from")), Instant.parse(text(state, "to")))).content();
        String note = text(state, "note");
        if (note.isEmpty()) { note = candidates.isEmpty() ? "没有可用候选，可以刷新、结束或新建其他时间范围的流程"
                : "请选择候选时段，再核对预览；当前预约保持不变"; }
        return Map.of("candidatesJson", json.write(candidates), "status",
                candidates.isEmpty() ? "NO_CANDIDATES" : "WAITING_FOR_SELECTION", "note", note);
    }

    public Map<String, Object> prepare(UserIdentity caller, Map<String, Object> state) {
        Long selected = id(state, "selectedSlotId");
        List<SlotResult> candidates = json.read(text(state, "candidatesJson"), new TypeReference<>() { });
        if (selected == null || candidates.stream().noneMatch(slot -> slot.id().equals(selected))) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "目标时段必须来自此流程保存的候选列表");
        }
        try {
            var action = actions.prepareWorkflowRescheduling(caller, text(state, "workflowId"),
                    id(state, "appointmentId"), new RescheduleAppointmentCommand(id(state, "expectedSlotId"), selected));
            return Map.of("actionId", action.id(), "status", "WAITING_FOR_CONFIRMATION",
                    "note", "请核对服务器预览，再明确确认；当前预约保持不变");
        } catch (BusinessException exception) { throwConflict(exception); throw exception; }
    }

    public Map<String, Object> execute(UserIdentity caller, Map<String, Object> state) {
        try {
            actions.confirmForWorkflow(caller, text(state, "workflowId"), text(state, "actionId"));
            return Map.of("status", "COMPLETED", "note", "改期成功");
        } catch (BusinessException exception) { throwConflict(exception); throw exception; }
    }

    public Map<String, Object> reject(UserIdentity caller, Map<String, Object> state) {
        revokePreview(caller, state);
        return Map.of("status", "REJECTED", "note", "流程已结束，预览已撤回，预约没有因本流程改变");
    }

    private void revokePreview(UserIdentity caller, Map<String, Object> state) {
        if (!text(state, "actionId").isEmpty()) {
            actions.revokeForWorkflow(caller, text(state, "workflowId"), text(state, "actionId"));
        }
    }

    private void throwConflict(BusinessException exception) {
        if (exception.getCode() == ErrorCode.CONFLICT) {
            // 交给外层先回滚事务，再用独立事务重查候选，不能在 rollback-only 事务里继续。
            throw new WorkflowBusinessConflict("预约或时段已改变，已重新查询候选，请重新选择并确认", exception);
        }
    }
}
