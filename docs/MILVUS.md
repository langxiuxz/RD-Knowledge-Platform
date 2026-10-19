# Milvus 持久化向量存储

AI 服务默认通过 `MilvusVectorStore` 使用 Milvus Standalone 2.6.3，Python SDK 固定为 `pymilvus==2.6.3`。Spring Boot 继续负责业务权限和 MySQL；公共 API、业务数据库及文档状态契约均不变。

## 存储和隔离

- 集合默认名 `study_agent_local_hash_v1`。仅在集合不存在时创建；已有集合须通过字段、主键、向量维度和相似度检查，不清空或自动迁移不兼容集合。
- `embedding` 使用当前 `local-hash-v1` 的 64 维向量和 `AUTOINDEX + COSINE`。本次替换存储后端，不改变 Embedding 模型。
- 主键由 `user_id + knowledge_base_id + chunk_id` 的 SHA-256 生成，重试 upsert 不重复插入，同名 chunk 不会覆盖其他用户或知识库。
- 持久化 `chunk_id/user_id/knowledge_base_id/document_id/file_name/page/chunk_index/embedding_model/content/embedding`，并保存扩展 JSON metadata。
- 检索始终过滤 `user_id + knowledge_base_id`；文档删除额外过滤 `document_id`。删除幂等。
- 检索采用 Strong 一致性；写入与删除均等待 flush 成功，文档成功后才回报 READY。
- 同步 SDK 在线程中执行，RPC 配置超时。启动连接或集合校验失败会阻止服务启动；运行期错误映射为现有 `AI_SERVICE_ERROR`，不自动退回内存；适配器对外错误和日志不会包含 SDK 原始错误内容。

## Docker Compose

仓库根目录首次部署：

```powershell
Copy-Item .env.example .env
# 修改 .env 中数据库、JWT、内部令牌及 MinIO 凭据占位符
docker compose up -d --build
```

如果已有 `.env`，保留该文件并为新增的 Milvus 变量补充值。Compose 使用同一 MinIO 用户/密码启动 MinIO 并配置 Milvus。Milvus 等待专用 etcd/MinIO 健康后启动，AI 再等待 Milvus 健康后启动。Standalone 按该版本官方部署配置启用 Woodpecker WAL，并配置 `seccomp:unconfined` 以支持其底层异步 I/O。生产 Compose 默认不发布 Milvus、etcd、MinIO 端口。

| 命名卷 | 用途 |
| --- | --- |
| `milvus-data` | Milvus 本地数据及运行文件 |
| `milvus-etcd-data` | 集合与索引元数据 |
| `milvus-minio-data` | Milvus 数据对象与向量段 |

`docker compose restart ai milvus` 及 `docker compose down` 后再次 `up` 保留数据，前提是使用同一 Compose 项目和数据卷。备份/恢复应协调备份 Milvus 依赖的全部数据，不能只复制 `milvus-data`。`docker compose down -v` 删除数据卷，不是保留数据的重启命令。

宿主机单独运行 FastAPI 时，可只启动数据库并将服务端口限定到回环地址：

```powershell
docker compose -f docker-compose.yml -f docker-compose.milvus-dev.yml up -d milvus
```

在 `study-agent-ai/.env` 设置：

```dotenv
VECTOR_STORE_PROVIDER=milvus
MILVUS_URI=http://localhost:19530
MILVUS_TOKEN=
MILVUS_DB_NAME=default
MILVUS_COLLECTION=study_agent_local_hash_v1
MILVUS_TIMEOUT=10
```

再执行：

```powershell
.venv311\Scripts\python.exe -m pip install -e ".[dev]"
.venv311\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

也可连接已有 Milvus HTTP URI，无需启动 Compose 数据库。若启用了 Milvus 认证，通过秘密管理注入 `MILVUS_TOKEN`（PyMilvus 格式通常为 `username:password`）；非 default 数据库需预先创建。Compose 当前依靠私有网络保护 Milvus，未配置数据库用户认证，切勿把 Milvus 端口发布到公网。

`MILVUS_TIMEOUT` 单位为秒，范围大于 0 至 120，默认 10。大批量写入或冷启动超时可提高。SDK 导入不连接 Milvus；连接与集合检查在 FastAPI lifespan 启动阶段执行。

## 内存后端迁移

1. 部署 Milvus 并安装新增依赖。
2. 检查 AI 服务实际使用的 `.env`，将旧值 `VECTOR_STORE_PROVIDER=none` 或 `memory` 改为 `milvus`，核对 URI、数据库与集合。Compose 已固定使用 Milvus。
3. 之前的内存索引没有副本，不能从 MySQL 自动恢复。已有 READY 文档须通过现有业务上传流程重新上传并建立索引；MySQL 中 READY 状态不保证 Milvus 已有向量。不得手工修改文档状态，也不得让 AI 服务直连业务 MySQL。
4. 验证文档 READY、知识库检索正确后，重启 FastAPI 和 Milvus，再查询同一知识库和引用。

`VECTOR_STORE_PROVIDER=memory`（及兼容旧值 `none`）仅用于明确接受重启丢失的临时开发和测试。不支持的 provider 或非法集合名在配置阶段报错。普通测试显式使用内存后端，不触碰生产数据库。

未来更换 Embedding 模型时，须另行选定模型、维度、集合和完整重建方案，不可把不兼容向量混入当前集合。

## 验证

```powershell
cd study-agent-ai
.venv311\Scripts\python.exe -m compileall -q app
.venv311\Scripts\python.exe -m pytest -q
```

真实服务器集成测试通过随机命名的临时集合，跨 Python 进程写入并检索，检查去重、隔离、删除，最后只删除自己创建的集合：

```powershell
$env:MILVUS_TEST_URI="http://localhost:19530"
# 需要认证或自定义数据库时再设置 MILVUS_TEST_TOKEN / MILVUS_TEST_DB_NAME。
.venv311\Scripts\python.exe -m pytest -q tests/integration/test_milvus_persistence.py
```

未配置 `MILVUS_TEST_URI` 时集成测试会报告 skip；SDK 单元测试不能代替真实数据库验收。生产环境应另行在业务检索成功后执行 `docker compose restart ai milvus`，等待健康检查恢复后复查相同知识库。集成测试本身不声称已验证 Docker 卷重启。
