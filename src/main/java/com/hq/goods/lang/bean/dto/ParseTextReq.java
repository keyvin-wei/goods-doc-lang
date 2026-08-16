package com.hq.goods.lang.bean.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 描述文本解析请求
 */
@Data
public class ParseTextReq {
    @NotBlank(message = "描述文本不能为空")
    private String rawText;
}
