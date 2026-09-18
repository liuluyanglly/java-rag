package com.ragagent.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 航空/企业智能客服业务工具集 (Spring AI 2.0 @Tool 规范)
 * 供 ChatClient / Agent 在多轮对话中与 RAG 检索无缝协同
 */
@Slf4j
@Component
public class CustomerServiceTool {

    // 模拟订单库
    private static final Map<String, BookingDetail> MOCK_BOOKINGS = new ConcurrentHashMap<>();

    static {
        MOCK_BOOKINGS.put("BK20260901", new BookingDetail("BK20260901", "张伟", "CA1832", "北京", "上海", "经济舱", "已出票", "2026-09-20 08:30"));
        MOCK_BOOKINGS.put("BK20260902", new BookingDetail("BK20260902", "李娜", "MU5108", "上海", "广州", "商务舱", "已出票", "2026-09-22 14:15"));
    }

    public record BookingDetail(String bookingId, String passengerName, String flightNo,
                                String fromCity, String toCity, String cabinClass,
                                String status, String departureTime) {}

    public record QueryBookingRequest(String bookingId, String passengerName) {}

    public record CancelBookingRequest(String bookingId, String passengerName, String reason) {}

    @Tool(description = "根据预订号和乘客姓名查询航班预订详情与客舱状态")
    public String getBookingDetails(QueryBookingRequest request) {
        log.info("触发工具调用: getBookingDetails, 入参: {}", request);
        if (request == null || request.bookingId() == null) {
            return "请输入有效的预订号。";
        }
        BookingDetail detail = MOCK_BOOKINGS.get(request.bookingId().trim().toUpperCase());
        if (detail == null) {
            return "未找到预订号为 " + request.bookingId() + " 的行程记录，请核对预订号或姓名。";
        }
        if (request.passengerName() != null && !detail.passengerName().equals(request.passengerName().trim())) {
            return "预订号与乘客姓名不匹配，出于安全考虑无法提供行程详情。";
        }
        return String.format("【预订详情】预订号: %s, 乘客: %s, 航班号: %s (%s -> %s), 舱位: %s, 起飞时间: %s, 状态: %s",
                detail.bookingId(), detail.passengerName(), detail.flightNo(), detail.fromCity(),
                detail.toCity(), detail.cabinClass(), detail.departureTime(), detail.status());
    }

    @Tool(description = "执行航班退订或退款操作，必须在用户明确确认并提供退订原因后方可调用")
    public String cancelBooking(CancelBookingRequest request) {
        log.info("触发工具调用: cancelBooking, 入参: {}", request);
        if (request == null || request.bookingId() == null) {
            return "退订失败：未提供预订号。";
        }
        BookingDetail detail = MOCK_BOOKINGS.get(request.bookingId().trim().toUpperCase());
        if (detail == null) {
            return "退订失败：未查询到该有效预订。";
        }
        return String.format("已成功为乘客【%s】提交预订号【%s】的退订申请！退票款项将在 7 个工作日内原路退回，退票理由记录为：%s",
                detail.passengerName(), detail.bookingId(), request.reason() != null ? request.reason() : "用户自愿退订");
    }
}
