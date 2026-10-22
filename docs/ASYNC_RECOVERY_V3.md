# 异步任务中断恢复（V3）

> 版本：V3.0  
> 对应：DocumentVersion indexing、Quiz generation、CapabilityReport generation

V3 继续采用“**故障后结束等待，再由用户/管理员重新发起**”的最小恢复方案。

目标不是 exactly-once，也不是自动恢复执行；目标是：

```text
业务状态不会无限卡在执行中
旧任务不会覆盖新任务
迟到结果不会复活终态
用户能够明确重试
```

V3 仍不引入 Kafka/RabbitMQ/Celery 持久队列。

# 1. 覆盖资源

| 资源 | 执行态 | 正常终态 | 失败终态 |
| --- | --- | --- | --- |
| DocumentVersion | UPLOADED/PARSING/CHUNKING/EMBEDDING | READY | FAILED |
| Quiz | GENERATING | DRAFT/READY | FAILED |
| CapabilityReport | PROCESSING | READY | FAILED |

Drill 的确定性评分属于同步业务事务；AI explanation 失败不应让已完成的程序评分永远卡住，因此不作为本恢复模块的核心任务类型。

# 2. 通用工作方式

1. Spring Boot 先持久化业务记录；
2. 生成 `current_task_id`；
3. 调用现有异步/受控执行入口；
4. 运行期持续更新业务 `update_time`；
5. Spring Boot 启动完成和周期扫描时查找超时执行态；
6. 单条条件 UPDATE 将超时记录收敛为 FAILED；
7. 用户/管理员使用原业务动作重新发起；
8. 新任务替换 `current_task_id`；
9. 迟到回调/结果只有在 `taskId + 当前执行态` 同时匹配时才能提交。

# 3. DocumentVersion

## 3.1 正常链路

```text
Spring Boot
  |
  | create DocumentVersion
  | current_task_id = task_A
  v
FastAPI /internal/rag/index
  ↓
worker
  ↓
progress callback(task_A)
  ↓
READY
```

回调：

```text
POST /internal/callbacks/document-versions/{versionId}/progress
```

Spring Boot 更新条件至少包括：

```text
id = versionId
AND current_task_id = taskId
AND process_status IN execution states
```

## 3.2 旧回调

场景：

```text
task_A failed/stale
  ↓ reprocess
task_B becomes current
  ↓
task_A late READY callback arrives
```

结果：

```text
ignore task_A
```

绝不允许把 task_B 对应状态改回 task_A 的 READY。

## 3.3 READY 终态

READY 后迟到：

```text
PARSING/EMBEDDING callback
```

不得回退。

重复 READY 可以幂等确认，但不重复产生业务副作用。

# 4. Quiz Generation

Quiz 生成推荐由 Spring Boot 受控 executor 发起 FastAPI 同步调用。

```text
create Quiz(status=GENERATING, task_A)
  ↓
executor
  ↓
FastAPI /internal/quiz/generate
  ↓
Structured Output
  ↓
@Transactional
insert questions
update Quiz -> READY/DRAFT
```

最终提交必须：

```sql
UPDATE sa_quiz
SET status = 'READY', ...
WHERE id = ?
  AND current_task_id = ?
  AND status = 'GENERATING';
```

如果条件更新失败：

```text
ROLLBACK questions
```

防止超时扫描已经把 Quiz 置 FAILED 后，旧 executor 又写入题目并复活。

生成重试推荐创建新 revision/新 Quiz，而不是自动重放失败请求。

# 5. CapabilityReport

```text
create report(PROCESSING, task_A)
  ↓
Spring Boot builds facts_json
  ↓
FastAPI capability summary
  ↓
state-conditioned UPDATE
READY
```

报告生成结果提交：

```sql
UPDATE sa_capability_report
SET status='READY',
    ai_summary=?,
    risks_json=?,
    recommended_sources_json=?,
    generated_at=NOW()
WHERE id=?
  AND current_task_id=?
  AND status='PROCESSING';
```

若更新行数为 0：

```text
stale result
discard
```

用户重试时：

- 可以创建 `report_version + 1`；
- 或同一 FAILED report 生成新 taskId；
- 二选一必须在实现前固定，V3 推荐“新 reportVersion”，历史更清晰。

# 6. 扫描配置

建议环境变量：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `TASK_RECOVERY_ENABLED` | true | 启用启动与周期扫描 |
| `TASK_RECOVERY_SCAN_INTERVAL` | 30s | 扫描间隔 |
| `TASK_RECOVERY_DOCUMENT_VERSION_TIMEOUT` | 15m | 文档版本最后进度更新时间超时 |
| `TASK_RECOVERY_QUIZ_TIMEOUT` | 5m | Quiz GENERATING 超时 |
| `TASK_RECOVERY_REPORT_TIMEOUT` | 5m | CapabilityReport PROCESSING 超时 |
| `TASK_RECOVERY_BATCH_SIZE` | 100 | 每类每轮最大处理数 |
| `DOCUMENT_CALLBACK_ATTEMPTS` | 3 | FastAPI 回调总尝试次数 |
| `DOCUMENT_CALLBACK_RETRY_DELAY` | 0.25 | 首次退避秒数 |

如果当前实现仍使用旧变量名，可在一个兼容版本中同时读取旧名，但新文档和 `.env.example` 以 V3 名称为准。

# 7. 超时扫描 SQL 语义

DocumentVersion：

```sql
UPDATE sa_document_version
SET process_status = 'FAILED',
    error_message = '任务执行超时，请重新处理',
    update_time = CURRENT_TIMESTAMP
WHERE id IN (
  SELECT id
  FROM ...
  WHERE process_status IN ('UPLOADED','PARSING','CHUNKING','EMBEDDING')
    AND update_time < ?
  LIMIT ?
);
```

实际 MySQL 实现应规避同表 update/select 限制，可先查 ID 批次后逐批条件 UPDATE。

Quiz：

```text
status = GENERATING
AND update_time < deadline
```

Report：

```text
status = PROCESSING
AND update_time < deadline
```

每条 UPDATE 再带当前状态条件，避免扫描读取后与正常完成竞争。

# 8. 为什么不是“扫描到就自动重跑”

自动重跑需要持久化：

```text
完整请求快照
模型/Prompt版本
执行次数
退避
租约
副作用fencing
多实例竞争
任务优先级
死信
```

当前 V3 没有必要为了“企业级外观”引入整套队列系统。

失败后重新发起更符合项目目标：

```text
可解释
可观察
实现边界清楚
```

# 9. Callback 重试

Document callback：

- HTTP 408 / 429 / 5xx / network error：有限重试；
- 其他 4xx：不重试；
- HTTP 200 还必须检查统一响应 `code=1`；
- 请求体 taskId 和状态保持不变；
- 指数退避；
- 重试耗尽后由超时扫描结束业务等待。

回调客户端不得把 Spring Boot 返回的“旧 taskId 已忽略”当作可无限重试错误。

# 10. 状态竞争

## 10.1 扫描 vs 正常完成

扫描先成功：

```text
GENERATING -> FAILED
```

随后旧生成结果提交：

```text
WHERE status=GENERATING
0 rows
ROLLBACK
```

正常完成先成功：

```text
GENERATING -> READY
```

随后扫描：

```text
WHERE status=GENERATING
0 rows
```

## 10.2 重试 vs 旧结果

新任务：

```text
current_task_id task_B
```

旧 task_A 结果：

```text
WHERE current_task_id=task_A
0 rows
```

# 11. 扫描故障隔离

扫描器按资源类型隔离：

```text
recoverDocumentVersions()
recoverQuizzes()
recoverCapabilityReports()
```

一种资源扫描异常不应阻止其他资源。

单批失败：

- 记录错误；
- 不修改无关资源；
- 下一周期继续；
- 不让 scheduler 线程永久退出。

# 12. 手工重启演练

仅测试环境。

临时配置：

```text
TASK_RECOVERY_DOCUMENT_VERSION_TIMEOUT=60s
TASK_RECOVERY_QUIZ_TIMEOUT=60s
TASK_RECOVERY_REPORT_TIMEOUT=60s
TASK_RECOVERY_SCAN_INTERVAL=5s
```

## 12.1 DocumentVersion

1. 上传较慢文档；
2. 进入 PARSING/CHUNKING/EMBEDDING；
3. 停 FastAPI；
4. 超时后 Spring Boot 扫描 → FAILED；
5. 点击重新处理 → 新 taskId；
6. 延迟发送旧 taskId READY；
7. 确认旧回调不能覆盖新任务。

## 12.2 Quiz

1. 使用慢模型生成 Quiz；
2. GENERATING 后停止执行服务；
3. 超时 → FAILED；
4. 重新生成新 Quiz/revision；
5. 让旧结果延迟返回；
6. 确认旧事务条件更新失败且题目回滚。

## 12.3 CapabilityReport

1. Assignment 满足报告生成条件；
2. 创建 PROCESSING 报告；
3. 中断 AI 调用；
4. 超时 → FAILED；
5. 重新生成 reportVersion+1；
6. 旧 task 迟到；
7. 确认旧结果不能覆盖新报告。

# 13. 自动化测试

Spring Boot 至少覆盖：

```text
all DocumentVersion execution statuses
Quiz GENERATING timeout
Report PROCESSING timeout
recent task not timed out
terminal state protected
batch size
repeat scan idempotency
scan module fault isolation
taskId stale result
transaction rollback after state race
last update threshold boundary
```

FastAPI 至少覆盖：

```text
callback authentication
retryable/non-retryable HTTP
limited backoff
same callback body across retries
worker failure mapping
Milvus failure -> FAILED callback
```

真实 MySQL 测试使用随机测试库，不使用应用业务库。

# 14. 当前边界

V3 恢复机制仍然不提供：

- 自动重放 LLM；
- exactly-once；
- 持久执行租约；
- 持久重试计数；
- 请求快照队列；
- 多实例分布式 worker 协调；
- 上游计算强制终止；
- Milvus 写副作用 fencing；
- 管理员任务队列后台。

DocumentVersion 仍使用业务 `update_time` 代表最近进度；长时间没有 callback 的合法大任务可能被判超时，因此生产部署必须按文件规模调大阈值。

# 15. 最终结论

V3 的恢复语义是：

> 通过 taskId、状态条件更新、事务回滚和超时扫描，让异步业务状态最终收敛，并阻止旧任务覆盖当前事实。

它不是：

> 一个完整可靠消息队列或自动任务恢复系统。
