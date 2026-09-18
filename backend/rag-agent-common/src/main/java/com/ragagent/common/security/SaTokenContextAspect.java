package com.ragagent.common.security;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/**
 * Sa-Token 响应式与虚拟线程 (Virtual Thread) 上下文同步切面
 *
 * 背景与根因：
 * 在 Spring Boot 4 + Spring WebFlux 响应式架构开启 Java 21 虚拟线程 (spring.threads.virtual.enabled: true) 时，
 * WebFlux 的响应式线程（Netty EventLoop）调度到 Controller 同步方法执行时会切换至独立的虚拟线程执行。
 * 传统的 ThreadLocal 无法跨线程传递，导致在 Controller 或 Service 中直接调用 StpUtil 时抛出：
 * "SaTokenContextException: SaTokenContext 上下文尚未初始化"。
 *
 * 解决方案：
 * 本切面拦截所有 RestController 方法调用。只要方法参数列表中包含 ServerWebExchange，
 * 切面会在当前执行该 Controller 的具体线程（无论是 EventLoop 还是虚拟线程）中立即绑定 SaReactorSyncHolder，
 * 执行完毕后在 finally 块中安全清理上下文，防止线程复用污染。
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SaTokenContextAspect {

    /**
     * <h3>环绕通知：拦截 Controller 方法调用并动态绑定上下文</h3>
     * <p>
     * <b>学习核心知识点：</b>
     * <ul>
     *   <li>{@code @Order(Ordered.HIGHEST_PRECEDENCE)}：赋予最高执行优先级，保证在任何业务逻辑或鉴权注解生效前，安全上下文就已经妥善就绪。</li>
     *   <li>动态反射扫描入参：从 {@code joinPoint.getArgs()} 自动搜寻 {@link ServerWebExchange} 参数并写入当前工作线程。</li>
     *   <li>{@code finally} 块安全清理：防止在线程池复用场景下发生脏数据读取。</li>
     * </ul>
     *
     * @param joinPoint 连接点
     * @return 方法执行原返回值
     * @throws Throwable 目标方法可能抛出的异常
     */
    @Around("within(@org.springframework.web.bind.annotation.RestController *) || within(@org.springframework.stereotype.Controller *)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        ServerWebExchange exchange = null;
        Object[] args = joinPoint.getArgs();
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof ServerWebExchange swe) {
                    exchange = swe;
                    break;
                }
            }
        }

        if (exchange != null) {
            SaReactorSyncHolder.setContext(exchange);
            try {
                return joinPoint.proceed();
            } finally {
                SaReactorSyncHolder.clearContext();
            }
        }

        return joinPoint.proceed();
    }
}
