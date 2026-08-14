package com.hq.goods.lang.bean.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * @Description
 * @Author weiwenhan
 * @Date 2026/8/14 11:34
 */
@Data
public class AiTranslateDto {
    @NotBlank(message = "原文不能为空")
    @ApiModelProperty(value = "原文", example = "今天是星期一")
    private String text;

    @NotNull(message = "目标语言不能为空")
    @ApiModelProperty(value = "目标语言：1英语，2简体中文，3繁体中文，4日语，5俄语", example = "1")
    private Integer target;

    @ApiModelProperty(value = "来源：0正常，1EQ异常工单", example = "0")
    private Integer source;

}
