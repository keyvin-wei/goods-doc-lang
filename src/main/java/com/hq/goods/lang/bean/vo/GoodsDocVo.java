package com.hq.goods.lang.bean.vo;

import lombok.Data;

import java.util.List;

/**
 * 元器件基本资料（页面可编辑表单模型）
 */
@Data
public class GoodsDocVo {
    private String partNumber;
    private String brand;
    private String category;
    private String subcategory;
    private String series;
    private String packageType;
    private String mountingType;
    private Integer pinCount;
    private String dimensions;
    private List<ParamItem> parameters;
    private String operatingTemp;
    private String storageTemp;
    private String grade;
    private String rohs;
    private String packaging;
    private String moq;
    private String unit;
    private String hsCode;
    private String leadTime;
    private String priceRange;
    private String availability;
    private String datasheetUrl;
    private String imageUrl;
    private List<String> applications;
    private String descriptionEn;
    private String rawInput;
    private List<RagHit> topK;
}
