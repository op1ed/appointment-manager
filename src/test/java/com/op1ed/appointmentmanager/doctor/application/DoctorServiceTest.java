package com.op1ed.appointmentmanager.doctor.application;

import com.op1ed.appointmentmanager.doctor.application.impl.DoctorServiceImpl;
import com.op1ed.appointmentmanager.doctor.infrastructure.persistence.mapper.DoctorMapper;
import com.op1ed.appointmentmanager.identity.application.UserIdentity;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class DoctorServiceTest {
    @Test
    void shouldRequireAdministratorBeforeWritingDoctor() {
        DoctorMapper mapper = mock(DoctorMapper.class);
        DoctorService service = new DoctorServiceImpl(mapper);
        UserIdentity user = new UserIdentity(31L, "alice", "Alice", false);
        assertThatThrownBy(() -> service.create(user, new CreateDoctorCommand("王医生")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(mapper);
    }
}
