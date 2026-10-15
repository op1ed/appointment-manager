package com.op1ed.appointmentmanager.doctor.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("doctors")
public class Doctor {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    protected Doctor() {
    }

    public Doctor(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
