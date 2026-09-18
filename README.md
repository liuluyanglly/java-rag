# Antigravity Java RAG + Agent 智能体协同平台

基于 **Spring Boot (Java 21 / 虚拟线程)**、**Spring AI 2.0 (BOM)**、**Spring AI Alibaba Graph**、**全栈 PostgreSQL 16 (pgvector + 图谱 + Checkpoint)** 与 **Sa-Token** 打造的企业级知识协同与智能体编排平台。深度融合 **可插拔 MateClaw 架构**、**KnowledgeOps 知识资产体系**、**Agent 5 种模式** 与 **Spring AI 2.0 全链路监控评测体系**，支持 **RAG 逻辑隔离 与 物理隔离 双轨架构**，前端采用 **React 18 + TypeScript + Ant Design X + TailwindCSS**。

> 📖 **【学习者必读】**：项目专为深入学习 Spring AI 2.0、企业级 RAG 与 Agent 架构设计打造，详见 **[👉 深入学习路线图与源码导读文档 (docs/LEARNING_PATH.md)](file:///d:/MyDocuments/tools/gitspace/java-rag/docs/LEARNING_PATH.md)**，包含 5 阶渐进学习路径、各阶段核心源码导读与验证用例！

---

## 🌟 核心技术与业务体系

### 1. Spring AI 2.0 实时监控与全栈可观测性 (Observability)
- **三大支柱原生态支持**：
  - **Metrics 指标**：深度集成 Micrometer + Prometheus，端到端采集 `spring.ai.chat.client.operation`（调用频次、响应时长、P99 分布）、`gen_ai.client.token.usage`（输入、输出与总计 Token 统计）、`spring.ai.advisor` 与 `spring.ai.vector.store`。
  - **Tracing 链路追踪**：支持 Micrometer Tracing 与 OpenTelemetry (OTLP) 原生导出，完整记录请求从 ChatClient、Advisor 链、VectorStore 检索到 LLM 调用的每一跳 Span。
  - **Logging 日志增强**：内置挂载 `SimpleLoggerAdvisor`，调试期端到端审计输入 Prompt 详情、上下文注入与流式 Token 轨迹。
- **Token 消耗与成本看板**：提供 `/api/ai/metrics/summary` RESTful 接口，实时输出当前服务 Token 消耗统计、延迟分布与 API 调用成本预估。

### 2. Agent 5 种经典架构模式全景实现 (Agentic Patterns)
对齐大模型应用设计规范，落地企业级 `AgentPatternService`（`/api/agent/patterns/*`）：
1. **Chain 链式模式**：线性顺序推进（大纲提炼 -> 内容扩展 -> 语言润色 -> Markdown 格式化），前步输出作为后步输入。
2. **Parallelization 并行化模式**：基于 **Java 21 虚拟线程** 并发调度多个独立专家视角（技术架构、安全合规、财务预算），并通过专用 Aggregator 聚合器输出综合决策报告。
3. **Routing 路由模式**：基于大模型意图识别，自动将用户请求分流至不同专业处理链路（`KNOWLEDGE_QA` 知识库问答、`CUSTOMER_SERVICE` 航空客服业务工具、`GENERAL_CHAT` 通用模型直出）。
4. **Orchestrator-Workers 编排器-工作者模式**：编排器 LLM 动态将复杂宏观项目拆解为 2-4 个专业子任务（数据模型、业务逻辑、接口契约），多个工作者并行交付，最后由合成器融合成完整技术方案。
5. **Evaluator-Optimizer 评估器-优化器循环模式**：生成器输出方案 -> 严格质检专家多维度评分并输出改进意见 -> 若未达标则携带历史反馈闭环重构优化（最多 3 轮），实现交付质量的螺旋式跃升。

### 3. Spring-AI-Agent-Utils 启发的实战扩展组件
- **`AgentEnvironment` (运行环境自动感知)**：
  - 自动感知宿主操作系统、当前精确时间（YYYY-MM-DD HH:mm:ss）、Java 版本、当前工作区目录与 JVM 内存状态；
  - 自动注入 Agent System Prompt，彻底解决大模型在现实世界时空感知混乱与版本幻觉的问题。
- **`TodoTrackerTool` (结构化任务追踪器)**：
  - 提供大模型自驱动的待办清单管理工具，支持 `addTask`、`updateTaskStatus`（PENDING / IN_PROGRESS / COMPLETED / FAILED）与 `listTasks`；
  - 极大提升复杂多步骤 Agent 执行过程的透明度与可解释性。

### 4. 业务场景实战：航空智能客服工具链 (Function Calling)
- **`CustomerServiceTool` 业务工具集**：
  - `getBookingDetails`：支持机票预订号查询、旅客姓名一致性核验与航班动态回传；
  - `cancelBooking`：支持退票规则校验、退改签业务处理与预订状态原子更新；
- **防幻觉阻断防御机制**：
  - 当向量知识库未检索到高匹配度事实内容（或相似度低于阈值）时，系统执行空上下文阻断，杜绝大模型随意发挥产生幻觉。

### 5. 模型 RAG 评测与质检中枢 (Evaluation Center)
- 集成 Spring AI 原生评测器：
  - **`FactCheckingEvaluator`**：核验大模型回答是否严格基于检索到的知识库上下文（事实性、真实度）；
  - **`RelevancyEvaluator`**：评估检索到的文档片段与用户提问语义之间的相关性；
- 提供 `/api/rag/eval` 单次及批量评测端点，支持企业构建全自动化 RAG CI/CD 评测流水线。

### 6. RAG 双轨安全隔离与二阶段检索优化
- **双轨安全隔离架构**：
  - **逻辑隔离 (Logical Isolation)**：常规业务文档存储于共享表，通过元数据切片（`dataset_id`, `accessible_roles`, `security_level`）与 Sa-Token 权限动态注入 `FilterExpression` 实现数据隔离。
  - **物理隔离 (Physical Isolation)**：核心涉密文档存储于独立物理 `SCHEMA` 或独立实例，算力端强制锁定内网私有模型（Ollama / vLLM），严禁高密数据出外网。
- **二阶段检索与中文增强**：
  - **`ChineseTokenTextSplitter`**：原生适配中文标点与断句，消除西文分词切片断裂；
  - **二阶段检索**：粗排扩大召回（TopK=15~30）+ 精排 Cross-Encoder Rerank 重新打分输出。

### 7. Spring AI Alibaba Graph 状态图工作流引擎
- **多智能体图流转**：基于 `StateGraph`、`Node`、`ConditionalEdge` 驱动多 Agent 协作（大纲规划 -> 四维搜研 -> 反思质检 -> 长文报告合成）。
- **PostgreSQL Checkpoint 持久化**：工作流每步状态快照实时落入 `graph_checkpoint` 表，断点续跑零丢失。
- **Human-in-the-Loop (人机协同)**：支持在关键审批节点自动挂起（`SUSPEND`），等待人工干预后再唤醒恢复。

---

## 📁 系统模块与代码全景

```text
java-rag/
├── backend/                               # Spring Boot 3 + Spring AI 2.0 核心后端工程 (108+ 源文件)
│   ├── pom.xml                            # 声明 Spring AI 2.0 BOM、Actuator、Micrometer、Sa-Token
│   └── src/main/java/com/ragagent/
│       ├── RagAgentApplication.java      # 容器启动入口 (开启虚拟线程与自动装配)
│       ├── agent/                         # 智能体核心层
│       │   ├── advisor/                   # 对话拦截器 (ReReadingAdvisor 重读增强)
│       │   ├── config/                    # ChatClientConfig (装配 SimpleLoggerAdvisor 与 Redis 记忆)
│       │   ├── controller/                # ChatController 对话接口、PromptController 模板管理
│       │   ├── pattern/                   # Agent 5 种经典模式 (Chain/Parallel/Routing/Orchestrator/EvalOptimizer)
│       │   ├── service/                   # PromptFileService 提示词模板动态渲染服务
│       │   └── tools/                     # Agent 工具链 (CustomerServiceTool, TodoTrackerTool, AgentEnvironment)
│       ├── common/                        # 通共组件层
│       │   ├── config/                    # ChatMemoryConfig (RedisChatMemoryRepository 10号库滑动窗口)
│       │   ├── observability/             # 可观测性监控 (AiObservabilityService, AiObservabilityController)
│       │   └── result/                    # 统一 REST 响应封装 Result<T>
│       ├── memory/                        # 记忆持久层 (RedisChatMemoryRepository, ReflexionResult)
│       ├── rag/                           # KnowledgeOps 知识库与检索体系
│       │   ├── controller/                # RagEvaluationController 评测中心、ResearchController 深度研究
│       │   └── service/                   # ChineseTokenTextSplitter, RagSearchService (二阶段重排), RagEvaluationService
│       ├── graph/                         # Spring AI Alibaba Graph 引擎 (StateGraph/Checkpoint/Nodes)
│       └── system/                        # RBAC 权限体系 (用户/角色/部门/数据权限)
├── frontend/                              # React 18 + TS + Vite 高科技感工作台
│   └── src/
│       ├── pages/chat/                    # 智能对话工作台 (流式打字机 + 引用溯源卡片 + 记忆面板)
│       ├── pages/research/                # Deep Research 深度研报工作坊 (状态图流转进度展示)
│       └── pages/dataset/                 # 知识库管理面板 (双轨隔离、文档分块与向量化)
├── sql/
│   └── init.sql                           # PostgreSQL 16 初始化脚本 (含 pgvector 扩展与基础数据)
├── docker-compose.yml                     # 一键编排 PostgreSQL 16 (pgvector) + Redis 7
└── README.md
```

---

## 🛠️ API 核心接口一览

| 模块 | 请求方式 | 路径 | 功能描述 |
| :--- | :--- | :--- | :--- |
| **可观测性监控** | `GET` | `/api/ai/metrics/summary` | 获取 ChatClient 调用统计、Token 消耗及预估成本 |
| | `GET` | `/actuator/prometheus` | Prometheus 原生指标抓取端点 |
| **Agent 模式** | `POST` | `/api/agent/patterns/chain` | 模式一：Chain 链式流水线生成 |
| | `POST` | `/api/agent/patterns/parallel` | 模式二：Parallelization 多专家并行化与聚合研报 |
| | `POST` | `/api/agent/patterns/routing` | 模式三：Routing 智能意图分类分流 |
| | `POST` | `/api/agent/patterns/orchestrator` | 模式四：Orchestrator-Workers 动态任务拆分与编排 |
| | `POST` | `/api/agent/patterns/eval-optimizer` | 模式五：Evaluator-Optimizer 闭环评估优化循环 |
| **RAG 质检评测** | `POST` | `/api/rag/eval` | 单条 RAG 事实性与相关度核验 |
| | `POST` | `/api/rag/eval/batch` | 批量 RAG 问答质量综合质检 |
| **提示词管理** | `GET` | `/api/prompts` | 获取基于文件的提示词模板列表与可用变量 |
| | `POST` | `/api/prompts/render` | 根据模板文件与入参动态渲染最终 Prompt |
| **智能对话** | `POST` | `/api/chat` | 支持 Redis 短期记忆与知识库模式的流式/阻塞对话 |

---

## 🚀 极速启动与验证

### 1. 启动基础设施 (PostgreSQL 16 + Redis 7)
```bash
docker-compose up -d
```

### 2. 启动后端服务
```bash
cd backend
mvn spring-boot:run
```
- 后端服务监听端口：`8888`
- OpenAPI 接口文档地址：`http://localhost:8888/doc.html`
- Prometheus 监控端点：`http://localhost:8888/actuator/prometheus`

### 3. 运行自动化测试套件
```bash
mvn test -Dtest=SpringAi2IntegrationTest
```
*测试覆盖：Redis 对话记忆滑动窗口、ReReadingAdvisor 拦截、提示词模板文件解析、航空客服工具、RAG 事实性核验、环境自动感知、任务追踪器及 Micrometer 指标采集。*

### 4. 启动前端工作台
```bash
cd frontend
npm install
npm run dev
```
- 访问地址：`http://localhost:3000`
- 默认管理员账号：`admin` / 密码：`admin123`
