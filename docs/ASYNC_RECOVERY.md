# 异步任务中断恢复

本轮采用“故障后结束等待，再由用户重新发起”的最小方案，覆盖文档入库、测验生成、学习计划生成。
原 Worker、前端轮询和 API 都保留；不增加业务表，也不引入消息中间件。

## 工作方式

1. 服务端先持久化业务记录，再沿用原异步执行入口。
2. Spring Boot 启动完成及周期扫描时，从 MySQL 识别长期停留在执行态的记录。
3. 单条有条件 UPDATE 将超时记录改为 FAILED，并写入可展示的重试提示。
4. 用户通过已有操作重新发起；文档重处理会更换 taskId，测验和计划生成新记录。
5. 已结束任务不会被迟到回调重新变为 READY；生成提交的竞争失败会回滚题目/计划任务。

这不是自动恢复执行、持久队列或 exactly-once。它保证的是超时业务状态能收敛；
检查发生在阈值以后，实例停机期间不能扫描，积压超过批量时需要多轮。

## 配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| TASK_RECOVERY_ENABLED | true | 是否启用启动与定期扫描 |
| TASK_RECOVERY_SCAN_INTERVAL | 30s | 扫描完成到下一轮启动的间隔 |
| TASK_RECOVERY_DOCUMENT_TIMEOUT | 15m | 文档最后一次进度更新后的超时 |
| TASK_RECOVERY_GENERATION_TIMEOUT | 5m | 测验/计划最后更新时间后的超时，包含排队 |
| TASK_RECOVERY_BATCH_SIZE | 100 | 每类每轮最大更新数，1～1000 |
| DOCUMENT_CALLBACK_ATTEMPTS | 3 | 每次文档回调最多总投递次数，1～5 |
| DOCUMENT_CALLBACK_RETRY_DELAY | 0.25 | 首次退避秒数，后续指数增加，单次不超过 5 秒 |

Spring Boot 通过 application.yml 的环境变量占位符读取配置；Docker Compose 已透传这些变量。
FastAPI 通过 Settings 读取回调配置。长文件和慢模型应调大超时，避免将仍在工作的任务判为超时。
文档回调成功需业务确认 code=1；HTTP 408/429/5xx 和网络错误重试，其他 4xx 直接失败。

## 自动化验证

```powershell
cd D:\StudyAgent\study-agent-server
mvn test

cd D:\StudyAgent\study-agent-ai
.\.venv311\Scripts\python.exe -m pytest -q
```

真实 MySQL 测试默认跳过，提供以下环境变量后启用；账号需要临时建库/删库权限。
测试会生成独立的随机 `studyagent_recovery_test_<uuid>` 数据库，加载项目现有 V1 schema，
完成后仅删除该随机测试库，不使用应用业务库。

```powershell
$env:TEST_MYSQL_URL="jdbc:mysql://localhost:3306/?serverTimezone=Asia/Shanghai"
$env:TEST_MYSQL_USERNAME="your-test-user"
$env:TEST_MYSQL_PASSWORD="your-test-password"
mvn -Dtest=TaskRecoveryMySqlTest test
```

覆盖：四个文档执行阶段、测验/计划超时、近期任务与终态保护、重复扫描、分批处理、重处理竞争、
旧 taskId 保护，以及超时发生在生成结果读写之间时真实 MySQL 事务对子记录的回滚。
单元测试还覆盖扫描模块故障隔离、配置绑定/非法值、回调认证与有限退避、队列拒绝和迟到生成结果。

## 手工重启演练（仅测试环境）

1. 临时设置 `TASK_RECOVERY_DOCUMENT_TIMEOUT=60s`、`TASK_RECOVERY_GENERATION_TIMEOUT=60s`、
   `TASK_RECOVERY_SCAN_INTERVAL=5s`，重启 Spring Boot 使配置生效。
2. 使用足够慢的文档或模型启动一个任务，确认处于执行态后终止执行它的服务。
3. 重启服务：阈值以内仍等待；超过阈值后，启动检查或后续扫描应令其 FAILED，页面查询能看到原因。
4. 文档点击重处理，应产生新 taskId；测验/计划重新发起生成，应创建新记录并完成。
5. 在测试中延迟一个旧回调，确认它无法把 FAILED 或新 taskId 对应记录改回 READY。
6. 完成演练后恢复默认阈值，避免影响正常长任务。

真实 MySQL 自动化验证模拟持久记录保留和进程内状态丢失，不等同于已经执行完整三服务 kill/restart 演练。

## 当前边界

- 不自动重放 LLM 请求，不记录持久重试次数，不提供新的人工管理后台。
- 无执行心跳，使用已有业务更新时间；活跃但长时间无进度的任务也会超时。
- 不终止已超时的上游计算，不提供向量副作用 fencing，旧新文档 Worker 重叠运行仍需后续治理。
- 大表扫描尚未加索引，后续需要依据实际负载评估并单独提出数据库迁移。
