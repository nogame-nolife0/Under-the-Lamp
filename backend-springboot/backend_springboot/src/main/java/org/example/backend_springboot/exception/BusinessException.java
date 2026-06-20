package org.example.backend_springboot.exception;

import lombok.Getter;
import org.example.backend_springboot.common.BizCode;

@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(BizCode bizCode) {
        super(bizCode.getMsg());
        this.code = bizCode.getCode();
    }

    public BusinessException(BizCode bizCode, String message) {
        super(message);
        this.code = bizCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
