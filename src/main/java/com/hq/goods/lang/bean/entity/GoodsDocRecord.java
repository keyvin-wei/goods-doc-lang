package com.hq.goods.lang.bean.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 外贸商品文档记录实体
 */
@Getter
@Setter
@TableName("hq_goods_doc_record")
public class GoodsDocRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String partNumber;
    private String brand;
    private String category;
    private String subcategory;
    private String series;

    /** package 是 Java 关键字，用 @TableField 映射 package 列 */
    @TableField("package")
    private String packageType;

    private String mountingType;
    private Integer pinCount;
    private String dimensions;

    /** 动态参数 JSON 字符串 */
    private String parameters;

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

    /** 应用领域 JSON 数组字符串 */
    private String applications;

    private String descriptionEn;

    /** 多语言描述 JSON：{en,zh,zhTw,ja,ru} */
    private String multilingual;

    /** SEO JSON：{zh,en,ja,ru} */
    private String seo;

    private String rawInput;
    private Integer sourceType;
    private Integer status;
    private String creator;
    private String updater;
    private Integer deleteStatus;
    private LocalDateTime cTime;
    private LocalDateTime uTime;
}
