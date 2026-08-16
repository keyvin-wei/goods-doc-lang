package com.hq.goods.lang.bean.vo;

import lombok.Data;

/**
 * 动态参数项：参数名 / 数值 / 单位
 */
@Data
public class ParamItem {
    private String name;
    private String value;
    private String unit;
}
