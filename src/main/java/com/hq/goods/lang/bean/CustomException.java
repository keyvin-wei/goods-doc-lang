package com.hq.goods.lang.bean;

/**
 * 自定义异常
 * @author weiwh
 * @date 2020/6/7 23:50
 */
public class CustomException extends RuntimeException {
    private Integer code;

    public CustomException() {
        super();
    }

    public CustomException(Integer code) {
        super(ResponseEnum.getMessage(code));
        this.setCode(code);
    }

    public CustomException(Integer code, String message) {
        super(message);
        this.setCode(code);
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }
}
