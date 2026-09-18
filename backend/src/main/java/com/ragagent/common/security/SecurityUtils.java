package com.ragagent.common.security;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ServerWebExchange;

import java.util.Collections;
import java.util.List;

/**
 * 统一权限与安全工具类 (深度适配 WebFlux + Virtual Threads)
 */
@Slf4j
public class SecurityUtils {

    /**
     * 获取当前登录用户 ID (Long)
     * 若未登录或无法获取上下文，抛出明确的未登录异常
     */
    public static Long getLoginUserId() {
        try {
            if (StpUtil.isLogin()) {
                return StpUtil.getLoginIdAsLong();
            }
        } catch (Exception e) {
            log.warn("获取登录用户 ID 异常 (可能无 WebFlux 上下文或未登录): {}", e.getMessage());
        }
        throw new RuntimeException("用户未登录或登录凭证已失效");
    }

    /**
     * 传入 ServerWebExchange 获取登录用户 ID (最强防御，直接利用当前 exchange)
     */
    public static Long getLoginUserId(ServerWebExchange exchange) {
        if (exchange != null) {
            SaReactorSyncHolder.setContext(exchange);
            try {
                return StpUtil.getLoginIdAsLong();
            } finally {
                SaReactorSyncHolder.clearContext();
            }
        }
        return getLoginUserId();
    }

    /**
     * 获取当前登录用户 ID，如果未登录则返回指定的默认值 (如公开/兜底场景)
     */
    public static Long getLoginUserIdOrDefault(Long defaultUserId) {
        try {
            if (StpUtil.isLogin()) {
                return StpUtil.getLoginIdAsLong();
            }
        } catch (Exception e) {
            log.debug("当前请求无有效登录态，使用默认用户ID {}: {}", defaultUserId, e.getMessage());
        }
        return defaultUserId;
    }

    /**
     * 判断当前会话是否登录
     */
    public static boolean isLogin() {
        try {
            return StpUtil.isLogin();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取当前登录用户的权限码列表
     */
    public static List<String> getPermissionList() {
        try {
            if (StpUtil.isLogin()) {
                return StpUtil.getPermissionList();
            }
        } catch (Exception e) {
            log.warn("获取当前登录用户权限码异常: {}", e.getMessage());
        }
        return Collections.emptyList();
    }
}
