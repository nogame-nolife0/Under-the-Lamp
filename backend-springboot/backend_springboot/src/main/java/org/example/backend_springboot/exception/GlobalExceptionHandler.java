package org.example.backend_springboot.exception;

import lombok.extern.slf4j.Slf4j;
import org.example.backend_springboot.common.BizCode;
import org.example.backend_springboot.common.Result;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException ex) {
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValidationException(Exception ex) {
        return Result.fail(BizCode.BAD_REQUEST, "请求参数校验失败");
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public Result<Void> handleMultipartException(Exception ex) {
        return Result.fail(BizCode.BAD_REQUEST,
                "请使用 form-data 上传文件，字段名必须为 file，不能使用 raw JSON");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        log.error("服务器内部错误", ex);
        String message = ex.getMessage() != null ? ex.getMessage() : "服务器内部错误";
        return Result.fail(BizCode.INTERNAL_ERROR, message);
    }
}
