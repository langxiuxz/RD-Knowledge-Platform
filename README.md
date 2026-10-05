# StudyAgent

StudyAgent 是一个面向个人学习的 AI 学习平台，采用 Vue 3 + Spring Boot + FastAPI 的三层架构。浏览器只访问 Spring Boot 业务后端，FastAPI 仅作为内部 AI 服务使用。

## 核心功能

- 用户注册、登录、RefreshToken、个人资料和密码管理
- 个人知识库、文档上传、处理状态轮询和失败重试
- 文档解析、分块、向量索引、RAG 检索和引用来源
- CHAT / RAG / AGENT 三种 AI 对话模式
- LangGraph 编排、白名单 Tool Calling、SSE 流式回答和停止生成
- AI 测验、客观题确定性评分、简答题 AI 评分和错题本
- 学习记录、知识点进度、Dashboard、薄弱知识点
- AI 学习计划和计划任务完成状态

## 技术栈与架构

```text
Vue 3 + Vite + Element Plus + Pinia + Vue Router + Axios
                         |
                         v
Spring Boot 4 + Spring MVC + MyBatis + MySQL + Redis + JWT
                         |
                         v
FastAPI + Pydantic + LangChain/LangGraph + LLM + Vector Store
```

- `study-agent-web/`：前端 SPA，所有 REST 请求集中在 `src/api/`。
- `study-agent-server/`：业务事实、认证、权限、事务、MySQL、Redis 和文件存储。
- `study-agent-ai/`：文档处理、Embedding、RAG、LangGraph、Structured Output 和 AI 能力。
- `docs/`：API、数据库和架构契约。

## 环境要求

本地开发建议：

- JDK 17+
- Maven 3.9+
- Node.js 20+
- Python 3.11+
- MySQL 8+
- Redis 6+

当前实现的 MVP Vector Store 在无外部向量数据库时可使用内存适配器，重启 AI 服务后索引会丢失；生产环境应接入持久化 Vector Store。

## 环境变量

复制根目录 `.env.example`，或分别复制：

- `study-agent-server/.env.example`
- `study-agent-ai/.env.example`

常用变量：

| 变量 | 用途 |
| --- | --- |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Spring Boot 数据库连接 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 连接 |
| `JWT_SECRET` | JWT 签名密钥，生产环境必须替换 |
| `INTERNAL_TOKEN` | Spring Boot 与 FastAPI 内部认证令牌 |
| `AI_SERVICE_BASE_URL` | Spring Boot 调用 FastAPI 地址 |
| `LLM_PROVIDER` / `LLM_API_KEY` / `LLM_MODEL` / `LLM_BASE_URL` | LLM 配置；不配置时使用可运行的本地 fallback |
| `FILE_STORAGE_ROOT` | 文件存储目录 |
| `VITE_API_BASE_URL` | 前端 API 前缀，默认 `/api` |

不要把真实密码、JWT Secret、LLM API Key 或 Internal Token 提交到 Git。

## 数据库初始化

本项目使用 Flyway migration：

```text
study-agent-server/src/main/resources/db/migration/V1__init_schema.sql
```

本地已有数据库时可以先手工创建数据库：

```sql
CREATE DATABASE study_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

然后启动 Spring Boot，并设置 `FLYWAY_ENABLED=true` 完成初始化。仓库不包含真实数据库密码。

## 本地启动

### 1. 启动 MySQL / Redis

确保 MySQL 在 `3306`、Redis 在 `6379` 监听，并准备 `study_agent` 数据库。

### 2. 启动 FastAPI

```powershell
cd study-agent-ai
python -m venv .venv311
.venv311\Scripts\Activate.ps1
pip install -e ".[dev]"
$env:INTERNAL_TOKEN="replace-with-local-token"
$env:LLM_PROVIDER="none"
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

FastAPI 内部健康检查：`http://127.0.0.1:8000/internal/health`。它需要 `X-Internal-Token`。

### 3. 启动 Spring Boot

```powershell
cd study-agent-server
$env:SERVER_PORT="8080"
$env:DB_URL="jdbc:mysql://localhost:3306/study_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="<local-password>"
$env:JWT_SECRET="replace-with-a-long-local-secret"
$env:INTERNAL_TOKEN="replace-with-local-token"
$env:AI_SERVICE_BASE_URL="http://127.0.0.1:8000"
$env:FLYWAY_ENABLED="true"
./mvnw spring-boot:run # 或 mvn spring-boot:run
```

健康检查：`http://127.0.0.1:8080/api/health`。

### 4. 启动 Vue

```powershell
cd study-agent-web
npm ci
npm run dev
```

浏览器打开 `http://localhost:5173`。Vite 会把 `/api` 代理到 `http://localhost:8080`。

## Docker 启动

Docker Compose 会启动 MySQL、Redis、FastAPI、Spring Boot 和 Nginx/Vue。FastAPI、MySQL、Redis 只加入内部网络，不映射到宿主机公网端口。

```powershell
Copy-Item .env.example .env
# 编辑 .env，填写本地密码和需要的 LLM 配置

docker compose up --build
```

访问：`http://localhost`。

停止：

```powershell
docker compose down
```

删除本地开发卷（会删除数据库数据）前请确认目标：

```powershell
docker compose down -v
```

## LLM 配置

没有真实 LLM Key 也可以启动项目：`LLM_PROVIDER=none` 会使用本地 fallback，保证健康检查、基础联调和测试可以执行。需要真实模型时设置：

```text
LLM_PROVIDER=openai-compatible
LLM_API_KEY=...
LLM_MODEL=...
LLM_BASE_URL=https://api.openai.com/v1
```

## 测试

```powershell
# Vue
cd study-agent-web
npm run build

# FastAPI
cd ..\study-agent-ai
.venv311\Scripts\python.exe -m compileall -q app
.venv311\Scripts\python.exe -m pytest -q

# Spring Boot
cd ..\study-agent-server
mvn test
```

重点测试认证、资源归属、JWT、文档状态回调、RAG 隔离、Tool 白名单、Structured Output、SSE 和测验提交事务。

## 主要页面截图预留

后续可以将演示截图放在：

```text
README-assets/
├── dashboard.png
├── knowledge-base.png
├── chat-sse.png
├── quiz.png
└── study-plan.png
```

## 常见问题

### 页面打开但 API 失败

检查 Spring Boot 是否运行、`VITE_API_BASE_URL` 是否正确，以及浏览器请求是否通过 `/api` 进入 Spring Boot。

### FastAPI 健康检查未通过

检查 Spring Boot 和 FastAPI 的 `INTERNAL_TOKEN` 是否一致。不要让浏览器直接访问 `/internal/**`。

### 没有 LLM Key 能否启动？

可以。将 `LLM_PROVIDER=none`，AI 服务会使用本地可运行的 fallback；真实生成质量需要配置兼容 OpenAI API 的模型。

### RAG 索引重启后消失

当前默认 Vector Store 是 MVP 内存适配器。生产部署需要接入持久化向量数据库或对象存储索引。

### SSE 没有实时输出

确认 Nginx 的 SSE location 保持 `proxy_buffering off`，并使用前端 `fetch + ReadableStream`，不要改成原生 EventSource。

## 当前状态

项目已达到本地可运行、可演示的 MVP 整合状态。生产化仍需要持久化 Vector Store、完整监控、HTTPS、限流和更完整的端到端自动化测试。
