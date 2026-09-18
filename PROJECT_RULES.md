# Java RAG + Agent 平台全局开发与架构设计规约 (Project Rules)

> 本规约是本项目所有模块开发、数据库维护、前后端交互及智能体编排必须严格遵守的核心准则。

---

## 一、库表与数据模型设计规约 (Database & Schema Rules)

### 1. 表命名与主键规范
- **命名规则**：数据库表名统一采用小写下划线命名法（`snake_case`），如 `sys_user`、`ai_dataset`、`ai_document_chunk`。
- **主键规范**：单表主键统一命名为 `id` 或 `表名_id`（如 `user_id`），类型采用 `BIGSERIAL` / `BIGINT`，对应 Java 实体属性 `Long`，主键生成策略为 MyBatis-Plus `@TableId(type = IdType.AUTO)`。
- **关联表规范**：中间关联表命名为两表组合，如 `sys_user_role`、`sys_role_menu`、`ai_dataset_role`，采用联合主键。

### 2. 字段规范与强制注释原则（严禁缺失注释）
- **字段命名**：所有列名采用小写下划线命名法（`snake_case`）。
- **通用审计字段**：
  - `create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP`：创建入库时间
  - `update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP`：最后修改时间
  - `del_flag CHAR(1) DEFAULT '0'`：逻辑删除标志（`0`-正常存在，`2`-代表已删除）
  - `status CHAR(1) DEFAULT '0'`：状态（`0`-正常，`1`-停用）
- **强制注释**：所有新增表必须显式编写 `COMMENT ON TABLE`，且**每一个字段必须编写 `COMMENT ON COLUMN`**，清晰注明业务含义、枚举值取值范围（例如：`0-正常, 1-停用`）。

### 3. 自动化版本迁移规约 (参考 kumhosunny-admin-basic / Liquibase)
- **严禁直接修改数据库**：禁止开发者或运维直接在 Navicat/DBeaver 中手动执行 DDL 修改表结构。
- **增量 YAML 机制**：
  - 每次新增业务表或修改字段时，必须在 `backend/src/main/resources/db/changelog/postgresql/` 目录下新增一个以序号命名的 YAML 文件（例如：`04_add_user_dept_field.yaml`）。
  - 在 `db.changelog-master.yaml` 中通过 `- include: file: ...` 引入该文件。
  - 应用启动时，Liquibase 自动检查 `databasechangelog` 表，无感自动按顺序更新数据库，确保全团队与线上环境环境一致、版本可追溯、安全可回滚。

### 4. RAG 数据安全与双轨隔离规范
- **逻辑隔离 (Logical Isolation)**：
  - 常规知识库与文档分块统一存放在共享表 `ai_document_chunk` 中；
  - 切片元数据必须打上 `dataset_id`、`accessible_roles`、`security_level`；
  - 向量与全文检索时，系统自动根据当前登录用户的 Sa-Token 权限与密级生成动态过滤表达式，严防越权获取。
- **物理隔离 (Physical Isolation)**：
  - 高密/绝密知识库（`isolation_type = 'PHYSICAL'`）必须在 PostgreSQL 中动态创建独立的专属 Schema（例如 `isolated_sec_xxx`），向量与分块物理独立隔离；
  - 检索与推理强制限定走局域网私有化部署的大模型，严禁数据流向公网 API。

---

## 二、前后端框架设计与分层规范 (Framework Architecture Rules)

### 1. 后端工程分层与技术栈
- **核心基座**：Spring Boot 3.3.4 + Spring AI 1.0.0-M7 + Sa-Token 1.42.0 + MyBatis-Plus 3.5.7 + PostgreSQL 16 (pgvector)。
- **包结构规范**：
  ```
  com.ragagent
  ├── agent/        # 智能体中心 (Prompt编排、会话持久化、流式SSE对话、运行时工具)
  ├── graph/        # Spring AI Alibaba Graph 状态机引擎 (StateGraph, Checkpointer)
  ├── rag/          # KnowledgeOps 核心 (四维检索、隔离路由、分块解析、知识图谱)
  ├── runtime/      # MateClaw 可插拔运行时 (DSH 引擎、沙箱安全隔离)
  ├── system/       # 若依规范 RBAC 系统管理 (用户、角色、菜单、鉴权适配)
  └── common/       # 统一响应体、全局异常捕获、Sa-Token拦截配置
  ```
- **API 统一规范**：
  - 接口返回值统一包装为 `Result<T>`（包含 `code`、`msg`、`data`、`timestamp`）；
  - 控制器严禁暴露底层 SQL/持久层异常，由 `GlobalExceptionHandler` 统一拦截转化为友好的标准响应；
  - 鉴权注解：细粒度权限控制统一使用 Sa-Token 注解 `@SaCheckPermission("ai:dataset:list")`。

### 2. Spring AI Alibaba Graph 状态机规范
- **状态模型**：所有状态图流转的 State（如 `GraphState`）必须具备自包含性，支持 JSON 序列化。
- **节点幂等**：图节点（Node）执行必须具备重试幂等性。
- **断点续跑**：必须通过 `PostgresCheckpointer` 自动将每次节点流转的快照写入 `graph_checkpoint` 表，实现任务崩溃无损恢复与全链路可观测回溯。

---

## 三、前端产品形态与“双端分离”规范 (UI/UX & Dual-Portal Rules)

系统明确划分为 **用户端 (User Workplace)** 与 **管理端 (Admin Console)** 两套体系：

```
                    ┌─────────────────────────┐
                    │      统一登录 / SSO      │
                    │  (Sa-Token + JWT 凭据)   │
                    └────────────┬────────────┘
                                 │
           ┌─────────────────────┴─────────────────────┐
           ▼                                           ▼
┌───────────────────────┐                   ┌───────────────────────┐
│       用户工作台       │                   │       管理控制台       │
│    (User Workplace)   │                   │    (Admin Console)    │
├───────────────────────┤                   ├───────────────────────┤
│ • 极简沉浸式对话      │  管理员一键切换   │ • 知识库批量切片与清洗 │
│ • 深度搜研长文研报    │ ◄───────────────► │ • 智能体 Prompt 编排  │
│ • 企业公开助手广场    │                   │ • 双轨隔离与密级授权  │
│ • 个人历史会话        │                   │ • 若依 RBAC 权限中心  │
└───────────────────────┘                   └───────────────────────┘
```

### 1. 用户端规范 (User Workplace)
- **目标用户**：全企业普通员工、研发人员、业务顾问。
- **视觉风格**：极简、无中后台冗余侧边栏、沉浸式打字机流（类似 ChatGPT / Claude / Perplexity）。
- **核心组件**：
  - `<ThoughtChain>`：思维链折叠/展开组件，直观呈现 DeepSeek-R1 / OpenAI 推理过程；
  - `<Citations>`：引用溯源卡片，可点击溯源原始切片与文档定位；
  - `<ReportViewer>`：深度研究研报呈现器，支持 Markdown 渲染与目录导航。

### 2. 管理端规范 (Admin Console)
- **目标用户**：系统管理员、知识运营工程师 (Knowledge Ops)、AI 提示词工程师。
- **视觉风格**：经典若依中后台左侧树形导航 + 顶部面包屑与标签页。
- **核心管控**：
  - 知识库管理：文档解析、向量化状态、图谱实体关系审核、逻辑/物理隔离与安全密级配置；
  - 智能体编排：模型路由配置、人设 Prompt 调试、运行时插件工具挂载；
  - 权限中心：用户列表、角色权限分配、菜单字典树。

---

## 四、多环境与配置隔离规约 (Environment Configuration Rules)

- **公共配置 (`application.yml`)**：
  - 存放与环境无关的基础配置（应用名、Sa-Token 凭据规则、MyBatis-Plus 全局映射、Knife4j API 配置等）。
  - 默认激活配置：`spring.profiles.active: dev`。
- **开发环境 (`application-dev.yml`)**：
  - 内网 PostgreSQL：`192.168.200.188:5432/java_rag`，用户 `postgres` / 密码 `postgres`；
  - 内网 Redis：`192.168.200.188:6379`，**指定使用 10 号数据库**；
  - 内网 Liquibase：开启自动版本变更；
  - 端口配置：`server.port: 8080`。
- **生产环境 (`application-prod.yml`)**：
  - 通过环境变量 `${DB_HOST}`、`${REDIS_HOST}` 动态注入，关闭调试日志输出。
