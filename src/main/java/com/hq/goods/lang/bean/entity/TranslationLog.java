package com.hq.goods.lang.bean.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 翻译日志实体
 *
 * @author ai-assistant
 * @since 2026/06/16
 */
@ApiModel(value = "TranslationLog", description = "翻译日志")
@Getter
@Setter
@TableName("hq_translation_log")
public class TranslationLog implements Serializable {

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
    @ApiModelProperty(value = "源文本")
    private String sourceText;

    /**
     * AI翻译结果
     */
    @TableField("ai_translation")
    @ApiModelProperty(value = "AI翻译结果")
    private String aiTranslation;

    /**
     * 人工修正后译文
     */
    @TableField("final_translation")
    @ApiModelProperty(value = "人工修正后译文")
    private String finalTranslation;

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
     * 命中的术语列表,JSON格式
     */
    @TableField("matched_terms")
    @ApiModelProperty(value = "命中的术语列表,JSON格式")
    private String matchedTerms;

    /**
     * 命中的TM记录ID列表,JSON格式
     */
    @TableField("matched_tm_ids")
    @ApiModelProperty(value = "命中的TM记录ID列表,JSON格式")
    private String matchedTmIds;

    /**
     * 是否被人工修改:0-否,1-是
     */
    @TableField("modified")
    @ApiModelProperty(value = "是否被人工修改:0-否,1-是")
    private Integer modified;

    /**
     * 错误类型:term_error/translation_error/style_error
     */
    @TableField("error_type")
    @ApiModelProperty(value = "错误类型")
    private String errorType;

    /**
     * 校对人员
     */
    @TableField("reviewer")
    @ApiModelProperty(value = "校对人员")
    private String reviewer;

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
     * 删除状态:0-有效,1-删除
     */
    @TableField("delete_status")
    @ApiModelProperty(value = "删除状态:0-有效,1-删除")
    private Integer deleteStatus;

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
