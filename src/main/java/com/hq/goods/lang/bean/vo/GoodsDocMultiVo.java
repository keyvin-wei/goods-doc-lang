package com.hq.goods.lang.bean.vo;

import lombok.Data;

import java.util.Map;

/**
 * 多语言 + SEO 生成结果
 */
@Data
public class GoodsDocMultiVo {
    /** 语言码 → 描述文本：en/zh/zhTw/ja/ru */
    private Map<String, String> multilingual;
    /** 语言码 → SEO：zh/en/ja/ru */
    private Map<String, SeoVo> seo;
}
