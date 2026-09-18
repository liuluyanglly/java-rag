package com.ragagent.common.security;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ServerWebExchange;

import java.util.Collections;
import java.util.List;

/**
 * <h1>统一权限与安全工具类 (SecurityUtils)</h1>
 * <p>
 * <b>深度适配 WebFlux 响应式架构与 Java 21+ 虚拟线程池 (Virtual Threads)</b>
 * <p>
 * <b>学习核心知识点（经典避坑指南）：</b>
 * <ol>
 *   <li><b>为什么在 WebFlux 下传统框架会报 {@code SaTokenContext 上下文尚未初始化}？</b>
 *       <br>在传统 Spring MVC 中，一个 HTTP 请求由一个固定的 Tomcat 线程全程处理，认证信息安全存放在当前线程的 {@code ThreadLocal} 中。
 *       但在 Spring WebFlux 响应式体系中，Netty 的 Reactor 线程只负责非阻塞网络 IO，业务逻辑会切换到不同的 Worker 线程或虚拟线程中执行，
 *       导致原线程中的 {@code ThreadLocal} 丢失！</li>
 *   <li><b>解决方案一 (Context Synchronizer)：</b>
 *       <br>调用 {@link SaReactorSyncHolder#setContext(ServerWebExchange)} 将当前响应式交换机（{@code exchange}）显式同步到当前线程，
 *       使得后续代码调用 {@code StpUtil.isLogin()} 时能够从 {@code exchange.getRequest()} 正常读取 Cookie 或 Header。</li>
 *   <li><b>解决方案二 (Explicit Passing 显式传参)：</b>
 *       <br>在异步线程、定时任务或深层业务 Service 中，<b>推荐显式传递 {@code userId}</b>，绝不依赖隐式上下文，从根本上杜绝跨线程上下文错乱。</li>
 * </ol>
 *
 * @author Java-RAG Team
 */
@Slf4j
public class SecurityUtils {

    /**
     * <h3>获取当前登录用户 ID (无参强校验版)</h3>
     * <p>尝试从当前安全上下文提取 loginId。若未登录或上下文丢失，直接抛出未登录运行时异常。</p>
     *
     * @return 当前登录用户的主键 ID
     * @throws RuntimeException 当凭证失效或未登录时抛出
     */
    public static Long getLoginUserId() {
        try {
            if (StpUtil.isLogin()) {
                return StpUtil.getLoginIdAsLong();
            }
        } catch (Exception e) {
            log.warn("【SecurityUtils】获取登录用户 ID 异常 (可能无 WebFlux 上下文或未登录): {}", e.getMessage());
        }
        throw new RuntimeException("用户未登录或登录凭证已失效");
    }

    /**
     * <h3>传入 ServerWebExchange 获取登录用户 ID (安全上下文强绑定)</h3>
     * <p>
     * <b>学习核心知识点：</b>
     * 利用传入的响应式 {@code exchange}，显式建立当前线程的 Sa-Token 运行时上下文，确保 {@code StpUtil} 安全取值。
     * <b>注意：</b> 不可在此方法内部调用 {@code clearContext()}，否则会导致后续同一切面拦截链崩溃，清理动作应统一由 AOP 或 Filter 在请求结束时执行。
     *
     * @param exchange 响应式服务器网络交换机
     * @return 登录用户 ID
     */
    public static Long getLoginUserId(ServerWebExchange exchange) {
        if (exchange != null) {
            SaReactorSyncHolder.setContext(exchange);
            try {
                return StpUtil.getLoginIdAsLong();
            } catch (Exception e) {
                log.warn("【SecurityUtils】从 ServerWebExchange 解析 loginId 异常: {}", e.getMessage());
            }
        }
        return getLoginUserId();
    }

    /**
     * <h3>获取当前登录用户 ID（带降级默认值，非阻塞免报障）</h3>
     * <p>用于公开接口、匿名检索或无法获取登录态的场景，保证业务连续性。</p>
     *
     * @param defaultUserId 默认兜底用户 ID (通常为 1L 超管)
     * @return 成功解析出的 userId 或默认 fallback 值
     */
    public static Long getLoginUserIdOrDefault(Long defaultUserId) {
        try {
            if (StpUtil.isLogin()) {
                return StpUtil.getLoginIdAsLong();
            }
        } catch (Exception e) {
            log.debug("【SecurityUtils】当前请求无有效登录态，平滑降级使用默认用户ID {}: {}", defaultUserId, e.getMessage());
        }
        return defaultUserId;
    }

    /**
     * <h3>传入 ServerWebExchange 获取登录用户 ID（带默认降级值）</h3>
     *
     * @param exchange      响应式交换机
     * @param defaultUserId 默认兜底用户 ID
     * @return 用户 ID
     */
    public static Long getLoginUserIdOrDefault(ServerWebExchange exchange, Long defaultUserId) {
        if (exchange != null) {
            SaReactorSyncHolder.setContext(exchange);
        }
        return getLoginUserIdOrDefault(defaultUserId);
    }

    /**
     * <h3>判断当前请求是否已成功通过身份认证 (是否已登录)</h3>
     *
     * @return true: 已登录; false: 未登录或上下文异常
     */
    public static boolean isLogin() {
        try {
            return StpUtil.isLogin();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * <h3>显式根据指定用户 ID 查询其拥有的权限码集合</h3>
     * <p>
     * <b>学习核心知识点：</b>
     * 显式传参版本，直接利用 {@code StpUtil.getPermissionList(loginId)} 查询指定账号的权限码，
     * 绝不依赖任何易丢失的 {@code ThreadLocal}，非常适合异步任务与跨线程调度。
     *
     * @param loginId 用户主键 ID
     * @return 权限标识符列表 (如: ["dataset:view", "agent:manage"])
     */
    public static List<String> getPermissionList(Object loginId) {
        try {
            if (loginId != null) {
                return StpUtil.getPermissionList(loginId);
            }
        } catch (Exception e) {
            log.warn("【SecurityUtils】获取用户 [{}] 权限码列表异常: {}", loginId, e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * <h3>获取当前登录用户的权限码集合 (上下文推断版)</h3>
     *
     * @return 权限码列表
     */
    public static List<String> getPermissionList() {
        try {
            if (StpUtil.isLogin()) {
                return StpUtil.getPermissionList();
            }
        } catch (Exception e) {
            log.warn("【SecurityUtils】从上下文获取权限码异常: {}", e.getMessage());
        }
        return Collections.emptyList();
    }
}
