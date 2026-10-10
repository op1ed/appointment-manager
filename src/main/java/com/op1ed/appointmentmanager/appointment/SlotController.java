package com.op1ed.appointmentmanager.appointment;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/doctors/{doctorId}/slots")
public class SlotController {

    private final SlotService slotService;

    public SlotController(SlotService slotService) {
        this.slotService = slotService;
    }

    @PostMapping
    public ResponseEntity<SlotResponse> createSlot(
            @PathVariable("doctorId") long doctorId,
            @Valid @RequestBody CreateSlotRequest request
    ) {
        SlotResponse slot = slotService.create(doctorId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(slot);
    }

    @GetMapping
    public List<SlotResponse> listSlots(
            @PathVariable("doctorId") long doctorId
    ) {
        return slotService.findByDoctorId(doctorId);
    }
}