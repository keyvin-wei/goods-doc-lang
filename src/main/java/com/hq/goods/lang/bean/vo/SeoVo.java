package com.hq.goods.lang.bean.vo;

import lombok.Data;

import java.util.List;

/**
 * SEO 内容：Title / Keywords / Description
 */
@Data
public class SeoVo {
    private String title;
    private List<String> keywords;
    private String description;
}
