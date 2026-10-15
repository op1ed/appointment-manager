package com.op1ed.appointmentmanager.appointment.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 一次预约请求的输入与成功结果快照。
 *
 * <p>创建过程中 responseJson 暂时为空，但请求、预约、操作记录与结果在同一事务提交。
 * 失败会全部回滚，因此不会留下一个需要定时接管的“处理中”记录。
 */
@TableName("appointment_booking_requests")
public class AppointmentBookingRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerId;
    private String requestId;
    private String customerName;
    private Long slotId;
    private String responseJson;
    private Instant createdAt;

    protected AppointmentBookingRequest() { }

    public AppointmentBookingRequest(Long ownerId, String requestId, String customerName, Long slotId) {
        this.ownerId = ownerId;
        this.requestId = requestId;
        this.customerName = customerName;
        this.slotId = slotId;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** 按规范化后的业务输入精确比较，避免数据库排序规则忽略姓名的大小写。 */
    public boolean matches(String customerName, Long slotId) {
        return this.customerName.equals(customerName) && this.slotId.equals(slotId);
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getRequestId() { return requestId; }
    public String getCustomerName() { return customerName; }
    public Long getSlotId() { return slotId; }
    public String getResponseJson() { return responseJson; }
    public Instant getCreatedAt() { return createdAt; }
}
