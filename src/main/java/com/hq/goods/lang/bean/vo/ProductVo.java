package com.hq.goods.lang.bean.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 客户页面（官网 SSR）消费的数据
 */
@Data
public class ProductVo {
    private String partNumber;
    private String brand;
    private String category;
    private String subcategory;
    private String series;
    private String packageType;
    private List<ParamItem> parameters;
    private String descriptionEn;
    private List<String> applications;
    private Map<String, String> multilingual;
    private Map<String, SeoVo> seo;
    private String imageUrl;
    private String datasheetUrl;
}
