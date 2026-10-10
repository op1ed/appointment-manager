package com.op1ed.appointmentmanager.appointment;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "doctor_name", nullable = false, length = 100)
    private String doctorName;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    protected Appointment() {
    }

    public Appointment(
            String customerName,
            String doctorName,
            Instant startTime
    ) {
        this.customerName = customerName;
        this.doctorName = doctorName;
        this.startTime = startTime;
    }

    public Long getId() {
        return id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public Instant getStartTime() {
        return startTime;
    }
}