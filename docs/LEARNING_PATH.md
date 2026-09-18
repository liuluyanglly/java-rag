# Spring AI 2.0 + RAG + Agent 深入学习路线图与源码导读

> **专为想要深入掌握 Spring AI 2.0、企业级 RAG 与 Agent 架构设计的开发者量身定制。**  
> 本路线图从基础到进阶分为 **5 大阶段**，每个阶段均配有**对应源码类**、**设计理念**与**验证测试用例**。

---

## 🗺️ 5 阶渐进式学习路线图

```plain
┌─────────────────────────────────────────────────────────────────────────┐
│                  阶段 5：Agent 5 种模式与全栈可观测性                     │
│  - Chain / Parallel / Routing / Orchestrator / Evaluator 模式深度落地     │
│  - Micrometer + Prometheus 指标采集、Token 成本核算与链路追踪              │
└────────────────────────────────────▲────────────────────────────────────┘
                                     │
┌────────────────────────────────────┴────────────────────────────────────┐
│                  阶段 4：大模型工具扩展与 RAG 质检评测                   │
│  - Function Calling / @Tool 机制（航空客服退改签实战）                   │
│  - FactChecking 事实性核验 + Relevancy 相关度评测（防幻觉闭环）           │
└────────────────────────────────────▲────────────────────────────────────┘
                                     │
┌────────────────────────────────────┴────────────────────────────────────┐
│                  阶段 3：企业级 RAG 全链路检索与隔离                    │
│  - 中文分块增强 (ChineseTokenTextSplitter)                               │
│  - 逻辑隔离 (FilterExpression 元数据下推) + 物理隔离                      │
│  - 二阶段检索：粗排扩大召回 (TopK=15~30) + 精排 Cross-Encoder Rerank     │
└────────────────────────────────────▲────────────────────────────────────┘
                                     │
┌────────────────────────────────────┴────────────────────────────────────┐
│                  阶段 2：对话记忆与 Advisor 拦截切面                    │
│  - Redis 10 号库会话短期记忆持久化 (滑动窗口 FIFO 淘汰 + TTL 过期)         │
│  - SimpleLoggerAdvisor 全链路日志审计 + ReReadingAdvisor 推理强化        │
└────────────────────────────────────▲────────────────────────────────────┘
                                     │
┌────────────────────────────────────┴────────────────────────────────────┐
│                  阶段 1：Spring AI 2.0 基座与提示词工程                 │
│  - Java 21 虚拟线程与 ChatClient.Builder 统一工厂装配                   │
│  - StringTemplate (.st) 外部文件化提示词管理 (PromptFileService)        │
│  - Agent 宿主环境时空自动感知 (AgentEnvironment)                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 📚 各阶段重点源码导读与核心知识点

### 阶段 1：Spring AI 2.0 基座与提示词工程
* **目标**：理解 Spring AI 2.0 的调用核心抽象 `ChatClient`，告别硬编码 Prompt，掌握现实环境注入。
* **核心源码**：
  1. [`ChatClientConfig.java`](file:///d:/MyDocuments/tools/gitspace/java-rag/backend/src/main/java/com/ragagent/agent/config/ChatClientConfig.java)：
     - **学习重点**：查看 `ChatClient.Builder` 如何通过 Spring 依赖注入统配模型与全局拦截器。
  2. [`PromptFileService.java`](file:///d:/MyDocuments/tools/gitspace/java-rag/backend/src/main/java/com/ragagent/agent/service/PromptFileService.java) & [`backend/src/main/resources/prompts/`](file:///d:/MyDocuments/tools/gitspace/java-rag/backend/src/main/resources/prompts/)：
     - **学习重点**：观察 `.st`（StringTemplate）语法如何使用 `<variable>` 进行模板占位，以及如何正则自动抽取模板所需变量。
  3. [`AgentEnvironment.java`](file:///d:/MyDocuments/tools/gitspace/java-rag/backend/src/main/java/com/ragagent/agent/tools/AgentEnvironment.java)：
     - **学习重点**：借鉴 `Spring-AI-Agent-Utils`，如何自动感知宿主操作系统、当前准确时间、工作空间与 JVM 内存，并在提示词开头提供时空锚点。
* **验证测试**：
  - 运行 `SpringAi2IntegrationTest#testPromptFileService` 与 `testAgentEnvironmentAndTodoTracker`。

---

### 阶段 2：对话记忆持久化与 Advisor 拦截切面
* **目标**：掌握大模型上下文有状态会话的维护方式，以及类似 Spring AOP 的 Advisor 切面拦截机制。
* **核心源码**：
  1. [`RedisChatMemoryRepository.java`](file:///d:/MyDocuments/tools/gitspace/java-rag/backend/src/main/java/com/ragagent/memory/repository/RedisChatMemoryRepository.java)：
     - **学习重点**：实现 Spring AI 2.0 原生 `ChatMemoryRepository` 接口，使用 Redis `List` 结构与 `LTRIM` 实现滑动窗口（默认保留最近 20 条），配置 7 天 TTL 防止内存泄漏。
  2. [`ReReadingAdvisor.java`](file:///d:/MyDocuments/tools/gitspace/java-rag/backend/src/main/java/com/ragagent/agent/advisor/ReReadingAdvisor.java)：
     - **学习重点**：实现 `CallAdvisor`，体验请求前置通知（Before advice）与后置增强。学习学术界 **Re2 (Re-Reading)** 策略——在用户提问后追加“再次仔细阅读并深入审题”，有效激活大模型深度思考。
* **验证测试**：
  - 运行 `SpringAi2IntegrationTest#testRedisChatMemoryRepository` 与 `testReReadingAdvisor`。

---

### 阶段 3：企业级 RAG 全链路检索与二阶段优化
* **目标**：吃透从中文文档分块、向量入库、元数据安全隔离到二阶段检索与防幻觉的完整闭环。
* **核心源码**：
  1. [`ChineseTokenTextSplitter.java`](file:///d:/MyDocuments/tools/gitspace/java-rag/backend/src/main/java/com/ragagent/rag/service/ChineseTokenTextSplitter.java)：
     - **学习重点**：理解传统西文分词器在遇到中文无空格语境时容易切断语义的痛点；学习如何通过中文标点（句号、感叹号、问号、分号）优先断句与滑动重叠切分。
  2. [`RagSearchService.java`](file:///d:/MyDocuments/tools/gitspace/java-rag/backend/src/main/java/com/ragagent/rag/service/RagSearchService.java)：
     - **学习重点 1**：**逻辑隔离下推**：学习如何提取当前用户的 `dataset_id` 集合，动态拼接 Spring AI 2.0 `FilterExpression`（如 `datasetId in ['1', '2']`），由 pgvector 原生在数据库层过滤无关与未授权数据。
     - **学习重点 2**：**二阶段检索**：学习“粗排扩大召回（`TopK * 3`，降低阈值至 0.35）” -> “精排 Rerank 打分（结合综合相关度保留最终 TopN）”。
  3. [`ChatController.java`](file:///d:/MyDocuments/tools/gitspace/java-rag/backend/src/main/java/com/ragagent/agent/controller/ChatController.java)：
     - **学习重点**：**空上下文防幻觉防御**：当知识库检索未召回任何有效切片时，主动阻断大模型胡言乱语，返回标准友好兜底提示。
* **验证测试**：
  - 运行 `SpringAi2IntegrationTest#testChineseTokenTextSplitter`。

---

### 阶段 4：大模型工具调用 (Tools) 与模型 RAG 评测
* **目标**：理解 LLM 与真实业务系统的桥梁（Function Calling），掌握现代 AI 质检（EvalOps）体系。
* **核心源码**：
  1. [`CustomerServiceTool.java`](file:///d:/MyDocuments/tools/gitspace/java-rag/backend/src/main/java/com/ragagent/agent/tools/CustomerServiceTool.java)：
     - **学习重点**：使用 Spring AI 2.0 `@Tool` 注解声明业务方法、参数 schema 与入参防伪校验。体验大模型如何根据语义自主决定是否调用 `getBookingDetails` 或 `cancelBooking`。
  2. [`TodoTrackerTool.java`](file:///d:/MyDocuments/tools/gitspace/java-rag/backend/src/main/java/com/ragagent/agent/tools/TodoTrackerTool.java)：
     - **学习重点**：借鉴 Claude Code 的 Todo 追踪机制，智能体在多步复杂执行时自主分解任务并更新状态（`PENDING / IN_PROGRESS / COMPLETED`）。
  3. [`RagEvaluationService.java`](file:///d:/MyDocuments/tools/gitspace/java-rag/backend/src/main/java/com/ragagent/rag/service/RagEvaluationService.java)：
     - **学习重点**：集成 Spring AI 2.0 的 `FactCheckingEvaluator`（事实性核验：回答是否由上下文支撑）与 `RelevancyEvaluator`（语义相关度：文档与提问的相关度），结合规则兜底计算加权总分与风险等级。
* **验证测试**：
  - 运行 `SpringAi2IntegrationTest#testCustomerServiceTool` 与 `testRagEvaluationServiceRuleFallback`。

---

### 阶段 5：Agent 5 种经典架构模式与可观测性
* **目标**：超越简单聊天问答，掌握构建高自主性、高可靠性复合智能体系统（Compound AI Systems）的核心设计模式。
* **核心源码**：
  1. [`AgentPatternService.java`](file:///d:/MyDocuments/tools/gitspace/java-rag/backend/src/main/java/com/ragagent/agent/pattern/AgentPatternService.java)：
     - **模式 1 (Chain)**：线性顺序水线（大纲 -> 内容扩展 -> 语言润色 -> Markdown 格式化）。
     - **模式 2 (Parallelization)**：Java 21 虚拟线程并发调度多专家（技术、合规、财务）+ 聚合器（Aggregator）综合合成。
     - **模式 3 (Routing)**：大模型分类器识别意图，分流至 RAG 问答、业务工具或通用模型。
     - **模式 4 (Orchestrator-Workers)**：LLM 动态拆解复杂任务，多个 Worker 并发执行，编排器合成成果。
     - **模式 5 (Evaluator-Optimizer)**：生成初稿 -> 严格评估打分 -> 携带反馈闭环迭代（最多 3 轮），螺旋提升质量。
  2. [`AiObservabilityService.java`](file:///d:/MyDocuments/tools/gitspace/java-rag/backend/src/main/java/com/ragagent/common/observability/AiObservabilityService.java)：
     - **学习重点**：通过 Micrometer `MeterRegistry` 读取 `spring.ai.chat.client.operation`（耗时统计）与 `gen_ai.client.token.usage`（输入/输出 Token 消耗），实现端到端的成本监控。
* **验证测试**：
  - 运行 `SpringAi2IntegrationTest#testAiObservabilityService`。

---

## 💡 动手实践与调试指南

### 1. 运行完整集成测试
想要验证上述全套机制是否全部正常运行，只需在命令行执行：
```powershell
$env:JAVA_HOME = "C:\Users\NB22069\.jdks\ms-21.0.12.1"
$env:PATH = "C:\Users\NB22069\.jdks\ms-21.0.12.1\bin;$env:PATH"
mvn test -Dtest=SpringAi2IntegrationTest
```
看到控制台输出 `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0` 即代表所有设计机制 100% 验证通过。

### 2. 本地启动服务并体验 API
- 启动 PostgreSQL 16 与 Redis 7：`docker-compose up -d`
- 双击根目录下的 `start-backend.bat` 一键启动后端
- 打开浏览器访问 OpenAPI 交互文档：`http://localhost:8888/doc.html`
- 即可在线调用 `/api/agent/patterns/*`、`/api/ai/metrics/summary`、`/api/rag/eval` 等全量功能！
