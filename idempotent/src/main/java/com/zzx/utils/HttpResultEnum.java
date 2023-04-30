package com.zzx.utils;

public enum HttpResultEnum {
    SUCCESS(true, 200, "成功"),
    FAIL(false, 2000, "失败");

    private Boolean success;
    private Integer code;
    private String message;

    HttpResultEnum(Boolean success, Integer code, String message) {
        this.success = success;
        this.code = code;
        this.message = message;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}