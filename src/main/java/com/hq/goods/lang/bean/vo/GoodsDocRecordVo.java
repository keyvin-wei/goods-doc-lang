package com.hq.goods.lang.bean.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 后台详情（编辑回填）
 */
@Data
public class GoodsDocRecordVo {
    private Long id;
    private GoodsDocVo basic;
    private Map<String, String> multilingual;
    private Map<String, SeoVo> seo;
    private Integer sourceType;
    private LocalDateTime cTime;
    private LocalDateTime uTime;
}
