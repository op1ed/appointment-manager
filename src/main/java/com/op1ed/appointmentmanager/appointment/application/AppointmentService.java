package com.op1ed.appointmentmanager.appointment.application;

import java.time.Instant;
import java.util.Optional;

import com.op1ed.appointmentmanager.appointment.domain.Appointment;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentAction;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentEvent;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentSlot;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.AppointmentRepository;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.AppointmentEventRepository;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.AppointmentSlotRepository;
import com.op1ed.appointmentmanager.doctor.application.DoctorInfo;
import com.op1ed.appointmentmanager.doctor.application.DoctorService;
import com.op1ed.appointmentmanager.identity.application.UserIdentity;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotRepository slotRepository;
    private final DoctorService doctorService;
    private final AppointmentEventRepository eventRepository;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            AppointmentSlotRepository slotRepository,
            DoctorService doctorService,
            AppointmentEventRepository eventRepository
    ) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.doctorService = doctorService;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public AppointmentResult create(UserIdentity caller, BookAppointmentCommand command) {
        UserIdentity user = UserIdentity.requireAuthenticated(caller);
        // 创建预约的第一条数据库查询：锁住对应时段。
        AppointmentSlot slot = slotRepository
                .findByIdForUpdate(command.slotId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "预约时段不存在"
                ));

        if (!slot.isInFutureAt(Instant.now())) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "该时段已经开始，不能预约"
            );
        }

        DoctorInfo doctor = doctorService.requireById(slot.getDoctorId());

        boolean occupied = appointmentRepository.existsBySlotIdAndStatus(
                slot.getId(),
                AppointmentStatus.BOOKED
        );

        if (occupied) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "该时段已经被预约"
            );
        }

        Appointment appointment = new Appointment(
                user.id(),
                command.customerName(),
                slot.getId(),
                doctor.name(),
                slot.getStartTime(),
                slot.getEndTime()
        );

        Appointment saved = appointmentRepository.saveAndFlush(appointment);
        recordEvent(saved, user, AppointmentAction.BOOKED, null, saved.getSlotId());
        return toResult(saved);
    }

    public AppointmentPageResult search(UserIdentity caller, AppointmentQuery query) {
        UserIdentity user = UserIdentity.requireAuthenticated(caller);
        PageRequest pageable = PageRequest.of(
                query.page(), query.size(), Sort.by(Sort.Direction.ASC, "id")
        );
        Page<AppointmentResult> page = appointmentRepository.search(
                user.administrator() ? null : user.id(),
                query.status(),
                query.customerName(),
                query.fromTime(),
                query.toTime(),
                pageable
        ).map(this::toResult);

        // 用 long 计算页号加一，避免极大页号发生整数溢出。
        boolean hasNext = (long) page.getNumber() + 1 < page.getTotalPages();
        return new AppointmentPageResult(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                hasNext
        );
    }

    public Optional<AppointmentResult> findById(UserIdentity caller, long id) {
        return findVisible(UserIdentity.requireAuthenticated(caller), id)
                .map(this::toResult);
    }

    @Transactional
    public Optional<AppointmentResult> cancel(UserIdentity caller, long id) {
        UserIdentity user = UserIdentity.requireAuthenticated(caller);
        // 与改期保持一致：先锁预约，再锁时段；等待后读取最新状态。
        return findVisibleForUpdate(user, id)
                .map(appointment -> {
                    if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
                        return toResult(appointment);
                    }
                    if (appointment.getSlotId() != null) {
                        lockSlot(appointment.getSlotId());
                    }

                    appointment.cancel();
                    appointmentRepository.flush();
                    recordEvent(appointment, user, AppointmentAction.CANCELLED, appointment.getSlotId(), null);
                    return toResult(appointment);
                });
    }

    @Transactional
    public Optional<AppointmentResult> reschedule(UserIdentity caller, long id, RescheduleAppointmentCommand command) {
        UserIdentity user = UserIdentity.requireAuthenticated(caller);
        return findVisibleForUpdate(user, id).map(appointment -> {
            Long originalSlotId = appointment.getSlotId();
            if (appointment.getStatus() != AppointmentStatus.BOOKED || originalSlotId == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "该预约不能改期");
            }
            // 已经处于目标时段时，重试直接返回，不产生重复操作记录。
            if (originalSlotId.equals(command.targetSlotId())) {
                return toResult(appointment);
            }
            if (!originalSlotId.equals(command.expectedSlotId())) {
                throw new BusinessException(ErrorCode.CONFLICT, "预约时段已改变，请刷新后重试");
            }

            // 两个时段按 ID 升序加锁，避免不同预约同时交叉改期时反向等待。
            AppointmentSlot target;
            if (originalSlotId < command.targetSlotId()) {
                lockSlot(originalSlotId);
                target = lockSlot(command.targetSlotId());
            } else {
                target = lockSlot(command.targetSlotId());
                lockSlot(originalSlotId);
            }
            Instant now = Instant.now();
            if (!target.isInFutureAt(now)) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "目标时段已经开始，不能改期");
            }
            DoctorInfo doctor = doctorService.requireById(target.getDoctorId());
            if (appointmentRepository.existsBySlotIdAndStatus(target.getId(), AppointmentStatus.BOOKED)) {
                throw new BusinessException(ErrorCode.CONFLICT, "目标时段已经被预约");
            }
            appointment.rescheduleTo(target, doctor.name(), Instant.now());
            appointmentRepository.flush();
            recordEvent(appointment, user, AppointmentAction.RESCHEDULED, originalSlotId, target.getId());
            return toResult(appointment);
        });
    }

    public Optional<AppointmentHistoryPageResult> history(UserIdentity caller, long id, AppointmentHistoryQuery query) {
        UserIdentity user = UserIdentity.requireAuthenticated(caller);
        return findVisible(user, id).map(appointment -> {
            Page<AppointmentHistoryEventResult> page = eventRepository.findByAppointmentId(appointment.getId(),
                    PageRequest.of(query.page(), query.size(), Sort.by(Sort.Direction.ASC, "id")))
                    .map(event -> new AppointmentHistoryEventResult(event.getId(), event.getActorId(), event.getAction(),
                            event.getFromSlotId(), event.getToSlotId(), event.getOccurredAt()));
            return new AppointmentHistoryPageResult(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages(), (long) page.getNumber() + 1 < page.getTotalPages());
        });
    }

    private Optional<Appointment> findVisibleForUpdate(UserIdentity user, long id) {
        return appointmentRepository.findVisibleByIdForUpdate(id, user.administrator() ? null : user.id());
    }

    private AppointmentSlot lockSlot(Long id) {
        return slotRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "预约时段不存在"));
    }

    private void recordEvent(Appointment appointment, UserIdentity actor, AppointmentAction action,
            Long fromSlotId, Long toSlotId) {
        // 与预约变更共用一个事务；记录保存失败时业务变更也会回滚。
        eventRepository.save(new AppointmentEvent(appointment.getId(), actor.id(), action,
                fromSlotId, toSlotId, Instant.now()));
    }

    private Optional<Appointment> findVisible(UserIdentity user, long id) {
        // 在查询中限制归属；他人的记录与不存在的记录都返回相同的 404。
        return user.administrator()
                ? appointmentRepository.findById(id)
                : appointmentRepository.findByIdAndOwnerId(id, user.id());
    }

    private AppointmentResult toResult(Appointment appointment) {
        return new AppointmentResult(
                appointment.getId(),
                appointment.getSlotId(),
                appointment.getCustomerName(),
                appointment.getDoctorName(),
                appointment.getStartTime(),
                appointment.getEndTime(),
                appointment.getStatus()
        );
    }
}
