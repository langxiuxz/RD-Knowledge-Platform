# StudyAgent AGENTS.md

> 版本：V2.0  
> 适用范围：StudyAgent 整个代码仓库  
> 开发依据：`docs/API.md`、`docs/DATABASE.md`、`docs/ARCHITECTURE.md`  
> 目标：约束 Codex 在 StudyAgent 中的分析、实现、测试和文档修改行为。

# 1. 项目说明

StudyAgent 是一个 Java Web + AI 智能学习平台，仓库由三个主要应用组成：

```text
StudyAgent/
├── AGENTS.md
├── docs/
│   ├── API.md
│   ├── DATABASE.md
│   └── ARCHITECTURE.md
├── study-agent-web/
├── study-agent-server/
└── study-agent-ai/
```

职责：

```text
study-agent-web     Vue 3 前端
study-agent-server  Spring Boot 业务后端
study-agent-ai      FastAPI + LangChain + LangGraph AI服务
```

浏览器只访问 Spring Boot。FastAPI 是内部 AI 服务，不直接暴露给浏览器。

# 2. 开始任何任务前

Codex 在修改代码前必须：

1. 阅读本 `AGENTS.md`。
2. 阅读与任务相关的 `docs/API.md` 接口。
3. 阅读与任务相关的 `docs/DATABASE.md` 表结构。
4. 阅读 `docs/ARCHITECTURE.md` 中对应模块和调用链路。
5. 检查现有代码，优先复用已有实现。
6. 明确任务属于 Web、Spring Boot、AI 服务还是跨服务功能。
7. 若文档之间存在冲突，先报告冲突，不得自行猜测并静默改变契约。

不得因为“更方便实现”而擅自修改已经冻结的 API 路径、字段、状态枚举、数据库语义或服务边界。

# 3. 文档优先级与职责

四份文档职责不同：

```text
API.md           接口契约
DATABASE.md      数据模型契约
ARCHITECTURE.md  系统边界和调用方式
AGENTS.md        开发行为规则
```

它们应共同满足，而不是简单用一份覆盖另一份。

若确实无法同时满足：

```text
停止相关实现
指出具体冲突
给出最小修订建议
等待契约修订后继续
```

# 4. 总体架构硬约束

必须保持：

```text
Browser
   ↓
Vue 3
   ↓
Spring Boot
   ↓
FastAPI
   ↓
LangGraph / LangChain
   ↓
LLM / Vector Store
```

同时：

```text
Spring Boot -> MySQL
Spring Boot -> Redis
Spring Boot -> Object Storage
```

禁止：

```text
Browser -> FastAPI /internal/**
FastAPI -> 直接修改Spring Boot业务MySQL
Vue -> MySQL
LangGraph -> 任意SQL
Controller -> Mapper
```

Spring Boot 是业务事实和用户权限的权威服务。

# 5. Java 后端规范

技术栈：

```text
Java
Spring Boot
Spring MVC
MyBatis
MySQL
Redis
JWT
```

按业务模块组织包，每个模块内部按需包含：

```text
controller
service
mapper
entity
dto
vo
```

公共能力放：

```text
common
config
security
ai
internal
```

依赖方向：

```text
Controller -> Service -> Mapper -> MySQL
                   |
                   +-> AiClient
                   +-> Redis
                   +-> FileStorageService
```

Controller 只负责参数、认证上下文、Service 调用和响应，不写业务流程，不直接调用 Mapper。

优先使用构造器注入，不使用字段注入作为默认方式。

事务必须放在 Service 层。涉及多张业务表的一致性修改使用 `@Transactional`。

# 6. DTO / VO / Entity

严格区分：

```text
DTO     请求输入
VO      API响应
Entity  数据库映射
```

禁止为了少写类而把 Entity 直接作为所有 Controller 请求和响应模型。

API 返回字段必须以 `docs/API.md` 为准，不因为数据库多了字段就自动暴露。

# 7. MyBatis 与数据库

数据库设计已经冻结为 `docs/DATABASE.md` 的当前版本。

Codex 不得：

```text
自行新增业务表
自行删除字段
改变主外键语义
改变状态含义
把Embedding向量塞进MySQL
保存明文密码或refreshToken
```

如任务确实需要数据库变更，先说明原因和迁移方案。

所有用户私有资源查询必须包含用户归属约束，例如：

```sql
WHERE id = #{id}
  AND user_id = #{userId}
```

或通过可信父资源完成等价校验。

不得只查询 `id` 后默认当前用户有权限。

# 8. 认证与安全

用户身份只来自 Spring Boot 已验证的认证上下文。

禁止：

```text
@RequestParam Long userId
```

然后把它作为当前登录身份。

密码：

```text
BCrypt / Argon2 hash
```

refreshToken：

```text
客户端持有明文
数据库只保存hash
```

accessToken 使用：

```text
Authorization: Bearer <JWT>
```

日志不得记录：

```text
password
完整JWT
refreshToken
LLM API Key
```

# 9. FastAPI AI 服务规范

FastAPI 负责：

```text
LLM
RAG
Embedding
Vector Store
LangGraph
Tool Calling
Structured Output
文档解析
AI测验生成
AI学习计划生成
SHORT简答题AI评分
```

FastAPI 不承担：

```text
用户登录
/api/**业务CRUD
业务资源最终权限判定
直接修改业务MySQL
```

所有 `/internal/**` 使用内部服务令牌。

请求中的 `userId` 必须来自 Spring Boot 已认证上下文，不信任浏览器或模型自行生成的身份。

# 10. LangGraph 规范

LangGraph 是编排层，不是“万能业务层”。

Graph Node 应保持单一职责。

建议核心流程遵循 `ARCHITECTURE.md`：

```text
validate_input
router
retrieve_context
agent_reasoning
tool_node
generate_answer
finalize
```

State 只保存一次执行所需状态。

不要：

```text
把整个数据库对象塞进State
在Node中直接写SQL
在Prompt里实现业务权限
让LLM决定当前userId
```

# 11. Agent Tool Calling

只注册白名单工具。

V2.1 首批业务 Tool：

```text
get_learning_progress
get_wrong_questions
get_weak_topics
get_recent_quiz_results
```

这些工具通过 FastAPI → Spring Boot Tool Bridge 获取业务数据。

禁止：

```text
任意SQL工具
任意Shell工具
任意HTTP请求工具
模型自由拼接内部URL
```

Tool 参数使用 Pydantic/结构化 Schema。

V2.1 Tool Bridge 为只读。未经 API/架构文档明确允许，不增加业务写工具。

# 12. RAG 规范

RAG 数据隔离必须使用：

```text
user_id + knowledge_base_id
```

文档向量还应携带：

```text
document_id
chunk_id
file_name
page
chunk_index
embedding_model
```

Retriever 不允许只按 `knowledge_base_id` 过滤。

文档删除：

```text
userId + knowledgeBaseId + documentId
```

知识库删除：

```text
userId + knowledgeBaseId
```

向量删除接口必须幂等。

# 13. 文档异步处理

生命周期固定：

```text
UPLOADED
   ↓
PARSING
   ↓
CHUNKING
   ↓
EMBEDDING
   ↓
READY

任一阶段 -> FAILED
```

FastAPI 通过 `docs/API.md` 定义的内部文档进度回调上报 Spring Boot。

Spring Boot 校验：

```text
documentId
taskId == currentTaskId
合法状态迁移
progress
```

旧 task 的迟到回调不得覆盖新 task 状态。

READY：

```text
progress = 100
```

FAILED：

```text
errorMessage != null
```

# 14. SSE 规范

外部：

```text
POST /api/conversations/{id}/stream
```

内部：

```text
POST /internal/agent/stream
```

端到端保持流式。

外部事件：

```text
start
node
tool
token
citation
done
error
```

SSE 不使用普通 `Result<T>` JSON 包装。

Vue 因为需要 POST、JSON Body 和 Authorization Header，默认使用：

```text
fetch + ReadableStream
```

不要直接用只支持 GET 场景的原生 EventSource 实现当前接口。

Spring Boot 不得先完整读取 FastAPI 响应后再一次性返回。

token 不得逐 token 写 MySQL。

# 15. AI 消息持久化

流式请求：

```text
保存user message
创建assistant message(status=GENERATING)
开始AI流
累积token
done -> 更新完整content + COMPLETED
error -> FAILED
cancel -> CANCELLED
```

取消运行态放 Redis。

数据库保存最终业务状态，不保存每一个 SSE token。

# 16. Structured Output

以下 AI 输出必须结构化校验：

```text
测验题
学习计划
SHORT评分
Tool参数
```

必须使用 Pydantic/JSON Schema 等进行验证。

模型输出不能因为“看起来像JSON”就直接入库。

# 17. 测验规则

AI 负责生成题目，但测验业务状态由 Spring Boot 管理。

客观题：

```text
SINGLE
MULTIPLE
JUDGE
```

由 Spring Boot 根据标准答案确定性评分。

SHORT：

```text
Spring Boot -> /internal/quiz/grade-short -> FastAPI
```

默认阈值：

```text
score >= 0.6 -> correct
```

SHORT AI评分全部成功后，Spring Boot 才能在事务中提交：

```text
quiz_answer
quiz status
wrong_question
topic_progress
learning_record
```

如果 SHORT 评分失败：

```text
不保存部分提交结果
quiz保持READY
允许用户重试
```

# 18. 学习计划

Spring Boot 先组装学习画像，再传给 FastAPI：

```text
weakTopics
learningProgress
recentQuizResults
```

FastAPI 不直接查询 `sa_topic_progress`。

计划生成与业务状态分离：

```text
generation_status = PROCESSING / READY / FAILED
status = ACTIVE / COMPLETED / ARCHIVED
```

不得把两种状态混为一个数据库字段。

# 19. 文件存储

Controller 不直接依赖本地磁盘/MinIO/S3 SDK。

使用：

```text
FileStorageService
```

实现可以替换。

用户原始文件名仅作为 metadata。

实际 storage key 使用随机值，防止路径穿越和名称冲突。

# 20. Vue 前端规范

技术栈：

```text
Vue 3
Vite
Element Plus
Axios
Pinia
Vue Router
```

所有 REST 请求集中在：

```text
src/api/
```

组件不应散落重复 Axios 配置。

认证状态集中管理。

401 refresh 必须避免并发刷新风暴：

```text
一个refresh进行中
其他401等待
成功后统一重放
```

异步任务轮询必须有停止条件：

```text
READY
FAILED
组件卸载
超时
```

# 21. 错误处理

Spring Boot 使用统一异常体系和 `Result<T>`。

SSE 建立前：

```text
使用正常HTTP错误
```

SSE 建立后：

```text
event: error
data: {...}
```

FastAPI 不返回 Python traceback 给前端。

内部 AI 错误必须映射为 `API.md` 已定义业务码。

# 22. 日志与追踪

跨 AI 链路使用：

```text
traceId
```

推荐日志上下文：

```text
requestId
userId
traceId
conversationId/documentId/quizId
duration
status
```

不得记录完整 RAG 文档内容作为普通日志。

# 23. 配置

所有密钥使用环境变量/外部配置。

仓库只允许：

```text
.env.example
application-example.yml
```

禁止提交真实：

```text
DB password
JWT secret
AI internal token
LLM API key
```

# 24. 测试要求

实现功能后必须运行与任务相关的测试。

至少考虑：

```text
正常路径
参数错误
未认证
资源越权
资源不存在
重复请求/幂等
AI超时/失败
```

AI/RAG 特别测试：

```text
user_id + knowledge_base_id隔离
Structured Output非法
Tool白名单
SSE done/error/cancel
旧document task callback不能覆盖新任务
```

不要通过删除失败测试来“让测试变绿”。

# 25. 开发阶段

按 `ARCHITECTURE.md` 的 Phase 顺序推进：

```text
Phase 0  项目骨架
Phase 1  认证
Phase 2  知识库CRUD/上传
Phase 3  文档入库/RAG
Phase 4  会话/非流式AI
Phase 5  SSE
Phase 6  Agent/Tools
Phase 7  测验/错题
Phase 8  学习记录/Dashboard
Phase 9  学习计划
Phase 10 部署/完整测试
```

不要一次生成整个项目。

# 26. 每次任务完成时的输出

Codex 完成任务后必须报告：

```text
1. 创建/修改了哪些文件
2. 实现了哪些功能
3. 做了哪些关键设计
4. 执行了哪些测试/命令
5. 测试结果
6. 是否存在未完成项或文档冲突
```

如果无法执行测试，必须明确说明原因，不能声称已经通过。

# 27. Definition of Done

功能完成至少满足：

```text
API契约一致
数据库设计一致
架构边界一致
资源归属校验
参数校验
异常处理
必要事务
关键测试通过
无硬编码密钥
无浏览器直连FastAPI
无FastAPI直接修改业务MySQL
文档与实现一致
```

AI 功能还必须：

```text
超时可控
错误可追踪
Structured Output校验
RAG租户隔离
Tool白名单
SSE可结束/取消
```

# 28. 禁止擅自增加复杂度

除非任务或文档明确要求，不要自行引入：

```text
Spring Cloud
Nacos/Eureka
Gateway
Kafka
RabbitMQ
Kubernetes
Elasticsearch
CQRS
Event Sourcing
GraphQL
WebSocket替代现有SSE
```

优先完成可运行、可测试、符合契约的 StudyAgent V1。
