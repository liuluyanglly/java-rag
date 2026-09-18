package com.ragagent.common.config;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.WebFilter;

@Slf4j
@Configuration
public class SaTokenConfig {

    /**
     * WebFlux 全局上下文注入过滤器 (最高优先级)
     * 在反应式流执行生命周期内保持 exchange 传递
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public WebFilter saReactorContextWebFilter() {
        return (exchange, chain) -> {
            SaReactorSyncHolder.setContext(exchange);
            return chain.filter(exchange)
                    .doFinally(signalType -> SaReactorSyncHolder.clearContext());
        };
    }

    @Bean
    @Primary
    public cn.dev33.satoken.config.SaTokenConfig saTokenConfiguration() {
        cn.dev33.satoken.config.SaTokenConfig config = new cn.dev33.satoken.config.SaTokenConfig();
        config.setTokenName("satoken");
        config.setTimeout(7 * 24 * 60 * 60);
        config.setActiveTimeout(-1);
        config.setIsConcurrent(true);
        config.setIsShare(true);
        config.setTokenStyle("tik");
        config.setIsLog(false);
        config.setIsReadHeader(true);
        config.setIsReadCookie(false);
        config.setIsReadBody(false);
        return config;
    }

    /**
     * 注册 Sa-Token 全局响应式拦截过滤器 (WebFlux / Reactor 模式)
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
                .addInclude("/api/**")
                .addExclude(
                        // 登录、注册、验证码等公开认证接口
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/captcha",
                        // API 在线接口文档与 OpenAPI 契约
                        "/scalar",
                        "/docs",
                        "/doc.html",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/favicon.ico",
                        // 错误路由与健康探针
                        "/error",
                        "/actuator/**"
                )
                .setAuth(obj -> {
                    // 对需要鉴权的路由进行登录校验
                    SaRouter.match("/api/**")
                            .notMatch("/api/auth/login", "/api/auth/register", "/api/auth/captcha")
                            .check(r -> StpUtil.checkLogin());
                })
                .setError(e -> {
                    log.warn("Sa-Token 鉴权拦截: {}", e.getMessage());
                    return SaResult.error("未登录或 Token 已失效: " + e.getMessage()).setCode(401);
                });
    }

    /**
     * WebFlux 跨域配置 (支持前端 Vite / React 跨域联调与 SSE 响应式流式长连接)
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE - 1)
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
