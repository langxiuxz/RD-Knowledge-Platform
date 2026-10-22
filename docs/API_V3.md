StudyAgent  
接口文档

V3.0 · 研发知识交接与能力验证平台

| 文档项 | 内容 |
| --- | --- |
| 项目名称 | StudyAgent |
| 版本 | V3.0 |
| 产品定位 | 研发知识运营与能力验证平台 |
| 前端 | Vue 3 + Vite + Element Plus + Axios |
| 业务后端 | Spring Boot + MyBatis + MySQL + Redis |
| AI 服务 | FastAPI + LangChain + LangGraph |
| 向量存储 | Milvus（通过 VectorStoreAdapter） |
| 认证 | JWT + Refresh Token |
| 项目角色 | OWNER / MAINTAINER / LEARNER |
| 接口风格 | RESTful + JSON；AI 流式响应使用 SSE |

# 修订记录

V3.0 是一次业务域重构。系统不再以“个人 AI 学习工具”为主线，而以“一个研发负责人让新成员具备接手某个服务的能力，并能够查看过程、结果和风险”为核心用户故事。

| 版本 | 主要修订 |
| --- | --- |
| V3.0 | 新增 Project 协作空间、成员角色、知识版本/发布快照、接手路径、故障演练、能力报告、内容影响分析；Agent 调整为横向能力；移除旧学习计划/错题本/学习记录等主线接口 |
| V2.0 | 个人知识库、AI 会话、测验、学习计划、Dashboard、异步任务与内部 AI 接口 |
| V1.0 | StudyAgent 首版接口设计 |

> V3.0 是后续编码的目标契约。旧 V2 接口是否临时保留由迁移实现决定；除本文件明确声明的兼容项外，不应默认认为 V2 与 V3 对外契约完全兼容。

# 0. 接口约定

文档采用“模块 → 接口 → 基本信息 → 请求参数 → 响应数据 → 备注说明”的结构。浏览器只访问 Spring Boot 对外接口；FastAPI/LangGraph 为内部 AI 服务。Spring Boot 是用户身份、项目权限、状态机、事务和业务事实的唯一权威。

## 0.1 通用约定

| 项目 | 约定 |
| --- | --- |
| 对外 API 前缀 | `/api` |
| 内部接口前缀 | `/internal` |
| 默认 Content-Type | `application/json` |
| 时间格式 | `yyyy-MM-dd'T'HH:mm:ss` |
| 分页 | `page` 从 1 开始；`pageSize` 默认 10；返回 `total + rows` |
| 认证头 | `Authorization: Bearer <JWT>` |
| 内部服务认证 | `X-Internal-Token: <configured token>` |
| 成功业务码 | `code=1` |
| SSE | `Content-Type: text/event-stream` |
| Token 续期 | accessToken 短时有效；refreshToken 仅用于 `/api/auth/refresh` |
| 幂等性 | 删除、回调、任务状态收敛、演练动作重传必须可安全重复 |
| 项目归属 | 业务资源通过 `projectId` 或父资源追溯到项目 |
| 成员权限 | 所有项目资源操作必须校验当前用户是 ACTIVE 项目成员并满足角色权限 |
| 发布语义 | 已发布知识版本、知识快照、已开始的评估结果不得被原地覆盖 |
| 异步任务 | 文档向量化、AI 测验生成、能力报告生成可以返回 PROCESSING |
| 审计 | 成员变更、知识发布、路径发布、评估复核等关键操作写审计日志 |

统一普通响应：

```json
{
  "code": 1,
  "msg": "success",
  "data": {}
}
```

普通接口失败时同时返回合理 HTTP 状态与稳定业务码。SSE 建连成功后发生的错误使用 `event: error`，不再切换为普通 JSON。

## 0.2 HTTP 状态码

| HTTP | 说明 |
| --- | --- |
| 200 | 成功 |
| 201 | 创建成功 |
| 202 | 已接受异步任务 |
| 400 | 参数错误 |
| 401 | 未登录 / Token 无效 |
| 403 | 无项目权限或角色不足 |
| 404 | 资源不存在 |
| 409 | 状态冲突、重复提交或并发冲突 |
| 413 | 文件过大 |
| 422 | 业务校验失败 |
| 429 | 请求过频 |
| 500 | 服务异常 |
| 503 | AI 服务或依赖不可用 |

## 0.3 Project 角色权限

V3 不引入“组织 → 团队 → 项目”多级租户树。`Project` 本身就是一个研发团队围绕某个服务/项目协作的空间。

| 能力 | OWNER | MAINTAINER | LEARNER |
| --- | --- | --- | --- |
| 修改项目设置 | 是 | 否 | 否 |
| 管理成员与角色 | 是 | 否 | 否 |
| 查看项目知识 | 是 | 是 | 是 |
| 创建/修改知识草稿 | 是 | 是 | 否 |
| 审核/发布知识 | 是 | 是 | 否 |
| 创建/发布接手路径 | 是 | 是 | 否 |
| 分配接手路径 | 是 | 是 | 否 |
| 创建/发布测验与演练 | 是 | 是 | 否 |
| 执行自己的测验/演练 | 是 | 是 | 是 |
| 复核评估结果 | 是 | 是 | 否 |
| 查看团队级 Insight | 是 | 是 | 否 |
| 查看自己的能力报告 | 是 | 是 | 是 |

OWNER 不能删除项目最后一个 OWNER；角色变更必须写审计日志。

## 0.4 知识版本与快照

- `Document` 是逻辑文档。
- `DocumentVersion` 是不可变文件内容版本。
- 文档版本先完成解析/切分/Embedding，再经过审核与发布。
- `KnowledgeSnapshot` 是某个知识库在某一时刻“所有有效已发布文档版本”的不可变集合。
- Onboarding Path、Quiz、Drill Scenario 和运行中的 Assignment 必须绑定明确的知识快照或其来源版本。
- Learner 的正式 RAG 只允许检索已发布快照中的文档版本；草稿仅允许 OWNER/MAINTAINER 通过预览能力访问。
- 新版本发布后，历史 Assignment 不自动切换快照，避免“同一训练过程依据变化”。

## 0.5 评估结果三层结构

1. **程序确定性结果**：客观题、规则命中、关键动作、风险动作、证据查看、恢复验证等。
2. **结构化评分**：按已发布 rubric 的维度和分值计算，并记录 `rubricVersion`。
3. **AI 辅助解释**：解释遗漏、证据不足、风险点和推荐资料；AI 不得静默覆盖程序事实。
4. **人工复核**：OWNER/MAINTAINER 可给出最终判定或分数覆盖；自动结果和人工结果同时保留。

---

# 1. 用户与认证

V3 沿用 V2 的 JWT + Refresh Token 机制。用户账号不等同于项目权限；登录后仍需通过 `sa_project_member` 校验项目成员身份。

## 1.1 用户注册

### 1.1.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/auth/register` |
| 请求方式 | POST |
| 接口描述 | 创建用户账号。 |

### 1.1.2 请求参数

| 参数名 | 类型 | 必须 | 备注 |
| --- | --- | --- | --- |
| username | string | 是 | 4～32 位，唯一 |
| password | string | 是 | 8～64 位 |
| nickname | string | 是 | 昵称 |
| email | string | 否 | 邮箱 |

```json
{
  "username": "lin",
  "password": "Study@123456",
  "nickname": "林同学",
  "email": "lin@example.com"
}
```

### 1.1.3 响应数据

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "id": 21,
    "username": "lin"
  }
}
```

## 1.2 用户登录

### 1.2.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/auth/login` |
| 请求方式 | POST |
| 接口描述 | 校验账号密码并签发 accessToken 与 refreshToken。 |

### 1.2.2 请求参数

| 参数名 | 类型 | 必须 | 备注 |
| --- | --- | --- | --- |
| username | string | 是 | 用户名 |
| password | string | 是 | 密码 |

### 1.2.3 响应数据

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "user": {
      "id": 21,
      "username": "lin",
      "nickname": "林同学"
    },
    "token": "eyJhbGciOiJIUzI1NiJ9.xxx",
    "expiresIn": 7200,
    "refreshToken": "rt_xxx",
    "refreshExpiresIn": 604800
  }
}
```

## 1.3 退出登录

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/auth/logout` |
| 请求方式 | POST |
| 接口描述 | 撤销当前 accessToken `sid` 对应的 refreshToken 会话。 |

成功返回 `data=null`。重复退出保持幂等。

## 1.4 获取当前用户

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/users/me` |
| 请求方式 | GET |
| 接口描述 | 查询当前用户资料。 |

响应字段：`id / username / nickname / avatar / email / createTime`。

## 1.5 修改个人资料

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/users/me` |
| 请求方式 | PUT |
| 接口描述 | 修改昵称、头像和邮箱。 |

请求字段：`nickname / avatar / email`，均可选。

## 1.6 修改密码

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/users/me/password` |
| 请求方式 | PUT |
| 接口描述 | 校验旧密码后修改密码。 |

```json
{
  "oldPassword": "Study@123456",
  "newPassword": "Study@654321"
}
```

## 1.7 刷新访问令牌

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/auth/refresh` |
| 请求方式 | POST |
| 接口描述 | 使用有效 refreshToken 获取新的 accessToken。 |

```json
{
  "refreshToken": "rt_xxx"
}
```

失效、撤销或过期返回 401。

---

# 2. Project 协作空间

Project 是 V3 的顶层业务边界。知识、接手路径、测验、演练、会话、报告和 Insight 均归属于 Project。

## 2.1 项目列表

### 2.1.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects` |
| 请求方式 | GET |
| 接口描述 | 查询当前用户参与的项目空间。 |

### 2.1.2 请求参数

| 参数名 | 类型 | 必须 | 备注 |
| --- | --- | --- | --- |
| keyword | string | 否 | 名称/编码模糊查询 |
| status | string | 否 | ACTIVE/ARCHIVED |
| role | string | 否 | OWNER/MAINTAINER/LEARNER |
| page | number | 是 | 页码 |
| pageSize | number | 是 | 每页条数 |

### 2.1.3 响应数据

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "total": 1,
    "rows": [
      {
        "id": 1001,
        "name": "Order Service",
        "code": "order-service",
        "role": "LEARNER",
        "status": "ACTIVE",
        "memberCount": 8,
        "updatedAt": "2026-10-20T10:00:00"
      }
    ]
  }
}
```

## 2.2 创建项目

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects` |
| 请求方式 | POST |
| 接口描述 | 创建项目空间；创建人自动成为 OWNER。 |

请求：

```json
{
  "name": "Order Service",
  "code": "order-service",
  "description": "订单域服务研发知识与交接空间"
}
```

响应 `data.id` 为项目 ID。

## 2.3 项目详情

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects/{id}` |
| 请求方式 | GET |
| 接口描述 | 查询项目基本信息与当前用户角色。 |

响应字段至少包括：`id / name / code / description / status / myRole / memberCount / createTime / updateTime`。

## 2.4 修改项目

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects/{id}` |
| 请求方式 | PUT |
| 接口描述 | 修改项目名称、编码或描述。仅 OWNER。 |

项目编码修改必须校验唯一性并写审计。

## 2.5 归档项目

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects/{id}/archive` |
| 请求方式 | POST |
| 接口描述 | 将项目置为 ARCHIVED；不物理删除历史训练、评估和审计数据。 |

归档后禁止新建知识版本、路径、评估和会话；历史记录保持可读。

## 2.6 项目成员列表

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects/{id}/members` |
| 请求方式 | GET |
| 接口描述 | 查询项目成员。 |

查询参数：`keyword / role / page / pageSize`。

响应行至少包括：`userId / username / nickname / role / status / joinedAt`。

## 2.7 添加项目成员

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects/{id}/members` |
| 请求方式 | POST |
| 接口描述 | 将已注册用户加入项目。仅 OWNER。 |

```json
{
  "username": "lin",
  "role": "LEARNER"
}
```

同一用户重复添加返回 409。

## 2.8 修改成员角色

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects/{projectId}/members/{userId}` |
| 请求方式 | PUT |
| 接口描述 | 修改成员角色。仅 OWNER。 |

```json
{
  "role": "MAINTAINER"
}
```

禁止把项目最后一个 OWNER 降级。

## 2.9 移除成员

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects/{projectId}/members/{userId}` |
| 请求方式 | DELETE |
| 接口描述 | 移除成员。仅 OWNER。 |

移除成员不会删除其历史答题、演练、报告和审计记录；后续资源访问失效。

## 2.10 项目审计日志

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects/{id}/audit-logs` |
| 请求方式 | GET |
| 接口描述 | 查询项目关键操作审计。OWNER/MAINTAINER 可见。 |

查询参数：`action / resourceType / userId / startTime / endTime / page / pageSize`。

响应行示例：

```json
{
  "id": 8801,
  "userId": 7,
  "action": "KNOWLEDGE_VERSION_PUBLISHED",
  "resourceType": "DOCUMENT_VERSION",
  "resourceId": 30018,
  "detail": {
    "snapshotId": 901
  },
  "createTime": "2026-10-20T11:30:00"
}
```

---

# 3. Knowledge 知识资产管理

## 3.1 知识库列表

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects/{projectId}/knowledge-bases` |
| 请求方式 | GET |
| 接口描述 | 查询项目知识库。 |

查询参数：`name / status / page / pageSize`。

响应行：`id / name / description / status / documentCount / currentSnapshotId / currentSnapshotNo / updateTime`。

## 3.2 创建知识库

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects/{projectId}/knowledge-bases` |
| 请求方式 | POST |
| 接口描述 | 创建项目知识库。OWNER/MAINTAINER。 |

```json
{
  "name": "Order Service Runbook",
  "description": "架构、接口、数据库、告警与故障排查资料"
}
```

## 3.3 知识库详情

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/knowledge-bases/{id}` |
| 请求方式 | GET |
| 接口描述 | 查询知识库详情与当前发布快照。 |

## 3.4 修改知识库

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/knowledge-bases/{id}` |
| 请求方式 | PUT |
| 接口描述 | 修改名称和描述。OWNER/MAINTAINER。 |

## 3.5 归档知识库

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/knowledge-bases/{id}/archive` |
| 请求方式 | POST |
| 接口描述 | 归档知识库；历史快照和历史引用保留。 |

归档知识库不能再创建新文档版本，也不能被新建路径/评估选择。

## 3.6 文档列表

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/knowledge-bases/{id}/documents` |
| 请求方式 | GET |
| 接口描述 | 分页查询逻辑文档。 |

查询参数：`keyword / status / page / pageSize`。

响应行示例：

```json
{
  "id": 2001,
  "title": "订单超时排查手册",
  "status": "ACTIVE",
  "currentPublishedVersionId": 30017,
  "currentVersionNo": 4,
  "latestVersionProcessStatus": "READY",
  "latestVersionPublishStatus": "PUBLISHED",
  "updateTime": "2026-10-20T09:00:00"
}
```

## 3.7 上传首个文档版本

### 3.7.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/knowledge-bases/{id}/documents` |
| 请求方式 | POST |
| Content-Type | `multipart/form-data` |
| 接口描述 | 创建逻辑文档并上传首个不可变文件版本，异步解析和向量化。 |

### 3.7.2 请求参数

| 参数名 | 类型 | 必须 | 备注 |
| --- | --- | --- | --- |
| title | string | 是 | 逻辑文档标题 |
| file | file | 是 | PDF/DOCX/TXT/MD，建议 ≤20MB |
| changeNote | string | 否 | 首版说明 |

### 3.7.3 响应数据

HTTP 202：

```json
{
  "code": 1,
  "msg": "accepted",
  "data": {
    "documentId": 2001,
    "versionId": 30001,
    "versionNo": 1,
    "processStatus": "UPLOADED",
    "taskId": "task_doc_93a1"
  }
}
```

## 3.8 文档详情

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/documents/{id}` |
| 请求方式 | GET |
| 接口描述 | 查询逻辑文档、当前发布版本和版本列表摘要。 |

## 3.9 上传新版本

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/documents/{id}/versions` |
| 请求方式 | POST |
| Content-Type | `multipart/form-data` |
| 接口描述 | 为已有逻辑文档创建新版本。OWNER/MAINTAINER。 |

参数：`file` 必填；`changeNote` 可选。

新版本不会自动替代当前已发布版本；必须完成处理、审核和发布。

## 3.10 文档版本详情

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/document-versions/{id}` |
| 请求方式 | GET |
| 接口描述 | 查询文件元数据、处理状态、审核状态和发布信息。 |

响应字段至少包括：

```json
{
  "id": 30018,
  "documentId": 2001,
  "versionNo": 5,
  "fileName": "order-runbook-v5.pdf",
  "fileSize": 2457600,
  "contentHash": "sha256:...",
  "processStatus": "READY",
  "progress": 100,
  "chunkCount": 86,
  "publishStatus": "DRAFT",
  "changeNote": "补充 MQ 积压排查",
  "reviewer": null,
  "reviewComment": null,
  "publishedAt": null
}
```

`processStatus`：`UPLOADED/PARSING/CHUNKING/EMBEDDING/READY/FAILED`。  
`publishStatus`：`DRAFT/IN_REVIEW/APPROVED/REJECTED/PUBLISHED/SUPERSEDED`。

## 3.11 查询文档版本处理状态

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/document-versions/{id}/status` |
| 请求方式 | GET |
| 接口描述 | 轮询解析、切分、Embedding 与向量入库进度。 |

READY 时 `progress=100`；FAILED 时 `errorMessage` 必须非空。

## 3.12 重新处理失败版本

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/document-versions/{id}/reprocess` |
| 请求方式 | POST |
| 接口描述 | 对 FAILED 版本重新发起索引任务，生成新 taskId。 |

只有 `processStatus=FAILED` 可重处理；并发重复请求返回 409。

## 3.13 提交知识审核

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/document-versions/{id}/submit-review` |
| 请求方式 | POST |
| 接口描述 | 将 READY + DRAFT 版本提交审核。 |

成功后 `publishStatus=IN_REVIEW`。

## 3.14 审核文档版本

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/document-versions/{id}/review` |
| 请求方式 | POST |
| 接口描述 | OWNER/MAINTAINER 审核版本。审核人不得是系统匿名用户。 |

```json
{
  "decision": "APPROVE",
  "comment": "内容已核对，可进入正式知识快照"
}
```

`decision`：`APPROVE/REJECT`。  
APPROVE → `APPROVED`；REJECT → `REJECTED`。

## 3.15 发布文档版本

### 3.15.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/document-versions/{id}/publish` |
| 请求方式 | POST |
| 接口描述 | 将 APPROVED + READY 版本发布，并原子创建新的 KnowledgeSnapshot。 |

### 3.15.2 响应数据

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "documentId": 2001,
    "versionId": 30018,
    "publishStatus": "PUBLISHED",
    "snapshotId": 905,
    "snapshotNo": 12,
    "publishedAt": "2026-10-20T12:00:00"
  }
}
```

### 3.15.3 备注说明

发布事务必须同时完成：

1. 校验版本 READY + APPROVED；
2. 将上一当前发布版本标记为 SUPERSEDED；
3. 更新逻辑文档 `currentPublishedVersionId`；
4. 基于所有 ACTIVE 文档当前发布版本创建不可变快照；
5. 更新知识库 `currentSnapshotId`；
6. 将引用旧版本的 Quiz / Drill / Path Task 依赖标记为 `NEEDS_REVIEW`；
7. 写审计日志。

任何一步失败不得留下“版本已发布但快照未更新”的半状态。

## 3.16 知识快照列表

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/knowledge-bases/{id}/snapshots` |
| 请求方式 | GET |
| 接口描述 | 查询历史发布快照。 |

响应行：`id / snapshotNo / itemCount / createdBy / createTime / reason`。

## 3.17 知识快照详情

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/knowledge-snapshots/{id}` |
| 请求方式 | GET |
| 接口描述 | 查看快照中固定的 documentVersion 列表。 |

```json
{
  "id": 905,
  "snapshotNo": 12,
  "knowledgeBaseId": 501,
  "items": [
    {
      "documentId": 2001,
      "documentVersionId": 30018,
      "versionNo": 5,
      "title": "订单超时排查手册"
    }
  ]
}
```

## 3.18 查看知识变更影响

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/document-versions/{id}/impact` |
| 请求方式 | GET |
| 接口描述 | 查看该版本发布后被标记为需要复核的测验、演练和接手路径任务。 |

响应：

```json
{
  "versionId": 30018,
  "affected": [
    {
      "dependencyId": 7701,
      "targetType": "DRILL",
      "targetId": 610,
      "targetName": "订单消息积压演练",
      "reviewStatus": "NEEDS_REVIEW"
    }
  ]
}
```

## 3.19 确认受影响内容已复核

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/content-dependencies/{id}/review` |
| 请求方式 | POST |
| 接口描述 | OWNER/MAINTAINER 完成受影响训练内容复核后确认。 |

```json
{
  "status": "CONFIRMED",
  "note": "演练规则已按 v5 文档更新"
}
```

## 3.20 归档逻辑文档

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/documents/{id}/archive` |
| 请求方式 | POST |
| 接口描述 | 归档文档，不破坏历史快照。 |

已被历史快照、评估结果或能力报告引用的发布版本不物理删除。

---

# 4. Onboarding 项目接手路径

## 4.1 接手路径列表

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects/{projectId}/onboarding-paths` |
| 请求方式 | GET |
| 接口描述 | 查询项目内可复用的接手路径。 |

查询参数：`keyword / status / targetRole / page / pageSize`。

状态：`DRAFT/PUBLISHED/ARCHIVED`。

## 4.2 创建接手路径

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects/{projectId}/onboarding-paths` |
| 请求方式 | POST |
| 接口描述 | 创建 DRAFT 路径。 |

```json
{
  "name": "订单服务接手路径",
  "targetRole": "ORDER_SERVICE_MAINTAINER",
  "description": "覆盖架构、核心 API、消息链路和常见故障",
  "knowledgeSnapshotId": 905
}
```

## 4.3 接手路径详情

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/onboarding-paths/{id}` |
| 请求方式 | GET |
| 接口描述 | 查询路径、版本、任务和依赖关系。 |

任务示例：

```json
{
  "id": 4101,
  "type": "READ",
  "title": "阅读系统架构",
  "resourceType": "DOCUMENT_VERSION",
  "resourceId": 30011,
  "completionRule": "MANUAL",
  "estimatedMinutes": 30,
  "sortOrder": 1,
  "prerequisiteTaskIds": []
}
```

任务类型：`READ/QUIZ/DRILL/CHECKPOINT`。

## 4.4 修改接手路径

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/onboarding-paths/{id}` |
| 请求方式 | PUT |
| 接口描述 | 修改 DRAFT 路径元数据。 |

已发布路径需要先创建新的编辑版本；不得直接改变正在执行的 Assignment 语义。

## 4.5 新增路径任务

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/onboarding-paths/{id}/tasks` |
| 请求方式 | POST |
| 接口描述 | 向 DRAFT 路径加入阅读、测验、演练或人工检查点。 |

```json
{
  "type": "DRILL",
  "title": "订单消息积压排查",
  "resourceType": "DRILL",
  "resourceId": 610,
  "completionRule": "AUTO",
  "estimatedMinutes": 45,
  "sortOrder": 3,
  "prerequisiteTaskIds": [4101, 4102]
}
```

## 4.6 修改路径任务

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/onboarding-path-tasks/{id}` |
| 请求方式 | PUT |
| 接口描述 | 修改 DRAFT 路径任务。 |

## 4.7 删除路径任务

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/onboarding-path-tasks/{id}` |
| 请求方式 | DELETE |
| 接口描述 | 删除 DRAFT 路径任务及其依赖边。 |

## 4.8 调整任务顺序

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/onboarding-paths/{id}/task-order` |
| 请求方式 | PUT |
| 接口描述 | 一次提交完整任务顺序。 |

```json
{
  "taskIds": [4101, 4102, 4103, 4104]
}
```

顺序调整不得产生依赖环。

## 4.9 发布接手路径

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/onboarding-paths/{id}/publish` |
| 请求方式 | POST |
| 接口描述 | 发布路径版本。 |

发布前必须校验：

- 路径至少包含一个任务；
- 依赖图无环；
- READ 引用的文档版本属于绑定快照；
- QUIZ/DRILL 已发布；
- 被依赖资源不存在 `NEEDS_REVIEW` 未确认项；
- `knowledgeSnapshotId` 有效且已发布。

成功后 `version + 1`，状态为 PUBLISHED。

## 4.10 分配接手路径

### 4.10.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/onboarding-paths/{id}/assignments` |
| 请求方式 | POST |
| 接口描述 | 将已发布路径分配给一个或多个 LEARNER。 |

### 4.10.2 请求参数

```json
{
  "learnerUserIds": [21, 22],
  "dueAt": "2026-10-27T18:00:00"
}
```

### 4.10.3 响应数据

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "assignmentIds": [7001, 7002]
  }
}
```

创建 Assignment 时必须复制任务运行快照，并固定 `pathVersion + knowledgeSnapshotId`。

## 4.11 Assignment 列表

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects/{projectId}/assignments` |
| 请求方式 | GET |
| 接口描述 | OWNER/MAINTAINER 查看团队 Assignment；LEARNER 只能查看自己。 |

查询参数：`assigneeUserId / status / pathId / dueBefore / page / pageSize`。

状态：`ASSIGNED/IN_PROGRESS/REVIEW_PENDING/PASSED/NEEDS_WORK/CANCELLED`。

## 4.12 Assignment 详情

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/assignments/{id}` |
| 请求方式 | GET |
| 接口描述 | 查看固定路径版本、快照和每个任务执行状态。 |

任务状态：`LOCKED/PENDING/IN_PROGRESS/COMPLETED/FAILED/SKIPPED`。

## 4.13 开始 Assignment

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/assignments/{id}/start` |
| 请求方式 | POST |
| 接口描述 | LEARNER 开始接手路径；重复调用幂等。 |

## 4.14 完成阅读/检查点任务

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/assignment-tasks/{id}/complete` |
| 请求方式 | POST |
| 接口描述 | 完成 READ 或 CHECKPOINT。QUIZ/DRILL 由对应评估结果自动完成。 |

```json
{
  "note": "已阅读架构图并核对订单状态机"
}
```

未满足前置任务时返回 409。

## 4.15 提交 Assignment 复核

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/assignments/{id}/submit` |
| 请求方式 | POST |
| 接口描述 | 所有必需任务完成后提交负责人复核。 |

成功后 `status=REVIEW_PENDING`，并允许生成能力报告。

## 4.16 复核 Assignment

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/assignments/{id}/review` |
| 请求方式 | POST |
| 接口描述 | OWNER/MAINTAINER 给出最终接手判定。 |

```json
{
  "decision": "PASS",
  "comment": "排障路径和风险意识达到接手要求"
}
```

`decision`：`PASS/NEEDS_WORK`。人工复核不覆盖历史自动分数，只决定 Assignment 最终状态。

---

# 5. Assessment 能力评估

# 5A. 技术测验

## 5.1 AI 生成测验草稿

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects/{projectId}/quizzes/generate` |
| 请求方式 | POST |
| 接口描述 | 基于已发布知识快照异步生成测验草稿。 |

```json
{
  "title": "订单服务核心链路测验",
  "topic": "订单状态机、事务、消息一致性",
  "knowledgeSnapshotId": 905,
  "questionCount": 10,
  "difficulty": "MEDIUM",
  "types": ["SINGLE", "MULTIPLE", "JUDGE", "SHORT"],
  "passScore": 70
}
```

HTTP 202：

```json
{
  "code": 1,
  "msg": "accepted",
  "data": {
    "quizId": 501,
    "status": "GENERATING"
  }
}
```

状态：`GENERATING/DRAFT/READY/PUBLISHED/FAILED/ARCHIVED`。

## 5.2 测验列表

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects/{projectId}/quizzes` |
| 请求方式 | GET |
| 接口描述 | 查询项目测验模板。 |

## 5.3 测验详情

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/quizzes/{id}` |
| 请求方式 | GET |
| 接口描述 | MAINTAINER 可查看草稿答案与来源；LEARNER 仅在开始 attempt 后获得不含标准答案的题目。 |

## 5.4 修改测验题目

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/quiz-questions/{id}` |
| 请求方式 | PUT |
| 接口描述 | 修改未发布测验题目、标准答案、知识点和来源引用。 |

请求字段可包括：`knowledgeKey / content / options / answer / explanation / maxScore / sourceRefs`。

## 5.5 发布测验

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/quizzes/{id}/publish` |
| 请求方式 | POST |
| 接口描述 | 发布已人工检查的测验。 |

发布后题目、标准答案、rubric 和来源引用不可原地修改；需要修改时创建新 revision。

## 5.6 开始测验 Attempt

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/quizzes/{id}/attempts` |
| 请求方式 | POST |
| 接口描述 | 开始一次测验，可关联 Onboarding Assignment Task。 |

```json
{
  "assignmentTaskId": 8102
}
```

响应：

```json
{
  "attemptId": 9201,
  "status": "IN_PROGRESS",
  "rubricVersion": 1,
  "questions": []
}
```

## 5.7 提交测验 Attempt

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/quiz-attempts/{id}/submit` |
| 请求方式 | POST |
| 接口描述 | 提交整份答案。客观题由程序确定性评分；SHORT 由 AI 生成评分建议并可进入人工复核。 |

```json
{
  "answers": [
    {"questionId": 5101, "answer": "A"},
    {"questionId": 5102, "answer": ["A", "C"]},
    {"questionId": 5103, "answer": "先确认消费延迟和堆积分区，再检查下游依赖。"}
  ]
}
```

响应：

```json
{
  "attemptId": 9201,
  "status": "REVIEW_PENDING",
  "autoScore": 78.0,
  "reviewRequired": true
}
```

若不存在 SHORT 且无需人工复核，可直接 `COMPLETED`。

## 5.8 测验结果

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/quiz-attempts/{id}` |
| 请求方式 | GET |
| 接口描述 | 查看原始答案、评分来源、自动分、AI 解释和人工复核结果。 |

每道答案应返回：`userAnswer / ruleScore / aiSuggestedScore / finalScore / scoringSource / feedback / sourceRefs`。

## 5.9 人工复核测验

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/quiz-attempts/{id}/review` |
| 请求方式 | POST |
| 接口描述 | OWNER/MAINTAINER 复核 SHORT 或争议结果。 |

```json
{
  "decision": "PASS",
  "scoreOverride": 82,
  "comment": "定位逻辑正确，但缺少恢复后的数据一致性验证"
}
```

自动分与覆盖分都必须保留。

# 5B. 故障演练

## 5.10 演练场景列表

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects/{projectId}/drill-scenarios` |
| 请求方式 | GET |
| 接口描述 | 查询可复用故障演练模板。 |

状态：`DRAFT/PUBLISHED/ARCHIVED`。

## 5.11 创建演练场景

### 5.11.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects/{projectId}/drill-scenarios` |
| 请求方式 | POST |
| 接口描述 | 创建结构化演练草稿。 |

### 5.11.2 请求参数

```json
{
  "name": "订单消息积压演练",
  "description": "验证 MQ 积压定位、风险操作和恢复确认能力",
  "severity": "P2",
  "knowledgeSnapshotId": 905,
  "initialSymptom": "订单创建成功，但状态长时间停留在 PAID，延迟告警持续上升。",
  "passScore": 70,
  "rubric": [
    {"code":"LOCALIZATION","name":"问题定位","maxScore":25},
    {"code":"EVIDENCE","name":"证据使用","maxScore":25},
    {"code":"ORDER","name":"排查顺序","maxScore":20},
    {"code":"RISK","name":"风险意识","maxScore":20},
    {"code":"VERIFY","name":"结果验证","maxScore":10}
  ],
  "rules": [
    {
      "code":"CHECK_CONSUMER_LAG",
      "knowledgeKey":"mq.consumer.lag",
      "type":"REQUIRED",
      "points":15,
      "aliases":["检查消费延迟","查看 consumer lag"]
    },
    {
      "code":"RESTART_BEFORE_EVIDENCE",
      "knowledgeKey":"mq.restart.risk",
      "type":"RISKY",
      "points":-15,
      "aliases":["直接重启消费者","先重启服务"]
    }
  ],
  "evidence": [
    {
      "code":"LAG_METRIC",
      "type":"METRIC",
      "title":"消费者延迟",
      "content":"order-consumer lag=183245",
      "unlockRuleCode":"CHECK_CONSUMER_LAG"
    }
  ]
}
```

### 5.11.3 备注说明

规则与证据属于演练模板的一部分。程序评分只能依据已发布规则，不允许 AI 在运行时临时发明加减分标准。

## 5.12 修改演练场景

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/drill-scenarios/{id}` |
| 请求方式 | PUT |
| 接口描述 | 修改 DRAFT 场景。 |

已发布场景需要创建新 revision，不原地修改历史版本。

## 5.13 演练场景详情

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/drill-scenarios/{id}` |
| 请求方式 | GET |
| 接口描述 | MAINTAINER 可查看规则；LEARNER 在运行时只看到初始现象和已解锁证据，不返回隐藏评分规则。 |

## 5.14 发布演练场景

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/drill-scenarios/{id}/publish` |
| 请求方式 | POST |
| 接口描述 | 发布演练模板。 |

必须校验 rubric 总分、规则 code 唯一、证据引用有效、知识快照存在。

## 5.15 开始演练

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/drill-scenarios/{id}/runs` |
| 请求方式 | POST |
| 接口描述 | 创建一次 Drill Run，可绑定 Assignment Task。 |

```json
{
  "assignmentTaskId": 8103
}
```

响应：

```json
{
  "runId": 9301,
  "status": "IN_PROGRESS",
  "initialSymptom": "订单创建成功，但状态长时间停留在 PAID，延迟告警持续上升。",
  "evidence": []
}
```

## 5.16 提交下一步排查动作

### 5.16.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/drill-runs/{id}/actions` |
| 请求方式 | POST |
| 接口描述 | 提交用户下一步排查动作并开放对应证据。 |

### 5.16.2 请求参数

```json
{
  "clientRequestId": "0a0b4a8d-493a-4aa2-a214-8f74e93a3de9",
  "actionText": "先看 order-consumer 的 lag 和异常分区"
}
```

### 5.16.3 响应数据

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "actionId": 9401,
    "normalizedAction": "CHECK_CONSUMER_LAG",
    "unlockedEvidence": [
      {
        "code": "LAG_METRIC",
        "type": "METRIC",
        "title": "消费者延迟",
        "content": "order-consumer lag=183245"
      }
    ]
  }
}
```

### 5.16.4 备注说明

- `clientRequestId` 在同一 Run 内唯一，用于断网重试幂等。
- 运行中不向 LEARNER 返回“这是必做动作/这是扣分动作”等隐藏评分信息。
- `normalizedAction` 可来自规则别名或 AI 辅助归一化；只有匹配到已发布 rule 的结果才能产生确定性分值。
- AI 归一化结果必须记录 `matchSource` 和置信信息，便于复核。

## 5.17 提交演练结论

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/drill-runs/{id}/submit` |
| 请求方式 | POST |
| 接口描述 | 提交根因、恢复方案和验证方案，结束操作阶段并计算程序分。 |

```json
{
  "rootCause": "order-consumer 在单个分区持续反序列化失败，导致 lag 快速累积。",
  "recoveryPlan": "先隔离坏消息并修复兼容逻辑，再逐步恢复消费。",
  "validationPlan": "确认 lag 持续下降、订单状态恢复推进，并抽查补偿后的数据一致性。",
  "evidenceCodes": ["LAG_METRIC", "ERROR_LOG"]
}
```

响应：

```json
{
  "runId": 9301,
  "status": "REVIEW_PENDING",
  "programScore": 76,
  "passCandidate": true,
  "reviewRequired": true
}
```

## 5.18 演练结果

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/drill-runs/{id}` |
| 请求方式 | GET |
| 接口描述 | 回放动作轨迹、证据、规则命中、结构化评分、AI 解释和人工复核。 |

结果至少包括：

- `rubricVersion`
- `knowledgeSnapshotId`
- `actions[]`
- `viewedEvidence[]`
- `requiredActionsCompleted[]`
- `missingRequiredActions[]`
- `riskyActions[]`
- `scoreBreakdown[]`
- `programScore`
- `aiExplanation`
- `reviewDecision`
- `scoreOverride`
- `finalScore`

## 5.19 人工复核演练

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/drill-runs/{id}/review` |
| 请求方式 | POST |
| 接口描述 | OWNER/MAINTAINER 对演练进行最终复核。 |

```json
{
  "decision": "PASS",
  "scoreOverride": 80,
  "comment": "排查顺序合理；需要加强恢复后的业务指标验证"
}
```

---

# 6. Assistant / Agent 横向能力

本模块保留会话、RAG、LangGraph 和 Tool Calling，但它不再定义产品主线。Agent 用于文档问答、资料总结、训练解释和个性化建议；项目权限、训练任务完成、评分结果和复核结论始终由 Spring Boot 决定。

## 6.1 创建会话

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/conversations` |
| 请求方式 | POST |
| 接口描述 | 创建项目内 AI 会话。 |

```json
{
  "projectId": 1001,
  "title": "订单超时排查资料问答",
  "knowledgeBaseId": 501,
  "knowledgeSnapshotId": 905,
  "mode": "RAG"
}
```

`mode` 保留 `CHAT/RAG/AGENT`，仅表示 AI 执行策略，不代表产品业务模块。

## 6.2 会话列表

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects/{projectId}/conversations` |
| 请求方式 | GET |
| 接口描述 | 查询当前用户在项目中的历史会话。 |

## 6.3 会话详情

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/conversations/{id}` |
| 请求方式 | GET |
| 接口描述 | 查询会话绑定的项目、知识库和快照。 |

## 6.4 修改会话标题

`PUT /api/conversations/{id}`，请求 `{ "title": "..." }`。

## 6.5 删除会话

`DELETE /api/conversations/{id}`。只删除当前用户自己的会话；项目知识与评估事实不受影响。

## 6.6 历史消息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/conversations/{id}/messages` |
| 请求方式 | GET |
| 接口描述 | 游标加载历史消息与引用。 |

查询参数：`beforeId / pageSize`。

citation 字段增加：`documentVersionId / snapshotId`。

## 6.7 发送消息（非流式）

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/conversations/{id}/messages` |
| 请求方式 | POST |
| 接口描述 | 发送问题并等待完整回答。 |

```json
{
  "content": "订单状态长时间停留在 PAID 时，文档建议先确认哪些证据？",
  "useKnowledgeBase": true,
  "enableTools": false
}
```

正式项目知识问答默认使用会话绑定的已发布快照。LEARNER 不允许切换到未发布版本。

## 6.8 发送消息（SSE）

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/conversations/{id}/stream` |
| 请求方式 | POST |
| 接口描述 | SSE 流式返回 LangGraph 执行过程与答案。 |

事件：

```text
event: start
data: {"traceId":"trace_93aa","messageId":9010}

event: node
data: {"node":"retrieve_context","status":"completed"}

event: tool
data: {"tool":"get_assignment_context","status":"completed"}

event: token
data: {"content":"建议先确认消费者延迟"}

event: citation
data: {"documentId":2001,"documentVersionId":30018,"snapshotId":905,"fileName":"order-runbook-v5.pdf","page":12}

event: done
data: {"messageId":9011,"finishReason":"stop"}
```

## 6.9 停止生成

`DELETE /api/conversations/{id}/generation/{traceId}`。重复停止幂等。

## 6.10 消息反馈

`POST /api/messages/{id}/feedback`，请求 `{ "rating": 1, "reason": "引用清楚" }`。

---

# 7. Insight 与能力报告

## 7.1 项目总览

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects/{projectId}/insights/overview` |
| 请求方式 | GET |
| 接口描述 | OWNER/MAINTAINER 查看交接、评估和知识维护总览。 |

```json
{
  "activeLearners": 5,
  "activeAssignments": 4,
  "assignmentCompletionRate": 0.72,
  "assessmentPassRate": 0.68,
  "pendingKnowledgeReviews": 3,
  "pendingAssessmentReviews": 2,
  "knowledgeGapCount": 6
}
```

## 7.2 团队 Assignment 进度

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects/{projectId}/insights/assignments` |
| 请求方式 | GET |
| 接口描述 | 汇总路径完成度、逾期和待复核状态。 |

## 7.3 知识薄弱点

### 7.3.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects/{projectId}/insights/knowledge-gaps` |
| 请求方式 | GET |
| 接口描述 | 将 Quiz 与 Drill 的失败事实按 `knowledgeKey` 聚合。 |

### 7.3.2 响应数据

```json
{
  "rows": [
    {
      "knowledgeKey": "mq.consumer.lag",
      "label": "消费者积压定位",
      "participantCount": 10,
      "quizWrongRate": 0.4,
      "drillMissRate": 0.6,
      "riskActionRate": 0.2,
      "relatedDocumentIds": [2001],
      "severity": "HIGH"
    }
  ]
}
```

Insight 是统计投影，不得反向篡改历史评估事实。

## 7.4 知识内容影响列表

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects/{projectId}/insights/content-impact` |
| 请求方式 | GET |
| 接口描述 | 查询 `NEEDS_REVIEW` 的 Quiz / Drill / Path 依赖。 |

支持按 `targetType / documentId / status` 过滤。

## 7.4.1 待复核队列

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/projects/{projectId}/insights/pending-reviews` |
| 请求方式 | GET |
| 接口描述 | OWNER/MAINTAINER 查询项目内待复核的 QuizAttempt、DrillRun 和 Assignment。 |

每项包含 `targetType / targetId / userId / userName / title / status / automaticScore / finalScore / submittedAt`；该队列只读，提交复核仍调用对应 Assessment 或 Assignment 复核接口。

## 7.5 生成能力报告

### 7.5.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/assignments/{id}/capability-report/generate` |
| 请求方式 | POST |
| 接口描述 | 基于已完成任务、测验、演练和复核事实生成可解释能力报告。 |

HTTP 202：

```json
{
  "reportId": 12001,
  "status": "PROCESSING",
  "reportVersion": 1
}
```

AI 只负责摘要、解释和建议；任务完成事实、分数、风险动作和人工复核结果由 Spring Boot 组装后传入。

## 7.6 能力报告详情

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/capability-reports/{id}` |
| 请求方式 | GET |
| 接口描述 | 查看一个可复核、可回放的项目接手能力报告。 |

响应至少包括：

```json
{
  "id": 12001,
  "assignmentId": 7001,
  "reportVersion": 1,
  "status": "READY",
  "knowledgeSnapshotId": 905,
  "pathVersion": 3,
  "overallDecision": "PASS",
  "summary": "已具备独立处理常见订单链路故障的基础能力。",
  "dimensions": [
    {"code":"KNOWLEDGE","score":82,"evidence":["quizAttempt:9201"]},
    {"code":"TROUBLESHOOTING","score":80,"evidence":["drillRun:9301"]},
    {"code":"RISK","score":75,"evidence":["drillRun:9301"]}
  ],
  "risks": [
    "恢复后业务一致性验证步骤仍不够稳定"
  ],
  "recommendedSources": [
    {"documentVersionId":30018,"title":"订单超时排查手册"}
  ],
  "generatedAt": "2026-10-20T15:00:00"
}
```

LEARNER 只能读取自己的报告；OWNER/MAINTAINER 可读取项目成员报告。

---

# 8. 文件与系统接口

## 8.1 通用文件上传

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/files/upload` |
| 请求方式 | POST |
| Content-Type | `multipart/form-data` |
| 接口描述 | 上传头像/普通图片等非知识文档文件。 |

知识文档必须通过 Knowledge 模块上传，不能绕过文档版本和审核流程。

## 8.2 系统健康检查

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/api/health` |
| 请求方式 | GET |
| 认证 | 否 |
| 接口描述 | 返回 Spring Boot、数据库和 AI 服务聚合健康状态。 |

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "status": "UP",
    "aiService": "UP",
    "database": "UP"
  }
}
```

---

# 9. 内部 AI 服务接口

以下接口仅供 Spring Boot、FastAPI 之间调用，不直接暴露给浏览器。

## 9.1 Agent 非流式执行

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/internal/agent/invoke` |
| 请求方式 | POST |
| 调用方向 | Spring Boot → FastAPI |
| 接口描述 | 执行 CHAT/RAG/AGENT。 |

```json
{
  "userId": 21,
  "projectId": 1001,
  "conversationId": 301,
  "messageId": 9000,
  "content": "订单消息积压应该先看什么？",
  "mode": "RAG",
  "knowledgeBaseId": 501,
  "knowledgeSnapshotId": 905,
  "allowedDocumentVersionIds": [30011, 30018, 30022],
  "recentMessages": []
}
```

FastAPI 不自行决定用户能访问哪些 documentVersion；允许集合由 Spring Boot 在项目权限与快照校验后提供。

## 9.2 Agent 流式执行

`POST /internal/agent/stream`，请求字段与 9.1 一致，响应为 SSE：

`node / tool / token / citation / done / error`。

## 9.3 RAG 检索

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/internal/rag/retrieve` |
| 请求方式 | POST |
| 接口描述 | 在指定项目、知识库和允许文档版本集合中检索。 |

```json
{
  "projectId": 1001,
  "knowledgeBaseId": 501,
  "knowledgeSnapshotId": 905,
  "documentVersionIds": [30011, 30018, 30022],
  "query": "消费者积压如何定位",
  "topK": 5,
  "scoreThreshold": 0.65
}
```

检索过滤必须至少包含：

```text
project_id = projectId
AND knowledge_base_id = knowledgeBaseId
AND document_version_id IN documentVersionIds
```

## 9.4 文档版本入库

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/internal/rag/index` |
| 请求方式 | POST |
| 接口描述 | 解析一个 documentVersion 并写入向量库。 |

```json
{
  "projectId": 1001,
  "knowledgeBaseId": 501,
  "documentId": 2001,
  "documentVersionId": 30018,
  "taskId": "task_doc_93a1",
  "fileUrl": "http://server/internal/files/uuid.pdf",
  "fileType": "pdf",
  "fileName": "order-runbook-v5.pdf",
  "contentHash": "sha256:..."
}
```

AI 服务只负责解析、切分、Embedding、向量写入和进度回调；不修改业务数据库。

## 9.5 文档版本进度回调

| 项目 | 内容 |
| --- | --- |
| 请求路径 | `/internal/callbacks/document-versions/{versionId}/progress` |
| 请求方式 | POST |
| 调用方向 | FastAPI → Spring Boot |

```json
{
  "taskId": "task_doc_93a1",
  "status": "EMBEDDING",
  "progress": 75,
  "chunkCount": 60,
  "errorMessage": null
}
```

Spring Boot 只接受 `taskId == current_task_id` 的当前执行态回调；旧任务、重复回调和终态迟到回调不得覆盖新状态。

## 9.6 向量删除与清理

| 能力 | 请求路径 | 说明 |
| --- | --- | --- |
| 删除未发布版本向量 | `DELETE /internal/rag/document-versions/{versionId}` | 查询参数带 `projectId + knowledgeBaseId` |
| 清理知识库全部向量 | `DELETE /internal/rag/knowledge-bases/{knowledgeBaseId}` | 仅项目归档/管理任务使用 |
| 清理项目全部向量 | `DELETE /internal/rag/projects/{projectId}` | 仅受控运维/测试清理使用 |

已发布且被任一 KnowledgeSnapshot 引用的版本，Spring Boot 不得调用物理删除。

## 9.7 AI 生成测验

`POST /internal/quiz/generate`

请求至少包含：

`projectId / knowledgeSnapshotId / documentVersionIds / topic / questionCount / difficulty / types`。

响应题目结构必须包含：

`type / knowledgeKey / content / options / answer / explanation / sourceRefs`。

Structured Output 校验失败映射为稳定错误，不保存半套题目。

## 9.8 SHORT 评分建议

`POST /internal/quiz/grade-short`

```json
{
  "question": "为什么不能在确认消费积压原因前直接重启消费者？",
  "referenceAnswer": "需要先保留证据并确认积压来源，直接重启可能破坏现场且无法确认是否存在坏消息或下游依赖。",
  "userAnswer": "先查 lag 和日志，不要立刻重启。",
  "rubric": {
    "maxScore": 10,
    "requiredPoints": ["保留证据", "确认根因", "风险意识"]
  }
}
```

响应：

```json
{
  "scoreSuggestion": 8,
  "feedback": "已体现风险意识，但没有说明重启可能掩盖坏消息或依赖异常。",
  "evidence": ["先查 lag 和日志", "不要立刻重启"]
}
```

该结果是评分建议；Spring Boot 记录 `scoringSource=AI_SUGGESTION`，需要人工复核时不得伪装为确定性评分。

## 9.9 演练动作归一化与解释

`POST /internal/drills/normalize-action`

输入：`scenarioId / actionText / candidateRules`。  
输出：`normalizedAction / matchedRuleCode / confidence / explanation`。

FastAPI 不能返回新的分值或临时创建 rule；实际加减分只由 Spring Boot 读取已发布规则计算。

`POST /internal/drills/explain-result`

输入为 Spring Boot 已计算的结构化事实：动作、证据、命中规则、缺失动作、风险动作、分数。  
输出仅为 `summary / strengths / gaps / recommendedKnowledgeKeys`。

## 9.10 能力报告摘要

`POST /internal/reports/capability-summary`

Spring Boot 传入：

- 固定的 `assignmentId / pathVersion / knowledgeSnapshotId`
- 测验与演练原始结果摘要
- 程序分数与人工复核
- 知识薄弱点
- 可推荐的已发布文档版本

FastAPI 返回结构化 `summary / risks / strengths / recommendedSources`。  
AI 不得改变原始分数、人工决定或任务完成事实。

## 9.11 Agent Tool Bridge

以下接口由 FastAPI 白名单 Tool 调用 Spring Boot，均只读：

| Tool | 请求路径 |
| --- | --- |
| `get_assignment_context` | `/internal/tools/assignment-context` |
| `get_assessment_summary` | `/internal/tools/assessment-summary` |
| `get_project_knowledge_summary` | `/internal/tools/project-knowledge` |
| `get_recommended_sources` | `/internal/tools/recommended-sources` |

请求至少包含 `userId + projectId`。Spring Boot 必须重新校验项目成员和资源归属，不因“来自 AI 服务”而跳过权限。

V3 默认不开放：

- 任意 SQL；
- 任意 Shell；
- 任意 HTTP；
- 修改成员角色；
- 发布知识；
- 直接修改评分；
- 自动通过 Assignment。

---

# 10. 错误码与安全约定

| 业务码 | HTTP | 含义 |
| --- | --- | --- |
| 1 | 200/201/202 | 成功 |
| 1001 | 400 | 参数校验失败 |
| 1002 | 409 | 用户名已存在 |
| 1003 | 401 | 账号或密码错误 |
| 1004 | 401 | JWT 无效/过期 |
| 1005 | 403 | 无资源权限 |
| 1006 | 401 | refreshToken 无效/过期 |
| 1101 | 404 | 项目不存在 |
| 1102 | 403 | 非项目成员 |
| 1103 | 403 | 项目角色权限不足 |
| 1104 | 409 | 不能移除/降级最后一个 OWNER |
| 1105 | 409 | 项目已归档 |
| 2001 | 404 | 知识库不存在 |
| 2002 | 404 | 文档不存在 |
| 2003 | 404 | 文档版本不存在 |
| 2004 | 422 | 文档类型不支持 |
| 2005 | 413 | 文件过大 |
| 2006 | 500 | 文档解析失败 |
| 2007 | 409 | 文档版本正在处理中 |
| 2008 | 409 | 文档版本状态不允许当前操作 |
| 2009 | 404 | 知识快照不存在 |
| 2010 | 409 | 已发布历史版本仍被快照引用，禁止物理删除 |
| 2011 | 409 | 训练内容受知识变更影响，需复核 |
| 3001 | 503 | AI 服务不可用 |
| 3002 | 429 | AI 请求频率超限 |
| 3003 | 422 | Structured Output 校验失败 |
| 3004 | 500 | 向量检索失败 |
| 3005 | 409 | AI 生成任务已取消或已结束 |
| 3006 | 500 | 向量删除失败 |
| 4001 | 404 | 测验不存在 |
| 4002 | 404 | 测验 Attempt 不存在 |
| 4003 | 409 | 测验状态不允许开始/提交 |
| 4004 | 500 | 测验生成失败 |
| 4101 | 404 | 演练场景不存在 |
| 4102 | 404 | Drill Run 不存在 |
| 4103 | 409 | 演练已结束 |
| 4104 | 409 | 重复演练动作请求 |
| 4105 | 422 | 演练规则配置不合法 |
| 5001 | 404 | 接手路径不存在 |
| 5002 | 404 | Assignment 不存在 |
| 5003 | 409 | 前置任务未完成 |
| 5004 | 409 | 路径未发布或存在失效依赖 |
| 5005 | 409 | Assignment 尚未满足提交条件 |
| 6001 | 404 | 能力报告不存在 |
| 6002 | 500 | 能力报告生成失败 |

## 10.1 安全规则

- 密码使用 BCrypt/Argon2 等哈希算法，不保存明文。
- refreshToken 服务端只保存哈希并支持撤销。
- 浏览器传来的 `userId` 不作为认证依据。
- 所有 Project 资源先校验成员，再校验角色，再校验资源归属。
- 项目成员共享知识，因此 RAG 不再按 `userId` 过滤；改为 `projectId + knowledgeBaseId + documentVersionIds`。
- Learner 正式 RAG 只能访问发布快照。
- 上传文件校验扩展名、MIME、大小、随机 storageKey 和路径穿越。
- 已发布知识和评估模板不原地覆盖。
- AI 结果必须与程序事实分字段保存，不能只存一个“模型最终分”。
- 日志禁止记录密码、完整 JWT、refreshToken、LLM API Key、完整上传文档和完整 Prompt。
- 内部接口必须使用独立服务令牌，且 Nginx 不暴露 `/internal/**`。

---

# 11. V3 功能覆盖与 V2 迁移边界

## 11.1 V3 主线能力

| 业务域 | 能力 |
| --- | --- |
| Project | 项目空间、成员、角色、审计 |
| Knowledge | 文档版本、审核、发布、知识快照、影响分析 |
| Onboarding | 可复用接手路径、任务依赖、分配、完成、负责人复核 |
| Assessment | 技术测验、故障演练、结构化评分、人工复核 |
| Insight | 团队完成情况、知识薄弱点、内容影响、能力报告 |
| Assistant | 文档问答、RAG、LangGraph、只读 Tool |
| Reliability | taskId、旧回调保护、超时状态收敛、幂等重试 |
| Storage | MySQL 业务事实、Milvus 向量、Redis 运行态、对象存储文件 |

## 11.2 V2 能力处理

| V2 能力 | V3 处理 |
| --- | --- |
| 个人知识库 | 改为 Project 共享知识库 |
| `userId + knowledgeBaseId` RAG 隔离 | 改为 Project + KB + Snapshot DocumentVersion 集合 |
| AI 会话 | 保留，但项目化并降级为横向能力 |
| AI 测验 | 保留并升级为 Assessment，可发布、可复核、可追溯版本 |
| 错题本 | 不再作为独立业务模块；错误事实进入 Quiz Attempt 与 Knowledge Gap |
| 学习记录 | 移出 V3 核心契约 |
| Topic mastery | 不再维护个人“掌握度公式”；改为项目级可解释统计 |
| AI 学习计划 | 用 Onboarding Path 替代 |
| 番茄钟 | 移出 V3 核心契约 |
| Learner Profile | 不作为持久业务实体；能力报告从评估事实生成 |
| Long-term Memory | 移出 V3 核心契约 |
| 学习 Dashboard | 改为 Project Insight |
| Agent Tool 学习画像 | 改为 Assignment / Assessment / Knowledge 只读上下文 |

## 11.3 V3 明确不做

为了围绕“研发负责人让新成员具备接手某服务的能力”保持主线，V3 暂不引入：

- 组织/部门/公司多级租户；
- 邮件邀请与消息通知系统；
- OAuth/SSO；
- 真正 Kubernetes/数据库/生产集群故障注入；
- 任意 Shell/SQL 执行 Agent；
- LMS 课程商城、积分、排行榜；
- 实时多人协作文档编辑；
- Kafka/RabbitMQ；
- Spring Cloud 微服务拆分；
- 复杂 CQRS/Event Sourcing。

故障演练采用“初始现象 → 用户动作 → 开放证据 → 最终结论 → 规则评分 + AI 解释 + 人工复核”的受控模拟，不直接操作真实生产基础设施。
