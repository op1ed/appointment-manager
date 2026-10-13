package com.op1ed.appointmentmanager.appointment.application;

import java.time.Instant;
import java.util.Optional;

import com.op1ed.appointmentmanager.appointment.domain.Appointment;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentSlot;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.AppointmentRepository;
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

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            AppointmentSlotRepository slotRepository,
            DoctorService doctorService
    ) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.doctorService = doctorService;
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
        return findVisible(UserIdentity.requireAuthenticated(caller), id)
                .map(appointment -> {
                    if (appointment.getSlotId() != null) {
                        slotRepository
                                .findByIdForUpdate(appointment.getSlotId())
                                .orElseThrow(() -> new BusinessException(
                                        ErrorCode.NOT_FOUND,
                                        "预约时段不存在"
                                ));
                    }

                    appointment.cancel();
                    return toResult(appointment);
                });
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
