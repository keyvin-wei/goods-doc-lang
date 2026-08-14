package com.hq.goods.lang.service;

import com.hq.goods.lang.bean.dto.AiTranslateDto;
import com.hq.goods.lang.bean.entity.TranslationMemory;
import com.hq.goods.lang.bean.entity.TranslationTerm;
import org.springframework.scheduling.annotation.Async;

import java.util.List;

/**
 * 翻译服务接口
 *
 * <p>统一管理翻译相关的术语召回、翻译记忆召回和翻译日志记录功能。</p>
 *
 * @author ai-assistant
 * @since 2026/06/26
 */
public interface TranslationService {

    /**
     * 根据待翻译文本召回匹配的术语列表
     *
     * @param sourceText 源文本
     * @return 按weight降序排列的匹配术语列表
     */
    List<TranslationTerm> recallTerms(String sourceText);

    /**
     * 召回翻译记忆
     *
     * @param sourceText 源文本
     * @param targetLang 目标语言：1-en, 2-zh
     * @return 按相关度排序的翻译记忆列表（最多5条）
     */
    List<TranslationMemory> recallMemory(String sourceText, Integer targetLang);

    /**
     * 增加术语使用计数
     *
     * @param termIds term记录ID
     */
    void incrementTermUsageCount(List<Long> termIds);

    /**
     * 增加TM使用计数
     *
     * @param memoryIds TM记录ID
     */
    void incrementMemoryUsageCount(List<Long> memoryIds);

    /**
     * 异步记录翻译日志（不阻塞主翻译链路）
     *
     * @param sourceText      源文本
     * @param aiTranslation   AI翻译结果
     * @param targetLang      目标语言
     * @param termIds    命中的术语列表
     * @param memoryIds       命中的TM ID列表
     */
    @Async("taskExecutor")
    void recordTranslationLogAsync(String sourceText, String aiTranslation, Integer targetLang, List<Long> termIds, List<Long> memoryIds);

    /**
     * 构建System Prompt
     *
     * @param targetLang 目标语言code
     * @param terms      召回的术语列表
     * @param memories   召回的翻译记忆
     * @return 完整的System Prompt
     */
    String buildSystemPrompt(Integer targetLang, List<TranslationTerm> terms, List<TranslationMemory> memories);

    /**
     * 构建用户Prompt
     *
     * @param sourceText 源文本
     * @param targetLang 目标语言code
     * @param sourceType 来源类型：0正常，1EQ异常工单
     * @return 用户Prompt
     */
    String buildUserPrompt(String sourceText, Integer targetLang, Integer sourceType);

    /**
     * 根据条件查询历史翻译，完全一样则直接返回
     * @param dto 查询条件
     * @return 历史翻译结果
     */
    String findHistoryTranslation(AiTranslateDto dto);
}