package com.op1ed.appointmentmanager.agent.application;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.op1ed.appointmentmanager.agent.domain.AgentAction;
import com.op1ed.appointmentmanager.agent.domain.AgentActionKind;
import com.op1ed.appointmentmanager.agent.domain.AgentActionStatus;
import com.op1ed.appointmentmanager.agent.infrastructure.persistence.AgentActionRepository;
import com.op1ed.appointmentmanager.appointment.application.AppointmentResult;
import com.op1ed.appointmentmanager.appointment.application.AppointmentService;
import com.op1ed.appointmentmanager.appointment.application.BookAppointmentCommand;
import com.op1ed.appointmentmanager.appointment.application.RescheduleAppointmentCommand;
import com.op1ed.appointmentmanager.appointment.application.SlotResult;
import com.op1ed.appointmentmanager.appointment.application.SlotService;
import com.op1ed.appointmentmanager.doctor.application.DoctorService;
import com.op1ed.appointmentmanager.identity.application.UserIdentity;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.BusinessInputs;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AgentActionService {
    private final AgentActionRepository actions;
    private final AppointmentService appointments;
    private final SlotService slots;
    private final DoctorService doctors;
    private final ObjectMapper json;
    private final Duration actionTtl;

    public AgentActionService(AgentActionRepository actions, AppointmentService appointments, SlotService slots,
            DoctorService doctors, ObjectMapper json, @Value("${app.agent.action-ttl:PT5M}") Duration actionTtl) {
        if (actionTtl.isNegative() || actionTtl.isZero() || actionTtl.toMillis() < 1) {
            throw new IllegalArgumentException("动作有效期至少为 1 毫秒");
        }
        this.actions = actions;
        this.appointments = appointments;
        this.slots = slots;
        this.doctors = doctors;
        this.json = json;
        this.actionTtl = actionTtl;
    }

    @Transactional
    public AgentActionResult prepareBooking(UserIdentity caller, BookAppointmentCommand command) {
        UserIdentity user = UserIdentity.requireAuthenticated(caller);
        SlotResult target = requireAvailableSlot(command.slotId());
        var preview = new AgentActionPreview("预约以下医生和时段，确认后才创建预约", null,
                proposal(command.customerName(), target));
        return save(user, AgentActionKind.BOOK, null, command.customerName(), null, target.id(), preview);
    }

    @Transactional
    public AgentActionResult prepareCancellation(UserIdentity caller, long appointmentId) {
        UserIdentity user = UserIdentity.requireAuthenticated(caller);
        AppointmentResult original = requireAppointment(user, appointmentId);
        return save(user, AgentActionKind.CANCEL, original.id(), null, original.slotId(), null,
                new AgentActionPreview("取消以下预约，确认前预约保持原状", original, null));
    }

    @Transactional
    public AgentActionResult prepareRescheduling(UserIdentity caller, long appointmentId,
            RescheduleAppointmentCommand command) {
        UserIdentity user = UserIdentity.requireAuthenticated(caller);
        AppointmentResult original = requireAppointment(user, appointmentId);
        if (!original.booked() || original.slotId() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "该预约不能改期");
        }
        if (!original.slotId().equals(command.expectedSlotId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "预约时段已改变，请刷新后重新生成预览");
        }
        if (!original.startTime().isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.CONFLICT, "预约已开始，不能改期");
        }
        SlotResult target = original.slotId().equals(command.targetSlotId())
                ? requireSlot(command.targetSlotId()) : requireAvailableSlot(command.targetSlotId());
        return save(user, AgentActionKind.RESCHEDULE, original.id(), null, original.slotId(), target.id(),
                new AgentActionPreview("将以下预约改到新的医生和时段，确认前原预约保持不变", original,
                        proposal(original.customerName(), target)));
    }

    public AgentActionResult find(UserIdentity caller, String id) {
        UserIdentity user = UserIdentity.requireAuthenticated(caller);
        return toResult(actions.findByIdAndOwnerId(requireActionId(id), user.id()).orElseThrow(this::notFound));
    }

    @Transactional
    public AgentActionResult confirm(UserIdentity caller, String id) {
        UserIdentity user = UserIdentity.requireAuthenticated(caller);
        // 此事务的第一条业务查询使用当前读并锁住动作。重试和并发确认都返回首次执行的结果。
        AgentAction action = actions.findByIdAndOwnerIdForUpdate(requireActionId(id), user.id())
                .orElseThrow(this::notFound);
        if (action.getStatus() == AgentActionStatus.CONFIRMED) {
            return toResult(action);
        }
        Instant acceptedAt = Instant.now();
        if (action.isExpiredAt(acceptedAt)) {
            throw new BusinessException(ErrorCode.CONFLICT, "动作预览已过期，请重新生成预览");
        }
        // 只执行服务器保存的参数，身份取自登录凭证；这里不再进行普通查询，保留业务服务的加锁顺序。
        AppointmentResult result = switch (action.getKind()) {
            case BOOK -> appointments.create(user,
                    new BookAppointmentCommand(action.getCustomerName(), action.getTargetSlotId()));
            case CANCEL -> appointments.cancelIfUnchanged(user, action.getAppointmentId(), action.getExpectedSlotId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "预约不存在"));
            case RESCHEDULE -> appointments.reschedule(user, action.getAppointmentId(),
                    new RescheduleAppointmentCommand(action.getExpectedSlotId(), action.getTargetSlotId()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "预约不存在"));
        };
        // 有效期在接收确认时判断；业务锁等待不应令一个已接收的确认再次过期。
        action.confirm(writeJson(result), acceptedAt);
        actions.flush();
        return toResult(action);
    }

    private AgentActionResult save(UserIdentity user, AgentActionKind kind, Long appointmentId, String customerName,
            Long expectedSlotId, Long targetSlotId, AgentActionPreview preview) {
        Instant created = Instant.now();
        return toResult(actions.save(new AgentAction(UUID.randomUUID().toString(), user.id(), kind, appointmentId,
                customerName, expectedSlotId, targetSlotId, writeJson(preview), created, created.plus(actionTtl))));
    }

    private AppointmentResult requireAppointment(UserIdentity user, long id) {
        BusinessInputs.requirePositiveId(id, "预约 ID");
        return appointments.findById(user, id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "预约不存在"));
    }

    private SlotResult requireSlot(long id) {
        return slots.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "预约时段不存在"));
    }

    private SlotResult requireAvailableSlot(long id) {
        SlotResult slot = requireSlot(id);
        if (!slot.available()) {
            throw new BusinessException(ErrorCode.CONFLICT, "该时段已开始或已被预约，请查询其他可用时段");
        }
        return slot;
    }

    private AgentActionPreview.ProposedAppointment proposal(String customerName, SlotResult slot) {
        return new AgentActionPreview.ProposedAppointment(customerName, doctors.requireById(slot.doctorId()).name(),
                slot.id(), slot.startTime(), slot.endTime());
    }

    private AgentActionResult toResult(AgentAction action) {
        String state = action.isExpiredAt(Instant.now()) ? "EXPIRED" : action.getStatus().name();
        return new AgentActionResult(action.getId(), action.getKind().name(), state, action.getCreatedAt(),
                action.getExpiresAt(), action.getConfirmedAt(), readJson(action.getPreviewJson()),
                action.getResultJson() == null ? null : readJson(action.getResultJson()));
    }

    private String requireActionId(String id) {
        try {
            String normalized = UUID.fromString(id).toString();
            if (!normalized.equals(id)) {
                throw new IllegalArgumentException();
            }
            return normalized;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "动作 ID 必须为标准 UUID");
        }
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "动作不存在");
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法保存动作数据", exception);
        }
    }

    private JsonNode readJson(String value) {
        try {
            return json.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取动作数据", exception);
        }
    }
}
