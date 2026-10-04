package com.studyagent.common.exception;

public enum ErrorCode {
    VALIDATION_FAILED(1001, "参数校验失败"),
    USERNAME_EXISTS(1002, "用户名已存在"),
    INVALID_CREDENTIALS(1003, "账号或密码错误"),
    UNAUTHORIZED(1004, "未登录或令牌无效"),
    FORBIDDEN(1005, "无资源权限"),
    REFRESH_TOKEN_INVALID(1006, "refreshToken无效或已过期"),
    KNOWLEDGE_BASE_NOT_FOUND(2001, "知识库不存在"),
    FILE_INVALID(2002, "文档类型或文件名不合法"),
    FILE_TOO_LARGE(2003, "文件超过大小限制"),
    DOCUMENT_PARSE_FAILED(2004, "文档解析失败"),
    DOCUMENT_PROCESSING(2005, "文档正在处理中，不能重复处理"),
    DOCUMENT_NOT_FOUND(2006, "文档不存在"),
    CONVERSATION_NOT_FOUND(2007, "会话不存在"),
    INTERNAL_ERROR(5000, "服务内部错误"),
    AI_SERVICE_UNAVAILABLE(3001, "AI服务不可用"),
    AI_RATE_LIMITED(3002, "AI请求频率超限"),
    STRUCTURED_OUTPUT_INVALID(3003, "模型结构化输出校验失败"),
    VECTOR_RETRIEVAL_FAILED(3004, "向量检索失败"),
    GENERATION_CANCELLED(3005, "AI生成任务已取消或已结束"),
    VECTOR_DELETE_FAILED(3006, "向量删除失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
