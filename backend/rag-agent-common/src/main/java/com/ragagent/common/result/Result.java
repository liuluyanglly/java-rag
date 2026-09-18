package com.ragagent.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// 不可显式指定 @Schema(name=...)：泛型类一旦固定名称，各泛型实例会被塌缩成同一个 Schema，
// 导致 data 的实际类型（及其级联引用的实体）从契约中丢失。
@Schema(description = "统一响应体，业务数据在 data 字段中")
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 状态码：200 成功，其他为错误码
     */
    @Schema(description = "状态码：200-成功，401-未登录或凭据失效，403-权限不足，500-服务内部错误", example = "200")
    private int code;

    /**
     * 提示信息
     */
    @Schema(description = "提示信息，成功时为“操作成功”或业务自定义文案，失败时为错误原因", example = "操作成功")
    private String msg;

    /**
     * 数据对象
     */
    @Schema(description = "业务数据载荷，无返回值的接口为 null")
    private T data;

    /**
     * 时间戳
     */
    @Schema(description = "服务端响应时间戳（毫秒）", example = "1789540952053")
    @Builder.Default
    private long timestamp = System.currentTimeMillis();

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
                .code(200)
                .msg("操作成功")
                .data(data)
                .build();
    }

    public static <T> Result<T> success(String msg, T data) {
        return Result.<T>builder()
                .code(200)
                .msg(msg)
                .data(data)
                .build();
    }

    public static <T> Result<T> error(String msg) {
        return error(500, msg);
    }

    public static <T> Result<T> error(int code, String msg) {
        return Result.<T>builder()
                .code(code)
                .msg(msg)
                .build();
    }
}
