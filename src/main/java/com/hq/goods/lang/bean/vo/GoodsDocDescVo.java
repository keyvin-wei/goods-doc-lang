package com.hq.goods.lang.bean.vo;

import lombok.Data;

/**
 * 英文描述生成结果
 */
@Data
public class GoodsDocDescVo {
    private String description;

    public GoodsDocDescVo() {
    }

    public GoodsDocDescVo(String description) {
        this.description = description;
    }
}
