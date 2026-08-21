package com.hq.goods.lang.bean.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 阶梯价格档位：数量标签 / 单价 / 总价
 */
@Data
public class PriceTier {
    /** 如 1+ / 5+ / 10+ / 100+ */
    private String qtyLabel;
    private BigDecimal unitPrice;
    private BigDecimal extPrice;
}
