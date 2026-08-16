package com.hq.goods.lang.bean.dto;

import com.hq.goods.lang.bean.vo.GoodsDocVo;
import com.hq.goods.lang.bean.vo.SeoVo;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * 保存请求（无 id 新增 / 有 id 更新）
 */
@Data
public class SaveReq {
    private Long id;

    @NotNull(message = "基本资料不能为空")
    private GoodsDocVo goodsDoc;

    /** 语言码 → 描述文本：en/zh/zhTw/ja/ru */
    private Map<String, String> multilingual;

    /** 语言码 → SEO：zh/en/ja/ru */
    private Map<String, SeoVo> seo;

    /** 1=型号解析 2=文本解析 */
    private Integer sourceType;
}
