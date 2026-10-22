# Milvus 持久化向量存储（V3）

> 版本：V3.0  
> 对应：`API_V3.md`、`DATABASE_V3.md`、`ARCHITECTURE_V3.md`

AI 服务通过 `MilvusVectorStore` 使用 Milvus Standalone 2.6.3，Python SDK 固定为 `pymilvus==2.6.3`。Spring Boot 继续负责 Project 权限、KnowledgeSnapshot、发布状态和 MySQL；Milvus 只保存可检索的文档版本 Chunk/Embedding。

V3 的关键变化：

```text
V2: user_id + knowledge_base_id
V3: project_id + knowledge_base_id + allowed document_version_ids
```

共享项目知识不能继续按上传用户隔离。

# 1. Collection 与 Schema

建议新建 collection：

```text
study_agent_project_hash_v2
```

不要把 V3 schema 直接写入旧的 `study_agent_local_hash_v1`。

Embedding 暂时继续使用：

```text
model: local-hash-v1
dimension: 64
index: AUTOINDEX
metric: COSINE
consistency: Strong
```

更换真实 Embedding 模型属于独立重建，不与 V3 业务迁移混合。

## 1.1 字段

至少持久化：

| 字段 | 说明 |
| --- | --- |
| `pk` | SHA-256 主键 |
| `chunk_id` | 文档版本内 Chunk ID |
| `project_id` | Project ID |
| `knowledge_base_id` | KnowledgeBase ID |
| `document_id` | 逻辑 Document ID |
| `document_version_id` | 不可变 DocumentVersion ID |
| `file_name` | 文件名快照 |
| `page` | 页码，可空 |
| `chunk_index` | 文档版本内顺序 |
| `content_hash` | 文档内容 SHA-256 |
| `embedding_model` | Embedding 模型 |
| `content` | Chunk 文本 |
| `embedding` | 向量 |
| `metadata` | 扩展 JSON |

主键建议：

```text
SHA256(
  project_id
  + knowledge_base_id
  + document_version_id
  + chunk_id
)
```

同一 task 重试 upsert 不重复插入；不同 Project/Version 不互相覆盖。

# 2. 检索隔离

Spring Boot 负责：

```text
JWT
  ↓
Project membership
  ↓
KnowledgeSnapshot
  ↓
snapshot items
  ↓
allowed documentVersionIds
```

FastAPI/Milvus 负责防御性过滤：

```text
project_id == projectId
AND knowledge_base_id == knowledgeBaseId
AND document_version_id IN documentVersionIds
```

要求：

- `documentVersionIds` 为空时直接返回空结果，不允许退化成“整个知识库”；
- FastAPI 不自行从 userId 推导知识权限；
- LEARNER 的版本集合必须来自已发布 KnowledgeSnapshot；
- OWNER/MAINTAINER 草稿预览必须由 Spring Boot 显式下发版本 ID，不能接受浏览器任意版本集合；
- 检索使用 Strong 一致性。

V3 正式向量过滤不包含 `user_id == ...`。用户访问权已经在 Spring Boot 的 Project membership 层决定。

# 3. READY 与 PUBLISHED 的区别

Milvus 只参与处理状态：

```text
parse -> chunk -> embedding -> upsert -> flush -> READY
```

Knowledge 发布状态在 MySQL：

```text
DRAFT -> IN_REVIEW -> APPROVED -> PUBLISHED
```

因此：

```text
Milvus中存在向量
≠
Learner可以正式检索
```

只有 Spring Boot 依据 KnowledgeSnapshot 下发允许版本时，这些向量才进入正式 RAG。

# 4. 写入

DocumentVersion 入库：

1. 校验内部服务令牌；
2. 校验 `projectId/knowledgeBaseId/documentId/documentVersionId/taskId`；
3. 从 Spring Boot 内部文件接口读取已登记文件；
4. 解析；
5. Chunk；
6. Embedding；
7. upsert；
8. flush；
9. 回调 READY；
10. Spring Boot 仅在 taskId 仍为当前任务时更新业务状态。

任何 Milvus 写入/flush 失败：

```text
DocumentVersion -> FAILED
```

不得自动切换到内存后端并假装成功。

# 5. 删除与归档

## 5.1 未发布版本

Spring Boot 确认版本未被 KnowledgeSnapshot/历史业务引用后，可调用：

```text
DELETE /internal/rag/document-versions/{versionId}
```

过滤：

```text
project_id
knowledge_base_id
document_version_id
```

删除幂等。

## 5.2 已发布历史版本

若被任一 KnowledgeSnapshot 引用：

```text
禁止物理删除向量
```

因为历史 Assignment、Citation、CapabilityReport 可能需要回放。

逻辑 Document 归档不等于 Milvus 删除。

## 5.3 KnowledgeBase / Project 清理

仅以下场景允许批量清理：

- 测试资源；
- 明确的数据迁移清理；
- 项目已被允许做最终物理销毁；
- Spring Boot 已确认不存在需要保留的快照/评估/审计引用。

接口：

```text
DELETE /internal/rag/knowledge-bases/{knowledgeBaseId}
DELETE /internal/rag/projects/{projectId}
```

不能把“归档项目”直接实现成“清空 Milvus”。

# 6. Docker Compose

仓库根目录首次部署：

```powershell
Copy-Item .env.example .env
# 修改数据库、JWT、内部令牌及 MinIO 凭据
docker compose up -d --build
```

Compose 继续使用独立：

```text
Milvus
etcd
MinIO
```

命名卷：

| 命名卷 | 用途 |
| --- | --- |
| `milvus-data` | Milvus 本地数据与运行文件 |
| `milvus-etcd-data` | metadata |
| `milvus-minio-data` | Milvus segment/object |

`docker compose down` 后再次 `up` 保留数据；`docker compose down -v` 会删除卷。

生产默认不发布 Milvus/etcd/MinIO 端口到公网。

# 7. 环境变量

FastAPI：

```dotenv
VECTOR_STORE_PROVIDER=milvus
MILVUS_URI=http://localhost:19530
MILVUS_TOKEN=
MILVUS_DB_NAME=default
MILVUS_COLLECTION=study_agent_project_hash_v2
MILVUS_TIMEOUT=10
```

宿主机独立运行 FastAPI 时：

```powershell
docker compose -f docker-compose.yml -f docker-compose.milvus-dev.yml up -d milvus
```

然后：

```powershell
.venv311\Scripts\python.exe -m pip install -e ".[dev]"
.venv311\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

`MILVUS_TIMEOUT` 单位秒，建议范围 `>0 && <=120`。

SDK 导入不连接 Milvus；连接与 Collection schema 校验在 FastAPI lifespan 启动阶段执行。

# 8. Collection 兼容性检查

启动时若 Collection 已存在，必须校验：

```text
primary key
required scalar fields
vector dimension
metric
index compatibility
```

不兼容：

```text
fail fast
```

不得：

```text
drop existing collection automatically
clear data automatically
silently create another random collection
fallback to memory
```

# 9. V2 → V3 向量迁移

旧 V2 collection：

```text
study_agent_local_hash_v1
user_id + knowledge_base_id isolation
```

V3：

```text
study_agent_project_hash_v2
project_id + knowledge_base_id + document_version_id
```

不做原地 schema 迁移。

推荐：

1. 部署 V3 collection；
2. 先完成 MySQL Project/DocumentVersion/KnowledgeSnapshot 迁移；
3. 对需要保留的正式文档版本重新走 DocumentVersion indexing；
4. 校验 V3 `READY`；
5. 创建/校验 KnowledgeSnapshot；
6. 使用相同 snapshot 做 RAG 验证；
7. 重启 FastAPI/Milvus 后再次验证；
8. V3 稳定后再决定是否清理旧 collection。

旧内存索引没有可靠副本，不能从 MySQL `READY` 自动推断 Milvus 已有向量。

# 10. 内存后端

```text
VECTOR_STORE_PROVIDER=memory
```

仅用于明确接受重启丢失的测试/临时开发。

V3 测试必须显式选择内存后端，生产配置不允许自动 fallback。

# 11. Integration Test

单元测试不能代替真实 Milvus。

配置：

```powershell
$env:MILVUS_TEST_URI="http://localhost:19530"
.venv311\Scripts\python.exe -m pytest -q tests/integration/test_milvus_persistence.py
```

真实测试使用随机临时 collection，最后只删除自己创建的 collection。

至少验证：

```text
1. upsert idempotency
2. Project A / Project B isolation
3. same user in two projects remains isolated
4. same knowledge_base numeric id in different project does not cross
5. snapshot 11 version set cannot retrieve version only in snapshot 12
6. new document version can coexist with old version
7. delete unpublished version removes only that version
8. historical published version remains retrievable when requested by old snapshot
9. process restart keeps data
10. strong read after flush
```

未配置 `MILVUS_TEST_URI` 时允许 integration test skip，但发布前应执行真实环境验收。

# 12. 安全

- Milvus 只在内部网络暴露；
- 认证环境使用 secret 注入 `MILVUS_TOKEN`；
- 日志不输出原始 SDK 凭据；
- API 错误不返回 Milvus 原始连接串/Token；
- `project_id` 和 `document_version_id` 过滤必须在 adapter 层集中实现，避免业务调用漏过滤；
- 不提供任意 expression 给浏览器，过滤表达式由服务端构造。

# 13. 最终结论

V3 中 Milvus 解决的是：

> 对“不可变文档版本 Chunk”做持久语义检索。

它不解决：

> 哪个用户有权限、哪版资料已审核、哪个快照是训练依据。

后者全部由 Spring Boot + MySQL 决定。
