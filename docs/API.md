  
StudyAgent  
接口文档

V2.0 · Java Web + LangChain/LangGraph AI 智能学习平台

| 文档项 | 内容 |
| --- | --- |
| 项目名称 | StudyAgent |
| 版本 | V1.0 |
| 前端 | Vue 3 + Vite + Element Plus + Axios |
| 业务后端 | Spring Boot + MyBatis + MySQL + Redis |
| AI服务 | FastAPI + LangChain + LangGraph |
| 认证 | JWT |
| 接口风格 | RESTful + JSON；AI流式响应使用SSE |

# 修订记录

V2.0 为自查修订版：统一响应定义、补充令牌续期、修正 SSE、补全 AI 内部流式链路、明确异步任务与状态枚举，并修正文档中的路径/示例一致性问题。

| 版本 | 主要修订 |
| --- | --- |
| V2.0 | 接口一致性、安全性、流式链路、异步任务、响应字段说明与示例修订 |
| V1.0 | StudyAgent 首版接口设计 |

# 0. 接口约定

文档采用“模块 → 接口 → 基本信息 → 请求参数 → 响应数据 → 备注说明”的结构。浏览器只访问 Spring Boot 对外接口；FastAPI/LangGraph 为内部 AI 服务。 V2.0 规定：路径参数不重复放入 JSON Body；业务接口统一返回 Result<T>，SSE 接口除外。

| 项目 | 约定 |
| --- | --- |
| API前缀 | /api |
| 默认Content-Type | application/json |
| 时间格式 | yyyy-MM-dd'T'HH:mm:ss |
| 分页 | page从1开始；pageSize默认10；返回total + rows |
| 认证头 | Authorization: Bearer <JWT> |
| 成功响应 | code=1 |
| SSE | Content-Type: text/event-stream |
| Token续期 | accessToken 短时有效；refreshToken 仅用于 /api/auth/refresh |
| 幂等性 | 删除、任务状态更新等接口需保证重复请求不会产生额外副作用 |
| 资源归属 | 所有带资源ID的接口均按当前用户校验资源归属 |
| 异步任务 | 文档入库、AI测验/计划生成可返回 PROCESSING，客户端轮询对应详情/状态接口 |

| HTTP状态码 | 说明 |
| --- | --- |
| 200 | 成功 |
| 201 | 创建成功 |
| 400 | 参数错误 |
| 401 | 未登录/令牌失效 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 资源冲突 |
| 413 | 文件过大 |
| 422 | 业务校验失败 |
| 429 | 请求过频 |
| 500 | 服务异常 |
| 503 | AI服务不可用 |

# 1. 用户与认证

## 1.1 用户注册

### 1.1.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/auth/register |
| 请求方式 | POST |
| 接口描述 | 创建用户账号。 |

### 1.1.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| username | string | 是 | 4~32位，唯一 |
| password | string | 是 | 8~64位 |
| nickname | string | 是 | 昵称 |
| email | string | 否 | 邮箱 |

请求参数样例：

```text
{
  "username": "daqiao",
  "password": "Study@123456",
  "nickname": "大乔",
  "email": "daqiao@example.com"
}
```

### 1.1.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.id | number | 否 | 响应业务字段 |
| data.username | string | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "id": 1,
    "username": "daqiao"
  }
}
```

## 1.2 用户登录

### 1.2.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/auth/login |
| 请求方式 | POST |
| 接口描述 | 校验账号密码并下发JWT。 |

### 1.2.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| username | string | 是 | 用户名 |
| password | string | 是 | 密码 |

请求参数样例：

```text
{
  "username": "daqiao",
  "password": "Study@123456"
}
```

### 1.2.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.user | object | 否 | 响应业务字段 |
| data.user.id | number | 否 | 响应业务字段 |
| data.user.username | string | 否 | 响应业务字段 |
| data.user.nickname | string | 否 | 响应业务字段 |
| data.token | string | 否 | 响应业务字段 |
| data.expiresIn | number | 否 | 响应业务字段 |
| data.refreshToken | string | 否 | 响应业务字段 |
| data.refreshExpiresIn | number | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "user": {
      "id": 1,
      "username": "daqiao",
      "nickname": "大乔"
    },
    "token": "eyJhbGciOiJIUzI1NiJ9.xxx",
    "expiresIn": 7200,
    "refreshToken": "rt_7c2dxxxx",
    "refreshExpiresIn": 604800
  }
}
```

### 1.2.4 备注说明

除注册、登录和健康检查外，其余对外接口默认携带 Authorization: Bearer <JWT>。

## 1.3 退出登录

### 1.3.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/auth/logout |
| 请求方式 | POST |
| 接口描述 | 退出当前登录状态。 |

### 1.3.2 请求参数

参数格式：无

无

### 1.3.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 1.4 获取当前用户

### 1.4.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/users/me |
| 请求方式 | GET |
| 接口描述 | 查询当前登录用户资料。 |

### 1.4.2 请求参数

参数格式：无

无

### 1.4.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.id | number | 否 | 响应业务字段 |
| data.username | string | 否 | 响应业务字段 |
| data.nickname | string | 否 | 响应业务字段 |
| data.avatar | string | 否 | 响应业务字段 |
| data.email | string | 否 | 响应业务字段 |
| data.createTime | string | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "id": 1,
    "username": "daqiao",
    "nickname": "大乔",
    "avatar": "https://cdn.example.com/a.png",
    "email": "daqiao@example.com",
    "createTime": "2026-10-04T10:00:00"
  }
}
```

## 1.5 修改个人资料

### 1.5.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/users/me |
| 请求方式 | PUT |
| 接口描述 | 修改昵称、头像和邮箱。 |

### 1.5.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| nickname | string | 否 | 昵称 |
| avatar | string | 否 | 头像URL |
| email | string | 否 | 邮箱 |

请求参数样例：

```text
{
  "nickname": "大乔同学",
  "avatar": "https://cdn.example.com/a.png",
  "email": "daqiao@example.com"
}
```

### 1.5.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 1.6 修改密码

### 1.6.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/users/me/password |
| 请求方式 | PUT |
| 接口描述 | 修改当前用户密码。 |

### 1.6.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| oldPassword | string | 是 | 原密码 |
| newPassword | string | 是 | 新密码 |

请求参数样例：

```text
{
  "oldPassword": "Study@123456",
  "newPassword": "Study@654321"
}
```

### 1.6.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 1.7 刷新访问令牌

### 1.7.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/auth/refresh |
| 请求方式 | POST |
| 接口描述 | 使用有效 refreshToken 换取新的 accessToken，避免 accessToken 过期后要求用户重新登录。 |

### 1.7.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| refreshToken | string | 是 | 登录接口返回的刷新令牌 |

请求参数样例：

```text
{
  "refreshToken": "rt_7c2dxxxx"
}
```

### 1.7.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / null | 否 | 业务数据 |
| data.token | string | 否 | 响应业务字段 |
| data.expiresIn | number | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.new",
    "expiresIn": 7200
  }
}
```

### 1.7.4 备注说明

refreshToken 失效、撤销或超过有效期时返回 401；刷新令牌应支持服务端撤销。

# 2. 知识库管理

## 2.1 知识库列表查询

### 2.1.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/knowledge-bases |
| 请求方式 | GET |
| 接口描述 | 条件分页查询当前用户知识库。 |

### 2.1.2 请求参数

参数格式：queryString

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| name | string | 否 | 名称模糊查询 |
| status | string | 否 | PROCESSING/READY/FAILED |
| page | number | 是 | 页码 |
| pageSize | number | 是 | 每页条数 |

请求参数样例：

```text
/api/knowledge-bases?name=Java&status=READY&page=1&pageSize=10
```

### 2.1.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.total | number | 否 | 响应业务字段 |
| data.rows | array | 否 | 响应业务字段 |
| data.rows[].id | number | 否 | 响应业务字段 |
| data.rows[].name | string | 否 | 响应业务字段 |
| data.rows[].documentCount | number | 否 | 响应业务字段 |
| data.rows[].chunkCount | number | 否 | 响应业务字段 |
| data.rows[].status | string | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "total": 1,
    "rows": [
      {
        "id": 12,
        "name": "Java Web复习库",
        "documentCount": 8,
        "chunkCount": 326,
        "status": "READY"
      }
    ]
  }
}
```

## 2.2 创建知识库

### 2.2.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/knowledge-bases |
| 请求方式 | POST |
| 接口描述 | 创建个人知识库。 |

### 2.2.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| name | string | 是 | 1~64位 |
| description | string | 否 | 描述 |
| embeddingModel | string | 否 | 不传使用系统默认 |

请求参数样例：

```text
{
  "name": "Java Web复习库",
  "description": "Java、Spring、MyBatis学习资料"
}
```

### 2.2.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.id | number | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "id": 12
  }
}
```

## 2.3 知识库详情

### 2.3.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/knowledge-bases/{id} |
| 请求方式 | GET |
| 接口描述 | 根据ID查询知识库详情。 |

### 2.3.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 知识库ID |

请求参数样例：

```text
/api/knowledge-bases/12
```

### 2.3.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.id | number | 否 | 响应业务字段 |
| data.name | string | 否 | 响应业务字段 |
| data.description | string | 否 | 响应业务字段 |
| data.documentCount | number | 否 | 响应业务字段 |
| data.chunkCount | number | 否 | 响应业务字段 |
| data.status | string | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "id": 12,
    "name": "Java Web复习库",
    "description": "Java、Spring、MyBatis学习资料",
    "documentCount": 8,
    "chunkCount": 326,
    "status": "READY"
  }
}
```

## 2.4 修改知识库

### 2.4.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/knowledge-bases/{id} |
| 请求方式 | PUT |
| 接口描述 | 修改名称和描述。 |

### 2.4.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 路径参数，不属于 JSON Body |
| name | string | 是 | 名称 |
| description | string | 否 | 描述 |

请求参数样例：

```text
{
  "name": "Java全栈知识库",
  "description": "Java Web + AI"
}
```

### 2.4.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 2.5 删除知识库

### 2.5.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/knowledge-bases/{id} |
| 请求方式 | DELETE |
| 接口描述 | 删除知识库及文档索引、向量数据。 |

### 2.5.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 知识库ID |

请求参数样例：

```text
/api/knowledge-bases/12
```

### 2.5.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

### 2.5.4 备注说明

不可逆操作；必须校验资源归属。

## 2.6 上传知识库文档

### 2.6.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/knowledge-bases/{id}/documents |
| 请求方式 | POST |
| 接口描述 | 上传文档并异步执行解析、切分、Embedding和向量入库。 |

### 2.6.2 请求参数

参数格式：multipart/form-data

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 知识库ID |
| file | file | 是 | PDF/DOCX/TXT/MD，建议≤20MB |

请求参数样例：

```text
form-data: file=@Spring学习笔记.pdf
```

### 2.6.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.documentId | number | 否 | 响应业务字段 |
| data.status | string | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "documentId": 101,
    "status": "PROCESSING"
  }
}
```

### 2.6.4 备注说明

接口按 202 Accepted 语义创建异步任务，不等待解析和向量化完成。前端轮询 2.8。状态统一为 UPLOADED/PARSING/CHUNKING/EMBEDDING/READY/FAILED。

## 2.7 文档列表查询

### 2.7.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/knowledge-bases/{id}/documents |
| 请求方式 | GET |
| 接口描述 | 分页查询知识库文档。 |

### 2.7.2 请求参数

参数格式：queryString

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 知识库ID |
| fileName | string | 否 | 文件名 |
| status | string | 否 | 状态 |
| page | number | 是 | 页码 |
| pageSize | number | 是 | 每页条数 |

请求参数样例：

```text
/api/knowledge-bases/12/documents?status=READY&page=1&pageSize=10
```

### 2.7.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.total | number | 否 | 响应业务字段 |
| data.rows | array | 否 | 响应业务字段 |
| data.rows[].id | number | 否 | 响应业务字段 |
| data.rows[].fileName | string | 否 | 响应业务字段 |
| data.rows[].fileType | string | 否 | 响应业务字段 |
| data.rows[].fileSize | number | 否 | 响应业务字段 |
| data.rows[].status | string | 否 | 响应业务字段 |
| data.rows[].chunkCount | number | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "total": 1,
    "rows": [
      {
        "id": 101,
        "fileName": "Spring学习笔记.pdf",
        "fileType": "pdf",
        "fileSize": 2457600,
        "status": "READY",
        "chunkCount": 86
      }
    ]
  }
}
```

## 2.8 查询文档处理状态

### 2.8.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/documents/{id}/status |
| 请求方式 | GET |
| 接口描述 | 查询解析与向量化进度。 |

### 2.8.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 文档ID |

请求参数样例：

```text
/api/documents/101/status
```

### 2.8.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.status | string | 否 | 响应业务字段 |
| data.progress | number | 否 | 响应业务字段 |
| data.errorMessage | null | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "status": "EMBEDDING",
    "progress": 75,
    "errorMessage": null
  }
}
```

### 2.8.4 备注说明

progress 取值 0~100；FAILED 时 errorMessage 必须返回；READY 时 progress=100。

## 2.9 重新处理文档

### 2.9.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/documents/{id}/reprocess |
| 请求方式 | POST |
| 接口描述 | 对失败文档重新发起处理。 |

### 2.9.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 文档ID |

请求参数样例：

```text
/api/documents/101/reprocess
```

### 2.9.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.taskId | string | 否 | 响应业务字段 |
| data.status | string | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "taskId": "task_8f31",
    "status": "PROCESSING"
  }
}
```

## 2.10 删除知识库文档

### 2.10.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/documents/{id} |
| 请求方式 | DELETE |
| 接口描述 | 删除文档、切片及对应向量。 |

### 2.10.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 文档ID |

请求参数样例：

```text
/api/documents/101
```

### 2.10.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

# 3. AI对话与会话管理

## 3.1 创建会话

### 3.1.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/conversations |
| 请求方式 | POST |
| 接口描述 | 创建AI会话，可绑定知识库。 |

### 3.1.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| title | string | 否 | 标题 |
| knowledgeBaseId | number | 否 | 知识库ID |
| mode | string | 是 | CHAT/RAG/AGENT |

请求参数样例：

```text
{
  "title": "Spring IOC复习",
  "knowledgeBaseId": 12,
  "mode": "AGENT"
}
```

### 3.1.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.id | number | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "id": 301
  }
}
```

## 3.2 会话列表查询

### 3.2.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/conversations |
| 请求方式 | GET |
| 接口描述 | 分页查询历史会话。 |

### 3.2.2 请求参数

参数格式：queryString

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| keyword | string | 否 | 标题关键字 |
| mode | string | 否 | CHAT/RAG/AGENT |
| page | number | 是 | 页码 |
| pageSize | number | 是 | 每页条数 |

请求参数样例：

```text
/api/conversations?mode=AGENT&page=1&pageSize=20
```

### 3.2.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.total | number | 否 | 响应业务字段 |
| data.rows | array | 否 | 响应业务字段 |
| data.rows[].id | number | 否 | 响应业务字段 |
| data.rows[].title | string | 否 | 响应业务字段 |
| data.rows[].mode | string | 否 | 响应业务字段 |
| data.rows[].messageCount | number | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "total": 1,
    "rows": [
      {
        "id": 301,
        "title": "Spring IOC复习",
        "mode": "AGENT",
        "messageCount": 6
      }
    ]
  }
}
```

## 3.3 会话详情

### 3.3.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/conversations/{id} |
| 请求方式 | GET |
| 接口描述 | 查询会话信息。 |

### 3.3.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 会话ID |

请求参数样例：

```text
/api/conversations/301
```

### 3.3.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.id | number | 否 | 响应业务字段 |
| data.title | string | 否 | 响应业务字段 |
| data.knowledgeBaseId | number | 否 | 响应业务字段 |
| data.mode | string | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "id": 301,
    "title": "Spring IOC复习",
    "knowledgeBaseId": 12,
    "mode": "AGENT"
  }
}
```

## 3.4 修改会话标题

### 3.4.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/conversations/{id} |
| 请求方式 | PUT |
| 接口描述 | 修改会话标题。 |

### 3.4.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 会话ID |
| title | string | 是 | 标题 |

请求参数样例：

```text
{
  "title": "Spring核心复习"
}
```

### 3.4.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 3.5 删除会话

### 3.5.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/conversations/{id} |
| 请求方式 | DELETE |
| 接口描述 | 删除会话及消息。 |

### 3.5.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 会话ID |

请求参数样例：

```text
/api/conversations/301
```

### 3.5.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 3.6 历史消息列表

### 3.6.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/conversations/{id}/messages |
| 请求方式 | GET |
| 接口描述 | 加载历史消息。 |

### 3.6.2 请求参数

参数格式：queryString

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 会话ID |
| beforeId | number | 否 | 向前加载锚点 |
| pageSize | number | 是 | 数量 |

请求参数样例：

```text
/api/conversations/301/messages?pageSize=20
```

### 3.6.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.rows | array | 否 | 响应业务字段 |
| data.rows[].id | number | 否 | 响应业务字段 |
| data.rows[].role | string | 否 | 响应业务字段 |
| data.rows[].content | string | 否 | 响应业务字段 |
| data.rows[].citations | array | 否 | 响应业务字段 |
| data.rows[].citations[].documentId | number | 否 | 响应业务字段 |
| data.rows[].citations[].fileName | string | 否 | 响应业务字段 |
| data.rows[].citations[].page | number | 否 | 响应业务字段 |
| data.hasMore | boolean | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "rows": [
      {
        "id": 9001,
        "role": "assistant",
        "content": "IOC关注对象创建，DI关注依赖注入。",
        "citations": [
          {
            "documentId": 101,
            "fileName": "Spring学习笔记.pdf",
            "page": 12
          }
        ]
      }
    ],
    "hasMore": false
  }
}
```

## 3.7 发送消息（非流式）

### 3.7.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/conversations/{id}/messages |
| 请求方式 | POST |
| 接口描述 | 发送问题并等待完整AI回答。 |

### 3.7.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 会话ID |
| content | string | 是 | 问题 |
| useKnowledgeBase | boolean | 否 | 是否启用RAG |

请求参数样例：

```text
{
  "content": "IOC和DI有什么区别？",
  "useKnowledgeBase": true
}
```

### 3.7.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.userMessageId | number | 否 | 响应业务字段 |
| data.assistantMessage | object | 否 | 响应业务字段 |
| data.assistantMessage.id | number | 否 | 响应业务字段 |
| data.assistantMessage.content | string | 否 | 响应业务字段 |
| data.traceId | string | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "userMessageId": 9000,
    "assistantMessage": {
      "id": 9001,
      "content": "IOC关注对象创建，DI关注依赖注入。"
    },
    "traceId": "trace_a81c"
  }
}
```

## 3.8 发送消息（SSE流式）

### 3.8.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/conversations/{id}/stream |
| 请求方式 | POST |
| 接口描述 | 通过SSE流式返回LangGraph执行结果。 |

### 3.8.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 会话ID |
| content | string | 是 | 问题 |
| useKnowledgeBase | boolean | 否 | 是否RAG |
| enableTools | boolean | 否 | 是否允许工具调用 |

请求参数样例：

```text
{
  "content": "根据我的学习进度安排本周复习计划",
  "useKnowledgeBase": true,
  "enableTools": true
}
```

### 3.8.3 响应数据

参数格式：text/event-stream

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |

响应数据样例：

```text
event: start
data: {"traceId":"trace_93aa","messageId":9010}

event: node
data: {"node":"router","status":"completed"}

event: tool
data: {"tool":"get_learning_progress","status":"completed"}

event: token
data: {"content":"根据你的学习情况"}

event: citation
data: {"documentId":101,"fileName":"Spring学习笔记.pdf","page":12}

event: done
data: {"messageId":9011,"finishReason":"stop"}
```

### 3.8.4 备注说明

Content-Type 必须为 text/event-stream；事件类型：start/node/tool/token/citation/done/error。服务端每个事件以空行分隔；SSE 接口不使用普通 Result<T> JSON 包装。

## 3.9 停止生成

### 3.9.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/conversations/{id}/generation/{traceId} |
| 请求方式 | DELETE |
| 接口描述 | 终止AI生成任务。 |

### 3.9.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 会话ID |
| traceId | string | 是 | 追踪ID |

请求参数样例：

```text
/api/conversations/301/generation/trace_93aa
```

### 3.9.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 3.10 消息反馈

### 3.10.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/messages/{id}/feedback |
| 请求方式 | POST |
| 接口描述 | 点赞/点踩AI回答。 |

### 3.10.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | AI消息ID |
| rating | number | 是 | 1有帮助，-1无帮助 |
| reason | string | 否 | 原因 |

请求参数样例：

```text
{
  "rating": 1,
  "reason": "解释清楚"
}
```

### 3.10.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

# 4. AI测验与错题本

## 4.1 生成测验

### 4.1.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/quizzes/generate |
| 请求方式 | POST |
| 接口描述 | 根据主题或知识库生成测验。 |

### 4.1.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| title | string | 是 | 标题 |
| topic | string | 否 | 主题 |
| knowledgeBaseId | number | 否 | 知识库ID |
| questionCount | number | 是 | 1~50 |
| difficulty | string | 是 | EASY/MEDIUM/HARD |
| types | string[] | 是 | SINGLE/MULTIPLE/JUDGE/SHORT |

请求参数样例：

```text
{
  "title": "Spring核心测验",
  "topic": "IOC、DI、AOP",
  "knowledgeBaseId": 12,
  "questionCount": 10,
  "difficulty": "MEDIUM",
  "types": [
    "SINGLE",
    "JUDGE"
  ]
}
```

### 4.1.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.quizId | number | 否 | 响应业务字段 |
| data.status | string | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "quizId": 501,
    "status": "PROCESSING"
  }
}
```

### 4.1.4 备注说明

AI 生成可能耗时，创建任务后返回 PROCESSING；完成后变为 READY，失败为 FAILED。客户端可轮询 4.3。

## 4.2 测验列表查询

### 4.2.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/quizzes |
| 请求方式 | GET |
| 接口描述 | 分页查询测验。 |

### 4.2.2 请求参数

参数格式：queryString

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| keyword | string | 否 | 标题 |
| status | string | 否 | READY/COMPLETED |
| page | number | 是 | 页码 |
| pageSize | number | 是 | 每页条数 |

请求参数样例：

```text
/api/quizzes?status=READY&page=1&pageSize=10
```

### 4.2.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.total | number | 否 | 响应业务字段 |
| data.rows | array | 否 | 响应业务字段 |
| data.rows[].id | number | 否 | 响应业务字段 |
| data.rows[].title | string | 否 | 响应业务字段 |
| data.rows[].difficulty | string | 否 | 响应业务字段 |
| data.rows[].questionCount | number | 否 | 响应业务字段 |
| data.rows[].status | string | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "total": 1,
    "rows": [
      {
        "id": 501,
        "title": "Spring核心测验",
        "difficulty": "MEDIUM",
        "questionCount": 10,
        "status": "READY"
      }
    ]
  }
}
```

## 4.3 测验详情

### 4.3.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/quizzes/{id} |
| 请求方式 | GET |
| 接口描述 | 查询题目；提交前不返回答案解析。 |

### 4.3.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 测验ID |

请求参数样例：

```text
/api/quizzes/501
```

### 4.3.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.id | number | 否 | 响应业务字段 |
| data.questions | array | 否 | 响应业务字段 |
| data.questions[].id | number | 否 | 响应业务字段 |
| data.questions[].type | string | 否 | 响应业务字段 |
| data.questions[].content | string | 否 | 响应业务字段 |
| data.questions[].options | array | 否 | 响应业务字段 |
| data.questions[].options[].key | string | 否 | 响应业务字段 |
| data.questions[].options[].text | string | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "id": 501,
    "questions": [
      {
        "id": 7001,
        "type": "SINGLE",
        "content": "IOC核心含义？",
        "options": [
          {
            "key": "A",
            "text": "对象由容器管理"
          },
          {
            "key": "B",
            "text": "数据库连接"
          }
        ]
      }
    ]
  }
}
```

## 4.4 提交测验

### 4.4.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/quizzes/{id}/submit |
| 请求方式 | POST |
| 接口描述 | 提交答案并评分。 |

### 4.4.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 测验ID |
| answers | object[] | 是 | 答案列表 |
| answers[].questionId | number | 是 | 题目ID |
| answers[].answer | string/string[] | 是 | 答案 |

请求参数样例：

```text
{
  "answers": [
    {
      "questionId": 7001,
      "answer": "A"
    }
  ]
}
```

### 4.4.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.score | number | 否 | 响应业务字段 |
| data.correctCount | number | 否 | 响应业务字段 |
| data.wrongCount | number | 否 | 响应业务字段 |
| data.analysis | string | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "score": 90,
    "correctCount": 9,
    "wrongCount": 1,
    "analysis": "建议加强AOP。"
  }
}
```

## 4.5 测验结果详情

### 4.5.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/quizzes/{id}/result |
| 请求方式 | GET |
| 接口描述 | 查询逐题结果和AI解析。 |

### 4.5.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 测验ID |

请求参数样例：

```text
/api/quizzes/501/result
```

### 4.5.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.quizId | number | 否 | 响应业务字段 |
| data.score | number | 否 | 响应业务字段 |
| data.questions | array | 否 | 响应业务字段 |
| data.questions[].questionId | number | 否 | 响应业务字段 |
| data.questions[].userAnswer | string | 否 | 响应业务字段 |
| data.questions[].correctAnswer | string | 否 | 响应业务字段 |
| data.questions[].correct | boolean | 否 | 响应业务字段 |
| data.questions[].explanation | string | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "quizId": 501,
    "score": 90,
    "questions": [
      {
        "questionId": 7001,
        "userAnswer": "A",
        "correctAnswer": "A",
        "correct": true,
        "explanation": "IOC将对象管理权交给容器。"
      }
    ]
  }
}
```

## 4.6 错题列表查询

### 4.6.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/wrong-questions |
| 请求方式 | GET |
| 接口描述 | 分页查询错题。 |

### 4.6.2 请求参数

参数格式：queryString

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| topic | string | 否 | 知识点 |
| mastered | boolean | 否 | 是否掌握 |
| page | number | 是 | 页码 |
| pageSize | number | 是 | 每页条数 |

请求参数样例：

```text
/api/wrong-questions?topic=AOP&mastered=false&page=1&pageSize=10
```

### 4.6.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.total | number | 否 | 响应业务字段 |
| data.rows | array | 否 | 响应业务字段 |
| data.rows[].id | number | 否 | 响应业务字段 |
| data.rows[].topic | string | 否 | 响应业务字段 |
| data.rows[].wrongCount | number | 否 | 响应业务字段 |
| data.rows[].mastered | boolean | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "total": 1,
    "rows": [
      {
        "id": 801,
        "topic": "Spring AOP",
        "wrongCount": 2,
        "mastered": false
      }
    ]
  }
}
```

## 4.7 标记错题已掌握

### 4.7.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/wrong-questions/{id}/mastered |
| 请求方式 | PUT |
| 接口描述 | 更新错题掌握状态。 |

### 4.7.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 错题ID |
| mastered | boolean | 是 | 状态 |

请求参数样例：

```text
{
  "mastered": true
}
```

### 4.7.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 4.8 删除错题记录

### 4.8.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/wrong-questions/{id} |
| 请求方式 | DELETE |
| 接口描述 | 删除错题记录。 |

### 4.8.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 错题ID |

请求参数样例：

```text
/api/wrong-questions/801
```

### 4.8.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

# 5. 学习进度与学习计划

## 5.1 记录学习行为

### 5.1.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/learning/records |
| 请求方式 | POST |
| 接口描述 | 记录阅读、测验、复习、对话行为。 |

### 5.1.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| type | string | 是 | READ/QUIZ/REVIEW/CHAT |
| topic | string | 是 | 知识点 |
| durationMinutes | number | 否 | 时长 |
| sourceId | number | 否 | 关联资源 |
| remark | string | 否 | 备注 |

请求参数样例：

```text
{
  "type": "REVIEW",
  "topic": "MyBatis动态SQL",
  "durationMinutes": 45,
  "remark": "复习if/where/foreach"
}
```

### 5.1.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.id | number | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "id": 1001
  }
}
```

## 5.2 学习记录查询

### 5.2.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/learning/records |
| 请求方式 | GET |
| 接口描述 | 条件分页查询学习记录。 |

### 5.2.2 请求参数

参数格式：queryString

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| begin | string | 否 | 开始日期 |
| end | string | 否 | 结束日期 |
| type | string | 否 | 类型 |
| topic | string | 否 | 知识点 |
| page | number | 是 | 页码 |
| pageSize | number | 是 | 每页条数 |

请求参数样例：

```text
/api/learning/records?begin=2026-10-01&end=2026-10-07&page=1&pageSize=10
```

### 5.2.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.total | number | 否 | 响应业务字段 |
| data.rows | array | 否 | 响应业务字段 |
| data.rows[].id | number | 否 | 响应业务字段 |
| data.rows[].type | string | 否 | 响应业务字段 |
| data.rows[].topic | string | 否 | 响应业务字段 |
| data.rows[].durationMinutes | number | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "total": 1,
    "rows": [
      {
        "id": 1001,
        "type": "REVIEW",
        "topic": "MyBatis动态SQL",
        "durationMinutes": 45
      }
    ]
  }
}
```

## 5.3 学习进度统计

### 5.3.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/learning/progress |
| 请求方式 | GET |
| 接口描述 | 统计知识点掌握度、正确率和学习时长。 |

### 5.3.2 请求参数

参数格式：queryString

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| range | string | 否 | 7d/30d/all |

请求参数样例：

```text
/api/learning/progress?range=30d
```

### 5.3.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.topics | array | 否 | 响应业务字段 |
| data.topics[].topic | string | 否 | 响应业务字段 |
| data.topics[].mastery | number | 否 | 响应业务字段 |
| data.topics[].quizAccuracy | number | 否 | 响应业务字段 |
| data.topics[].studyMinutes | number | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "topics": [
      {
        "topic": "Spring IOC",
        "mastery": 85,
        "quizAccuracy": 90,
        "studyMinutes": 180
      },
      {
        "topic": "MyBatis动态SQL",
        "mastery": 55,
        "quizAccuracy": 60,
        "studyMinutes": 90
      }
    ]
  }
}
```

## 5.4 生成AI学习计划

### 5.4.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/study-plans/generate |
| 请求方式 | POST |
| 接口描述 | 综合学习记录、测验、错题生成个性化计划。 |

### 5.4.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| goal | string | 是 | 目标 |
| startDate | string | 是 | 开始日期 |
| endDate | string | 是 | 结束日期 |
| dailyMinutes | number | 是 | 每日分钟 |
| focusTopics | string[] | 否 | 重点 |

请求参数样例：

```text
{
  "goal": "一周复习Java Web核心知识",
  "startDate": "2026-10-05",
  "endDate": "2026-10-11",
  "dailyMinutes": 90,
  "focusTopics": [
    "MyBatis",
    "Spring AOP"
  ]
}
```

### 5.4.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.planId | number | 否 | 响应业务字段 |
| data.status | string | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "planId": 1201,
    "status": "PROCESSING"
  }
}
```

### 5.4.4 备注说明

AI 计划生成按异步任务处理：PROCESSING → READY/FAILED。计划 READY 后，5.6 才保证返回完整 tasks。

## 5.5 学习计划列表

### 5.5.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/study-plans |
| 请求方式 | GET |
| 接口描述 | 分页查询学习计划。 |

### 5.5.2 请求参数

参数格式：queryString

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| status | string | 否 | ACTIVE/COMPLETED/ARCHIVED |
| page | number | 是 | 页码 |
| pageSize | number | 是 | 每页条数 |

请求参数样例：

```text
/api/study-plans?status=ACTIVE&page=1&pageSize=10
```

### 5.5.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.total | number | 否 | 响应业务字段 |
| data.rows | array | 否 | 响应业务字段 |
| data.rows[].id | number | 否 | 响应业务字段 |
| data.rows[].title | string | 否 | 响应业务字段 |
| data.rows[].status | string | 否 | 响应业务字段 |
| data.rows[].progress | number | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "total": 1,
    "rows": [
      {
        "id": 1201,
        "title": "Java Web一周复习计划",
        "status": "ACTIVE",
        "progress": 0
      }
    ]
  }
}
```

## 5.6 学习计划详情

### 5.6.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/study-plans/{id} |
| 请求方式 | GET |
| 接口描述 | 查询计划及每日任务。 |

### 5.6.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 计划ID |

请求参数样例：

```text
/api/study-plans/1201
```

### 5.6.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.id | number | 否 | 响应业务字段 |
| data.tasks | array | 否 | 响应业务字段 |
| data.tasks[].id | number | 否 | 响应业务字段 |
| data.tasks[].date | string | 否 | 响应业务字段 |
| data.tasks[].topic | string | 否 | 响应业务字段 |
| data.tasks[].type | string | 否 | 响应业务字段 |
| data.tasks[].estimatedMinutes | number | 否 | 响应业务字段 |
| data.tasks[].completed | boolean | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "id": 1201,
    "tasks": [
      {
        "id": 1,
        "date": "2026-10-05",
        "topic": "MyBatis动态SQL",
        "type": "REVIEW",
        "estimatedMinutes": 45,
        "completed": false
      }
    ]
  }
}
```

## 5.7 更新计划任务状态

### 5.7.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/study-plans/{planId}/tasks/{taskId} |
| 请求方式 | PUT |
| 接口描述 | 更新任务完成状态。 |

### 5.7.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| planId | number | 是 | 计划ID |
| taskId | number | 是 | 任务ID |
| completed | boolean | 是 | 是否完成 |

请求参数样例：

```text
{
  "completed": true
}
```

### 5.7.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

## 5.8 删除学习计划

### 5.8.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/study-plans/{id} |
| 请求方式 | DELETE |
| 接口描述 | 删除学习计划。 |

### 5.8.2 请求参数

参数格式：路径参数

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| id | number | 是 | 计划ID |

请求参数样例：

```text
/api/study-plans/1201
```

### 5.8.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

# 6. 数据统计

## 6.1 学习总览

### 6.1.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/dashboard/overview |
| 请求方式 | GET |
| 接口描述 | 首页学习总览。 |

### 6.1.2 请求参数

参数格式：queryString

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| range | string | 否 | 7d/30d/all |

请求参数样例：

```text
/api/dashboard/overview?range=7d
```

### 6.1.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.studyMinutes | number | 否 | 响应业务字段 |
| data.quizCount | number | 否 | 响应业务字段 |
| data.averageScore | number | 否 | 响应业务字段 |
| data.knowledgeBaseCount | number | 否 | 响应业务字段 |
| data.streakDays | number | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "studyMinutes": 420,
    "quizCount": 6,
    "averageScore": 82.5,
    "knowledgeBaseCount": 3,
    "streakDays": 5
  }
}
```

## 6.2 学习趋势统计

### 6.2.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/dashboard/trend |
| 请求方式 | GET |
| 接口描述 | 按天统计学习时长和测验分数。 |

### 6.2.2 请求参数

参数格式：queryString

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| begin | string | 是 | 开始日期 |
| end | string | 是 | 结束日期 |

请求参数样例：

```text
/api/dashboard/trend?begin=2026-10-01&end=2026-10-07
```

### 6.2.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.dates | array | 否 | 响应业务字段 |
| data.studyMinutes | array | 否 | 响应业务字段 |
| data.quizScores | array | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "dates": [
      "2026-10-01",
      "2026-10-02",
      "2026-10-03"
    ],
    "studyMinutes": [
      60,
      90,
      45
    ],
    "quizScores": [
      80,
      85,
      88
    ]
  }
}
```

## 6.3 薄弱知识点统计

### 6.3.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/dashboard/weak-topics |
| 请求方式 | GET |
| 接口描述 | 根据错题和正确率计算薄弱知识点。 |

### 6.3.2 请求参数

参数格式：queryString

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| limit | number | 否 | 默认10 |

请求参数样例：

```text
/api/dashboard/weak-topics?limit=5
```

### 6.3.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data[].topic | string | 否 | 响应业务字段 |
| data[].mastery | number | 否 | 响应业务字段 |
| data[].wrongCount | number | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": [
    {
      "topic": "MyBatis动态SQL",
      "mastery": 55,
      "wrongCount": 5
    },
    {
      "topic": "Spring AOP",
      "mastery": 62,
      "wrongCount": 3
    }
  ]
}
```

# 7. 文件与系统接口

## 7.1 通用文件上传

### 7.1.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/files/upload |
| 请求方式 | POST |
| 接口描述 | 上传头像等普通文件。 |

### 7.1.2 请求参数

参数格式：multipart/form-data

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| file | file | 是 | 文件 |
| type | string | 是 | AVATAR/IMAGE |

请求参数样例：

```text
form-data: file=@avatar.png&type=AVATAR
```

### 7.1.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.url | string | 否 | 响应业务字段 |
| data.fileName | string | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "url": "https://cdn.example.com/avatar/1.png",
    "fileName": "avatar.png"
  }
}
```

## 7.2 系统健康检查

### 7.2.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /api/health |
| 请求方式 | GET |
| 接口描述 | 检查业务后端、AI服务和数据库状态；无需登录。 |

### 7.2.2 请求参数

参数格式：无

无

### 7.2.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.status | string | 否 | 响应业务字段 |
| data.aiService | string | 否 | 响应业务字段 |
| data.database | string | 否 | 响应业务字段 |

响应数据样例：

```text
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

# 8. 内部AI服务接口（Spring Boot → FastAPI）

以下接口不直接暴露给浏览器，仅供服务间调用。建议部署在内网并增加服务令牌。

## 8.1 Agent执行

### 8.1.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /internal/agent/invoke |
| 请求方式 | POST |
| 接口描述 | 执行LangGraph路由、RAG、工具调用和答案生成。 |

### 8.1.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| userId | number | 是 | 用户ID |
| conversationId | number | 是 | 会话ID |
| messageId | number | 是 | 消息ID |
| content | string | 是 | 问题 |
| mode | string | 是 | CHAT/RAG/AGENT |
| knowledgeBaseId | number | 否 | 知识库ID |
| stream | boolean | 是 | 是否流式 |

请求参数样例：

```text
{
  "userId": 1,
  "conversationId": 301,
  "messageId": 9000,
  "content": "根据我的错题安排复习",
  "mode": "AGENT",
  "knowledgeBaseId": 12,
  "stream": false
}
```

### 8.1.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.answer | string | 否 | 响应业务字段 |
| data.citations | array | 否 | 响应业务字段 |
| data.toolCalls | array | 否 | 响应业务字段 |
| data.toolCalls[].name | string | 否 | 响应业务字段 |
| data.toolCalls[].status | string | 否 | 响应业务字段 |
| data.traceId | string | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "answer": "建议先复习MyBatis动态SQL。",
    "citations": [],
    "toolCalls": [
      {
        "name": "get_learning_progress",
        "status": "success"
      }
    ],
    "traceId": "trace_93aa"
  }
}
```

## 8.2 RAG检索

### 8.2.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /internal/rag/retrieve |
| 请求方式 | POST |
| 接口描述 | 在指定知识库执行向量检索。 |

### 8.2.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| userId | number | 是 | 用户ID |
| knowledgeBaseId | number | 是 | 知识库ID |
| query | string | 是 | 问题 |
| topK | number | 否 | 默认5 |
| scoreThreshold | number | 否 | 阈值 |

请求参数样例：

```text
{
  "userId": 1,
  "knowledgeBaseId": 12,
  "query": "IOC和DI的区别",
  "topK": 5,
  "scoreThreshold": 0.65
}
```

### 8.2.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.chunks | array | 否 | 响应业务字段 |
| data.chunks[].chunkId | string | 否 | 响应业务字段 |
| data.chunks[].documentId | number | 否 | 响应业务字段 |
| data.chunks[].fileName | string | 否 | 响应业务字段 |
| data.chunks[].page | number | 否 | 响应业务字段 |
| data.chunks[].content | string | 否 | 响应业务字段 |
| data.chunks[].score | number | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "chunks": [
      {
        "chunkId": "chunk_101_18",
        "documentId": 101,
        "fileName": "Spring学习笔记.pdf",
        "page": 12,
        "content": "IOC关注对象的创建和管理……",
        "score": 0.89
      }
    ]
  }
}
```

## 8.3 文档入库任务

### 8.3.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /internal/rag/index |
| 请求方式 | POST |
| 接口描述 | 解析、切分、Embedding并写入向量库。 |

### 8.3.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| userId | number | 是 | 由Spring Boot认证上下文传递的用户ID，FastAPI不得从浏览器获取 |
| knowledgeBaseId | number | 是 | 知识库ID |
| documentId | number | 是 | 文档ID |
| taskId | string | 是 | Spring Boot生成的当前文档处理任务ID |
| fileUrl | string | 是 | Spring Boot内部文件读取地址 |
| fileType | string | 是 | pdf/docx/txt/md |
| fileName | string | 是 | 原始文件名metadata，不作为物理路径 |

请求参数样例：

```text
{
  "userId": 1,
  "knowledgeBaseId": 12,
  "documentId": 101,
  "taskId": "task_8f31",
  "fileUrl": "http://localhost:8080/internal/files/2f2b6c6e-101.pdf",
  "fileType": "pdf",
  "fileName": "Spring学习笔记.pdf"
}
```

### 8.3.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.taskId | string | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "taskId": "task_8f31"
  }
}
```

## 8.4 文档相关内部接口补充

以下接口只允许Spring Boot与FastAPI之间调用，必须携带：

`X-Internal-Token: <configured internal token>`

浏览器不得直接访问这些接口。

### 8.4.1 Spring Boot内部文件读取

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /internal/files/{storageKey} |
| 请求方式 | GET |
| 接口描述 | FastAPI通过该接口读取已登记文档内容；storageKey必须来自已登记的sa_document。 |

### 8.4.2 文档处理进度回调

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /internal/callbacks/documents/{documentId}/progress |
| 请求方式 | POST |
| 请求体 | taskId、status、progress、chunkCount、errorMessage |
| 状态 | PARSING、CHUNKING、EMBEDDING、READY、FAILED |

Spring Boot只接受 `taskId == sa_document.current_task_id` 的回调；旧任务回调被忽略，重复回调幂等。READY必须为100，FAILED必须带errorMessage。

### 8.4.3 向量删除

| 项目 | 文档删除 | 知识库删除 |
| --- | --- | --- |
| 请求路径 | DELETE /internal/rag/documents/{documentId} | DELETE /internal/rag/knowledge-bases/{knowledgeBaseId} |
| 查询参数 | userId、knowledgeBaseId | userId |
| 说明 | 删除操作必须同时按userId、knowledgeBaseId、documentId过滤，并保持幂等。 | 删除操作必须同时按userId、knowledgeBaseId过滤，并保持幂等。 |

## 8.5 生成测验题

### 8.5.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /internal/quiz/generate |
| 请求方式 | POST |
| 接口描述 | 调用LLM生成结构化测验题。 |

### 8.5.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| userId | number | 是 | 用户ID |
| topic | string | 否 | 主题 |
| knowledgeBaseId | number | 否 | 知识库ID |
| questionCount | number | 是 | 数量 |
| difficulty | string | 是 | 难度 |
| types | string[] | 是 | 题型 |

请求参数样例：

```text
{
  "userId": 1,
  "topic": "Spring IOC",
  "knowledgeBaseId": 12,
  "questionCount": 10,
  "difficulty": "MEDIUM",
  "types": [
    "SINGLE",
    "JUDGE"
  ]
}
```

### 8.5.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.questions | array | 否 | 响应业务字段 |
| data.questions[].type | string | 否 | 响应业务字段 |
| data.questions[].content | string | 否 | 响应业务字段 |
| data.questions[].answer | string | 否 | 响应业务字段 |
| data.questions[].explanation | string | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "questions": [
      {
        "type": "SINGLE",
        "content": "IOC核心含义？",
        "answer": "A",
        "explanation": "对象管理权交给容器。"
      }
    ]
  }
}
```

## 8.5 生成学习计划

### 8.5.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /internal/plans/generate |
| 请求方式 | POST |
| 接口描述 | 基于学习画像生成结构化计划。 |

### 8.5.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| userId | number | 是 | 用户ID |
| goal | string | 是 | 目标 |
| startDate | string | 是 | 开始 |
| endDate | string | 是 | 结束 |
| dailyMinutes | number | 是 | 每日分钟 |
| profile | object | 是 | 学习画像 |

请求参数样例：

```text
{
  "userId": 1,
  "goal": "复习Java Web",
  "startDate": "2026-10-05",
  "endDate": "2026-10-11",
  "dailyMinutes": 90,
  "profile": {
    "weakTopics": [
      "MyBatis动态SQL",
      "Spring AOP"
    ]
  }
}
```

### 8.5.3 响应数据

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / array / null | 否 | 业务数据 |
| data.tasks | array | 否 | 响应业务字段 |
| data.tasks[].date | string | 否 | 响应业务字段 |
| data.tasks[].topic | string | 否 | 响应业务字段 |
| data.tasks[].type | string | 否 | 响应业务字段 |
| data.tasks[].estimatedMinutes | number | 否 | 响应业务字段 |

响应数据样例：

```text
{
  "code": 1,
  "msg": "success",
  "data": {
    "tasks": [
      {
        "date": "2026-10-05",
        "topic": "MyBatis动态SQL",
        "type": "REVIEW",
        "estimatedMinutes": 45
      }
    ]
  }
}
```

## 8.6 Agent流式执行

### 8.6.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 请求路径 | /internal/agent/stream |
| 请求方式 | POST |
| 接口描述 | Spring Boot 调用 FastAPI/LangGraph 的内部流式接口，并将事件转发为对外 SSE，避免外部流式接口内部仍使用非流式调用。 |

### 8.6.2 请求参数

参数格式：application/json

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| userId | number | 是 | 用户ID |
| conversationId | number | 是 | 会话ID |
| messageId | number | 是 | 用户消息ID |
| content | string | 是 | 问题 |
| mode | string | 是 | CHAT/RAG/AGENT |
| knowledgeBaseId | number | 否 | 知识库ID |

请求参数样例：

```text
{
  "userId": 1,
  "conversationId": 301,
  "messageId": 9010,
  "content": "根据我的学习进度安排本周复习计划",
  "mode": "AGENT",
  "knowledgeBaseId": 12
}
```

### 8.6.3 响应数据

参数格式：text/event-stream

| 参数名 | 类型 | 是否必须 | 备注 |
| --- | --- | --- | --- |
| code | number | 是 | 1成功；非1失败 |
| msg | string | 否 | 提示信息 |
| data | object / null | 否 | 业务数据 |

响应数据样例：

```text
event: node
data: {"node":"router","status":"completed"}

event: token
data: {"content":"建议先复习"}

event: done
data: {"traceId":"trace_93aa","finishReason":"stop"}
```

### 8.6.4 备注说明

仅允许内网服务调用；响应 Content-Type 为 text/event-stream。Spring Boot 负责鉴权、资源归属校验、消息持久化和向前端转发。

# 9. 错误码与安全约定

| 业务码 | HTTP | 含义 |
| --- | --- | --- |
| 1 | 200/201 | 成功 |
| 1001 | 400 | 参数校验失败 |
| 1002 | 409 | 用户名已存在 |
| 1003 | 401 | 账号或密码错误 |
| 1004 | 401 | JWT无效/过期 |
| 1005 | 403 | 无资源权限 |
| 2001 | 404 | 知识库不存在 |
| 2002 | 422 | 文档类型不支持 |
| 2003 | 413 | 文件过大 |
| 2004 | 500 | 文档解析失败 |
| 3001 | 503 | AI服务不可用 |
| 3002 | 429 | AI请求频率超限 |
| 3003 | 422 | 模型结构化输出校验失败 |
| 3004 | 500 | 向量检索失败 |
| 4001 | 404 | 测验不存在 |
| 4002 | 409 | 测验已提交 |
| 1006 | 401 | refreshToken 无效或已过期 |
| 2005 | 409 | 文档正在处理中，不能重复处理 |
| 3005 | 409 | AI 生成任务已取消或已结束 |
| 4003 | 500 | 测验生成失败 |
| 5001 | 500 | 学习计划生成失败 |

- 密码使用 BCrypt/Argon2 等哈希算法存储，不保存明文。
- 所有用户资源接口同时校验身份与资源归属，避免水平越权。
- JWT 仅通过 Authorization 请求头传递，不写入URL。
- 上传文件校验扩展名、MIME、大小，并使用随机对象名存储。
- Agent Tool Calling 仅开放白名单工具；写操作再次校验权限与参数。
- RAG检索以 userId + knowledgeBaseId 做租户隔离。
- 日志禁止记录明文密码、完整JWT、模型API Key。
## 9.1 补充安全说明

refreshToken 建议服务端保存可撤销状态；外部 SSE 建连前完成 JWT 与会话归属校验；内部 FastAPI 不得信任浏览器传入的 userId，userId 由 Spring Boot 从已认证上下文传递。

# 10. V2功能覆盖清单

| 模块 | 功能 |
| --- | --- |
| 用户与认证 | 注册、登录、退出、Token续期、个人资料、密码 |
| 知识库 | CRUD、文档上传、状态、重处理、删除 |
| AI会话 | 会话CRUD、历史消息、普通/流式聊天、停止、反馈 |
| RAG | 解析、切片、Embedding、检索、引用来源 |
| LangGraph Agent | 路由、工具调用、执行追踪、内部流式执行 |
| AI测验 | 生成、列表、详情、提交、结果 |
| 错题本 | 列表、掌握状态、删除 |
| 学习进度 | 记录、查询、掌握度统计 |
| 学习计划 | AI生成、列表、详情、任务状态、删除 |
| 数据看板 | 总览、趋势、薄弱知识点 |
| 系统 | 通用上传、健康检查 |
