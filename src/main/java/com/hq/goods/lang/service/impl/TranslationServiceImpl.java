package com.hq.goods.lang.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hq.goods.lang.bean.Constants;
import com.hq.goods.lang.bean.CustomsEnum;
import com.hq.goods.lang.bean.dto.AiTranslateDto;
import com.hq.goods.lang.bean.entity.TranslationLog;
import com.hq.goods.lang.bean.entity.TranslationMemory;
import com.hq.goods.lang.bean.entity.TranslationTerm;
import com.hq.goods.lang.dao.TranslationLogDao;
import com.hq.goods.lang.dao.TranslationMemoryDao;
import com.hq.goods.lang.dao.TranslationTermDao;
import com.hq.goods.lang.service.TranslationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 翻译服务实现类
 *
 * <p>统一实现翻译相关的术语召回、翻译记忆召回和翻译日志记录功能。</p>
 * <p>术语召回策略：精确匹配(cn) + 别名匹配(aliases) → 按weight降序返回</p>
 * <p>翻译记忆召回策略：MySQL FULLTEXT检索 + 关键词覆盖度排序 → Top 5</p>
 *
 * @author ai-assistant
 * @since 2026/06/26
 */
@Slf4j
@Service
public class TranslationServiceImpl implements TranslationService {
    @Autowired
    private TranslationTermDao translationTermDao;
    @Autowired
    private TranslationMemoryDao translationMemoryDao;
    @Autowired
    private TranslationLogDao translationLogDao;
    

    @Override
    public List<TranslationTerm> recallTerms(String sourceText) {
        if (StringUtils.isBlank(sourceText)) {
            return Collections.emptyList();
        }

        List<TranslationTerm> allTerms = translationTermDao.selectAllValid();
        if (allTerms == null || allTerms.isEmpty()) {
            return Collections.emptyList();
        }

        String lowerText = sourceText.toLowerCase();
        List<TranslationTerm> matchedList = new ArrayList<>();

        // 直接遍历所有术语进行匹配
        for (TranslationTerm term : allTerms) {
            if (isMatch(lowerText, term)) {
                matchedList.add(term);
            }
        }

        // 按weight降序，相同weight按id升序
        matchedList.sort((a, b) -> {
            int weightCompare = Integer.compare(
                    b.getWeight() == null ? 100 : b.getWeight(),
                    a.getWeight() == null ? 100 : a.getWeight()
            );
            if (weightCompare != 0) {
                return weightCompare;
            }
            return Long.compare(
                    a.getId() == null ? 0L : a.getId(),
                    b.getId() == null ? 0L : b.getId()
            );
        });

        // TopK截断：避免过多术语撑爆Prompt
        if (matchedList.size() > Constants.MAX_RECALL_TERMS) {
            return matchedList.subList(0, Constants.MAX_RECALL_TERMS);
        }
        return matchedList;
    }

    @Override
    public List<TranslationMemory> recallMemory(String sourceText, Integer targetLang) {
        if (StringUtils.isBlank(sourceText)) {
            return Collections.emptyList();
        }
        // 提取关键词：取前30个字符作为FULLTEXT查询输入，避免过长
        String keywords = extractKeywords(sourceText);
        List<TranslationMemory> candidates = translationMemoryDao.selectByFullText(keywords, Constants.MAX_RECALL_MEMORIES);
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        // 过滤语言方向匹配的候选
        List<TranslationMemory> filtered = candidates.stream().filter(m -> Objects.equals(targetLang, m.getTargetLang())).collect(Collectors.toList());
        if (filtered.isEmpty()) {
            return Collections.emptyList();
        }
        // 按关键词覆盖度二次排序（覆盖度越高越靠前）
        String lowerSource = sourceText.toLowerCase();
        filtered.sort(Comparator.comparingDouble(m -> -calculateCoverage(lowerSource, m.getSourceText())));
        return filtered.stream().limit(Constants.MAX_RECALL_MEMORIES).collect(Collectors.toList());
    }

    @Override
    public void incrementTermUsageCount(List<Long> termIds) {
        if (!CollectionUtils.isEmpty(termIds)) {
            translationTermDao.incrementUsageCount(termIds);
            log.info("[TranslationServiceImpl] 术语使用计数更新成功, termIds:{}", JSONObject.toJSONString(termIds));
        }
    }

    @Override
    public void incrementMemoryUsageCount(List<Long> memoryIds) {
        if (!CollectionUtils.isEmpty(memoryIds)) {
            translationMemoryDao.incrementUsageCount(memoryIds);
            log.info("[TranslationServiceImpl] TM使用计数更新成功, memoryIds:{}", JSONObject.toJSONString(memoryIds));
        }
    }

    @Override
    @Async("taskExecutor")
    public void recordTranslationLogAsync(String sourceText, String aiTranslation, Integer targetLang, List<Long> termIds, List<Long> memoryIds) {
        try {
            TranslationLog record = new TranslationLog();
            record.setSourceText(sourceText);
            record.setAiTranslation(aiTranslation);
            record.setSourceLang(0);
            record.setTargetLang(targetLang);
            record.setMatchedTerms(termIds == null ? "[]" : JSON.toJSONString(termIds));
            record.setMatchedTmIds(memoryIds == null ? "[]" : JSON.toJSONString(memoryIds));
            record.setModified(0);
            record.setErrorType("");
            record.setReviewer("");
            record.setDeleteStatus(0);
            translationLogDao.insert(record);
        } catch (Exception e) {
            log.warn("[TranslationServiceImpl] 异步记录翻译日志失败", e);
        }
    }



    /**
     * 判断单个术语是否匹配源文本
     */
    private boolean isMatch(String lowerText, TranslationTerm term) {
        String cn = term.getCn();
        if (StringUtils.isNotBlank(cn)) {
            if (lowerText.contains(cn.toLowerCase())) {
                return true;
            }
        }

        String cnAliasesJson = term.getCnAliases();
        if (StringUtils.isNotBlank(cnAliasesJson)) {
            try {
                JSONArray cnAliases = JSON.parseArray(cnAliasesJson);
                if (cnAliases != null) {
                    for (int i = 0; i < cnAliases.size(); i++) {
                        String alias = cnAliases.getString(i);
                        if (StringUtils.isNotBlank(alias) && lowerText.contains(alias.toLowerCase())) {
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[TranslationServiceImpl] 中文别名JSON解析失败: id={}, aliases={}", term.getId(), cnAliasesJson);
            }
        }

        String en = term.getEn();
        if (StringUtils.isNotBlank(en)) {
            if (lowerText.contains(en.toLowerCase())) {
                return true;
            }
        }

        String enAliasesJson = term.getEnAliases();
        if (StringUtils.isNotBlank(enAliasesJson)) {
            try {
                JSONArray enAliases = JSON.parseArray(enAliasesJson);
                if (enAliases != null) {
                    for (int i = 0; i < enAliases.size(); i++) {
                        String alias = enAliases.getString(i);
                        if (StringUtils.isNotBlank(alias) && lowerText.contains(alias.toLowerCase())) {
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[TranslationServiceImpl] 英文别名JSON解析失败: id={}, aliases={}", term.getId(), enAliasesJson);
            }
        }

        return false;
    }

    /**
     * 提取关键词用于FULLTEXT检索
     * <p>策略：去除标点，取前50个字符，避免超长查询影响性能</p>
     */
    private String extractKeywords(String sourceText) {
        if (sourceText == null) {
            return "";
        }
        // 移除常见标点符号
        String cleaned = sourceText.replaceAll("[\\p{P}\\p{S}]", " ");
        cleaned = cleaned.trim().replaceAll("\\s+", " ");
        if (cleaned.length() > 50) {
            cleaned = cleaned.substring(0, 50);
        }
        return cleaned;
    }

    /**
     * 计算源文本与TM源文本的关键词覆盖度（简单实现）
     *
     * @param sourceText  当前源文本（小写）
     * @param tmSourceText TM中的源文本
     * @return 覆盖度 0.0-1.0
     */
    private double calculateCoverage(String sourceText, String tmSourceText) {
        if (StringUtils.isBlank(tmSourceText)) {
            return 0.0;
        }
        String lowerTm = tmSourceText.toLowerCase();
        // 简单策略：按空格分词，计算当前文本中包含TM源文本词汇的比例
        String[] tmWords = lowerTm.split("\\s+");
        if (tmWords.length == 0) {
            return 0.0;
        }
        int matchCount = 0;
        for (String word : tmWords) {
            if (word.length() > 1 && sourceText.contains(word)) {
                matchCount++;
            }
        }
        return (double) matchCount / tmWords.length;
    }

    @Override
    public String buildSystemPrompt(Integer targetLang, List<TranslationTerm> terms, List<TranslationMemory> memories) {
        String langName = CustomsEnum.getLangName(targetLang);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(BASE_PROMPT_TEMPLATE, langName, langName));

        // 注入术语对照表
        String termSection = buildTermSection(terms, targetLang);
        if (!termSection.isEmpty()) {
            sb.append("\n").append(termSection);
        }

        // 注入历史翻译参考
        String memorySection = buildMemorySection(memories);
        if (!memorySection.isEmpty()) {
            sb.append("\n").append(memorySection);
        }

        return sb.toString();
    }

    @Override
    public String buildUserPrompt(String sourceText, Integer targetLang, Integer sourceType) {
        String langName = CustomsEnum.getLangName(targetLang);
        if (langName == null) {
            langName = "英文";
        }
        boolean isZhTarget = CustomsEnum.LANG_ZH.getCode().equals(targetLang);
        if (isZhTarget) {
            if (CustomsEnum.LANG_SOURCE_1.getCode().equals(sourceType)) {
                // EQ异常工单特殊处理
                return "请按PCB行业语言将下面的英文翻译为中文，识别到英文缩写词不需要翻译，直接将缩写词代入到译文中，原文为：`" + sourceText + "`";
            } else {
                return "翻译成" + langName + " (非中文翻译成中文、中文句子原样返回、不翻译单位、不要说明、不要括号说明、直接给出翻译结果):" + sourceText;
            }
        } else {
            return "翻译成" + langName + " (不翻译单位、不要说明、不要括号说明、直接给出翻译结果):" + sourceText;
        }
    }

    /**
     * 构建术语对照表章节
     */
    private String buildTermSection(List<TranslationTerm> terms, Integer targetLang) {
        if (terms == null || terms.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 术语对照表（必须严格遵守）\n");
        sb.append("以下中英专业术语必须按照指定的翻译方式进行翻译，不得使用其他译法：\n ");

        boolean isZhTarget = Integer.valueOf(2).equals(targetLang);
        int count = 0;

        for (TranslationTerm term : terms) {
            String cn = term.getCn();
            String en = term.getEn();
            if (StringUtils.isBlank(cn) || StringUtils.isBlank(en)) {
                continue;
            }

            if (isZhTarget) {
                sb.append(en).append("=").append(cn);
            } else {
                sb.append(cn).append("=").append(en);
            }
            sb.append(" | ");
            count++;
        }

        if (count == 0) {
            return "";
        }

        // 删除最后的 " | "
        if (sb.length() >= 3) {
            sb.delete(sb.length() - 3, sb.length());
        }

        return sb.toString();
    }

    /**
     * 构建历史翻译参考章节
     */
    private String buildMemorySection(List<TranslationMemory> memories) {
        if (memories == null || memories.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 历史翻译参考（仅供参考，以术语对照表为准）\n");

        int idx = 1;
        for (TranslationMemory m : memories) {
            String src = m.getSourceText();
            String tgt = m.getTargetText();
            if (StringUtils.isBlank(src) || StringUtils.isBlank(tgt)) {
                continue;
            }
            sb.append(idx).append(". ").append(src)
              .append(" → ").append(tgt).append("\n");
            idx++;
        }

        return sb.toString();
    }

    private static final String BASE_PROMPT_TEMPLATE =
            "您是一位专业的 %s 母语翻译员，需要将文本流畅地翻译成 %s。\n" +
            "\n" +
            "## 翻译规则\n" +
            "1. 仅输出翻译内容，不包含解释或其他内容（例如\"翻译如：\"或\"翻译如下：\"）；\n" +
            "2. 返回的译文必须与原文保持完全相同的段落数和格式；\n" +
            "3. 如果文本包含 HTML 标签，请考虑标签在翻译中的放置位置，同时保持流畅性；\n" +
            "4. 对于不应翻译的内容（例如国际单位、数字、连接符、专有名词、代码等），请保留原文。\n" +
            "\n" +
            "## 输出格式：\n" +
            "- **单段输入** → 直接输出译文（无分隔符，无额外文本）\n" +
            "\n" +
            "### 单段输入：\n" +
            "单段内容\n" +
            "\n" +
            "### 单段输出：\n" +
            "直接翻译，无分隔符\n";

    /**
     * 根据条件查询历史翻译，完全一样则直接返回
     * @param dto 查询条件
     * @return 最近历史翻译结果
     */
    @Override
    public String findHistoryTranslation(AiTranslateDto dto) {
        // EQ的历史翻译直接返回
        if(CustomsEnum.LANG_SOURCE_1.getCode().equals(dto.getSource())){
            TranslationMemory memory = translationMemoryDao.findBySourceTextTarget(dto.getText(), dto.getTarget());
            if(memory!=null && StringUtils.isNotBlank(memory.getTargetText())){
                return memory.getTargetText();
            }
        }
        return "";
    }

}