package com.hq.goods.lang.bean.dto;

import com.hq.goods.lang.bean.vo.GoodsDocVo;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 英文描述生成请求
 */
@Data
public class GenerateDescReq {
    @NotNull(message = "基本资料不能为空")
    private GoodsDocVo goodsDoc;
}
