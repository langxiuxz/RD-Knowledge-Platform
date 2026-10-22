# StudyAgent DATABASE.md

> 版本：V3.0  
> 依据：`API_V3.md`  
> 数据库：MySQL 8.x  
> ORM：MyBatis  
> 字符集：utf8mb4  
> 存储引擎：InnoDB  
> 产品：研发知识交接与能力验证平台

# 1. 文档目的

本文件定义 StudyAgent V3 的数据契约，用于约束 Spring Boot、MyBatis、FastAPI/LangGraph 与后续 Codex 编码。

V3 数据模型的核心变化是：**业务资源不再以“当前用户的个人学习数据”为中心，而以 Project 作为共享业务边界；用户是项目成员、训练参与者和操作主体。**

数据库设计遵循以下原则：

1. `API_V3.md` 是对外/内部接口契约，数据库必须支撑其状态、权限、审计和版本语义。
2. Spring Boot + MySQL 是业务事实唯一权威。
3. FastAPI 不直接读写业务 MySQL。
4. Project 是共享资源边界；用户权限来自 `sa_project_member`，而不是“资源表上有相同 user_id”。
5. 已发布知识版本、知识快照、历史评估与能力报告必须可回放，不允许原地覆盖。
6. AI 评分建议、程序评分、人工复核必须分字段/分记录保存。
7. 文档文件本体进入对象存储；Chunk/Embedding 进入 Milvus；Redis 只保存短生命周期运行态。
8. 关键写操作必须有事务、幂等或状态竞争保护。
9. 关键管理操作写 `sa_audit_log`。
10. V3 首次实现不引入组织/部门/公司多级租户树；Project 即协作空间。
11. 已存在的 V1～V3 历史迁移文件不直接修改；V3 产品重构通过新的向前迁移完成。
12. 旧学习模块表可在迁移期保留，但停止作为 V3 新业务事实来源。

---

# 2. 数据存储边界

```text
Browser
  |
  v
Spring Boot
  |
  +---------------------- MySQL -----------------------+
  | Auth / Project / Member / Audit                    |
  | Knowledge metadata / versions / snapshots          |
  | Onboarding paths / assignments                     |
  | Quiz / Drill / Reviews / Capability Reports        |
  | Conversations / messages / citations               |
  +-----------------------------------------------------+
  |
  +---------------------- Redis -----------------------+
  | AI generation trace / cancel flags / short cache   |
  +-----------------------------------------------------+
  |
  +------------------ Object Storage ------------------+
  | document version files / avatars / images           |
  +-----------------------------------------------------+
  |
  v
FastAPI + LangGraph
  |
  +---------------------- Milvus ----------------------+
     project_id / knowledge_base_id / document_id
     document_version_id / chunk_id / content / embedding
```

## 2.1 MySQL 中不保存

- Embedding 向量；
- 每个 SSE token；
- Python coroutine/task；
- accessToken 明文；
- refreshToken 明文；
- LLM API Key；
- 文档二进制本体；
- Milvus 大规模 Chunk 正文副本；
- “模型脑内推理过程”。

## 2.2 Redis 不是事实库

Redis 可以保存：

```text
generation:{traceId}
rate:ai:{userId}
lock:report:{assignmentId}
cache:project-insight:{projectId}
```

任务结束后使用 TTL 清理。任何能力报告、分数、项目角色、知识发布状态均不得只存在 Redis。

---

# 3. V3 表清单

| 表名 | 作用 | 业务域 |
| --- | --- | --- |
| `sa_user` | 用户账号 | Auth |
| `sa_refresh_token` | 可撤销登录会话 | Auth |
| `sa_project` | 项目协作空间 | Project |
| `sa_project_member` | 项目成员与角色 | Project |
| `sa_audit_log` | 关键操作审计 | Project/Audit |
| `sa_knowledge_base` | 项目知识库 | Knowledge |
| `sa_document` | 逻辑文档 | Knowledge |
| `sa_document_version` | 不可变文档文件版本 | Knowledge |
| `sa_knowledge_snapshot` | 已发布知识快照 | Knowledge |
| `sa_knowledge_snapshot_item` | 快照固定文档版本项 | Knowledge |
| `sa_content_dependency` | 文档版本与训练内容依赖/影响 | Knowledge/Insight |
| `sa_conversation` | 项目内 AI 会话 | Assistant |
| `sa_message` | 用户/AI 消息 | Assistant |
| `sa_message_citation` | 回答来源快照 | Assistant/RAG |
| `sa_message_feedback` | AI 回答反馈 | Assistant |
| `sa_quiz` | 可发布测验模板 | Assessment |
| `sa_quiz_question` | 测验题目 | Assessment |
| `sa_quiz_attempt` | 一次测验执行 | Assessment |
| `sa_quiz_answer` | 一次逐题答案与评分 | Assessment |
| `sa_drill_scenario` | 故障演练模板 | Assessment |
| `sa_drill_rule` | 演练确定性规则 | Assessment |
| `sa_drill_evidence` | 演练可解锁证据 | Assessment |
| `sa_drill_run` | 一次故障演练 | Assessment |
| `sa_drill_run_action` | 演练动作轨迹 | Assessment |
| `sa_assessment_review` | 测验/演练/Assignment 人工复核 | Assessment |
| `sa_onboarding_path` | 可复用接手路径 | Onboarding |
| `sa_onboarding_path_task` | 路径模板任务 | Onboarding |
| `sa_onboarding_task_dependency` | 路径任务依赖边 | Onboarding |
| `sa_onboarding_assignment` | 路径分配实例 | Onboarding |
| `sa_onboarding_assignment_task` | 固定后的执行任务快照 | Onboarding |
| `sa_capability_report` | 可回放能力报告 | Insight |
| `sa_file_asset` | 头像/普通图片文件元数据 | File |

目标模型共 32 张表。V3 不新增长期记忆、个人学习时长、错题本、番茄钟等业务表。

---

# 4. 核心 ER 关系

```text
sa_user
  |
  +-- 1:N --> sa_refresh_token
  |
  +-- N:M --> sa_project
              |
              +-- 1:N --> sa_project_member
              +-- 1:N --> sa_audit_log
              |
              +-- 1:N --> sa_knowledge_base
              |              |
              |              +-- 1:N --> sa_document
              |              |              |
              |              |              +-- 1:N --> sa_document_version
              |              |
              |              +-- 1:N --> sa_knowledge_snapshot
              |                             |
              |                             +-- 1:N --> sa_knowledge_snapshot_item
              |
              +-- 1:N --> sa_onboarding_path
              |              |
              |              +-- 1:N --> sa_onboarding_path_task
              |
              +-- 1:N --> sa_onboarding_assignment
              |              |
              |              +-- 1:N --> sa_onboarding_assignment_task
              |
              +-- 1:N --> sa_quiz
              |              |
              |              +-- 1:N --> sa_quiz_question
              |              +-- 1:N --> sa_quiz_attempt
              |                             |
              |                             +-- 1:N --> sa_quiz_answer
              |
              +-- 1:N --> sa_drill_scenario
              |              |
              |              +-- 1:N --> sa_drill_rule
              |              +-- 1:N --> sa_drill_evidence
              |              +-- 1:N --> sa_drill_run
              |                             |
              |                             +-- 1:N --> sa_drill_run_action
              |
              +-- 1:N --> sa_conversation
              +-- 1:N --> sa_content_dependency
              +-- 1:N --> sa_capability_report
```

关键含义：

- `sa_user` 是身份，不是资源租户边界。
- `sa_project_member` 决定“当前用户是否能访问项目以及能做什么”。
- 知识版本和快照属于 Project。
- Attempt/Run/Assignment 同时记录 `project_id + user_id`：前者用于项目隔离，后者用于参与者追踪。
- 能力报告属于 Assignment，不是一个独立“AI 自画像”。

---

# 5. 表结构详细设计

## 5.1 `sa_user`

沿用原账号模型。

| 字段 | 类型 | NULL | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | 主键 |
| username | VARCHAR(32) | 否 | 唯一登录名 |
| password_hash | VARCHAR(255) | 否 | 密码哈希 |
| nickname | VARCHAR(64) | 否 | 昵称 |
| email | VARCHAR(128) | 是 | 邮箱 |
| avatar | VARCHAR(512) | 是 | 头像 URL |
| status | VARCHAR(16) | 否 | ACTIVE/DISABLED |
| create_time | DATETIME | 否 | 创建时间 |
| update_time | DATETIME | 否 | 修改时间 |

约束：`uk_user_username(username)`。

## 5.2 `sa_refresh_token`

沿用原 refreshToken 哈希模型。

主要字段：`user_id / session_id / token_hash / expires_at / revoked_at / create_time / last_used_at`。

`session_id`、`token_hash` 唯一；退出登录通过 accessToken `sid` 撤销。

## 5.3 `sa_project`

| 字段 | 类型 | NULL | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 项目 ID |
| name | VARCHAR(128) | 否 | - | 项目名 |
| code | VARCHAR(64) | 否 | - | 项目编码，全局唯一 |
| description | VARCHAR(1000) | 是 | NULL | 描述 |
| status | VARCHAR(16) | 否 | ACTIVE | ACTIVE/ARCHIVED |
| created_by | BIGINT UNSIGNED | 否 | - | 创建人 |
| create_time | DATETIME | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 否 | CURRENT_TIMESTAMP | 更新时间 |
| archived_at | DATETIME | 是 | NULL | 归档时间 |

索引：

- `uk_project_code(code)`
- `(status, update_time)`

归档不级联删除历史数据。

## 5.4 `sa_project_member`

| 字段 | 类型 | NULL | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| project_id | BIGINT UNSIGNED | 否 | - | 项目 |
| user_id | BIGINT UNSIGNED | 否 | - | 用户 |
| role | VARCHAR(16) | 否 | LEARNER | OWNER/MAINTAINER/LEARNER |
| status | VARCHAR(16) | 否 | ACTIVE | ACTIVE/REMOVED |
| joined_at | DATETIME | 否 | CURRENT_TIMESTAMP | 加入时间 |
| removed_at | DATETIME | 是 | NULL | 移除时间 |
| update_time | DATETIME | 否 | CURRENT_TIMESTAMP | 更新时间 |

约束：

- UNIQUE `(project_id, user_id)`
- `role` check
- `status` check

“至少保留一个 OWNER”由 Service 事务与行锁保证，不能只靠 CHECK。

## 5.5 `sa_audit_log`

append-only。

| 字段 | 类型 | NULL | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | 主键 |
| project_id | BIGINT UNSIGNED | 是 | 登录/系统级事件可为空 |
| actor_user_id | BIGINT UNSIGNED | 是 | 系统任务可为空 |
| action | VARCHAR(64) | 否 | 动作码 |
| resource_type | VARCHAR(32) | 否 | 资源类型 |
| resource_id | BIGINT UNSIGNED | 是 | 资源 ID |
| detail_json | JSON | 是 | 非敏感摘要 |
| request_id | VARCHAR(64) | 是 | 请求追踪 |
| create_time | DATETIME | 否 | 时间 |

典型 action：

```text
PROJECT_MEMBER_ADDED
PROJECT_MEMBER_ROLE_CHANGED
KNOWLEDGE_VERSION_REVIEWED
KNOWLEDGE_VERSION_PUBLISHED
ONBOARDING_PATH_PUBLISHED
ONBOARDING_ASSIGNED
ASSESSMENT_REVIEWED
PROJECT_ARCHIVED
```

禁止在 `detail_json` 写密码、Token、整份文档、完整 Prompt。

## 5.6 `sa_knowledge_base`

| 字段 | 类型 | NULL | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 知识库 ID |
| project_id | BIGINT UNSIGNED | 否 | - | 所属项目 |
| name | VARCHAR(128) | 否 | - | 名称 |
| description | VARCHAR(1000) | 是 | NULL | 描述 |
| status | VARCHAR(16) | 否 | ACTIVE | ACTIVE/ARCHIVED |
| current_snapshot_id | BIGINT UNSIGNED | 是 | NULL | 当前发布快照 |
| created_by | BIGINT UNSIGNED | 否 | - | 创建人 |
| create_time | DATETIME | 否 | CURRENT_TIMESTAMP | 创建 |
| update_time | DATETIME | 否 | CURRENT_TIMESTAMP | 更新 |

索引：`(project_id, status, update_time)`、`(project_id, name)`。

## 5.7 `sa_document`

逻辑文档，不保存二进制。

| 字段 | 类型 | NULL | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 文档 ID |
| project_id | BIGINT UNSIGNED | 否 | - | 冗余项目 ID |
| knowledge_base_id | BIGINT UNSIGNED | 否 | - | 知识库 |
| title | VARCHAR(255) | 否 | - | 逻辑标题 |
| status | VARCHAR(16) | 否 | ACTIVE | ACTIVE/ARCHIVED |
| current_published_version_id | BIGINT UNSIGNED | 是 | NULL | 当前正式版本 |
| created_by | BIGINT UNSIGNED | 否 | - | 创建人 |
| create_time | DATETIME | 否 | CURRENT_TIMESTAMP | 创建 |
| update_time | DATETIME | 否 | CURRENT_TIMESTAMP | 更新 |
| archived_at | DATETIME | 是 | NULL | 归档 |

逻辑文档不因上传新版本而创建新 document id。

## 5.8 `sa_document_version`

一个文件版本是不可变内容单元。

| 字段 | 类型 | NULL | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 版本 ID |
| document_id | BIGINT UNSIGNED | 否 | - | 逻辑文档 |
| project_id | BIGINT UNSIGNED | 否 | - | 项目 |
| knowledge_base_id | BIGINT UNSIGNED | 否 | - | 知识库 |
| version_no | INT UNSIGNED | 否 | - | 文档内版本号 |
| file_name | VARCHAR(255) | 否 | - | 原始文件名 |
| file_type | VARCHAR(16) | 否 | - | pdf/docx/txt/md |
| mime_type | VARCHAR(128) | 是 | NULL | MIME |
| file_size | BIGINT UNSIGNED | 否 | 0 | 字节 |
| content_hash | CHAR(64) | 否 | - | SHA-256 |
| storage_key | VARCHAR(512) | 否 | - | 对象存储随机键 |
| process_status | VARCHAR(16) | 否 | UPLOADED | UPLOADED/PARSING/CHUNKING/EMBEDDING/READY/FAILED |
| publish_status | VARCHAR(16) | 否 | DRAFT | DRAFT/IN_REVIEW/APPROVED/REJECTED/PUBLISHED/SUPERSEDED |
| progress | TINYINT UNSIGNED | 否 | 0 | 0～100 |
| chunk_count | INT UNSIGNED | 否 | 0 | 成功向量 Chunk 数 |
| current_task_id | VARCHAR(64) | 是 | NULL | 当前/最近任务 |
| change_note | VARCHAR(1000) | 是 | NULL | 变更说明 |
| error_message | VARCHAR(1000) | 是 | NULL | 处理错误 |
| reviewer_id | BIGINT UNSIGNED | 是 | NULL | 审核人 |
| review_comment | VARCHAR(1000) | 是 | NULL | 审核意见 |
| reviewed_at | DATETIME | 是 | NULL | 审核时间 |
| published_at | DATETIME | 是 | NULL | 发布时间 |
| create_time | DATETIME | 否 | CURRENT_TIMESTAMP | 创建 |
| update_time | DATETIME | 否 | CURRENT_TIMESTAMP | 更新 |

约束：

- UNIQUE `(document_id, version_no)`
- UNIQUE `(project_id, content_hash, document_id)` 可选；同一文档同内容可拒绝重复上传
- `progress <= 100`
- READY → `progress=100`
- FAILED → `error_message IS NOT NULL` 由业务层保证
- PUBLISHED/SUPERSEDED 必须 `process_status=READY`

## 5.9 `sa_knowledge_snapshot`

快照不可变。

| 字段 | 类型 | NULL | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | 主键 |
| project_id | BIGINT UNSIGNED | 否 | 项目 |
| knowledge_base_id | BIGINT UNSIGNED | 否 | 知识库 |
| snapshot_no | INT UNSIGNED | 否 | 递增序号 |
| reason | VARCHAR(32) | 否 | DOCUMENT_PUBLISH/MANUAL_IMPORT |
| created_by | BIGINT UNSIGNED | 否 | 发布者 |
| create_time | DATETIME | 否 | 时间 |

UNIQUE `(knowledge_base_id, snapshot_no)`。

快照创建后不 update；新知识状态通过新 snapshot 表达。

## 5.10 `sa_knowledge_snapshot_item`

| 字段 | 类型 | NULL | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | 主键 |
| snapshot_id | BIGINT UNSIGNED | 否 | 快照 |
| document_id | BIGINT UNSIGNED | 否 | 逻辑文档 |
| document_version_id | BIGINT UNSIGNED | 否 | 固定版本 |
| sort_order | INT UNSIGNED | 否 | 展示顺序 |
| create_time | DATETIME | 否 | 时间 |

约束：

- UNIQUE `(snapshot_id, document_id)`
- UNIQUE `(snapshot_id, document_version_id)`

## 5.11 `sa_content_dependency`

用于“文档更新 → 哪些测验/演练/路径需要复核”。

| 字段 | 类型 | NULL | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 主键 |
| project_id | BIGINT UNSIGNED | 否 | - | 项目 |
| source_document_id | BIGINT UNSIGNED | 否 | - | 来源逻辑文档 |
| source_version_id | BIGINT UNSIGNED | 否 | - | 内容创建时依据版本 |
| target_type | VARCHAR(16) | 否 | - | QUIZ/DRILL/PATH_TASK |
| target_id | BIGINT UNSIGNED | 否 | - | 目标资源 |
| review_status | VARCHAR(16) | 否 | CURRENT | CURRENT/NEEDS_REVIEW/CONFIRMED |
| detected_at | DATETIME | 是 | NULL | 新版本发布导致影响的时间 |
| confirmed_by | BIGINT UNSIGNED | 是 | NULL | 复核人 |
| confirmed_at | DATETIME | 是 | NULL | 复核时间 |
| note | VARCHAR(1000) | 是 | NULL | 备注 |
| create_time | DATETIME | 否 | CURRENT_TIMESTAMP | 创建 |

UNIQUE 可采用 `(source_version_id, target_type, target_id)`。

发布新文档版本时，不改 `source_version_id`；而是把依赖旧版本的记录标记 `NEEDS_REVIEW`。目标内容更新并确认后，新建/更新其依赖到新版本。

## 5.12 `sa_conversation`

| 字段 | 类型 | NULL | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | 主键 |
| project_id | BIGINT UNSIGNED | 否 | 所属项目 |
| user_id | BIGINT UNSIGNED | 否 | 会话创建者 |
| knowledge_base_id | BIGINT UNSIGNED | 是 | 可绑定知识库 |
| knowledge_snapshot_id | BIGINT UNSIGNED | 是 | 固定快照 |
| title | VARCHAR(128) | 是 | 标题 |
| mode | VARCHAR(16) | 否 | CHAT/RAG/AGENT |
| create_time | DATETIME | 否 | 创建 |
| update_time | DATETIME | 否 | 更新 |

会话仍属于用户本人，但其可访问知识由项目权限 + 快照决定。

## 5.13 `sa_message`

沿用 `conversation_id / user_id / role / content / status / trace_id / finish_reason / error_message / create_time`。

状态：`GENERATING/COMPLETED/CANCELLED/FAILED`。

## 5.14 `sa_message_citation`

| 字段 | 类型 | NULL | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | 主键 |
| message_id | BIGINT UNSIGNED | 否 | assistant 消息 |
| document_id | BIGINT UNSIGNED | 是 | 历史文档可归档 |
| document_version_id | BIGINT UNSIGNED | 是 | 具体来源版本 |
| knowledge_snapshot_id | BIGINT UNSIGNED | 是 | 来源快照 |
| chunk_id | VARCHAR(128) | 是 | Milvus chunk |
| file_name | VARCHAR(255) | 否 | 文件名快照 |
| page | INT UNSIGNED | 是 | 页码 |
| score | DECIMAL(8,6) | 是 | 相似度 |
| sort_order | INT UNSIGNED | 否 | 顺序 |
| create_time | DATETIME | 否 | 时间 |

历史引用应能回答“当时 AI 引用的是哪一版资料”。

## 5.15 `sa_message_feedback`

沿用 `message_id / user_id / rating(1/-1) / reason / create_time / update_time`。

## 5.16 `sa_quiz`

Quiz 是项目级可发布评估模板。

| 字段 | 类型 | NULL | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 测验 ID |
| project_id | BIGINT UNSIGNED | 否 | - | 项目 |
| knowledge_snapshot_id | BIGINT UNSIGNED | 否 | - | 来源快照 |
| revision_of_quiz_id | BIGINT UNSIGNED | 是 | NULL | 新版来源 |
| version_no | INT UNSIGNED | 否 | 1 | 内容版本 |
| title | VARCHAR(128) | 否 | - | 标题 |
| topic | VARCHAR(255) | 是 | NULL | 主题 |
| difficulty | VARCHAR(16) | 否 | - | EASY/MEDIUM/HARD |
| question_count | INT UNSIGNED | 否 | - | 题量 |
| types_json | JSON | 否 | - | 题型 |
| status | VARCHAR(16) | 否 | GENERATING | GENERATING/DRAFT/READY/PUBLISHED/FAILED/ARCHIVED |
| rubric_version | INT UNSIGNED | 否 | 1 | 评分标准版本 |
| rubric_json | JSON | 是 | NULL | 测验评分配置 |
| pass_score | DECIMAL(5,2) | 否 | 60 | 通过线 |
| current_task_id | VARCHAR(64) | 是 | NULL | 生成任务 |
| error_message | VARCHAR(1000) | 是 | NULL | 失败原因 |
| created_by | BIGINT UNSIGNED | 否 | - | 创建人 |
| published_at | DATETIME | 是 | NULL | 发布 |
| create_time | DATETIME | 否 | CURRENT_TIMESTAMP | 创建 |
| update_time | DATETIME | 否 | CURRENT_TIMESTAMP | 更新 |

PUBLISHED 后不再修改题目；修订创建新 quiz row。

## 5.17 `sa_quiz_question`

| 字段 | 类型 | NULL | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | 主键 |
| quiz_id | BIGINT UNSIGNED | 否 | 测验 |
| knowledge_key | VARCHAR(128) | 是 | Insight 聚合键 |
| type | VARCHAR(16) | 否 | SINGLE/MULTIPLE/JUDGE/SHORT |
| content | TEXT | 否 | 题干 |
| options_json | JSON | 是 | 选项 |
| correct_answer_json | JSON | 否 | 标准答案 |
| explanation | TEXT | 是 | 解析 |
| source_refs_json | JSON | 是 | documentVersion/page 等来源 |
| max_score | DECIMAL(5,2) | 否 | 本题满分 |
| sort_order | INT UNSIGNED | 否 | 顺序 |
| create_time | DATETIME | 否 | 时间 |

## 5.18 `sa_quiz_attempt`

| 字段 | 类型 | NULL | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | Attempt |
| project_id | BIGINT UNSIGNED | 否 | - | 项目 |
| quiz_id | BIGINT UNSIGNED | 否 | - | 测验模板 |
| user_id | BIGINT UNSIGNED | 否 | - | 作答人 |
| assignment_task_id | BIGINT UNSIGNED | 是 | NULL | 可关联路径任务 |
| status | VARCHAR(16) | 否 | IN_PROGRESS | IN_PROGRESS/SCORING/REVIEW_PENDING/COMPLETED/FAILED |
| rubric_version | INT UNSIGNED | 否 | - | 固定版本 |
| auto_score | DECIMAL(5,2) | 是 | NULL | 程序 + AI 建议汇总的自动候选分 |
| final_score | DECIMAL(5,2) | 是 | NULL | 最终分 |
| correct_count | INT UNSIGNED | 是 | NULL | 客观正确数 |
| review_required | TINYINT(1) | 否 | 0 | 是否需复核 |
| error_message | VARCHAR(1000) | 是 | NULL | 评分失败 |
| started_at | DATETIME | 否 | - | 开始 |
| submitted_at | DATETIME | 是 | NULL | 提交 |
| completed_at | DATETIME | 是 | NULL | 最终完成 |
| update_time | DATETIME | 否 | CURRENT_TIMESTAMP | 更新 |

一次 Attempt 只允许成功提交一次。

## 5.19 `sa_quiz_answer`

| 字段 | 类型 | NULL | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | 主键 |
| attempt_id | BIGINT UNSIGNED | 否 | Attempt |
| question_id | BIGINT UNSIGNED | 否 | 题目 |
| user_answer_json | JSON | 否 | 用户答案 |
| rule_score | DECIMAL(5,2) | 是 | 程序评分 |
| ai_suggested_score | DECIMAL(5,2) | 是 | AI 建议 |
| final_score | DECIMAL(5,2) | 是 | 人工复核后最终 |
| is_correct | TINYINT(1) | 是 | 客观题 |
| scoring_source | VARCHAR(16) | 否 | RULE/AI_SUGGESTION/REVIEW |
| ai_feedback | TEXT | 是 | AI 解释 |
| source_refs_json | JSON | 是 | 支撑来源 |
| create_time | DATETIME | 否 | 时间 |

UNIQUE `(attempt_id, question_id)`。

## 5.20 `sa_drill_scenario`

| 字段 | 类型 | NULL | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | 场景 ID |
| project_id | BIGINT UNSIGNED | 否 | 项目 |
| knowledge_snapshot_id | BIGINT UNSIGNED | 否 | 来源快照 |
| revision_of_scenario_id | BIGINT UNSIGNED | 是 | 修订来源 |
| version_no | INT UNSIGNED | 否 | 版本 |
| name | VARCHAR(128) | 否 | 名称 |
| description | VARCHAR(1000) | 是 | 描述 |
| severity | VARCHAR(16) | 是 | P1/P2/P3 等 |
| initial_symptom | TEXT | 否 | 初始现象 |
| status | VARCHAR(16) | 否 | DRAFT/PUBLISHED/ARCHIVED |
| rubric_version | INT UNSIGNED | 否 | rubric 版本 |
| rubric_json | JSON | 否 | 维度与满分 |
| pass_score | DECIMAL(5,2) | 否 | 通过线 |
| created_by | BIGINT UNSIGNED | 否 | 创建者 |
| published_at | DATETIME | 是 | 发布 |
| create_time | DATETIME | 否 | 创建 |
| update_time | DATETIME | 否 | 更新 |

PUBLISHED 后规则和证据不可原地修改。

## 5.21 `sa_drill_rule`

| 字段 | 类型 | NULL | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | 主键 |
| scenario_id | BIGINT UNSIGNED | 否 | 场景 |
| code | VARCHAR(64) | 否 | 场景内唯一动作码 |
| knowledge_key | VARCHAR(128) | 是 | Insight 聚合 |
| title | VARCHAR(128) | 否 | 管理端名称 |
| rule_type | VARCHAR(16) | 否 | REQUIRED/OPTIONAL/RISKY/VALIDATION |
| aliases_json | JSON | 是 | 规则匹配别名 |
| sequence_group | VARCHAR(64) | 是 | 顺序组 |
| min_order | INT UNSIGNED | 是 | 最早合理序位 |
| points | DECIMAL(6,2) | 否 | 可正可负 |
| sort_order | INT UNSIGNED | 否 | 排序 |
| create_time | DATETIME | 否 | 时间 |

UNIQUE `(scenario_id, code)`。

## 5.22 `sa_drill_evidence`

| 字段 | 类型 | NULL | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | 主键 |
| scenario_id | BIGINT UNSIGNED | 否 | 场景 |
| code | VARCHAR(64) | 否 | 场景内唯一 |
| evidence_type | VARCHAR(16) | 否 | LOG/METRIC/CONFIG/TRACE/HINT |
| title | VARCHAR(128) | 否 | 标题 |
| content | TEXT | 否 | 证据正文 |
| unlock_rule_code | VARCHAR(64) | 是 | 命中后开放 |
| sort_order | INT UNSIGNED | 否 | 顺序 |

## 5.23 `sa_drill_run`

| 字段 | 类型 | NULL | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | Run |
| project_id | BIGINT UNSIGNED | 否 | - | 项目 |
| scenario_id | BIGINT UNSIGNED | 否 | - | 场景 |
| user_id | BIGINT UNSIGNED | 否 | - | 参与者 |
| assignment_task_id | BIGINT UNSIGNED | 是 | NULL | 路径任务 |
| status | VARCHAR(16) | 否 | IN_PROGRESS | IN_PROGRESS/SCORING/REVIEW_PENDING/COMPLETED/CANCELLED |
| rubric_version | INT UNSIGNED | 否 | - | 固定评分标准 |
| program_score | DECIMAL(5,2) | 是 | NULL | 确定性分 |
| final_score | DECIMAL(5,2) | 是 | NULL | 最终分 |
| pass_candidate | TINYINT(1) | 是 | NULL | 自动候选 |
| conclusion_json | JSON | 是 | NULL | 根因/恢复/验证 |
| ai_explanation | TEXT | 是 | NULL | AI 解释 |
| started_at | DATETIME | 否 | - | 开始 |
| submitted_at | DATETIME | 是 | NULL | 提交 |
| completed_at | DATETIME | 是 | NULL | 完成 |
| update_time | DATETIME | 否 | CURRENT_TIMESTAMP | 更新 |

## 5.24 `sa_drill_run_action`

| 字段 | 类型 | NULL | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | 主键 |
| run_id | BIGINT UNSIGNED | 否 | Run |
| seq_no | INT UNSIGNED | 否 | 动作序号 |
| client_request_id | VARCHAR(36) | 否 | 幂等键 |
| action_text | VARCHAR(2000) | 否 | 原始输入 |
| normalized_action | VARCHAR(64) | 是 | 归一化动作 |
| matched_rule_id | BIGINT UNSIGNED | 是 | 命中规则 |
| match_source | VARCHAR(16) | 否 | RULE/AI/NONE |
| match_confidence | DECIMAL(5,4) | 是 | AI 归一化置信 |
| evidence_unlocked_json | JSON | 是 | 本动作开放证据 code |
| risk_flag | TINYINT(1) | 否 | 是否风险动作 |
| points_awarded | DECIMAL(6,2) | 否 | 本动作确定性分 |
| create_time | DATETIME | 否 | 时间 |

约束：

- UNIQUE `(run_id, client_request_id)`
- UNIQUE `(run_id, seq_no)`

重传同 `client_request_id` 返回原 action 结果。

## 5.25 `sa_assessment_review`

通用人工复核。

| 字段 | 类型 | NULL | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | 主键 |
| project_id | BIGINT UNSIGNED | 否 | 项目 |
| target_type | VARCHAR(24) | 否 | QUIZ_ATTEMPT/DRILL_RUN/ASSIGNMENT |
| target_id | BIGINT UNSIGNED | 否 | 目标 |
| reviewer_id | BIGINT UNSIGNED | 否 | OWNER/MAINTAINER |
| decision | VARCHAR(16) | 否 | PASS/NEEDS_WORK |
| score_override | DECIMAL(5,2) | 是 | 可选 |
| comment | VARCHAR(2000) | 是 | 意见 |
| create_time | DATETIME | 否 | 时间 |

历史 review 不删除；“当前决定”由目标资源状态反映。

## 5.26 `sa_onboarding_path`

| 字段 | 类型 | NULL | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 路径 |
| project_id | BIGINT UNSIGNED | 否 | - | 项目 |
| name | VARCHAR(128) | 否 | - | 名称 |
| target_role | VARCHAR(128) | 是 | NULL | 目标岗位/职责文本 |
| description | VARCHAR(1000) | 是 | NULL | 描述 |
| status | VARCHAR(16) | 否 | DRAFT | DRAFT/PUBLISHED/ARCHIVED |
| version_no | INT UNSIGNED | 否 | 0 | 每次发布递增 |
| knowledge_snapshot_id | BIGINT UNSIGNED | 否 | - | 固定来源快照 |
| created_by | BIGINT UNSIGNED | 否 | - | 创建人 |
| published_at | DATETIME | 是 | NULL | 发布 |
| create_time | DATETIME | 否 | CURRENT_TIMESTAMP | 创建 |
| update_time | DATETIME | 否 | CURRENT_TIMESTAMP | 更新 |

## 5.27 `sa_onboarding_path_task`

| 字段 | 类型 | NULL | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | 主键 |
| path_id | BIGINT UNSIGNED | 否 | 路径 |
| type | VARCHAR(16) | 否 | READ/QUIZ/DRILL/CHECKPOINT |
| title | VARCHAR(128) | 否 | 标题 |
| description | VARCHAR(1000) | 是 | 描述 |
| resource_type | VARCHAR(24) | 否 | DOCUMENT_VERSION/QUIZ/DRILL/NONE |
| resource_id | BIGINT UNSIGNED | 是 | 关联资源 |
| completion_rule | VARCHAR(16) | 否 | AUTO/MANUAL |
| estimated_minutes | INT UNSIGNED | 否 | 预计分钟 |
| required | TINYINT(1) | 否 | 是否必需 |
| sort_order | INT UNSIGNED | 否 | 顺序 |
| create_time | DATETIME | 否 | 时间 |
| update_time | DATETIME | 否 | 时间 |

## 5.28 `sa_onboarding_task_dependency`

| 字段 | 类型 | NULL | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | 主键 |
| path_id | BIGINT UNSIGNED | 否 | 路径 |
| task_id | BIGINT UNSIGNED | 否 | 后置任务 |
| prerequisite_task_id | BIGINT UNSIGNED | 否 | 前置任务 |

UNIQUE `(task_id, prerequisite_task_id)`。

依赖无环由发布前业务校验保证。

## 5.29 `sa_onboarding_assignment`

| 字段 | 类型 | NULL | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | Assignment |
| project_id | BIGINT UNSIGNED | 否 | - | 项目 |
| path_id | BIGINT UNSIGNED | 否 | - | 路径 |
| path_version | INT UNSIGNED | 否 | - | 分配时固定 |
| knowledge_snapshot_id | BIGINT UNSIGNED | 否 | - | 分配时固定 |
| assignee_user_id | BIGINT UNSIGNED | 否 | - | Learner |
| assigned_by | BIGINT UNSIGNED | 否 | - | 分配人 |
| status | VARCHAR(16) | 否 | ASSIGNED | ASSIGNED/IN_PROGRESS/REVIEW_PENDING/PASSED/NEEDS_WORK/CANCELLED |
| progress | DECIMAL(5,2) | 否 | 0 | 0～100 投影 |
| due_at | DATETIME | 是 | NULL | 截止 |
| started_at | DATETIME | 是 | NULL | 开始 |
| submitted_at | DATETIME | 是 | NULL | 提交复核 |
| completed_at | DATETIME | 是 | NULL | 最终完成 |
| create_time | DATETIME | 否 | CURRENT_TIMESTAMP | 创建 |
| update_time | DATETIME | 否 | CURRENT_TIMESTAMP | 更新 |

同一用户可以重复被分配同一路径不同批次，不设置 `(path_id,user_id)` 唯一。

## 5.30 `sa_onboarding_assignment_task`

Assignment 创建时从 path task 复制关键字段，形成执行快照。

| 字段 | 类型 | NULL | 说明 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | 主键 |
| assignment_id | BIGINT UNSIGNED | 否 | Assignment |
| source_path_task_id | BIGINT UNSIGNED | 是 | 原模板任务 |
| type | VARCHAR(16) | 否 | 固定任务类型 |
| title | VARCHAR(128) | 否 | 标题快照 |
| resource_type | VARCHAR(24) | 否 | 资源类型 |
| resource_id | BIGINT UNSIGNED | 是 | 固定资源 ID |
| prerequisite_task_ids_json | JSON | 是 | 依赖快照 |
| completion_rule | VARCHAR(16) | 否 | AUTO/MANUAL |
| required | TINYINT(1) | 否 | 是否必需 |
| sort_order | INT UNSIGNED | 否 | 顺序 |
| status | VARCHAR(16) | 否 | LOCKED/PENDING/IN_PROGRESS/COMPLETED/FAILED/SKIPPED |
| quiz_attempt_id | BIGINT UNSIGNED | 是 | 自动关联 |
| drill_run_id | BIGINT UNSIGNED | 是 | 自动关联 |
| completion_note | VARCHAR(1000) | 是 | 手工完成备注 |
| opened_at | DATETIME | 是 | 开始 |
| completed_at | DATETIME | 是 | 完成 |
| update_time | DATETIME | 否 | 更新 |

为什么复制而不是只引用 path task：路径以后可以发布新版本，但历史 Assignment 必须保持原任务语义。

## 5.31 `sa_capability_report`

| 字段 | 类型 | NULL | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 否 | AUTO_INCREMENT | 报告 |
| project_id | BIGINT UNSIGNED | 否 | - | 项目 |
| assignment_id | BIGINT UNSIGNED | 否 | - | 来源 Assignment |
| user_id | BIGINT UNSIGNED | 否 | - | 被评估人 |
| report_version | INT UNSIGNED | 否 | 1 | 报告版本 |
| status | VARCHAR(16) | 否 | PROCESSING | PROCESSING/READY/FAILED |
| knowledge_snapshot_id | BIGINT UNSIGNED | 否 | - | 依据知识 |
| path_version | INT UNSIGNED | 否 | - | 路径版本 |
| facts_json | JSON | 否 | - | Spring Boot 聚合的可回放事实 |
| dimensions_json | JSON | 是 | NULL | 维度分与证据 |
| ai_summary | TEXT | 是 | NULL | AI 摘要 |
| risks_json | JSON | 是 | NULL | 风险 |
| recommended_sources_json | JSON | 是 | NULL | 推荐已发布版本 |
| overall_decision | VARCHAR(16) | 是 | NULL | PASS/NEEDS_WORK |
| current_task_id | VARCHAR(64) | 是 | NULL | 异步任务 |
| model_name | VARCHAR(128) | 是 | NULL | 生成摘要模型 |
| error_message | VARCHAR(1000) | 是 | NULL | 失败 |
| generated_at | DATETIME | 是 | NULL | 完成 |
| create_time | DATETIME | 否 | CURRENT_TIMESTAMP | 创建 |
| update_time | DATETIME | 否 | CURRENT_TIMESTAMP | 更新 |

UNIQUE `(assignment_id, report_version)`。

`facts_json` 不是让 AI 自由生成，而是 Spring Boot 在生成前把固定评估事实快照化，保证报告可回放。

## 5.32 `sa_file_asset`

沿用通用文件模型：

`id / user_id / type(AVATAR/IMAGE) / original_name / storage_key / url / mime_type / file_size / create_time`。

知识文档禁止使用本表绕过 `sa_document_version`。

---

# 6. Milvus metadata 设计

V3 向量 metadata 至少包括：

| 字段 | 说明 |
| --- | --- |
| `chunk_id` | 文档版本内 Chunk ID |
| `project_id` | 项目隔离 |
| `knowledge_base_id` | 知识库 |
| `document_id` | 逻辑文档 |
| `document_version_id` | 不可变版本 |
| `file_name` | 文件名 |
| `page` | 页码 |
| `chunk_index` | 顺序 |
| `content_hash` | 内容哈希 |
| `embedding_model` | 模型 |
| `content` | Chunk 文本 |
| `embedding` | 向量 |
| `metadata` | 扩展 JSON |

正式 RAG 检索必须包含：

```text
project_id = 当前项目
AND knowledge_base_id = 当前知识库
AND document_version_id IN 当前 KnowledgeSnapshot 的版本集合
```

V3 不再以 `user_id` 作为共享知识向量隔离条件。

---

# 7. API → 数据表映射

| API 模块 | 主要表 |
| --- | --- |
| 1 Auth | `sa_user`, `sa_refresh_token` |
| 2 Project | `sa_project`, `sa_project_member`, `sa_audit_log` |
| 3 Knowledge | `sa_knowledge_base`, `sa_document`, `sa_document_version`, `sa_knowledge_snapshot`, `sa_knowledge_snapshot_item`, `sa_content_dependency` |
| 4 Onboarding | `sa_onboarding_path`, `sa_onboarding_path_task`, `sa_onboarding_task_dependency`, `sa_onboarding_assignment`, `sa_onboarding_assignment_task` |
| 5A Quiz | `sa_quiz`, `sa_quiz_question`, `sa_quiz_attempt`, `sa_quiz_answer`, `sa_assessment_review` |
| 5B Drill | `sa_drill_scenario`, `sa_drill_rule`, `sa_drill_evidence`, `sa_drill_run`, `sa_drill_run_action`, `sa_assessment_review` |
| 6 Assistant | `sa_conversation`, `sa_message`, `sa_message_citation`, `sa_message_feedback` |
| 7 Insight | 评估/Assignment 原始事实 + `sa_content_dependency` + `sa_capability_report` |
| 8 Files | `sa_file_asset` |
| 9 Internal AI | 业务状态仍写上述 MySQL 表；FastAPI 不直接写 |

---

# 8. 关键权限查询

## 8.1 项目成员校验

任何 Project 资源请求都不能只按资源 ID 查询后直接返回。

推荐先得到项目 ID，再校验：

```sql
SELECT role
FROM sa_project_member
WHERE project_id = #{projectId}
  AND user_id = #{currentUserId}
  AND status = 'ACTIVE';
```

没有记录 → 403。

## 8.2 二级资源归属

例如 documentVersion：

```sql
SELECT dv.*
FROM sa_document_version dv
JOIN sa_project_member pm
  ON pm.project_id = dv.project_id
 AND pm.user_id = #{currentUserId}
 AND pm.status = 'ACTIVE'
WHERE dv.id = #{versionId};
```

角色要求在 Service 层继续判断。

## 8.3 Learner 自有执行记录

LEARNER 查询 Attempt/Run/Assignment 时还要限制：

```text
record.user_id / assignee_user_id == currentUserId
```

OWNER/MAINTAINER 可以查看同项目团队结果。

---

# 9. 关键事务边界

## 9.1 创建 Project

一个事务内：

1. insert `sa_project`
2. insert creator `sa_project_member(role=OWNER)`
3. insert audit

项目不能出现“创建成功但没有 OWNER”。

## 9.2 修改成员角色

事务 + 行锁：

1. 锁定目标成员；
2. 若从 OWNER 降级/移除，统计当前 ACTIVE OWNER；
3. 最后一个 OWNER 禁止修改；
4. 更新角色；
5. 写 audit。

## 9.3 发布文档版本

这是 Knowledge 最关键事务。

```text
lock document + knowledge_base
  ↓
verify process_status=READY
verify publish_status=APPROVED
  ↓
previous current published version -> SUPERSEDED
current version -> PUBLISHED
document.current_published_version_id = current version
  ↓
create knowledge_snapshot
copy all ACTIVE document.current_published_version_id -> snapshot_item
knowledge_base.current_snapshot_id = snapshot
  ↓
mark dependencies on superseded version -> NEEDS_REVIEW
  ↓
audit
COMMIT
```

Milvus 向量必须在进入 READY 前已经 flush 成功，因此发布事务不再调用 Milvus。

## 9.4 创建 Assignment

一个事务：

1. 校验 path=PUBLISHED；
2. 校验 learner 为 ACTIVE project member；
3. 锁定 path version/snapshot；
4. 创建 assignment；
5. 将 path tasks + dependency ids 复制为 assignment tasks；
6. 根据依赖计算初始 `LOCKED/PENDING`；
7. 写 audit。

路径以后改变不会影响已创建 Assignment。

## 9.5 提交 Quiz Attempt

客观题先在服务内确定性评分。SHORT 需要 AI 建议时：

1. 先完成所有外部 AI 评分请求；
2. 任一外部评分失败，不写半套答案；
3. 开启 MySQL 事务；
4. 锁 Attempt，确认 `IN_PROGRESS`；
5. 写全部 answers；
6. 计算 `auto_score`；
7. 有 SHORT/策略要求 → `REVIEW_PENDING`，否则 `COMPLETED`；
8. 自动完成关联 Assignment Task（仅 COMPLETED 且满足通过条件）；
9. COMMIT。

## 9.6 提交 Drill Action

单条动作事务：

1. 校验 Run=IN_PROGRESS；
2. 先查询 `(run_id, client_request_id)`；
3. 已存在则返回原结果；
4. 根据 published rule 计算 `matched_rule_id / points / risk_flag`；
5. 写 action；
6. 返回该 action 解锁证据；
7. 不在动作阶段暴露完整 rubric。

AI 归一化可以发生在事务前，但 AI 只能匹配候选 rule，不能创建新 rule 或新分值。

## 9.7 提交 Drill Run

1. 锁 Run；
2. 读取固定 scenario/rubric；
3. 聚合动作与证据；
4. 程序计算 `program_score` 与 breakdown；
5. 保存 conclusion；
6. AI 解释失败不改变程序事实；可保存空解释并记录告警；
7. 需要人工复核 → REVIEW_PENDING；
8. 否则 COMPLETED；
9. 满足路径规则时更新 Assignment Task。

## 9.8 Assignment 复核

1. 锁 assignment；
2. 校验所有 required task 完成；
3. 插入 `sa_assessment_review(target_type=ASSIGNMENT)`；
4. 更新 assignment → PASSED/NEEDS_WORK；
5. audit；
6. 之后可生成/重生 capability report。

## 9.9 能力报告生成

Spring Boot 先生成 `facts_json`，再调用 AI。

最终写入时：

```sql
UPDATE sa_capability_report
SET status = 'READY', ...
WHERE id = #{id}
  AND current_task_id = #{taskId}
  AND status = 'PROCESSING';
```

受超时扫描或新任务影响的旧结果不能覆盖当前状态。

---

# 10. Insight 统计来源

V3 不再维护一个难以解释的“mastery 公式”。

## 10.1 知识薄弱点

原始事实：

- `sa_quiz_answer`
- `sa_quiz_question.knowledge_key`
- `sa_drill_run_action`
- `sa_drill_rule.knowledge_key`
- `sa_drill_run.program_score`
- `sa_assessment_review`

可以按 `project_id + knowledge_key` 聚合：

```text
参与人数
客观题错误率
SHORT 低分率
Required rule 漏做率
Risky rule 命中率
演练失败率
```

Insight 只是统计投影；出现不一致时从原始事实重算。

## 10.2 内容影响

事实来源是 `sa_content_dependency.review_status`。

新文档版本发布后：

```text
old source_version dependency
  -> NEEDS_REVIEW
```

管理员更新 Quiz/Drill/Path 内容并确认后：

```text
old dependency -> CONFIRMED
new dependency -> CURRENT
```

---

# 11. 目标 DDL 核心示例

> 本节是目标模型 SQL 基线，不等同于最终生产迁移脚本。编码阶段应把 V3 变更拆成新的向前 migration，不修改历史 migration。

```sql
CREATE TABLE sa_project (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    code VARCHAR(64) NOT NULL,
    description VARCHAR(1000) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT UNSIGNED NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    archived_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_code (code),
    KEY idx_project_status_time (status, update_time),
    CONSTRAINT chk_project_status CHECK (status IN ('ACTIVE','ARCHIVED')),
    CONSTRAINT fk_project_creator FOREIGN KEY (created_by) REFERENCES sa_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_project_member (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    role VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    removed_at DATETIME NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_member (project_id, user_id),
    KEY idx_member_user_status (user_id, status),
    CONSTRAINT chk_member_role CHECK (role IN ('OWNER','MAINTAINER','LEARNER')),
    CONSTRAINT chk_member_status CHECK (status IN ('ACTIVE','REMOVED')),
    CONSTRAINT fk_member_project FOREIGN KEY (project_id) REFERENCES sa_project(id),
    CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES sa_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_audit_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NULL,
    actor_user_id BIGINT UNSIGNED NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id BIGINT UNSIGNED NULL,
    detail_json JSON NULL,
    request_id VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_project_time (project_id, create_time),
    KEY idx_audit_resource (resource_type, resource_id),
    KEY idx_audit_actor_time (actor_user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_knowledge_snapshot (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    knowledge_base_id BIGINT UNSIGNED NOT NULL,
    snapshot_no INT UNSIGNED NOT NULL,
    reason VARCHAR(32) NOT NULL,
    created_by BIGINT UNSIGNED NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_snapshot_no (knowledge_base_id, snapshot_no),
    KEY idx_snapshot_project_time (project_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_knowledge_snapshot_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    snapshot_id BIGINT UNSIGNED NOT NULL,
    document_id BIGINT UNSIGNED NOT NULL,
    document_version_id BIGINT UNSIGNED NOT NULL,
    sort_order INT UNSIGNED NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_snapshot_document (snapshot_id, document_id),
    UNIQUE KEY uk_snapshot_version (snapshot_id, document_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sa_drill_run_action (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    run_id BIGINT UNSIGNED NOT NULL,
    seq_no INT UNSIGNED NOT NULL,
    client_request_id VARCHAR(36) NOT NULL,
    action_text VARCHAR(2000) NOT NULL,
    normalized_action VARCHAR(64) NULL,
    matched_rule_id BIGINT UNSIGNED NULL,
    match_source VARCHAR(16) NOT NULL,
    match_confidence DECIMAL(5,4) NULL,
    evidence_unlocked_json JSON NULL,
    risk_flag TINYINT(1) NOT NULL DEFAULT 0,
    points_awarded DECIMAL(6,2) NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_run_client_request (run_id, client_request_id),
    UNIQUE KEY uk_run_seq (run_id, seq_no),
    CONSTRAINT chk_action_match_source CHECK (match_source IN ('RULE','AI','NONE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

完整 migration 在真正编码时根据现有 V1～V3 schema 生成，不应直接拿本节覆盖线上库。

---

# 12. 异步任务状态与恢复

V3 需要恢复收敛的异步业务状态：

| 资源 | 执行态 | 终态 |
| --- | --- | --- |
| DocumentVersion | UPLOADED/PARSING/CHUNKING/EMBEDDING | READY/FAILED |
| Quiz | GENERATING | DRAFT/READY/FAILED |
| CapabilityReport | PROCESSING | READY/FAILED |

共同字段：

```text
current_task_id
update_time
error_message
```

原则：

- 超时扫描只把长期停留执行态的记录收敛为 FAILED；
- 不自动重放 LLM；
- 新重试生成新 taskId；
- 迟到结果必须 `WHERE current_task_id=? AND status=<执行态>` 才能提交；
- 子记录与 READY 状态同事务提交，状态竞争失败必须回滚子记录。

---

# 13. V2 → V3 数据迁移边界

## 13.1 复用

直接复用或扩展：

- `sa_user`
- `sa_refresh_token`
- `sa_knowledge_base`
- `sa_document`（语义升级为逻辑文档）
- `sa_conversation`
- `sa_message`
- `sa_message_citation`
- `sa_message_feedback`
- `sa_quiz`
- `sa_quiz_question`
- `sa_file_asset`

## 13.2 旧表停止作为 V3 新事实来源

以下旧模块不属于 V3 主线：

```text
sa_wrong_question
sa_learning_record
sa_topic_progress
sa_study_plan
sa_study_plan_task
sa_long_term_memory
```

迁移初期建议：

1. 不在第一批 migration 中 DROP；
2. 新 V3 Service 停止写入；
3. 如需保留旧 demo 数据，可只读展示或离线导出；
4. 等 V3 业务稳定后，再单独清理 legacy 表。

## 13.3 推荐迁移拆分

```text
V4__project_workspace.sql
  project / member / audit
  existing personal resources add project_id

V5__knowledge_versioning.sql
  document_version / snapshot / snapshot_item / dependency
  existing document rows迁移为 version_no=1

V6__onboarding_assessment.sql
  quiz attempt model
  drill
  onboarding path / assignment / review

V7__capability_report_and_legacy_freeze.sql
  capability report
  stop old learning-domain writes
  indexes / constraints / cleanup flags
```

不要把一次巨大重构写成一个不可审查的 migration。

---

# 14. 索引策略

核心索引至少包括：

```text
sa_project_member(user_id, status)
sa_project_member(project_id, role, status)

sa_document(project_id, knowledge_base_id, status)
sa_document_version(document_id, version_no)
sa_document_version(project_id, process_status, update_time)
sa_document_version(current_task_id)

sa_knowledge_snapshot(knowledge_base_id, snapshot_no)
sa_knowledge_snapshot_item(snapshot_id, document_version_id)

sa_content_dependency(project_id, review_status, target_type)
sa_content_dependency(source_document_id, source_version_id)

sa_quiz(project_id, status, update_time)
sa_quiz_attempt(project_id, user_id, status, started_at)
sa_quiz_answer(attempt_id, question_id)

sa_drill_run(project_id, user_id, status, started_at)
sa_drill_run_action(run_id, seq_no)

sa_onboarding_assignment(project_id, status, due_at)
sa_onboarding_assignment(project_id, assignee_user_id, status)
sa_onboarding_assignment_task(assignment_id, sort_order)

sa_capability_report(assignment_id, report_version)
sa_capability_report(project_id, user_id, status)
```

异步恢复扫描索引必须与实际 `WHERE status IN (...) AND update_time < ?` 查询一致，再通过 EXPLAIN 验证。

---

# 15. 数据一致性规则

1. `project_id` 冗余到二级事实表是为了权限过滤、统计和防止跨项目错误关联。
2. 资源 ID 查询仍必须校验项目成员，不能因为有 `project_id` 就省略权限逻辑。
3. KnowledgeSnapshot 一旦创建不可更新 item。
4. Assignment 一旦创建，不受后续 Path 编辑影响。
5. Published Quiz/Drill 不原地修改。
6. AI suggested score 永远不覆盖 rule score。
7. 人工 `score_override` 不删除自动分。
8. 报告 `facts_json` 固定当次生成依据。
9. `sa_content_dependency` 是知识更新闭环事实，不是临时缓存。
10. Insight 聚合可重算，不作为原始评分事实。
11. Milvus READY 不等于 Knowledge PUBLISHED；二者属于不同状态维度。
12. 项目归档不删除历史数据。
13. Published documentVersion 被 snapshot 引用时禁止物理删除。
14. 删除/归档跨 MySQL、Object Storage、Milvus 时不能宣称存在分布式 ACID；必须使用幂等清理和补偿记录。

---

# 16. Codex 实现约束

- Entity / DTO / VO 分离。
- Mapper 不处理项目权限。
- Service 每个项目资源入口先做 member + role 校验。
- FastAPI 不连接业务 MySQL。
- AI Tool Bridge 重新校验 Project 权限。
- 不用前端传来的 `userId` 决定身份。
- 不在一个字段中混淆 `process_status / publish_status / assignment status / assessment status`。
- 不把 `facts_json` 变成所有结构化表都不设计的借口；JSON 只用于“已稳定结构之外的快照/扩展数据”。
- 不允许 AI 输出直接 update 最终分数或最终 PASS。
- 所有 taskId 结果提交都带状态条件。
- 所有幂等键都应有数据库 UNIQUE 约束兜底。
- 每个 migration 必须可审查、可回滚或有明确前滚策略。
