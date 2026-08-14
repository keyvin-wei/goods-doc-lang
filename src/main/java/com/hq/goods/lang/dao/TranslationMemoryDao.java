package com.hq.goods.lang.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hq.goods.lang.bean.entity.TranslationMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 翻译记忆库 Mapper 接口
 *
 * @author ai-assistant
 * @since 2026/06/16
 */
@Mapper
@Repository
public interface TranslationMemoryDao extends BaseMapper<TranslationMemory> {

    /**
     * 基于FULLTEXT检索源文本
     *
     * @param keywords 关键词
     * @param limit    限制条数
     * @return 记忆列表
     */
    List<TranslationMemory> selectByFullText(@Param("keywords") String keywords, @Param("limit") Integer limit);

    /**
     * 原子递增使用计数
     *
     * @param memoryIds 记忆ID
     * @return 影响行数
     */
    int incrementUsageCount(@Param("memoryIds") List<Long> memoryIds);

    /**
     * 根据源文本和目标文本查询记忆
     * @param sourceText 源文本
     * @param targetLang 目标语言
     * @return 历史
     */
    TranslationMemory findBySourceTextTarget(@Param("sourceText")String sourceText, @Param("targetLang")Integer targetLang);
}
