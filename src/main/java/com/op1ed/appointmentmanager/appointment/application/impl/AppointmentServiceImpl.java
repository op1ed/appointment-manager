package com.op1ed.appointmentmanager.appointment.application.impl;

import java.time.Instant;
import java.util.Optional;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.op1ed.appointmentmanager.appointment.application.AppointmentHistoryEventResult;
import com.op1ed.appointmentmanager.appointment.application.AppointmentHistoryPageResult;
import com.op1ed.appointmentmanager.appointment.application.AppointmentHistoryQuery;
import com.op1ed.appointmentmanager.appointment.application.AppointmentPageResult;
import com.op1ed.appointmentmanager.appointment.application.AppointmentQuery;
import com.op1ed.appointmentmanager.appointment.application.AppointmentResult;
import com.op1ed.appointmentmanager.appointment.application.AppointmentService;
import com.op1ed.appointmentmanager.appointment.application.BookAppointmentCommand;
import com.op1ed.appointmentmanager.appointment.application.RescheduleAppointmentCommand;
import com.op1ed.appointmentmanager.appointment.domain.Appointment;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentBookingRequest;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentAction;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentEvent;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentSlot;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.mapper.AppointmentMapper;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.mapper.AppointmentBookingRequestMapper;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.mapper.AppointmentEventMapper;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.mapper.AppointmentSlotMapper;
import com.op1ed.appointmentmanager.doctor.application.DoctorInfo;
import com.op1ed.appointmentmanager.doctor.application.DoctorService;
import com.op1ed.appointmentmanager.identity.application.UserIdentity;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.BusinessInputs;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;
import com.op1ed.appointmentmanager.shared.persistence.StoragePage;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.op1ed.appointmentmanager.shared.persistence.PersistenceChecks.requireUpdatedRow;

/** 预约服务的实现：在事务内协调预约、时段锁和操作记录。 */
@Service
@Transactional(readOnly = true)
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentMapper appointmentMapper;
    private final AppointmentSlotMapper slotMapper;
    private final DoctorService doctorService;
    private final AppointmentEventMapper eventMapper;
    private final AppointmentBookingRequestMapper bookingRequestMapper;
    private final ObjectMapper json;

    public AppointmentServiceImpl(
            AppointmentMapper appointmentMapper,
            AppointmentSlotMapper slotMapper,
            DoctorService doctorService,
            AppointmentEventMapper eventMapper,
            AppointmentBookingRequestMapper bookingRequestMapper,
            ObjectMapper json
    ) {
        this.appointmentMapper = appointmentMapper;
        this.slotMapper = slotMapper;
        this.doctorService = doctorService;
        this.eventMapper = eventMapper;
        this.bookingRequestMapper = bookingRequestMapper;
        this.json = json;
    }

    @Override
    @Transactional
    public AppointmentResult create(UserIdentity caller, BookAppointmentCommand command) {
        UserIdentity user = UserIdentity.requireAuthenticated(caller);
        if (command.requestId() == null) {
            return createBooking(user, command);
        }

        // 1. 先占住“账号 + 请求 ID”。重复请求会在这条 SQL 等待前一个事务结束。
        // 必须先锁请求、后锁时段；两个请求抢同一个时段仍交给原来的预约规则处理。
        bookingRequestMapper.reserve(new AppointmentBookingRequest(
                user.id(), command.requestId(), command.customerName(), command.slotId()));
        AppointmentBookingRequest saved = bookingRequestMapper.lockByRequest(user.id(), command.requestId())
                .orElseThrow(() -> new IllegalStateException("预约请求记录不存在"));

        // 2. 同一个 ID 只能代表同一次创建意图，不能被改成另一个姓名或时段。
        if (!saved.matches(command.customerName(), command.slotId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该预约请求 ID 已用于其他预约内容，请使用新的请求 ID");
        }
        if (saved.getResponseJson() != null) {
            // 重试返回首次成功时的快照；即使后来取消或改期，也不会再次执行创建。
            return readBookingResult(saved.getResponseJson());
        }

        // 3. 仅首次执行创建；请求记录、预约、操作记录、结果快照一起提交或回滚。
        // 私有方法在当前 create 的事务里执行，不需要在私有方法上再加 @Transactional。
        AppointmentResult result = createBooking(user, command);
        requireUpdatedRow(bookingRequestMapper.complete(saved.getId(), writeBookingResult(result)));
        return result;
    }

    private AppointmentResult createBooking(UserIdentity user, BookAppointmentCommand command) {
        // 创建业务的第一条查询锁住对应时段；上面的请求去重只使用写入与锁定读取，
        // 不会在等待时段锁之前建立普通 SELECT 的一致性快照。
        AppointmentSlot slot = slotMapper
                .lockById(command.slotId())
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

        boolean occupied = appointmentMapper.existsOccupied(
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

        appointmentMapper.insert(appointment);
        recordEvent(appointment, user, AppointmentAction.BOOKED, null, appointment.getSlotId());
        return toResult(appointment);
    }

    private String writeBookingResult(AppointmentResult result) {
        try {
            return json.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法保存预约请求结果", exception);
        }
    }

    private AppointmentResult readBookingResult(String responseJson) {
        try {
            return Objects.requireNonNull(json.readValue(responseJson, AppointmentResult.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取预约请求结果", exception);
        }
    }

    @Override
    public AppointmentPageResult search(UserIdentity caller, AppointmentQuery query) {
        UserIdentity user = UserIdentity.requireAuthenticated(caller);
        StoragePage<AppointmentResult> page = StoragePage.from(appointmentMapper.search(
                StoragePage.request(query.page(), query.size()),
                user.administrator() ? null : user.id(),
                query.status(),
                query.customerName(),
                query.fromTime(),
                query.toTime()
        )).map(this::toResult);

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

    @Override
    public Optional<AppointmentResult> findById(UserIdentity caller, long id) {
        return findVisible(UserIdentity.requireAuthenticated(caller), id)
                .map(this::toResult);
    }

    @Override
    @Transactional
    public Optional<AppointmentResult> cancel(UserIdentity caller, long id) {
        return cancelInternal(UserIdentity.requireAuthenticated(caller), id, false, null);
    }

    @Override
    @Transactional
    public Optional<AppointmentResult> cancelIfUnchanged(UserIdentity caller, long id, Long expectedSlotId) {
        UserIdentity user = UserIdentity.requireAuthenticated(caller);
        BusinessInputs.requirePositiveId(id, "预约 ID");
        if (expectedSlotId != null) {
            BusinessInputs.requirePositiveId(expectedSlotId, "预览时段 ID");
        }
        return cancelInternal(user, id, true, expectedSlotId);
    }

    private Optional<AppointmentResult> cancelInternal(UserIdentity user, long id,
            boolean checkExpectedSlot, Long expectedSlotId) {
        // 与改期保持一致：先锁预约，再锁时段；等待后读取最新状态。
        return findVisibleForUpdate(user, id)
                .map(appointment -> {
                    if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
                        return toResult(appointment);
                    }
                    if (checkExpectedSlot && !Objects.equals(appointment.getSlotId(), expectedSlotId)) {
                        throw new BusinessException(ErrorCode.CONFLICT, "预约时段已改变，请重新生成取消预览");
                    }
                    if (appointment.getSlotId() != null) {
                        lockSlot(appointment.getSlotId());
                    }

                    appointment.cancel();
                    // MyBatis 需要显式更新，预约与操作记录仍在同一个事务中提交。
                    requireUpdatedRow(appointmentMapper.updateById(appointment));
                    recordEvent(appointment, user, AppointmentAction.CANCELLED, appointment.getSlotId(), null);
                    return toResult(appointment);
                });
    }

    @Override
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
            if (appointmentMapper.existsOccupied(target.getId(), AppointmentStatus.BOOKED)) {
                throw new BusinessException(ErrorCode.CONFLICT, "目标时段已经被预约");
            }
            appointment.rescheduleTo(target, doctor.name(), Instant.now());
            requireUpdatedRow(appointmentMapper.updateById(appointment));
            recordEvent(appointment, user, AppointmentAction.RESCHEDULED, originalSlotId, target.getId());
            return toResult(appointment);
        });
    }

    @Override
    public Optional<AppointmentHistoryPageResult> history(UserIdentity caller, long id, AppointmentHistoryQuery query) {
        UserIdentity user = UserIdentity.requireAuthenticated(caller);
        return findVisible(user, id).map(appointment -> {
            StoragePage<AppointmentHistoryEventResult> page = StoragePage.from(eventMapper.history(
                    StoragePage.request(query.page(), query.size()), appointment.getId()))
                    .map(event -> new AppointmentHistoryEventResult(event.getId(), event.getActorId(), event.getAction(),
                            event.getFromSlotId(), event.getToSlotId(), event.getOccurredAt()));
            return new AppointmentHistoryPageResult(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages(), (long) page.getNumber() + 1 < page.getTotalPages());
        });
    }

    private Optional<Appointment> findVisibleForUpdate(UserIdentity user, long id) {
        return appointmentMapper.lockVisible(id, user.administrator() ? null : user.id());
    }

    private AppointmentSlot lockSlot(Long id) {
        return slotMapper.lockById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "预约时段不存在"));
    }

    private void recordEvent(Appointment appointment, UserIdentity actor, AppointmentAction action,
            Long fromSlotId, Long toSlotId) {
        // 与预约变更共用一个事务；记录保存失败时业务变更也会回滚。
        eventMapper.insert(new AppointmentEvent(appointment.getId(), actor.id(), action,
                fromSlotId, toSlotId, Instant.now()));
    }

    private Optional<Appointment> findVisible(UserIdentity user, long id) {
        // 在查询中限制归属；他人的记录与不存在的记录都返回相同的 404。
        return user.administrator()
                ? Optional.ofNullable(appointmentMapper.selectById(id))
                : appointmentMapper.findOwned(id, user.id());
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
