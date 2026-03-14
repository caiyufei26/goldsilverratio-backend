package com.canLite.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一 API 返回封装。
 *
 * @param <T> 数据泛型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private int code;
    private String message;
    private T data;

    /**
     * 成功返回（带数据）
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    /**
     * 成功返回（无数据）
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /**
     * 失败返回
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
