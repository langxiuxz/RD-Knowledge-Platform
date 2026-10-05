# StudyAgent DATABASE.md

> 版本：V1.0\
> 依据：`StudyAgent接口文档_V2_自查修订版.md`\
> 数据库：MySQL 8.x\
> ORM：MyBatis\
> 字符集：utf8mb4\
> 存储引擎：InnoDB

## 1. 文档目的

本文件是 StudyAgent 的数据库设计基线，用于约束 Spring
Boot、MyBatis、FastAPI/LangGraph 与 Codex 后续编码时的数据模型。

数据库设计遵循以下原则：

1.  `API.md`
    是接口契约，数据库设计必须能够支撑接口中的请求、响应、分页、状态和资源归属校验。
2.  MySQL 保存业务事实、用户数据、会话、测验、学习记录和 AI
    任务的业务状态。
3.  文档原文件保存到对象存储/本地文件服务；MySQL
    只保存文件元数据和地址。
4.  文档 Chunk 与 Embedding 主要保存到向量数据库，不在 MySQL
    中保存大规模向量。
5.  Redis 保存短生命周期运行态，例如正在执行的 SSE/Agent
    任务、取消标记、缓存和限流数据。
6.  所有用户私有资源必须能够通过 `user_id` 或其父级资源追溯到所属用户。
7.  API
    中声明为不可逆删除的知识库、文档、会话、学习计划采用硬删除；不额外引入
    API 未定义的"回收站"语义。
8.  密码只保存密码哈希；refreshToken 只保存哈希，不保存明文。
9.  时间统一由服务端写入，数据库字段使用 `DATETIME`，API 输出为
    `yyyy-MM-dd'T'HH:mm:ss`。
10. 主键统一使用 `BIGINT UNSIGNED AUTO_INCREMENT`。

------------------------------------------------------------------------

# 2. 数据存储边界

StudyAgent 不是"所有数据都塞进 MySQL"的架构。

``` text
Vue
  |
  v
Spring Boot
  |
  +------------------- MySQL --------------------+
  |                                               |
  |  用户/Token/知识库元数据/文档元数据            |
  |  会话/消息/引用/反馈                           |
  |  测验/题目/答案/错题                           |
  |  学习记录/学习统计投影                         |
  |  学习计划/计划任务/文件元数据                   |
  |                                               |
  +-----------------------------------------------+
  |
  +------------------- Redis ---------------------+
  |  SSE/Agent运行状态、traceId取消标记、缓存、限流 |
  +-----------------------------------------------+
  |
  +---------------- Object Storage ---------------+
  |  PDF/DOCX/TXT/MD、头像、普通图片               |
  +-----------------------------------------------+
  |
  v
FastAPI + LangGraph
  |
  +---------------- Vector Store -----------------+
     chunk_id / user_id / knowledge_base_id
     document_id / page / chunk_index
     content / embedding / metadata
```

## 2.1 MySQL 中不保存的内容

以下内容默认不作为 MySQL 的主存储：

-   Embedding 向量；
-   LangGraph 每一个 token 的流式事件；
-   SSE 连接对象；
-   正在运行的 Python coroutine/task；
-   accessToken 明文；
-   refreshToken 明文；
-   模型 API Key；
-   PDF/DOCX 文件二进制本体。

------------------------------------------------------------------------

# 3. 表清单

  表名                    作用                      主要对应模块
  ----------------------- ------------------------- ------------------
  `sa_user`               用户账号与个人资料        1 用户与认证
  `sa_refresh_token`      可撤销刷新令牌/登录会话   1 用户与认证
  `sa_knowledge_base`     知识库元数据              2 知识库
  `sa_document`           知识库文档及处理状态      2 知识库 / 8 RAG
  `sa_conversation`       AI 会话                   3 AI 对话
  `sa_message`            用户/AI 消息              3 AI 对话
  `sa_message_citation`   AI 回答引用来源           3 AI 对话 / RAG
  `sa_message_feedback`   AI 回答点赞/点踩          3 AI 对话
  `sa_quiz`               AI 测验主表               4 AI 测验
  `sa_quiz_question`      测验题目                  4 AI 测验
  `sa_quiz_answer`        用户逐题作答结果          4 AI 测验
  `sa_wrong_question`     错题本                    4 错题本
  `sa_learning_record`    学习行为事实记录          5 学习进度
  `sa_topic_progress`     用户知识点统计投影        5/6 统计
  `sa_study_plan`         AI 学习计划               5 学习计划
  `sa_study_plan_task`    学习计划每日任务          5 学习计划
  `sa_file_asset`         通用头像/图片文件元数据   7 文件
  `sa_long_term_memory`   显式长期目标与学习偏好   6 Agent Context

共 18 张业务表（新增长期记忆表；Learner Profile 为实时聚合视图，不另建画像表）。

------------------------------------------------------------------------

# 4. 核心 ER 关系

``` text
sa_user
  |
  +-- 1:N --> sa_refresh_token
  |
  +-- 1:N --> sa_knowledge_base
  |              |
  |              +-- 1:N --> sa_document
  |
  +-- 1:N --> sa_conversation
  |              |
  |              +-- 1:N --> sa_message
  |                              |
  |                              +-- 1:N --> sa_message_citation
  |                              |
  |                              +-- 1:0..1 --> sa_message_feedback
  |
  +-- 1:N --> sa_quiz
  |              |
  |              +-- 1:N --> sa_quiz_question
  |                              |
  |                              +-- 1:0..1 --> sa_quiz_answer
  |                              |
  |                              +-- 1:0..1 --> sa_wrong_question
  |
  +-- 1:N --> sa_learning_record
  |
  +-- 1:N --> sa_topic_progress
  |
  +-- 1:N --> sa_study_plan
  |              |
  |              +-- 1:N --> sa_study_plan_task
  |
  +-- 1:N --> sa_file_asset
```

知识库还可以被会话和测验引用：

``` text
sa_knowledge_base 1 <-- N sa_conversation
sa_knowledge_base 1 <-- N sa_quiz
```

删除知识库时，文档随知识库删除；历史会话和测验保留，因此它们的
`knowledge_base_id` 使用 `ON DELETE SET NULL`。

------------------------------------------------------------------------

# 5. 表结构详细设计

## 5.1 `sa_user` 用户表

  字段            类型              NULL   默认值              说明
  --------------- ----------------- ------ ------------------- ---------------------
  id              BIGINT UNSIGNED   否     AUTO_INCREMENT      用户ID
  username        VARCHAR(32)       否     \-                  登录用户名，唯一
  password_hash   VARCHAR(255)      否     \-                  BCrypt/Argon2 哈希
  nickname        VARCHAR(64)       否     \-                  昵称
  email           VARCHAR(128)      是     NULL                邮箱，API未规定唯一
  avatar          VARCHAR(512)      是     NULL                头像URL
  status          VARCHAR(16)       否     ACTIVE              ACTIVE/DISABLED
  create_time     DATETIME          否     CURRENT_TIMESTAMP   创建时间
  update_time     DATETIME          否     CURRENT_TIMESTAMP   修改时间

约束：

-   `uk_user_username(username)`。
-   禁止保存明文密码。

对应 API：1.1～1.6。

------------------------------------------------------------------------

## 5.2 `sa_refresh_token` 刷新令牌表

  ---------------------------------------------------------------------------------------
  字段           类型           NULL           默认值              说明
  -------------- -------------- -------------- ------------------- ----------------------
  id             BIGINT         否             AUTO_INCREMENT      主键
                 UNSIGNED                                          

  user_id        BIGINT         否             \-                  用户ID
                 UNSIGNED                                          

  session_id     CHAR(36)       否             \-                  登录会话ID，同时写入
                                                                   accessToken 的 `sid`
                                                                   claim

  token_hash     CHAR(64)       否             \-                  refreshToken 的
                                                                   SHA-256 等哈希

  expires_at     DATETIME       否             \-                  过期时间

  revoked_at     DATETIME       是             NULL                撤销时间

  create_time    DATETIME       否             CURRENT_TIMESTAMP   创建时间

  last_used_at   DATETIME       是             NULL                最近刷新时间
  ---------------------------------------------------------------------------------------

设计说明：

-   API 要求 refreshToken 可服务端撤销，因此必须存在服务端状态。
-   登录时生成 `session_id`，accessToken 携带
    `sid`；`POST /api/auth/logout` 即使没有 refreshToken Body，也可根据
    accessToken 的 `sid` 撤销对应刷新令牌。
-   数据库只保存 `token_hash`，客户端持有明文 refreshToken。
-   `session_id`、`token_hash` 均唯一。

对应 API：1.2、1.3、1.7。

------------------------------------------------------------------------

## 5.3 `sa_knowledge_base` 知识库表

  ---------------------------------------------------------------------------------------------
  字段              类型           NULL           默认值              说明
  ----------------- -------------- -------------- ------------------- -------------------------
  id                BIGINT         否             AUTO_INCREMENT      知识库ID
                    UNSIGNED                                          

  user_id           BIGINT         否             \-                  所属用户
                    UNSIGNED                                          

  name              VARCHAR(64)    否             \-                  名称

  description       VARCHAR(500)   是             NULL                描述

  embedding_model   VARCHAR(128)   是             NULL                Embedding 模型；NULL
                                                                      表示系统默认

  status            VARCHAR(16)    否             READY               PROCESSING/READY/FAILED

  create_time       DATETIME       否             CURRENT_TIMESTAMP   创建时间

  update_time       DATETIME       否             CURRENT_TIMESTAMP   更新时间
  ---------------------------------------------------------------------------------------------

说明：

-   `documentCount` 和 `chunkCount` 不作为事实字段重复保存，分别由
    `sa_document` 的数量和 `SUM(chunk_count)` 计算。
-   `status` 是知识库聚合状态：存在处理中文档时可为
    PROCESSING；无处理中任务时根据文档结果更新为 READY/FAILED。
-   用户刚创建的空知识库可视为 READY。

索引：

-   `(user_id, status, create_time)`
-   `(user_id, name)`

对应 API：2.1～2.5。

------------------------------------------------------------------------

## 5.4 `sa_document` 知识库文档表

  字段                类型               NULL   默认值              说明
  ------------------- ------------------ ------ ------------------- ------------------------------
  id                  BIGINT UNSIGNED    否     AUTO_INCREMENT      文档ID
  knowledge_base_id   BIGINT UNSIGNED    否     \-                  知识库ID
  user_id             BIGINT UNSIGNED    否     \-                  所属用户，便于权限与租户过滤
  file_name           VARCHAR(255)       否     \-                  原始文件名
  file_type           VARCHAR(16)        否     \-                  pdf/docx/txt/md
  mime_type           VARCHAR(128)       是     NULL                MIME
  file_size           BIGINT UNSIGNED    否     0                   字节数
  storage_key         VARCHAR(512)       否     \-                  对象存储随机对象名
  file_url            VARCHAR(1024)      否     \-                  内部文件地址
  status              VARCHAR(16)        否     UPLOADED            处理状态
  progress            TINYINT UNSIGNED   否     0                   0～100
  chunk_count         INT UNSIGNED       否     0                   已生成 Chunk 数
  current_task_id     VARCHAR(64)        是     NULL                当前/最近一次入库任务ID
  error_message       VARCHAR(1000)      是     NULL                FAILED 原因
  create_time         DATETIME           否     CURRENT_TIMESTAMP   上传时间
  update_time         DATETIME           否     CURRENT_TIMESTAMP   更新时间
  processed_at        DATETIME           是     NULL                READY 时间

状态：

``` text
UPLOADED
   ↓
PARSING
   ↓
CHUNKING
   ↓
EMBEDDING
   ↓
READY

任一阶段失败 → FAILED
FAILED --reprocess--> PARSING...
```

约束：

-   `progress <= 100`。
-   READY 时业务层保证 `progress=100`。
-   FAILED 时业务层保证 `error_message` 有值。
-   重处理时更新 `current_task_id`，清空错误信息并重置进度。

对应 API：2.6～2.10、8.3。

------------------------------------------------------------------------

## 5.5 `sa_conversation` AI 会话表

  字段                类型              NULL   默认值              说明
  ------------------- ----------------- ------ ------------------- ----------------
  id                  BIGINT UNSIGNED   否     AUTO_INCREMENT      会话ID
  user_id             BIGINT UNSIGNED   否     \-                  所属用户
  knowledge_base_id   BIGINT UNSIGNED   是     NULL                可绑定知识库
  title               VARCHAR(128)      是     NULL                会话标题
  mode                VARCHAR(16)       否     CHAT                CHAT/RAG/AGENT
  create_time         DATETIME          否     CURRENT_TIMESTAMP   创建时间
  update_time         DATETIME          否     CURRENT_TIMESTAMP   最近更新时间

说明：

-   `messageCount` 由 `sa_message` 聚合，不重复存储。
-   删除会话时级联删除消息、引用和反馈。
-   删除知识库不删除历史会话，只将 `knowledge_base_id` 置 NULL。

对应 API：3.1～3.5。

------------------------------------------------------------------------

## 5.6 `sa_message` 会话消息表

  ------------------------------------------------------------------------------------------------------------
  字段              类型            NULL           默认值              说明
  ----------------- --------------- -------------- ------------------- ---------------------------------------
  id                BIGINT UNSIGNED 否             AUTO_INCREMENT      消息ID

  conversation_id   BIGINT UNSIGNED 否             \-                  会话ID

  user_id           BIGINT UNSIGNED 否             \-                  所属用户

  role              VARCHAR(16)     否             \-                  user/assistant/system/tool

  content           LONGTEXT        否             \-                  消息正文

  status            VARCHAR(16)     否             COMPLETED           GENERATING/COMPLETED/CANCELLED/FAILED

  trace_id          VARCHAR(64)     是             NULL                Agent 执行追踪ID

  finish_reason     VARCHAR(32)     是             NULL                stop/cancelled/error 等

  error_message     VARCHAR(1000)   是             NULL                失败原因

  create_time       DATETIME        否             CURRENT_TIMESTAMP   创建时间
  ------------------------------------------------------------------------------------------------------------

索引：

-   `(conversation_id, id)`：支持 `beforeId` 游标向前加载。
-   `(user_id, create_time)`。
-   `trace_id` 普通索引。

说明：

-   用户发送消息时先持久化 user 消息，再调用 FastAPI。
-   流式 assistant 消息可先创建 GENERATING 记录，结束后更新为
    COMPLETED。
-   `DELETE /generation/{traceId}` 的实时取消标记放
    Redis；最终状态同步到本表。
-   不把每一个 SSE token 单独存数据库。

对应 API：3.6～3.9、8.1、8.6。

------------------------------------------------------------------------

## 5.7 `sa_message_citation` 消息引用表

  ---------------------------------------------------------------------------------------------
  字段           类型           NULL           默认值              说明
  -------------- -------------- -------------- ------------------- ----------------------------
  id             BIGINT         否             AUTO_INCREMENT      主键
                 UNSIGNED                                          

  message_id     BIGINT         否             \-                  assistant 消息ID
                 UNSIGNED                                          

  document_id    BIGINT         是             NULL                来源文档；删除文档后可为空
                 UNSIGNED                                          

  chunk_id       VARCHAR(128)   是             NULL                向量库 Chunk ID

  file_name      VARCHAR(255)   否             \-                  文件名快照

  page           INT UNSIGNED   是             NULL                页码

  score          DECIMAL(8,6)   是             NULL                检索相似度

  sort_order     INT UNSIGNED   否             0                   展示顺序

  create_time    DATETIME       否             CURRENT_TIMESTAMP   创建时间
  ---------------------------------------------------------------------------------------------

说明：

-   `file_name` 是快照字段，避免源文档删除后历史消息完全失去来源展示。
-   `document_id` 使用 `ON DELETE SET NULL`。
-   Chunk 正文仍由向量库管理，不在本表重复保存。

对应 API：3.6、3.8、8.1、8.2。

------------------------------------------------------------------------

## 5.8 `sa_message_feedback` 消息反馈表

  字段          类型              NULL   默认值              说明
  ------------- ----------------- ------ ------------------- ----------
  id            BIGINT UNSIGNED   否     AUTO_INCREMENT      主键
  message_id    BIGINT UNSIGNED   否     \-                  AI消息ID
  user_id       BIGINT UNSIGNED   否     \-                  用户ID
  rating        TINYINT           否     \-                  1 / -1
  reason        VARCHAR(500)      是     NULL                原因
  create_time   DATETIME          否     CURRENT_TIMESTAMP   创建时间
  update_time   DATETIME          否     CURRENT_TIMESTAMP   更新时间

约束：

-   `(user_id, message_id)` 唯一，一个用户对一条 AI
    消息只保留一个当前反馈。
-   业务层只允许对 `role=assistant` 的消息反馈。
-   `rating` 仅允许 `1` 或 `-1`。

对应 API：3.10。

------------------------------------------------------------------------

## 5.9 `sa_quiz` 测验主表

  ----------------------------------------------------------------------------------------------------------
  字段                类型            NULL           默认值              说明
  ------------------- --------------- -------------- ------------------- -----------------------------------
  id                  BIGINT UNSIGNED 否             AUTO_INCREMENT      测验ID

  user_id             BIGINT UNSIGNED 否             \-                  所属用户

  knowledge_base_id   BIGINT UNSIGNED 是             NULL                来源知识库

  title               VARCHAR(128)    否             \-                  标题

  topic               VARCHAR(255)    是             NULL                主题

  difficulty          VARCHAR(16)     否             \-                  EASY/MEDIUM/HARD

  question_count      INT UNSIGNED    否             \-                  题目数量

  types_json          JSON            否             \-                  请求题型数组

  status              VARCHAR(16)     否             PROCESSING          PROCESSING/READY/FAILED/COMPLETED

  score               DECIMAL(5,2)    是             NULL                提交后总分

  correct_count       INT UNSIGNED    是             NULL                正确数

  wrong_count         INT UNSIGNED    是             NULL                错误数

  analysis            TEXT            是             NULL                AI 总体分析

  error_message       VARCHAR(1000)   是             NULL                生成失败原因

  create_time         DATETIME        否             CURRENT_TIMESTAMP   创建时间

  update_time         DATETIME        否             CURRENT_TIMESTAMP   更新时间

  submitted_at        DATETIME        是             NULL                提交时间
  ----------------------------------------------------------------------------------------------------------

状态：

``` text
PROCESSING -> READY -> COMPLETED
      |
      +------> FAILED
```

这套状态同时兼容 API 中"生成阶段 PROCESSING/READY/FAILED"和列表中的
READY/COMPLETED。

对应 API：4.1～4.5、8.4。

------------------------------------------------------------------------

## 5.10 `sa_quiz_question` 测验题目表

  字段                  类型              NULL   默认值              说明
  --------------------- ----------------- ------ ------------------- -----------------------------------
  id                    BIGINT UNSIGNED   否     AUTO_INCREMENT      题目ID
  quiz_id               BIGINT UNSIGNED   否     \-                  测验ID
  topic                 VARCHAR(128)      是     NULL                细分知识点；为空时继承 quiz.topic
  type                  VARCHAR(16)       否     \-                  SINGLE/MULTIPLE/JUDGE/SHORT
  content               TEXT              否     \-                  题干
  options_json          JSON              是     NULL                选项数组
  correct_answer_json   JSON              否     \-                  标准答案
  explanation           TEXT              是     NULL                解析
  sort_order            INT UNSIGNED      否     0                   顺序
  create_time           DATETIME          否     CURRENT_TIMESTAMP   创建时间

`options_json` 示例：

``` json
[
  {"key":"A","text":"对象由容器管理"},
  {"key":"B","text":"数据库连接"}
]
```

`correct_answer_json`：

``` text
单选/判断/简答：JSON 字符串，例如 "A"、"true"、"IOC..."
多选：JSON 数组，例如 ["A","C"]
```

说明：

-   GET 测验详情时不返回 `correct_answer_json` 和 `explanation`。
-   提交后结果接口才读取标准答案和解析。

------------------------------------------------------------------------

## 5.11 `sa_quiz_answer` 用户逐题答案表

  字段               类型              NULL   默认值              说明
  ------------------ ----------------- ------ ------------------- ----------
  id                 BIGINT UNSIGNED   否     AUTO_INCREMENT      主键
  quiz_id            BIGINT UNSIGNED   否     \-                  测验ID
  question_id        BIGINT UNSIGNED   否     \-                  题目ID
  user_id            BIGINT UNSIGNED   否     \-                  用户ID
  user_answer_json   JSON              否     \-                  用户答案
  is_correct         TINYINT(1)        否     0                   是否正确
  create_time        DATETIME          否     CURRENT_TIMESTAMP   作答时间

约束：

-   `(quiz_id, question_id, user_id)` 唯一。
-   当前 API 只支持一个测验提交一次，因此不额外引入 attemptId。
-   如果未来需要"同一测验多次作答"，再升级为
    `quiz_attempt + quiz_answer` 两层模型。

对应 API：4.4、4.5。

------------------------------------------------------------------------

## 5.12 `sa_wrong_question` 错题本

  字段             类型              NULL   默认值              说明
  ---------------- ----------------- ------ ------------------- --------------
  id               BIGINT UNSIGNED   否     AUTO_INCREMENT      错题ID
  user_id          BIGINT UNSIGNED   否     \-                  用户ID
  question_id      BIGINT UNSIGNED   否     \-                  原题ID
  topic            VARCHAR(128)      否     \-                  知识点快照
  wrong_count      INT UNSIGNED      否     1                   累计错误次数
  mastered         TINYINT(1)        否     0                   是否已掌握
  first_wrong_at   DATETIME          否     CURRENT_TIMESTAMP   首次错误
  last_wrong_at    DATETIME          否     CURRENT_TIMESTAMP   最近错误
  update_time      DATETIME          否     CURRENT_TIMESTAMP   更新时间

约束：

-   `(user_id, question_id)` 唯一。
-   同一道题再次答错时 `wrong_count + 1`，更新
    `last_wrong_at`，并可重新将 `mastered=0`。
-   `topic` 保存快照，优先取 `question.topic`，为空时取 `quiz.topic`。
-   删除错题记录只删除本表记录，不删除原题。

对应 API：4.6～4.8。

------------------------------------------------------------------------

## 5.13 `sa_learning_record` 学习行为表

  字段               类型              NULL   默认值              说明
  ------------------ ----------------- ------ ------------------- -----------------------
  id                 BIGINT UNSIGNED   否     AUTO_INCREMENT      记录ID
  user_id            BIGINT UNSIGNED   否     \-                  用户ID
  type               VARCHAR(16)       否     \-                  READ/QUIZ/REVIEW/CHAT
  topic              VARCHAR(128)      否     \-                  知识点
  duration_minutes   INT UNSIGNED      否     0                   学习分钟数
  source_id          BIGINT UNSIGNED   是     NULL                关联资源ID
  remark             VARCHAR(500)      是     NULL                备注
  create_time        DATETIME          否     CURRENT_TIMESTAMP   行为发生时间

说明：

-   `source_id` 是 API 已定义的多态资源ID，不建立数据库外键。
-   `type=QUIZ` 时可指向 quiz；READ 可指向 document；CHAT 可指向
    conversation。
-   Dashboard 的学习时长和连续学习天数以本表为主要事实来源。

索引：

-   `(user_id, create_time)`
-   `(user_id, type, create_time)`
-   `(user_id, topic, create_time)`

对应 API：5.1、5.2、6.1、6.2。

------------------------------------------------------------------------

## 5.14 `sa_topic_progress` 用户知识点统计投影表

  字段                     类型              NULL   默认值              说明
  ------------------------ ----------------- ------ ------------------- -------------------
  id                       BIGINT UNSIGNED   否     AUTO_INCREMENT      主键
  user_id                  BIGINT UNSIGNED   否     \-                  用户ID
  topic                    VARCHAR(128)      否     \-                  知识点
  mastery                  DECIMAL(5,2)      否     0                   掌握度 0～100
  quiz_accuracy            DECIMAL(5,2)      否     0                   测验正确率 0～100
  study_minutes            INT UNSIGNED      否     0                   累计学习分钟
  wrong_count              INT UNSIGNED      否     0                   当前统计错误次数
  quiz_question_count      INT UNSIGNED      否     0                   已统计题目数
  correct_question_count   INT UNSIGNED      否     0                   正确题目数
  update_time              DATETIME          否     CURRENT_TIMESTAMP   更新时间

约束：

-   `(user_id, topic)` 唯一。

重要说明：

-   这是**统计投影/缓存表，不是原始事实表**。
-   原始事实来自
    `sa_learning_record`、`sa_quiz_answer`、`sa_wrong_question`。
-   API.md 只规定需要返回
    `mastery / quizAccuracy / studyMinutes`，并没有规定掌握度算法，因此
    DATABASE.md **不擅自定义 mastery 的业务公式**。
-   Service 可在提交测验、记录学习行为后同步更新，或由异步任务重算。
-   如出现统计不一致，以原始事实表重算本表。

对应 API：5.3、6.3，以及 Agent 的 `get_learning_progress` 工具数据来源。

------------------------------------------------------------------------

## 5.15 `sa_long_term_memory` 长期记忆表

仅存储明确、相对稳定且有用的学习目标、解释偏好、学习方式偏好；不保存会话全文、mastery、错题、测验成绩、学习记录或学习计划业务事实。候选由 Spring Boot 基于所属 user message 原文再次验证；唯一键 `(user_id, category, memory_key)` 用于去重和显式更新覆盖。读取始终限制当前 user_id。

表结构通过 `V2__long_term_memory.sql` 新增，字段包括 user_id、category、memory_key、memory_value、source_message_id 和创建/更新时间；来源消息删除时仅将 source_message_id 置空，保留仍有效的跨会话长期记忆。Learner Profile 则从业务事实表按需即时聚合，不进入本表。

------------------------------------------------------------------------

## 5.16 `sa_study_plan` 学习计划主表

  --------------------------------------------------------------------------------------------------
  字段                类型            NULL           默认值              说明
  ------------------- --------------- -------------- ------------------- ---------------------------
  id                  BIGINT UNSIGNED 否             AUTO_INCREMENT      计划ID

  user_id             BIGINT UNSIGNED 否             \-                  用户ID

  title               VARCHAR(128)    否             \-                  计划标题

  goal                VARCHAR(500)    否             \-                  学习目标

  start_date          DATE            否             \-                  开始日期

  end_date            DATE            否             \-                  结束日期

  daily_minutes       INT UNSIGNED    否             \-                  每日计划分钟

  focus_topics_json   JSON            是             NULL                用户指定重点知识点

  generation_status   VARCHAR(16)     否             PROCESSING          PROCESSING/READY/FAILED

  status              VARCHAR(16)     否             ACTIVE              ACTIVE/COMPLETED/ARCHIVED

  error_message       VARCHAR(1000)   是             NULL                AI生成失败原因

  create_time         DATETIME        否             CURRENT_TIMESTAMP   创建时间

  update_time         DATETIME        否             CURRENT_TIMESTAMP   更新时间
  --------------------------------------------------------------------------------------------------

### 为什么这里有两个状态字段

API.md 中对学习计划的 `status` 有两组不同语义：

``` text
生成过程：PROCESSING -> READY / FAILED
计划业务状态：ACTIVE / COMPLETED / ARCHIVED
```

这两组状态不能安全地压进同一个字段，否则 READY 后如何变成 ACTIVE
没有接口定义。

因此数据库明确拆分：

-   `generation_status`：AI 是否已经把计划生成出来；
-   `status`：生成成功后的计划生命周期。

API 映射：

-   `POST /api/study-plans/generate` 返回的 `status` →
    `generation_status`。
-   计划列表的 `status` 查询参数与返回值 → `status`。
-   5.6 详情只有 `generation_status=READY` 时才保证 tasks 完整。

这不是修改 API，而是解决 API 中同名 `status` 的双重语义。

------------------------------------------------------------------------

## 5.17 `sa_study_plan_task` 学习计划任务表

  字段                类型              NULL   默认值              说明
  ------------------- ----------------- ------ ------------------- --------------------------
  id                  BIGINT UNSIGNED   否     AUTO_INCREMENT      任务ID
  plan_id             BIGINT UNSIGNED   否     \-                  计划ID
  user_id             BIGINT UNSIGNED   否     \-                  用户ID，便于权限校验
  task_date           DATE              否     \-                  日期
  topic               VARCHAR(128)      否     \-                  知识点
  type                VARCHAR(16)       否     \-                  READ/QUIZ/REVIEW/CHAT 等
  estimated_minutes   INT UNSIGNED      否     0                   预计分钟
  completed           TINYINT(1)        否     0                   是否完成
  completed_at        DATETIME          是     NULL                完成时间
  sort_order          INT UNSIGNED      否     0                   当日顺序
  create_time         DATETIME          否     CURRENT_TIMESTAMP   创建时间
  update_time         DATETIME          否     CURRENT_TIMESTAMP   更新时间

说明：

-   计划进度 `progress` 不重复存储，计算为：
    `completed task count / total task count * 100`。
-   更新任务完成状态后，根据全部任务是否完成决定是否把计划 `status`
    更新为 COMPLETED。

对应 API：5.6、5.7。

------------------------------------------------------------------------

## 5.18 `sa_file_asset` 通用文件元数据表

  字段            类型              NULL   默认值              说明
  --------------- ----------------- ------ ------------------- ----------------
  id              BIGINT UNSIGNED   否     AUTO_INCREMENT      文件ID
  user_id         BIGINT UNSIGNED   否     \-                  上传用户
  type            VARCHAR(16)       否     \-                  AVATAR/IMAGE
  original_name   VARCHAR(255)      否     \-                  原文件名
  storage_key     VARCHAR(512)      否     \-                  随机存储对象名
  url             VARCHAR(1024)     否     \-                  访问URL
  mime_type       VARCHAR(128)      是     NULL                MIME
  file_size       BIGINT UNSIGNED   否     0                   文件大小
  create_time     DATETIME          否     CURRENT_TIMESTAMP   上传时间

说明：

-   知识库文档不使用本表，而使用 `sa_document`。
-   本表主要支撑头像和普通图片上传、文件归属审计和后续垃圾文件清理。

对应 API：7.1。

------------------------------------------------------------------------

# 6. 向量数据库设计

向量库至少需要以下 metadata：

  字段                说明
  ------------------- ----------------------------------------
  chunk_id            全局唯一 Chunk ID，例如 `chunk_101_18`
  user_id             用户ID，必须参与租户隔离
  knowledge_base_id   知识库ID
  document_id         文档ID
  file_name           文件名
  page                页码，可为空
  chunk_index         文档内 Chunk 顺序
  content             Chunk 文本
  embedding           向量
  embedding_model     使用的模型
  metadata            其他扩展信息

检索过滤必须至少包含：

``` text
user_id = 当前认证用户
AND knowledge_base_id = 当前知识库
```

删除文档：

``` text
DELETE vector WHERE document_id = ?
```

删除知识库：

``` text
DELETE vector WHERE user_id = ? AND knowledge_base_id = ?
```

MySQL `sa_document.chunk_count` 保存成功写入向量库的 Chunk 数量，用于
API 列表和详情统计。

------------------------------------------------------------------------

# 7. Redis 运行态设计

Redis 不属于 DATABASE.md 的关系表，但以下 API 依赖它：

``` text
generation:{traceId}
```

建议值：

``` json
{
  "userId": 1,
  "conversationId": 301,
  "status": "RUNNING"
}
```

状态可为：

``` text
RUNNING / CANCEL_REQUESTED / DONE / FAILED
```

用于：

-   SSE 生成任务跟踪；
-   `/api/conversations/{id}/generation/{traceId}` 停止生成；
-   Spring Boot 与 FastAPI 流式任务取消协作。

建议设置 TTL，任务结束后短时间保留即可。

其他可选 Redis Key：

``` text
rate:ai:{userId}
cache:progress:{userId}
cache:dashboard:{userId}:{range}
```

Redis 不是业务事实的最终来源。

------------------------------------------------------------------------

# 8. API → 数据表映射

  API 模块                 主要数据表
  ------------------------ -------------------------------------------------
  1.1 注册                 `sa_user`
  1.2 登录                 `sa_user`, `sa_refresh_token`
  1.3 退出                 `sa_refresh_token`
  1.4～1.6 用户资料/密码   `sa_user`
  1.7 Token刷新            `sa_refresh_token`
  2.1～2.5 知识库 CRUD     `sa_knowledge_base`, `sa_document`
  2.6～2.10 文档           `sa_document` + Object Storage + Vector Store
  3.1～3.5 会话            `sa_conversation`
  3.6 历史消息             `sa_message`, `sa_message_citation`
  3.7/3.8 AI消息           `sa_message`, `sa_message_citation` + Redis
  3.9 停止生成             Redis + `sa_message`
  3.10 消息反馈            `sa_message_feedback`
  4.1～4.5 测验            `sa_quiz`, `sa_quiz_question`, `sa_quiz_answer`
  4.6～4.8 错题本          `sa_wrong_question`, `sa_quiz_question`
  5.1/5.2 学习记录         `sa_learning_record`
  5.3 学习进度             `sa_topic_progress` + 原始事实表
  5.4～5.8 学习计划        `sa_study_plan`, `sa_study_plan_task`
  6.1 学习总览             学习记录、测验、知识库
  6.2 学习趋势             学习记录、测验
  6.3 薄弱知识点           `sa_topic_progress`, `sa_wrong_question`
  7.1 通用文件上传         `sa_file_asset` + Object Storage
  7.2 健康检查             无业务表
  8.1/8.6 Agent            会话/消息/统计表 + Redis
  8.2 RAG检索              Vector Store + `sa_document`
  8.3 文档入库             `sa_document` + Vector Store
  8.4 AI生成测验           `sa_quiz`, `sa_quiz_question`
  8.5 AI生成计划           `sa_study_plan`, `sa_study_plan_task`

------------------------------------------------------------------------

# 9. 关键查询与索引策略

## 9.1 用户资源归属

任何资源 ID 查询都不能只写：

``` sql
SELECT * FROM sa_knowledge_base WHERE id = #{id};
```

必须包含当前用户：

``` sql
SELECT *
FROM sa_knowledge_base
WHERE id = #{id}
  AND user_id = #{userId};
```

对 document 等二级资源也应直接使用冗余 `user_id` 或通过父资源 JOIN
校验。

## 9.2 会话历史游标分页

API 使用 `beforeId`，推荐：

``` sql
SELECT id, role, content, status, create_time
FROM sa_message
WHERE conversation_id = #{conversationId}
  AND user_id = #{userId}
  AND (#{beforeId} IS NULL OR id < #{beforeId})
ORDER BY id DESC
LIMIT #{pageSize};
```

依赖索引：

``` text
(conversation_id, id)
```

## 9.3 Dashboard

学习总时长：

``` sql
SUM(sa_learning_record.duration_minutes)
```

测验数量：

``` sql
COUNT(sa_quiz.id)
WHERE status = 'COMPLETED'
```

平均分：

``` sql
AVG(sa_quiz.score)
WHERE status = 'COMPLETED'
```

知识库数量：

``` sql
COUNT(sa_knowledge_base.id)
```

连续学习天数：

-   以 `sa_learning_record.create_time` 的 distinct date 为基础计算；
-   不单独保存一个容易失真的 `streak_days` 字段。

## 9.4 薄弱知识点

优先从 `sa_topic_progress` 按 mastery 升序、wrong_count
降序查询；必要时从原始答题和错题事实重算。

------------------------------------------------------------------------

# 10. MySQL 建表 SQL

``` sql
CREATE DATABASE IF NOT EXISTS study_agent
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE study_agent;

CREATE TABLE sa_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(32) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    email VARCHAR(128) NULL,
    avatar VARCHAR(512) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    KEY idx_user_email (email),
    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_refresh_token (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    session_id CHAR(36) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_session (session_id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_user_expire (user_id, expires_at),
    CONSTRAINT fk_refresh_user
        FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_knowledge_base (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(500) NULL,
    embedding_model VARCHAR(128) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'READY',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_kb_user_status_time (user_id, status, create_time),
    KEY idx_kb_user_name (user_id, name),
    CONSTRAINT chk_kb_status CHECK (status IN ('PROCESSING','READY','FAILED')),
    CONSTRAINT fk_kb_user
        FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_document (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    knowledge_base_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(16) NOT NULL,
    mime_type VARCHAR(128) NULL,
    file_size BIGINT UNSIGNED NOT NULL DEFAULT 0,
    storage_key VARCHAR(512) NOT NULL,
    file_url VARCHAR(1024) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'UPLOADED',
    progress TINYINT UNSIGNED NOT NULL DEFAULT 0,
    chunk_count INT UNSIGNED NOT NULL DEFAULT 0,
    current_task_id VARCHAR(64) NULL,
    error_message VARCHAR(1000) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    processed_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_doc_kb_status_time (knowledge_base_id, status, create_time),
    KEY idx_doc_user_status (user_id, status),
    KEY idx_doc_user_filename (user_id, file_name),
    KEY idx_doc_task (current_task_id),
    CONSTRAINT chk_doc_status CHECK (
        status IN ('UPLOADED','PARSING','CHUNKING','EMBEDDING','READY','FAILED')
    ),
    CONSTRAINT chk_doc_progress CHECK (progress <= 100),
    CONSTRAINT fk_doc_kb
        FOREIGN KEY (knowledge_base_id) REFERENCES sa_knowledge_base(id) ON DELETE CASCADE,
    CONSTRAINT fk_doc_user
        FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_conversation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    knowledge_base_id BIGINT UNSIGNED NULL,
    title VARCHAR(128) NULL,
    mode VARCHAR(16) NOT NULL DEFAULT 'CHAT',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_conv_user_mode_time (user_id, mode, update_time),
    KEY idx_conv_kb (knowledge_base_id),
    CONSTRAINT chk_conv_mode CHECK (mode IN ('CHAT','RAG','AGENT')),
    CONSTRAINT fk_conv_user
        FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_conv_kb
        FOREIGN KEY (knowledge_base_id) REFERENCES sa_knowledge_base(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_message (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    role VARCHAR(16) NOT NULL,
    content LONGTEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED',
    trace_id VARCHAR(64) NULL,
    finish_reason VARCHAR(32) NULL,
    error_message VARCHAR(1000) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_msg_conv_id (conversation_id, id),
    KEY idx_msg_user_time (user_id, create_time),
    KEY idx_msg_trace (trace_id),
    CONSTRAINT chk_msg_role CHECK (role IN ('user','assistant','system','tool')),
    CONSTRAINT chk_msg_status CHECK (status IN ('GENERATING','COMPLETED','CANCELLED','FAILED')),
    CONSTRAINT fk_msg_conv
        FOREIGN KEY (conversation_id) REFERENCES sa_conversation(id) ON DELETE CASCADE,
    CONSTRAINT fk_msg_user
        FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_message_citation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    message_id BIGINT UNSIGNED NOT NULL,
    document_id BIGINT UNSIGNED NULL,
    chunk_id VARCHAR(128) NULL,
    file_name VARCHAR(255) NOT NULL,
    page INT UNSIGNED NULL,
    score DECIMAL(8,6) NULL,
    sort_order INT UNSIGNED NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_citation_message_order (message_id, sort_order),
    KEY idx_citation_document (document_id),
    CONSTRAINT fk_citation_message
        FOREIGN KEY (message_id) REFERENCES sa_message(id) ON DELETE CASCADE,
    CONSTRAINT fk_citation_document
        FOREIGN KEY (document_id) REFERENCES sa_document(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_message_feedback (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    message_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    rating TINYINT NOT NULL,
    reason VARCHAR(500) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_feedback_user_message (user_id, message_id),
    KEY idx_feedback_message (message_id),
    CONSTRAINT chk_feedback_rating CHECK (rating IN (-1, 1)),
    CONSTRAINT fk_feedback_message
        FOREIGN KEY (message_id) REFERENCES sa_message(id) ON DELETE CASCADE,
    CONSTRAINT fk_feedback_user
        FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_quiz (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    knowledge_base_id BIGINT UNSIGNED NULL,
    title VARCHAR(128) NOT NULL,
    topic VARCHAR(255) NULL,
    difficulty VARCHAR(16) NOT NULL,
    question_count INT UNSIGNED NOT NULL,
    types_json JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    score DECIMAL(5,2) NULL,
    correct_count INT UNSIGNED NULL,
    wrong_count INT UNSIGNED NULL,
    analysis TEXT NULL,
    error_message VARCHAR(1000) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    submitted_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_quiz_user_status_time (user_id, status, create_time),
    KEY idx_quiz_kb (knowledge_base_id),
    CONSTRAINT chk_quiz_difficulty CHECK (difficulty IN ('EASY','MEDIUM','HARD')),
    CONSTRAINT chk_quiz_status CHECK (status IN ('PROCESSING','READY','FAILED','COMPLETED')),
    CONSTRAINT chk_quiz_count CHECK (question_count BETWEEN 1 AND 50),
    CONSTRAINT fk_quiz_user
        FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_kb
        FOREIGN KEY (knowledge_base_id) REFERENCES sa_knowledge_base(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_quiz_question (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    quiz_id BIGINT UNSIGNED NOT NULL,
    topic VARCHAR(128) NULL,
    type VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    options_json JSON NULL,
    correct_answer_json JSON NOT NULL,
    explanation TEXT NULL,
    sort_order INT UNSIGNED NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_question_quiz_order (quiz_id, sort_order),
    KEY idx_question_topic (topic),
    CONSTRAINT chk_question_type CHECK (type IN ('SINGLE','MULTIPLE','JUDGE','SHORT')),
    CONSTRAINT fk_question_quiz
        FOREIGN KEY (quiz_id) REFERENCES sa_quiz(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_quiz_answer (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    quiz_id BIGINT UNSIGNED NOT NULL,
    question_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    user_answer_json JSON NOT NULL,
    is_correct TINYINT(1) NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_answer_quiz_question_user (quiz_id, question_id, user_id),
    KEY idx_answer_user_time (user_id, create_time),
    KEY idx_answer_question (question_id),
    CONSTRAINT fk_answer_quiz
        FOREIGN KEY (quiz_id) REFERENCES sa_quiz(id) ON DELETE CASCADE,
    CONSTRAINT fk_answer_question
        FOREIGN KEY (question_id) REFERENCES sa_quiz_question(id) ON DELETE CASCADE,
    CONSTRAINT fk_answer_user
        FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_wrong_question (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    question_id BIGINT UNSIGNED NOT NULL,
    topic VARCHAR(128) NOT NULL,
    wrong_count INT UNSIGNED NOT NULL DEFAULT 1,
    mastered TINYINT(1) NOT NULL DEFAULT 0,
    first_wrong_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_wrong_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wrong_user_question (user_id, question_id),
    KEY idx_wrong_user_mastered_topic (user_id, mastered, topic),
    KEY idx_wrong_user_count (user_id, wrong_count),
    CONSTRAINT fk_wrong_user
        FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_wrong_question
        FOREIGN KEY (question_id) REFERENCES sa_quiz_question(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_learning_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    type VARCHAR(16) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    duration_minutes INT UNSIGNED NOT NULL DEFAULT 0,
    source_id BIGINT UNSIGNED NULL,
    remark VARCHAR(500) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_learning_user_time (user_id, create_time),
    KEY idx_learning_user_type_time (user_id, type, create_time),
    KEY idx_learning_user_topic_time (user_id, topic, create_time),
    CONSTRAINT chk_learning_type CHECK (type IN ('READ','QUIZ','REVIEW','CHAT')),
    CONSTRAINT fk_learning_user
        FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_topic_progress (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    topic VARCHAR(128) NOT NULL,
    mastery DECIMAL(5,2) NOT NULL DEFAULT 0,
    quiz_accuracy DECIMAL(5,2) NOT NULL DEFAULT 0,
    study_minutes INT UNSIGNED NOT NULL DEFAULT 0,
    wrong_count INT UNSIGNED NOT NULL DEFAULT 0,
    quiz_question_count INT UNSIGNED NOT NULL DEFAULT 0,
    correct_question_count INT UNSIGNED NOT NULL DEFAULT 0,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_progress_user_topic (user_id, topic),
    KEY idx_progress_user_mastery (user_id, mastery),
    KEY idx_progress_user_wrong (user_id, wrong_count),
    CONSTRAINT chk_progress_mastery CHECK (mastery BETWEEN 0 AND 100),
    CONSTRAINT chk_progress_accuracy CHECK (quiz_accuracy BETWEEN 0 AND 100),
    CONSTRAINT fk_progress_user
        FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_study_plan (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(128) NOT NULL,
    goal VARCHAR(500) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    daily_minutes INT UNSIGNED NOT NULL,
    focus_topics_json JSON NULL,
    generation_status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    error_message VARCHAR(1000) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_plan_user_status_time (user_id, status, create_time),
    KEY idx_plan_user_generation (user_id, generation_status),
    CONSTRAINT chk_plan_generation CHECK (generation_status IN ('PROCESSING','READY','FAILED')),
    CONSTRAINT chk_plan_status CHECK (status IN ('ACTIVE','COMPLETED','ARCHIVED')),
    CONSTRAINT chk_plan_dates CHECK (end_date >= start_date),
    CONSTRAINT fk_plan_user
        FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_study_plan_task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    plan_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    task_date DATE NOT NULL,
    topic VARCHAR(128) NOT NULL,
    type VARCHAR(16) NOT NULL,
    estimated_minutes INT UNSIGNED NOT NULL DEFAULT 0,
    completed TINYINT(1) NOT NULL DEFAULT 0,
    completed_at DATETIME NULL,
    sort_order INT UNSIGNED NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_task_plan_date_order (plan_id, task_date, sort_order),
    KEY idx_task_user_date (user_id, task_date),
    CONSTRAINT fk_task_plan
        FOREIGN KEY (plan_id) REFERENCES sa_study_plan(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_user
        FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_file_asset (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    type VARCHAR(16) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    url VARCHAR(1024) NOT NULL,
    mime_type VARCHAR(128) NULL,
    file_size BIGINT UNSIGNED NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_storage_key (storage_key),
    KEY idx_file_user_type_time (user_id, type, create_time),
    CONSTRAINT chk_file_type CHECK (type IN ('AVATAR','IMAGE')),
    CONSTRAINT fk_file_user
        FOREIGN KEY (user_id) REFERENCES sa_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

------------------------------------------------------------------------

# 11. 事务边界

以下操作必须使用 Spring `@Transactional`：

## 11.1 提交测验

一个事务内至少完成：

``` text
校验 quiz 属于当前用户且 status=READY
  ↓
批量写 sa_quiz_answer
  ↓
计算 correct_count / wrong_count / score
  ↓
更新 sa_quiz -> COMPLETED
  ↓
UPSERT sa_wrong_question
  ↓
更新/重算 sa_topic_progress
  ↓
可选：写入 QUIZ 类型 sa_learning_record
```

若中途失败必须整体回滚。

## 11.2 更新学习计划任务

``` text
更新 sa_study_plan_task.completed
  ↓
重新计算计划 progress
  ↓
如果全部完成，更新 sa_study_plan.status=COMPLETED
```

## 11.3 删除会话

依靠外键级联：

``` text
sa_conversation
  -> sa_message
       -> sa_message_citation
       -> sa_message_feedback
```

## 11.4 删除知识库

MySQL 事务负责：

``` text
删除 sa_knowledge_base
  -> cascade sa_document
```

向量库和对象存储属于外部资源，不能依赖 MySQL
本地事务实现真正的分布式原子性。Service 应：

1.  校验用户资源归属；
2.  收集 document/vector/storage 标识；
3.  删除/标记外部资源；
4.  删除 MySQL 数据；
5.  外部删除失败时记录日志并提供补偿/重试机制。

------------------------------------------------------------------------

# 12. 数据一致性规则

1.  `sa_document.user_id` 必须等于所属 `sa_knowledge_base.user_id`。
2.  `sa_message.user_id` 必须等于所属 `sa_conversation.user_id`。
3.  `sa_study_plan_task.user_id` 必须等于所属 `sa_study_plan.user_id`。
4.  `sa_quiz_answer.user_id` 必须等于所属 `sa_quiz.user_id`。
5.  会话绑定知识库时，知识库必须属于同一用户。
6.  测验绑定知识库时，知识库必须属于同一用户。
7.  FastAPI 不能相信浏览器提交的 userId；内部调用中的 userId 必须由
    Spring Boot 的认证上下文产生。
8.  RAG 必须同时使用 `user_id + knowledge_base_id` 过滤。
9.  所有 `sourceId` 多态关联都由 Service 校验，不建立跨类型外键。
10. `sa_topic_progress` 是可重建投影，不可作为唯一事实来源。

------------------------------------------------------------------------

# 13. API 文档中发现的数据库语义点

本数据库设计没有修改 API，但有两个地方需要在编码阶段明确：

## 13.1 学习计划 `status` 有双重语义

API 同时定义：

``` text
PROCESSING -> READY / FAILED
```

以及：

``` text
ACTIVE / COMPLETED / ARCHIVED
```

数据库已通过：

``` text
generation_status
status
```

拆开解决。后续如果更新 API.md，建议也把两个字段名称明确区分。

## 13.2 测验当前只支持一次提交

API 使用：

``` text
POST /api/quizzes/{id}/submit
GET  /api/quizzes/{id}/result
```

且定义错误码"测验已提交"，没有 `attemptId`，因此当前数据库采用"一份 quiz
一次提交"。

如果未来要支持反复练习，需要升级为：

``` text
sa_quiz
  -> sa_quiz_attempt
       -> sa_quiz_answer
```

当前版本不要提前增加 API 未使用的 attempt 复杂度。

------------------------------------------------------------------------

# 14. Codex 实现约束

Codex 在实现数据库层时必须遵守：

1.  不得自行修改本文件已经确定的表名和核心字段语义。
2.  Entity 使用 Java 驼峰字段，MyBatis 映射到 snake_case。
3.  Controller 不直接访问 Mapper。
4.  所有用户资源查询必须携带当前 `userId` 做归属校验。
5.  禁止把前端提交的 userId 当作认证身份。
6.  密码、refreshToken 明文、JWT、API Key 禁止写数据库日志。
7.  JSON 字段通过 Jackson/TypeHandler 转换，不在 Controller 手写 JSON
    字符串拼接。
8.  测验提交、错题更新、学习进度更新使用事务。
9.  文档向量不写入 MySQL。
10. SSE token 不逐条写入 MySQL。
11. Redis 运行态与 MySQL 业务事实必须区分。
12. 任何需要偏离 DATABASE.md
    的实现，应先说明原因并同步修改文档，而不是静默改变数据库。

------------------------------------------------------------------------

# 15. 推荐项目文件位置

``` text
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

本文件建议保存为：

``` text
docs/DATABASE.md
```

------------------------------------------------------------------------

# 16. 当前数据库设计结论

当前 V1.0 数据模型已经覆盖 StudyAgent API V2.0 中的：

-   用户注册、登录、JWT refreshToken 撤销；
-   知识库 CRUD；
-   文档上传、异步解析、Chunk、Embedding 状态；
-   RAG 文档引用；
-   AI 会话、历史消息、SSE、停止生成；
-   消息点赞/点踩；
-   AI 测验生成、题目、提交、结果；
-   错题本；
-   学习行为；
-   知识点掌握度统计；
-   AI 学习计划及每日任务；
-   Dashboard 总览、趋势和薄弱知识点；
-   通用文件上传；
-   Spring Boot 与 FastAPI/LangGraph 的数据边界。

下一阶段应以 `API.md + DATABASE.md` 为共同约束继续确定
`ARCHITECTURE.md`，然后再让 Codex 按模块编码。
