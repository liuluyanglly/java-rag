# Antigravity Java RAG + Agent 智能体协同平台

基于 **Spring Boot 3.3.4 (Java 17 LTS)**、**Spring AI Alibaba Graph**、**全栈 PostgreSQL 16 (pgvector + 图谱 + Checkpoint)** 与 **Sa-Token 1.42+** 打造的企业级知识协同与智能体编排平台。深度融合 **可插拔 MateClaw 架构** 与 **KnowledgeOps 知识资产体系**，支持 **RAG 逻辑隔离 与 物理隔离 双轨架构**，前端采用 **React 18 + TypeScript + Ant Design X + TailwindCSS**。

---

## 🌟 核心技术体系

### 1. Spring AI Alibaba Graph 状态图工作流引擎
- **多智能体图流转**：基于阿里在 Spring AI 中打造的类似 LangGraph 的图引擎，通过 `StateGraph`、`Node`、`ConditionalEdge` 驱动多 Agent 协作（大纲规划 -> 四维搜研 -> 反思质检 -> 长文报告合成）。
- **全栈 PostgreSQL Checkpoint 持久化**：工作流执行的每一步状态快照均实时持久化至 `graph_checkpoint` 表，断点续跑零数据丢失。
- **Human-in-the-Loop (人机协同)**：支持在关键节点自动挂起（`SUSPEND`），等待人工审核/纠偏后再唤醒恢复。

### 2. RAG 双轨安全隔离体系
- **逻辑隔离 (Logical Isolation - 高效共享)**：
  - 适用于常规业务文档；
  - 统一存储于共享 `ai_document_chunk` 表，通过切片元数据标签（`dataset_id`, `accessible_roles`, `security_level`），结合 Sa-Token 当前登录用户权限在检索时动态注入 `FilterExpression`。
- **物理隔离 (Physical Isolation - 核心涉密)**：
  - 适用于高管决策、财务审计、核心专利等高密文档；
  - 存储上分配专属独立 `SCHEMA`（如 `isolated_sec_01`）或通过动态数据源路由至独立物理数据库；
  - 算力上**强制锁定企业内网私有模型（Ollama / vLLM）**，严禁高密切片流向外网公有云模型；
  - 启用独立加密落盘与独立高防审计。

### 3. 全栈 PostgreSQL 16 统一数据基座
单一 PostgreSQL 16 实例完美统御四大核心资产：
1. **业务与若依 RBAC 关系表**：强一致性事务保障；
2. **pgvector 向量检索**：1536 维向量存储与 HNSW 近似近邻索引；
3. **知识图谱 (GraphRAG)**：实体表（`ai_graph_entity`）与三元组关系网（`ai_graph_relation`）；
4. **工作流状态快照**：`graph_checkpoint` 存储 Graph 历史状态流转轨迹。

### 4. KnowledgeOps 四维混合检索与 Deep Research
- **四维混合检索**：Dense 向量 + Sparse 关键词 (BM25) + 知识图谱实体依赖 + 实时 Web；
- **RRF 倒数排名融合**：基于 Reciprocal Rank Fusion 算法进行多路证据融合与 Cross-Encoder 重排；
- **深度研究工坊**：输入长难课题，自主拆解大纲，并发多轮研搜与交叉验证，输出数十页结构化长篇研报。

### 5. Spring AI 2.0 模块化 RAG 与对话记忆架构 (全面演进)
- **对话记忆 (ChatMemory) Redis 持久化**：
  - 基于 Spring AI 2.0 原生 `ChatMemoryRepository` 接口实现 `RedisChatMemoryRepository`，将短期对话上下文统一落入 Redis 10 号数据库；
  - 装配 `MessageWindowChatMemory`（默认 20 条滑动窗口先进先出淘汰）与 `MessageChatMemoryAdvisor`，支持 TTL 自动失效与会话物理隔离。
- **Advisor 拦截器体系与可观测性**：
  - 挂载 Spring AI 2.0 内置 `SimpleLoggerAdvisor`（`DEBUG` 级别自动审计每次交互的 Prompt 细节与 LLM 生成轨迹）；
  - 提供自定义 `ReReadingAdvisor`（Re2 重读策略强化复杂问题推理深度）。
- **基于文件的提示词管理 (Prompt File Management)**：
  - 采用标准 StringTemplate (`.st`) 模板规范预置通用助手、智能客服、全栈架构师、知识库问答等模板；
  - 提供 `PromptFileService` 动态变量提取与渲染，并暴露 `/api/prompts` RESTful 管理接口。
- **RAG 全链路进阶与二阶段重排**：
  - 补齐 Spring AI 2.0 中文分词器 `ChineseTokenTextSplitter`，解决原生西文标点无法识别中文断句的痛点；
  - 支持 `FILTER_EXPRESSION` 动态元数据权限下推；
  - 实现“粗排召回 (TopK=15~30) + 精排重排 (Rerank TopN=5)”二阶段优化架构。

### 6. 可插拔 MateClaw Runtime & DSH
- **插件化热插拔**：统一兼容 Spring Bean 本地 `@Tool`、远程 MCP 协议服务与 DSH 动态脚本工具。
- **DSH (Dynamic Shell)**：提供动态交互式命令行终端，支持动态查询状态机与热调度。

---

## 📁 项目目录结构

```text
java-rag/
├── backend/                  # Java Spring Boot 核心工程 (76 个源文件全部编译通过)
│   ├── pom.xml               # Spring Boot 3 + Spring AI 1.0 + Sa-Token 1.42
│   └── src/main/java/com/ragagent/
│       ├── RagAgentApplication.java   # 启动类
│       ├── common/                    # 统一响应、全局异常拦截、SaToken配置与权限加载
│       ├── system/                    # RuoYi 风格 RBAC 权限系统 (用户/角色/菜单/认证)
│       ├── graph/                     # Spring AI Alibaba Graph 引擎 (StateGraph/Checkpoint/Nodes)
│       ├── runtime/                   # MateClaw Agent Runtime (状态机/DSH动态引擎/PluginManager)
│       └── rag/                       # KnowledgeOps 知识中枢 (四维检索/图谱/双轨隔离/深度研究)
├── frontend/                 # React 18 + TS + Vite 高科技感工作台
│   ├── src/
│   │   ├── layout/           # 侧边栏与导航 (含双轨隔离标识与深度研究入口)
│   │   └── pages/
│   │       ├── chat/         # AI 智能体工作台 (思维链 + 溯源卡片 + 打字机)
│   │       ├── research/     # 深度研究工坊 (StateGraph 步骤流转指示 + 研报合成)
│   │       ├── dataset/      # 知识库管理 (支持逻辑隔离 / 物理隔离双轨创建)
│   │       ├── agent/        # 智能体编排 (模型参数与工具挂载)
│   │       └── system/       # 用户管理、角色权限树、菜单管理
├── sql/
│   └── init.sql              # 全栈 PostgreSQL 初始化脚本 (RBAC + pgvector + 图谱 + Checkpoint)
├── docker-compose.yml        # 一键启动 PostgreSQL 16 (pgvector) + Redis 7
└── README.md
```

---

## 🚀 极速启动与验证

### 1. 一键启动统一 PostgreSQL 16 (带 pgvector) 与 Redis 7
```bash
docker-compose up -d
```

### 2. 启动后端服务
```bash
cd backend
mvn spring-boot:run
```
*交互式 API 接口文档：`http://localhost:8080/doc.html`*

### 3. 启动前端工作台
```bash
cd frontend
npm run dev
```
*访问系统：`http://localhost:3000`*  
*默认管理员账号：`admin` / 密码：`admin123`*
