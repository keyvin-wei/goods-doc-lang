package com.hq.goods.lang.bean.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 翻译记忆库实体
 *
 * @author ai-assistant
 * @since 2026/06/16
 */
@ApiModel(value = "TranslationMemory", description = "翻译记忆库")
@Getter
@Setter
@TableName("hq_translation_memory")
public class TranslationMemory implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键ID")
    private Long id;

    /**
     * 源文本
     */
    @TableField("source_text")
    @ApiModelProperty(value = "源文本")
    private String sourceText;

    /**
     * AI自动翻译结果
     */
    @TableField("ai_translation_text")
    @ApiModelProperty(value = "AI自动翻译结果")
    private String aiTranslationText;

    /**
     * 人工修正后的目标文本
     */
    @TableField("target_text")
    @ApiModelProperty(value = "人工修正后的目标文本")
    private String targetText;

    /**
     * 源语言:1-en英文,2-zh中文
     */
    @TableField("source_lang")
    @ApiModelProperty(value = "源语言:1-en,2-zh")
    private Integer sourceLang;

    /**
     * 目标语言:1-en英文,2-zh中文
     */
    @TableField("target_lang")
    @ApiModelProperty(value = "目标语言:1-en,2-zh")
    private Integer targetLang;

    /**
     * embedding向量,JSON数组格式,Phase1可空
     */
    @TableField("embedding_vector")
    @ApiModelProperty(value = "embedding向量,JSON数组")
    private String embeddingVector;

    /**
     * 质量分:0.0000-1.0000,默认0.9500
     */
    @TableField("quality_score")
    @ApiModelProperty(value = "质量分")
    private BigDecimal qualityScore;

    /**
     * 状态:0-draft草稿,1-verified已审核,2-gold金牌
     */
    @ApiModelProperty(value = "状态:0-draft,1-verified,2-gold")
    private Integer status;

    /**
     * 被召回次数
     */
    @TableField("usage_count")
    @ApiModelProperty(value = "被召回次数")
    private Integer usageCount;

    /**
     * 创建人员
     */
    @ApiModelProperty(value = "创建人员")
    private String creator;

    /**
     * 修改人员
     */
    @ApiModelProperty(value = "修改人员")
    private String updater;

    /**
     * 来源单号(EQ单号)
     */
    @TableField("source_order_no")
    @ApiModelProperty(value = "来源单号(EQ单号)")
    private String sourceOrderNo;

    /**
     * 关联单号(PCB订单号)
     */
    @TableField("related_order_no")
    @ApiModelProperty(value = "关联单号(PCB订单号)")
    private String relatedOrderNo;

    /**
     * 删除状态:0-有效,1-删除
     */
    @TableField("delete_status")
    @ApiModelProperty(value = "删除状态:0-有效,1-删除")
    private Integer deleteStatus;

    /**
     * 乐观锁版本号
     */
    @Version
    @TableField("version")
    @ApiModelProperty(value = "乐观锁版本号")
    private Integer version;

    /**
     * 数据创建时间
     */
    @TableField("c_time")
    @ApiModelProperty(value = "数据创建时间")
    private LocalDateTime cTime;

    /**
     * 数据更新时间
     */
    @TableField("u_time")
    @ApiModelProperty(value = "数据更新时间")
    private LocalDateTime uTime;

}