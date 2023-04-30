package com.zzx.utils;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * @author ZZX
 * @version 1.0.0
 * @date 2023:04:20 11:40:54
 */

@Data
public class HttpResult {
    private Boolean success;
    private Integer code;
    private String message;
    private Map<String, Object> data = new HashMap<>();

    /**
     * 成功，缺乏数据
     *
     * @return
     */
    public static HttpResult ok() {
        HttpResult httpResult = new HttpResult();
        httpResult.setSuccess(HttpResultEnum.SUCCESS.getSuccess());
        httpResult.setCode(HttpResultEnum.SUCCESS.getCode());
        httpResult.setMessage(HttpResultEnum.SUCCESS.getMessage());
        return httpResult;
    }

    /**
     * 失败，缺乏数据
     *
     * @return
     */
    public static HttpResult error() {
        HttpResult httpResult = new HttpResult();
        httpResult.setSuccess(HttpResultEnum.FAIL.getSuccess());
        httpResult.setCode(HttpResultEnum.FAIL.getCode());
        httpResult.setMessage(HttpResultEnum.FAIL.getMessage());
        return httpResult;
    }

    /**
     * 设置泛型，缺乏数据
     *
     * @param httpResultEnum
     * @return
     */
    public static HttpResult setResult(HttpResultEnum httpResultEnum) {
        HttpResult httpResult = new HttpResult();
        httpResult.setSuccess(httpResultEnum.getSuccess());
        httpResult.setCode(httpResultEnum.getCode());
        httpResult.setMessage(httpResultEnum.getMessage());
        return httpResult;
    }

    /**
     * 设置成功标志位
     *
     * @return
     */
    public HttpResult success() {
        this.setSuccess(HttpResultEnum.SUCCESS.getSuccess());
        return this;
    }

    /**
     * 设置失败标志位
     *
     * @return
     */
    public HttpResult fail() {
        this.setSuccess(HttpResultEnum.FAIL.getSuccess());
        return this;
    }

    /**
     * 添加单个键值对数据
     *
     * @param key
     * @param value
     * @return
     */
    public HttpResult data(String key, Object value) {
        this.data.put(key, value);
        return this;
    }

    /**
     * 添加集合数据
     *
     * @param map
     * @return
     */
    public HttpResult data(Map<String, Object> map) {
        this.setData(map);
        return this;
    }
}