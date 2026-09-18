package com.ragagent.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.List;

/**
 * OpenAPI 3 + Scalar 现代化交互式 API 文档引擎
 * <p>
 * 1. springdoc-openapi-starter-webflux-api 自动扫描所有 @RestController 注解，
 *    在 /v3/api-docs 端点生成包含完整入参 / 出参 / @Schema 字段注释的 OpenAPI 3 JSON。
 * 2. 本配置只负责：
 *    a) 提供 OpenAPI 全局元信息（标题、安全认证等）
 *    b) 在 /scalar、/docs、/doc.html 注册 Scalar 交互式文档 HTML 页面
 * 3. 彻底替换传统 Swagger UI，提供极致沉浸式暗黑科技风 API 体验
 */
@Slf4j
@Configuration
public class OpenApiConfig {

    @Value("${scalar.theme:deepSpace}")
    private String scalarTheme;

    @Value("${scalar.title:Java RAG + Agent 平台 API 接口文档}")
    private String scalarTitle;

    /**
     * 全局 OpenAPI 元信息（springdoc 自动合并此 Bean 与扫描结果）
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Java RAG + Agent 平台 API 接口文档")
                        .version("2.0.0")
                        .description("""
                                基于 Spring Boot 4 + Spring AI Alibaba 2.0 (Graph) + Sa-Token + 全栈 PostgreSQL 16 构建的企业级认知中枢与多 Agent 平台。
                                
                                ## 核心能力
                                - 🧠 **双轨安全隔离 RAG** — 逻辑隔离 + 物理 Schema 隔离
                                - 🔍 **四维混合检索** — 向量语义 × 关键词倒排 × 知识图谱 × 元数据过滤 + RRF 融合排序
                                - 🤖 **多 Agent 编排** — Spring AI Alibaba Graph 状态机驱动
                                - 📝 **深度搜研工坊** — StateGraph 工作流自动撰写长篇研报
                                - 🔐 **RBAC 统一权限体系** — Sa-Token + Redis 分布式会话
                                """)
                        .contact(new Contact().name("RAG Team").email("support@ragagent.com"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server().url("http://localhost:8888").description("本地后端开发环境"),
                        new Server().url("http://127.0.0.1:8888").description("本地回环接口")
                ))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Sa-Token JWT 鉴权令牌（登录后获取 Token 填入此处进行接口联调）")))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"));
    }

    /**
     * 注册 Scalar 现代化 API 文档界面路由
     * <p>
     * 注意：不再手动注册 /v3/api-docs 路由，由 springdoc 自动处理（包含完整的入参、出参、字段注释）
     */
    @Bean
    public RouterFunction<ServerResponse> scalarRouter() {
        return RouterFunctions
                .route(RequestPredicates.GET("/scalar")
                                .or(RequestPredicates.GET("/docs"))
                                .or(RequestPredicates.GET("/doc.html")),
                        req -> ServerResponse.ok()
                                .contentType(MediaType.TEXT_HTML)
                                .bodyValue(getScalarHtml()));
    }

    /**
     * 构建高颜值、极具科技感的 Scalar 官方最新版 Standalone 渲染页面
     */
    private String getScalarHtml() {
        return """
        <!doctype html>
        <html lang="zh-CN">
          <head>
            <title>%s</title>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1" />
            <link rel="icon" href="data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><text y='.9em' font-size='90'>🚀</text></svg>">
            <style>
              body {
                margin: 0;
                padding: 0;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, "Noto Sans SC", sans-serif;
              }
              /* 现代化滚动条 */
              ::-webkit-scrollbar { width: 6px; height: 6px; }
              ::-webkit-scrollbar-track { background: transparent; }
              ::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.15); border-radius: 3px; }
              ::-webkit-scrollbar-thumb:hover { background: rgba(255,255,255,0.25); }
            </style>
          </head>
          <body>
            <script
              id="api-reference"
              data-url="/v3/api-docs"
              data-configuration='{
                "theme": "%s",
                "darkMode": true,
                "layout": "modern",
                "showSidebar": true,
                "searchHotKey": "k",
                "hideModels": false,
                "hideDownloadButton": false,
                "hideTestRequestButton": false,
                "defaultHttpClient": {
                  "targetKey": "javascript",
                  "clientKey": "fetch"
                },
                "metaData": {
                  "title": "%s",
                  "description": "基于 Spring Boot 4 + Spring AI Alibaba 2.0 (Graph) + Sa-Token 打造的企业级 RAG 多 Agent 平台"
                }
              }'
              src="https://cdn.jsdelivr.net/npm/@scalar/api-reference"></script>
          </body>
        </html>
        """.formatted(scalarTitle, scalarTheme, scalarTitle);
    }
}
