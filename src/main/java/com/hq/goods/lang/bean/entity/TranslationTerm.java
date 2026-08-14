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

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSON;

/**
 * 翻译术语库实体
 *
 * @author ai-assistant
 * @since 2026/06/16
 */
@ApiModel(value = "TranslationTerm", description = "翻译术语库")
@Getter
@Setter
@TableName("hq_translation_term")
public class TranslationTerm implements Serializable {

    private static final long serialVersionUID = 1L;


    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键ID")
    private Long id;

    /**
     * 中文术语
     */
    @ApiModelProperty(value = "中文术语")
    private String cn;

    /**
     * 英文术语
     */
    @ApiModelProperty(value = "英文术语")
    private String en;

    /**
     * 中文别名列表,JSON格式,如["问题","工艺问题"]
     */
    @TableField("cn_aliases")
    @ApiModelProperty(value = "中文别名列表,JSON格式")
    private String cnAliases;

    /**
     * 英文别名列表,JSON格式,如["EQ","Engineering Question"]
     */
    @TableField("en_aliases")
    @ApiModelProperty(value = "英文别名列表,JSON格式")
    private String enAliases;

    /**
     * 分类,如:PCB工艺/材料/设备
     */
    @ApiModelProperty(value = "分类")
    private String category;

    /**
     * 状态:0-draft草稿,1-verified已审核,2-gold金牌
     */
    @ApiModelProperty(value = "状态:0-draft,1-verified,2-gold")
    private Integer status;

    /**
     * 术语权重,人工修正后提升,默认100
     */
    @ApiModelProperty(value = "术语权重")
    private Integer weight;

    /**
     * 引用次数
     */
    @TableField("usage_count")
    @ApiModelProperty(value = "引用次数")
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
