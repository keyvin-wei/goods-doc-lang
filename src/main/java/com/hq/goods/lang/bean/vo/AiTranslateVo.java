package com.hq.goods.lang.bean.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * @Description
 * @Author weiwenhan
 * @Date 2026/8/14 11:49
 */
@Data
public class AiTranslateVo {

    @ApiModelProperty(value = "原文", example = "今天是星期一")
    private String text;

    @ApiModelProperty(value = "目标语言：1英语，2简体中文，3繁体中文，4日语，5俄语", example = "1")
    private Integer target = 1;

    @ApiModelProperty(value = "译文", example = "Today is Monday")
    private String translation;

}
