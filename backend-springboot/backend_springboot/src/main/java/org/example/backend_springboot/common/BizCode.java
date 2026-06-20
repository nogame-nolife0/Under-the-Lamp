package org.example.backend_springboot.common;

import lombok.Getter;

@Getter
public enum BizCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "请求参数错误"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),
    SERVICE_UNAVAILABLE(503, "Agent 服务不可用"),

    WORD_FORMAT_UNSUPPORTED(40001, "Word 格式不支持"),
    FILE_TOO_LARGE(40002, "文件大小超限"),
    PARSE_RESULT_EMPTY(40003, "解析结果为空"),
    BATCH_STATUS_INVALID(40004, "批次状态不允许此操作"),
    QUESTION_NOT_ENOUGH(40005, "题目数量不足"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    USER_ALREADY_EXISTS(40006, "该手机号已注册"),
    USER_NOT_FOUND(40007, "账号不存在"),
    PASSWORD_INCORRECT(40008, "密码错误"),
    NOT_REGISTERED(40009, "尚未注册，请先注册账号"),

    AGENT_TIMEOUT(50001, "Agent 服务调用超时"),
    AGENT_PARSE_FAILED(50002, "Agent 解析失败"),
    LLM_CALL_FAILED(50003, "大模型调用失败");

    private final int code;
    private final String msg;

    BizCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
