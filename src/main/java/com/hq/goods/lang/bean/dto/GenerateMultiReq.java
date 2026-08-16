package com.hq.goods.lang.bean.dto;

import com.hq.goods.lang.bean.vo.GoodsDocVo;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 多语言 + SEO 生成请求
 */
@Data
public class GenerateMultiReq {
    @NotNull(message = "基本资料不能为空")
    private GoodsDocVo goodsDoc;

    private String description;
}
