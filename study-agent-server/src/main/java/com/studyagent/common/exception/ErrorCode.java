package com.studyagent.common.exception;

public enum ErrorCode {
    VALIDATION_FAILED(1001, "参数校验失败"),
    USERNAME_EXISTS(1002, "用户名已存在"),
    INVALID_CREDENTIALS(1003, "账号或密码错误"),
    UNAUTHORIZED(1004, "未登录或令牌无效"),
    FORBIDDEN(1005, "无资源权限"),
    REFRESH_TOKEN_INVALID(1006, "refreshToken无效或已过期"),
    KNOWLEDGE_BASE_NOT_FOUND(2001, "知识库不存在"),
    FILE_INVALID(2004, "文档类型或文件名不合法"),
    FILE_TOO_LARGE(2005, "文件超过大小限制"),
    DOCUMENT_PARSE_FAILED(2006, "文档解析失败"),
    DOCUMENT_PROCESSING(2007, "文档正在处理中，不能重复处理"),
    DOCUMENT_NOT_FOUND(2002, "文档不存在"),
    CONVERSATION_NOT_FOUND(2101, "会话不存在"),
    MESSAGE_NOT_FOUND(2102, "消息不存在"),
    FILE_NOT_FOUND(2103, "文件不存在"),
    DOCUMENT_VERSION_NOT_FOUND(2003, "文档版本不存在"),
    DOCUMENT_STATE_CONFLICT(2008, "文档版本状态不允许当前操作"),
    KNOWLEDGE_SNAPSHOT_NOT_FOUND(2009, "知识快照不存在"),
    DOCUMENT_VERSION_REFERENCED(2010, "历史版本仍被快照引用"),
    INTERNAL_ERROR(5000, "服务内部错误"),
    AI_SERVICE_UNAVAILABLE(3001, "AI服务不可用"),
    AI_RATE_LIMITED(3002, "AI请求频率超限"),
    STRUCTURED_OUTPUT_INVALID(3003, "模型结构化输出校验失败"),
    VECTOR_RETRIEVAL_FAILED(3004, "向量检索失败"),
    GENERATION_CANCELLED(3005, "AI生成任务已取消或已结束"),
    VECTOR_DELETE_FAILED(3006, "向量删除失败"),
    QUIZ_NOT_FOUND(4001, "测验不存在"),
    QUIZ_ALREADY_SUBMITTED(4002, "测验已提交"),
    QUIZ_GENERATION_FAILED(4003, "测验生成失败"),
    STUDY_PLAN_GENERATION_FAILED(5001, "学习计划生成失败"),
    PROJECT_NOT_FOUND(1101, "项目不存在"),
    PROJECT_NOT_MEMBER(1102, "非项目成员"),
    PROJECT_ROLE_INSUFFICIENT(1103, "项目角色权限不足"),
    PROJECT_LAST_OWNER(1104, "不能移除或降级最后一个OWNER"),
    PROJECT_ARCHIVED(1105, "项目已归档"),
    PROJECT_MEMBER_EXISTS(1106, "该用户已是项目成员"),
    PROJECT_CODE_EXISTS(1107, "项目编码已存在"),
    PROJECT_MEMBER_USER_NOT_FOUND(1108, "用户不存在或已停用"),
    PROJECT_MEMBER_NOT_FOUND(1109, "项目成员不存在"),
    ONBOARDING_PATH_NOT_FOUND(5001, "接手路径不存在"),
    ONBOARDING_ASSIGNMENT_NOT_FOUND(5002, "Assignment不存在"),
    ONBOARDING_PREREQUISITE_NOT_MET(5003, "前置任务未完成"),
    ONBOARDING_PATH_INVALID(5004, "路径未发布或存在失效依赖"),
    ONBOARDING_ASSIGNMENT_INCOMPLETE(5005, "Assignment尚未满足提交条件"),
    CAPABILITY_REPORT_NOT_FOUND(6001, "能力报告不存在");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) { this.code = code; this.message = message; }
    public int code() { return code; }
    public String message() { return message; }
}
