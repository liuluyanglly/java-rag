package com.ragagent.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 企业智能客服与业务工单服务工具集 (Spring AI 2.0 @Tool 规范)
 * 供 ChatClient / Agent 在多轮对话中与 RAG 知识库检索无缝协同
 */
@Slf4j
@Component
public class CustomerServiceTool {

    // 模拟企业服务订单/工单库
    private static final Map<String, BookingDetail> MOCK_BOOKINGS = new ConcurrentHashMap<>();

    static {
        MOCK_BOOKINGS.put("BK20260901", new BookingDetail("BK20260901", "张伟", "SRV-2026-X1", "华北中心", "上海分部", "企业标准服务", "已受理", "2026-09-20 08:30"));
        MOCK_BOOKINGS.put("BK20260902", new BookingDetail("BK20260902", "李娜", "SRV-2026-X2", "华东中心", "广州分部", "企业高级服务", "已受理", "2026-09-22 14:15"));
    }

    public record BookingDetail(String bookingId, String passengerName, String flightNo,
                                String fromCity, String toCity, String cabinClass,
                                String status, String departureTime) {}

    public record QueryBookingRequest(String bookingId, String passengerName) {}

    public record CancelBookingRequest(String bookingId, String passengerName, String reason) {}

    @Tool(description = "根据业务工单号和服务申请人姓名查询企业业务服务订单详情与当前处理状态")
    public String getBookingDetails(QueryBookingRequest request) {
        log.info("触发工具调用: getBookingDetails, 入参: {}", request);
        if (request == null || request.bookingId() == null) {
            return "请输入有效的业务服务单号。";
        }
        BookingDetail detail = MOCK_BOOKINGS.get(request.bookingId().trim().toUpperCase());
        if (detail == null) {
            return "未找到单号为 " + request.bookingId() + " 的企业业务工单，请核对单号或姓名。";
        }
        if (request.passengerName() != null && !detail.passengerName().equals(request.passengerName().trim())) {
            return "业务单号与申请人姓名不匹配，出于安全考虑无法提供业务详情。";
        }
        return String.format("【业务工单详情】单号: %s, 申请人: %s, 服务编号: %s (%s -> %s), 规格: %s, 预约时间: %s, 当前状态: %s",
                detail.bookingId(), detail.passengerName(), detail.flightNo(), detail.fromCity(),
                detail.toCity(), detail.cabinClass(), detail.departureTime(), detail.status());
    }

    @Tool(description = "执行企业业务工单取消或撤回操作，必须在用户明确确认并提供取消原因后方可调用")
    public String cancelBooking(CancelBookingRequest request) {
        log.info("触发工具调用: cancelBooking, 入参: {}", request);
        if (request == null || request.bookingId() == null) {
            return "工单撤销失败：未提供业务单号。";
        }
        BookingDetail detail = MOCK_BOOKINGS.get(request.bookingId().trim().toUpperCase());
        if (detail == null) {
            return "工单撤销失败：未查询到该有效业务工单。";
        }
        if (request.passengerName() != null && !detail.passengerName().equals(request.passengerName().trim())) {
            return "工单撤销失败：单号与申请人姓名不一致。";
        }
        MOCK_BOOKINGS.remove(request.bookingId().trim().toUpperCase());
        log.info("工单 {} 已成功办理撤销", request.bookingId());
        return String.format("已成功为申请人“%s”办理业务单号“%s”的撤销申请！工单已标记为已撤销，原因记录为：%s。",
                detail.passengerName(), detail.bookingId(), request.reason() != null ? request.reason() : "用户自主申请");
    }
}
