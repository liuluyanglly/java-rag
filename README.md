<div align="center">

# Antigravity Java RAG + Agent 智能体协同平台

**基于 Spring Boot (Java 21 / 虚拟线程) 与 Spring AI Alibaba 2.0 的企业级知识协同与智能体编排平台**

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5%20%2F%20Java%2021-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0--M1-blue.svg)](https://spring.io/projects/spring-ai)
[![Spring AI Alibaba](https://img.shields.io/badge/Spring%20AI%20Alibaba-2.0.0--M1-orange.svg)](https://github.com/alibaba/spring-ai-alibaba)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20(pgvector)-blue.svg)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7.0%20(DB%2010)-red.svg)](https://redis.io/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

四维混合检索 | 深度研报合成 | Agent 5种经典模式 | RAG双轨安全隔离 | 实时可观测性与Token核算 | 统一管理控制台

[项目简介](#-项目简介) •
[核心特性](#-核心特性) •
[架构全景](#-架构全景) •
[工作流与Agent模式](#-工作流与-agent-模式) •
[前端说明](#-前端说明) •
[API 接口一览](#-api-核心接口一览) •
[快速开始](#-快速开始与验证) •
[学习导读](#-深入学习指南)

</div>

---

## 📖 项目简介

**Antigravity Java RAG + Agent** 是一个基于 **Spring AI 2.0 (BOM)**、**Spring AI Alibaba Graph** 状态图引擎与 **全栈 PostgreSQL 16 (pgvector + 图谱 + Checkpoint)** 打造的企业级知识协同、深度研究与智能体编排平台。平台深度融合 **可插拔 MateClaw 架构** 与 **KnowledgeOps 知识资产体系**，支持 **RAG 逻辑隔离 与 物理隔离 双轨架构**。

平台提供清晰的 **前台用户交互工作台** 与 **后台统一管理控制台**：
- **前台工作台 (User Workplace)**：提供沉浸式 AI 对话、会话列表追踪、打字机流式输出、引用切片溯源、深度推理思考链折叠，以及基于 StateGraph 驱动的 **Deep Research 深度研究工坊**（多维研搜、反思自愈与高清 PDF 研报导出）。右上角提供一键直达后台的**管理台入口标志**。
- **后台管理台 (Admin Console)**：提供 **知识库中枢 (双轨隔离与切片管理)**、**对话记录与审计管理 (全局会话详情回溯与记忆清退)**、**Agent 智能体编排** 与 **RuoYi 风格系统权限管理中心 (用户/角色/菜单)**。

技术上全面对齐 **Spring AI 2.0 原生规范**，深度落地 **Java 21 虚拟线程并发**、**Redis 10 号库滑动窗口记忆**、**Advisor 拦截审计**、**中文断句增强分词**、**二阶段混合检索重排**、**RAG 事实性与相关度评测中心**，并完整落地业界主流的 **Agent 5 种经典架构范式 (Chain / Parallelization / Routing / Orchestrator-Workers / Evaluator-Optimizer)** 与 **Micrometer + Prometheus 全栈实时监控**。

---

## ✨ 核心特性

| 模块 / 特性 | 说明 | 业务与技术价值 |
| :--- | :--- | :--- |
| **StateGraph 深度研报工坊** | 基于阿里 Spring AI Graph 状态图工作流（大纲规划 → 全维搜研 → 反思质检 → 自愈重构 → 研报合成）。 | 支持自主拆解课题，多路并发调研与交叉验证，输出带引用溯源的高清研报。 |
| **Agent 5 种经典架构模式** | 完整实现 Chain 链式、Parallel 并行化、Routing 路由、Orchestrator 编排器、Evaluator 评估优化器。 | 超越简单问答，构建具备动态规划、对抗质检与多专家协同的复合智能体系统。 |
| **RAG 双轨安全隔离体系** | 支持**逻辑隔离**（FilterExpression 元数据权限下推）与**物理隔离**（独立 SCHEMA + 内网私有模型）。 | 彻底保障企业高密文档资产安全，杜绝核心涉密数据外泄。 |
| **二阶段检索与中文增强** | 中文优先分块器 (`ChineseTokenTextSplitter`) + 粗排召回 (TopK=15~30) + Cross-Encoder Rerank 精排打分。 | 攻克传统西文分词切断中文语义的痛点，显著提高检索准确率与抗噪能力。 |
| **模型 RAG 评测与防幻觉中枢** | 集成 Spring AI `FactCheckingEvaluator`（真实性核验）与 `RelevancyEvaluator`（语义相关度评估）。 | 空上下文阻断大模型胡编乱造，支持一键发起自动化问答质检打分。 |
| **对话记忆持久化 (ChatMemory)** | 基于 Spring AI 2.0 原生 `ChatMemoryRepository` 实现 `RedisChatMemoryRepository`（10号库）。 | 采用滑动窗口 FIFO 淘汰（默认20条）与 7 天 TTL 自动过期，会话状态物理隔离。 |
| **全栈实时监控与可观测性** | 深度集成 Micrometer + Prometheus，端到端统计 `spring.ai.chat.client.operation` 与 Token 消耗。 | 提供 `/api/ai/metrics/summary` 看板接口，精确到每次调用的延迟、耗时与成本核算。 |
| **对话记录与审计管理** | 管理后台统一监管全平台历史对话，支持按会话 ID 深入回溯问答、思考链与切片，支持清退短期记忆。 | 满足企业合规审计要求，解决会话散落、无法追溯与不可控风险。 |
| **环境感知与任务追踪工具** | 借鉴 `Spring-AI-Agent-Utils`，提供 `AgentEnvironment`（时空锚点）与 `TodoTrackerTool`（任务清单）。 | 消除大模型现实时空认知错位，赋能 Agent 多步骤任务自主规划与执行透明度。 |
| **全栈 PostgreSQL 16 基座** | 单一实例统摄业务关系表、pgvector 向量索引、知识图谱（实体与关系网）与 Graph 检查点。 | 架构极致简洁，无需额外部署 Milvus 或 Neo4j，大幅降低维护成本。 |

---

## 🏗️ 架构全景

```text
java-rag/
├── backend/                               # Spring Boot 3.3.5 / Java 21 核心服务 (Netty WebFlux 8888 端口)
│   ├── pom.xml                            # 统一引入 Spring AI 2.0 BOM、Actuator、Micrometer、Sa-Token
│   └── src/main/java/com/ragagent/
│       ├── RagAgentApplication.java      # 容器启动入口 (启用虚拟线程、注解驱动装配)
│       ├── agent/                         # 智能体核心模块
│       │   ├── advisor/                   # 对话拦截切面 (ReReadingAdvisor 推理强化)
│       │   ├── config/                    # ChatClientConfig (装配 SimpleLoggerAdvisor 与 Redis 记忆)
│       │   ├── controller/                # ChatController 对话接口、PromptController 模板管理
│       │   ├── pattern/                   # Agent 5 种模式引擎 (Chain / Parallel / Routing / Orchestrator / EvalOptimizer)
│       │   ├── service/                   # PromptFileService 提示词模板动态渲染服务
│       │   └── tools/                     # Agent 工具链 (CustomerServiceTool 业务工单, TodoTrackerTool, AgentEnvironment)
│       ├── common/                        # 通用基础设施
│       │   ├── config/                    # ChatMemoryConfig (RedisChatMemoryRepository 10号库滑动窗口)
│       │   ├── observability/             # 可观测性监控 (AiObservabilityService, AiObservabilityController)
│       │   └── result/                    # 统一 REST 响应封装 Result<T>
│       ├── memory/                        # 记忆持久层 (RedisChatMemoryRepository, ReflexionResult)
│       ├── rag/                           # KnowledgeOps 知识库与检索体系
│       │   ├── controller/                # RagEvaluationController 评测中心、ResearchController 深度研究
│       │   └── service/                   # ChineseTokenTextSplitter, RagSearchService (二阶段重排), RagEvaluationService
│       ├── graph/                         # Spring AI Alibaba Graph 引擎 (StateGraph/Checkpoint/Nodes)
│       └── system/                        # RuoYi 风格 RBAC 权限管理中心 (用户/角色/部门/数据权限)
├── frontend/                              # React 18 + TypeScript + Ant Design X + TailwindCSS 高科技感前端
│   ├── src/
│   │   ├── layout/
│   │   │   ├── UserLayout.tsx             # 前台工作台布局 (左侧纯净对话历史与功能导航，右上角管理台入口)
│   │   │   └── AdminLayout.tsx            # 后台管理台布局 (知识库中枢、对话记录管理、智能体编排、系统权限)
│   │   └── pages/
│   │       ├── chat/                      # 智能对话工作台 (流式打字机 + 引用溯源卡片 + 右上角管理台标志)
│   │       ├── research/                  # Deep Research 深度研报工作坊 (StateGraph 5 节点流转指示 + 高清 PDF 导出)
│   │       ├── chat-history/              # 对话记录与审计管理 (全局会话监控、问答历史回溯、记忆清理)
│   │       ├── dataset/                   # 知识库管理中枢 (双轨隔离、文档分块与向量化)
│   │       ├── agent/                     # 智能体编排控制台
│   │       └── system/                    # 用户管理、角色权限树、菜单字典
├── sql/
│   └── init.sql                           # PostgreSQL 16 初始化脚本 (RBAC + pgvector 扩展 + 图谱 + Checkpoint)
├── docker-compose.yml                     # 一键编排 PostgreSQL 16 (pgvector) + Redis 7
├── start-backend.bat                      # Windows 一键保活极速启动脚本 (端口防冲突 + JDK 21 自适应)
├── stop-backend.bat                       # Windows 一键安全停止后端脚本
└── README.md
```

---

## 🔄 工作流与 Agent 模式

### 1. StateGraph 深度研究研报合成工作流
在 `DeepResearchPage` 前端页面中，直观展现 **Spring AI Alibaba Graph** 的状态机执行流转：
```plain
┌─────────────┐     ┌────────────────┐     ┌────────────┐     ┌──────────────────┐     ┌────────────┐
│  PlanNode   │ ──► │ RetrievalNode  │ ──► │ CriticNode │ ──► │ QueryRewriteNode │ ──► │ ReportNode │
│ 课题大纲拆解 │     │ 四维混合检索   │     │ 反思一致性 │     │ 自愈重构拓搜回路  │     │ 长文研报合成 │
└─────────────┘     └────────────────┘     └────────────┘     └──────────────────┘     └────────────┘
                            ▲                                          │
                            └──────────────────────────────────────────┘
                                             自愈拓搜回路
```

### 2. Agent 5 种经典架构范式 (AgentPatternService)
- **模式一：Chain 链式**：线性顺序流水线（大纲提炼 → 详细扩展 → 语言润色 → Markdown 输出）。
- **模式二：Parallelization 并行化**：基于 **Java 21 虚拟线程** 并发调度多专家（技术、合规、财务）视角，再通过专用聚合器合成综合研报。
- **模式三：Routing 智能路由**：大模型分类器识别意图，自动分流至 RAG 知识库问答、企业业务工单工具或通用直出。
- **模式四：Orchestrator-Workers 编排器-工作者**：编排器 LLM 动态将复杂宏观项目拆解为专业子任务，Worker 并发执行，合成器汇聚最终成果。
- **模式五：Evaluator-Optimizer 评估器-优化器循环**：生成初稿 → 严格评审专家多维度打分与提出反馈 → 携带反馈闭环优化重构（最多 3 轮），实现质量螺旋跃升。

---

## 🌐 前端说明

前端采用现代前后台解耦的统一 SPA 架构：
1. **用户工作台 (`/chat`, `/research`)**：
   - 移除日常侧边栏的一切管理类杂乱按钮，保持左侧专注于会话切换与常用功能；
   - **右上角醒目管理台标志**：当登录用户具备管理员权限时，右上角常驻高辨识度 `管理台` 快捷入口，一键平滑跳转至控制台。
2. **管理控制台 (`/admin/*`)**：
   - **知识库中枢 (`/admin/dataset`)**：逻辑隔离与物理隔离双轨数据集管理、文档解析与切片管理。
   - **对话记录与审计管理 (`/admin/chat-history`)**：监管全平台会话，右侧抽屉按时间轴回溯对话问答、推理思考过程与切片引用，支持单条删除与记忆清空。
   - **智能体编排 (`/admin/agent`)**：模型选择、温度参数调整与工具挂载。
   - **系统权限中心 (`/admin/system/*`)**：用户、角色与菜单字典管理。

---

## 🛠️ API 核心接口一览

| 模块 | 请求方式 | 路径 | 功能描述 |
| :--- | :--- | :--- | :--- |
| **可观测性监控** | `GET` | `/api/ai/metrics/summary` | 获取 ChatClient 调用统计、Token 消耗及预估成本看板 |
| | `GET` | `/actuator/prometheus` | Prometheus 原生指标抓取端点 |
| **Agent 模式** | `POST` | `/api/agent/patterns/chain` | 模式一：Chain 链式流水线任务生成 |
| | `POST` | `/api/agent/patterns/parallel` | 模式二：Parallelization 虚拟线程多专家并发研报合成 |
| | `POST` | `/api/agent/patterns/routing` | 模式三：Routing 智能意图分类分流 |
| | `POST` | `/api/agent/patterns/orchestrator` | 模式四：Orchestrator-Workers 动态任务拆分与编排 |
| | `POST` | `/api/agent/patterns/eval-optimizer` | 模式五：Evaluator-Optimizer 闭环评估优化循环 |
| **RAG 质检评测** | `POST` | `/api/rag/eval` | 单条 RAG 事实性（FactChecking）与相关度（Relevancy）核验 |
| | `POST` | `/api/rag/eval/batch` | 批量 RAG 问答质量综合质检 |
| **提示词管理** | `GET` | `/api/prompts` | 获取基于外部文件的提示词模板列表与可用变量 |
| | `POST` | `/api/prompts/render` | 根据模板文件与入参动态渲染最终 Prompt |
| **智能对话** | `POST` | `/api/chat` | 支持 Redis 短期记忆、知识库问答与防幻觉阻断的流式对话 |

---

## 🚀 快速开始与验证

### 1. 启动基础设施
```bash
docker-compose up -d
```
启动 PostgreSQL 16 (内置 pgvector 扩展) 与 Redis 7 (预分配 10 号数据库)。

### 2. 启动后端服务
- **Windows 一键极速启动 (推荐)**：
  直接在根目录双击运行 `start-backend.bat` 脚本（自动清理端口冲突、自适应本地 JDK 21）。
- **命令行启动**：
  ```bash
  cd backend
  mvn spring-boot:run
  ```
- 后端服务端口：`8888`
- OpenAPI 交互文档：`http://localhost:8888/doc.html`
- Prometheus 监控端点：`http://localhost:8888/actuator/prometheus`

### 3. 运行自动化测试验证
```bash
mvn test -Dtest=SpringAi2IntegrationTest
```
*验证通过：Redis 短期记忆滑动窗口、ReReading 增强、Prompt 模板解析、企业服务工单工具、RAG 事实性核验、环境自动感知、任务追踪器及 Micrometer 观测指标采集（全部 7 项用例 100% 绿灯）。*

### 4. 启动前端工作台
```bash
cd frontend
npm install
npm run dev
```
- 前端访问地址：`http://localhost:3000`
- 默认管理员账号：`admin` / 密码：`admin123`

---

## 📚 深入学习指南

项目专为深入学习 Spring AI 2.0、企业级 RAG 与 Agent 架构设计打造，配有完整的源码导读与设计解析文档：
- **[👉 深入学习路线图与源码导读 (docs/LEARNING_PATH.md)](file:///d:/MyDocuments/tools/gitspace/java-rag/docs/LEARNING_PATH.md)**
  包含从基座搭建、会话记忆持久化、RAG 全链路进阶、工具扩展到 Agent 5 种模式的 5 阶学习路径与代码定位指导。
