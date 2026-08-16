package com.hq.goods.lang.bean.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 型号+品牌解析请求
 */
@Data
public class ParsePartReq {
    @NotBlank(message = "型号不能为空")
    private String partNumber;

    private String brand;
}
