package com.op1ed.appointmentmanager.agent;

import com.op1ed.appointmentmanager.agent.infrastructure.persistence.AgentActionRepository;
import com.op1ed.appointmentmanager.support.AgentApiFixtures;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

class AgentActionRollbackTest extends AgentApiFixtures {
    @MockitoSpyBean private AgentActionRepository actions;

    @Test
    void shouldRollBackAppointmentAndEventIfConfirmedResultCannotBeFlushed() {
        var slot = createSlots(1).get(0);
        var action = prepareBooking(restTemplate, slot.id());
        // 预约已经 saveAndFlush，操作记录已加入事务；模拟保存确认结果时失败。
        doThrow(new IllegalStateException("Simulated action result storage failure")).when(actions).flush();
        assertThat(confirm(restTemplate, action).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM appointments WHERE slot_id = ?", Long.class, slot.id())).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM appointment_events WHERE to_slot_id = ?", Long.class, slot.id())).isZero();
        var stored = db.queryForMap("SELECT status, result_json, confirmed_at FROM agent_actions WHERE id = ?", action.path("id").asText());
        assertThat(stored.get("status")).isEqualTo("PENDING");
        assertThat(stored.get("result_json")).isNull();
        assertThat(stored.get("confirmed_at")).isNull();
    }
}
