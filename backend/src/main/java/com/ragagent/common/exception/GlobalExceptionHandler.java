package com.ragagent.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.ragagent.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 未登录异常 (401)
     */
    @ExceptionHandler(NotLoginException.class)
    public Result<Void> handleNotLoginException(NotLoginException e) {
        log.warn("用户未登录或登录已过期: {}", e.getMessage());
        return Result.error(401, "尚未登录或登录凭证已过期，请重新登录");
    }

    /**
     * 无权限异常 (403)
     */
    @ExceptionHandler(NotPermissionException.class)
    public Result<Void> handleNotPermissionException(NotPermissionException e) {
        log.warn("权限不足: {}", e.getMessage());
        return Result.error(403, "没有该操作的权限: " + e.getPermission());
    }

    /**
     * 无角色异常 (403)
     */
    @ExceptionHandler(NotRoleException.class)
    public Result<Void> handleNotRoleException(NotRoleException e) {
        log.warn("缺少角色: {}", e.getMessage());
        return Result.error(403, "没有访问该资源的角色: " + e.getRole());
    }

    /**
     * 参数校验异常
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValidException(Exception e) {
        String msg = "参数校验失败";
        if (e instanceof MethodArgumentNotValidException me) {
            if (me.getBindingResult().getFieldError() != null) {
                msg = me.getBindingResult().getFieldError().getDefaultMessage();
            }
        }
        return Result.error(400, msg);
    }

    /**
     * WebFlux 响应状态异常处理 (404/405 等)
     */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public Result<Void> handleResponseStatusException(org.springframework.web.server.ResponseStatusException e) {
        log.warn("WebFlux 响应状态异常: {} - {}", e.getStatusCode(), e.getReason());
        return Result.error(e.getStatusCode().value(), e.getReason() != null ? e.getReason() : "请求的资源不存在");
    }

    /**
     * 通用未知异常
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统运行异常: ", e);
        return Result.error(500, e.getMessage() != null ? e.getMessage() : "服务器内部错误");
    }
}
