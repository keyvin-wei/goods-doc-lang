package com.hq.goods.lang.service.impl;

import com.hq.goods.lang.bean.Constants;
import com.hq.goods.lang.bean.CustomsEnum;
import com.hq.goods.lang.bean.dto.AiTranslateDto;
import com.hq.goods.lang.bean.entity.TranslationMemory;
import com.hq.goods.lang.bean.entity.TranslationTerm;
import com.hq.goods.lang.bean.vo.AiTranslateVo;
import com.hq.goods.lang.config.TranslatorProviderFactory;
import com.hq.goods.lang.service.AiLLMService;
import com.hq.goods.lang.service.TranslationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @Description 对接ai相关接口，对接AiHubMix实现类
 * 请求示例：
 * {"top_p": 1,
 * 	"max_tokens": 5000,
 * 	"stream": false,
 * 	"temperature": 0.7,
 * 	"messages": [{"role": "system","content": "根据这份bom表，帮我分析下这个设计可能是做什么产品的？"},
 *               {"role": "user",  "content": "xxxx"}],
 * 	"model": "deepseek-v4-pro"
 * }
 *
 * @Author weiwenhan
 * @Date 2026/8/14 17:53
 */
@Slf4j
@Service
public class AiLLMServiceImpl implements AiLLMService {
    @Value("${hq.nextpcb.ai.translation.enhanced.enabled:true}")
    private boolean translationEnhancedEnabled;

    @Autowired
    private TranslationService translationService;
    @Autowired
    private TranslatorProviderFactory translatorProviderFactory;


/*

    @Override
    public BloggerScoreVo bloggerScore(BloggerScoreDto dto) {
        // 构建缓存key
        String cacheKey = RedisKey.getBloggerKey(dto.getUserId());
        // 检查缓存
        if (dto.getTemp() != null && dto.getTemp() == 1) {
            String cachedResult = redisService.get(cacheKey);
            if (StringUtils.isNotBlank(cachedResult)) {
                BloggerScoreVo cachedVo = JSONObject.parseObject(cachedResult, BloggerScoreVo.class);
                log.info("博主AI评分命中缓存，userId:{}, cachedVo:{}", dto.getUserId(), JSON.toJSONString(cachedVo));
                return cachedVo;
            }
        }
        
        long start = System.currentTimeMillis();
        BloggerScoreVo scoreVo = new BloggerScoreVo();
        scoreVo.setUserId(dto.getUserId());
        scoreVo.setLink(dto.getLink());
        scoreVo.setTemp(dto.getTemp());
        try {
            JSONObject params = new JSONObject();
            params.put("model", "gemini-3.1-pro-preview-search");
            params.put("response_format", Constants.JSON_OBJ);
            params.put("max_tokens", 40000);
            params.put("temperature", 0.1);
            params.put("top_p", 0.2);
            params.put("stream", false);
            
            // 构建系统提示词
            JSONArray messages = new JSONArray();
            String contentSys = "你是一位专精于电子硬件（PCB/SMT/电子元器件制造）领域的海外 KOL 营销专家。\n" +
                    "请严格按照以下【筛选规则】与【分析评估体系】，对输入的 YouTube 频道/达人进行逐一审核与深度量化评估，并输出结构化的JSON结果。\n\n" +
                    "### 第一步：初筛（排除法与红线控制）\n\n" +
                    "【严格保留条件】（满足其一即可进入第二步）：\n" +
                    "1. Creator / Maker（如硬核电子DIY、创意硬件、IoT/微控制器、机器人开发）。\n" +
                    "2. 硬件改造 / 逆向工程 / 芯片级维修（如 Micro-soldering 微焊、BGA植锡、游戏机/老旧设备 Modding、电路故障排查）。\n" +
                    "3. 电子工程教育 / 教学频道。\n" +
                    "4. 无法判断类型，但有明显硬件制作或拆解痕迹。\n\n" +
                    "【硬红线排除条件】（满足任意一项，直接排除并在表格中标记）：\n" +
                    "1. 停更红线：最后更新时间停留在 2024 年及以前（即 2025 年及 2026 年无任何更新）。\n" +
                    "2. 实体企业与机构：公司/企业官方号、芯片或设备品牌方、Solution Provider、经销商/代理商、线下培训机构、非盈利组织。\n" +
                    "3. 纯非电子类内容：纯硬纸板/塑胶手工、纯软体/少儿编程（无硬件落地）、纯贵金属提炼/废料回收（不涉及电路设计或装配）。\n\n" +
                    "### 第二步：深度量化评估（双轨制评分）\n\n" +
                    "针对通过第一步筛选的保留频道，进行以下维度的深度分析（基于其最新 5-10 个视频的内容）：\n\n" +
                    "1. 频道分类（最多选2项）：\n" +
                    "   教学 | 项目 | 维修/微焊 | 硬件改造(Modding) | 评测 | 实验\n\n" +
                    "2. 维度 A：PCB 设计能力评估（1-10分）\n" +
                    "   - 是否在视频中展示原理图（Schematic）或 PCB Layout（如 KiCad, Altium, EasyEDA）？\n" +
                    "   - 是否展示定制 PCB 的开箱、走线细节、高频/阻抗控制或多层板结构？\n" +
                    "   - 得分逻辑：从零设计复杂多层板为 8-10分；使用简单的开源板改版为 5-7分；纯维修/不画板子为 1-3分。\n\n" +
                    "3. 维度 B：SMT/钢网/焊接装配能力评估（1-10分）\n" +
                    "   - 是否展示使用不锈钢钢网（Stencil）涂抹锡膏/焊膏（Solder Paste）？\n" +
                    "   - 是否展示使用加热台、回流焊炉、热风枪进行 SMD 贴片或 BGA/QFN 芯片对齐贴装？\n" +
                    "   - 是否有批量/小批量贴装需求，或抱怨过手工贴片繁琐（潜在 SMT 代工需求）？\n" +
                    "   - 得分逻辑：高频使用钢网、极精细微焊/BGA、或大批量贴片为 8-10分；常规手工焊贴片为 5-7分；纯直插元件或无焊接过程为 1-4分。\n\n" +
                    "4. 综合合作建议 rating：\n" +
                    "   -  强烈推荐：[维度A] 或 [维度B] 任意一项  >=8分，且更新活跃、粉丝粘性高。\n" +
                    "   -  推荐：任意一项在 6-7分，受众垂直度高。\n" +
                    "   -  可进一步验证：侧重娱乐性/维修背书，或单项得分 4-5分。\n" +
                    "   -  不建议优先：受众重合度低或动手落地能力较弱。\n" +
                    "   -  不建议合作：完全无 PCB/SMT 场景需求。\n\n" +
                    "### 输出格式要求\n" +
                    "返回严格的JSON格式，包含以下字段：\n" +
                    "- primaryScreening: 初筛结果，0-符合硬红线排除条件，1-符合严格保留条件。如果用户既满足排除又满足保留，优先排除\n" +
                    "- rating: 评分分数，1-不建议合作，2-不建议优先，3-可进一步验证，4-推荐，5-强烈推荐。如果初筛结果排除则不需要评分和说明了，rating=0\n" +
                    "- explain: 分析说明 初筛结果说明 分数说明，60字左右\n\n" +
                    "示例输出：\n" +
                    "{\"primaryScreening\": 1, \"rating\": 4, \"explain\": \"聚焦于计算机架构、硬核电子工程和数字电路设计，他的日常项目（如用 74 系列芯片手工焊 CPU、设计模拟电路）极度消耗基础电子元器件、面包板、PCB 打样和焊接工具，推荐联系合作\"}\n\n" +
                    "【重要】必须严格按照上述JSON格式输出，不要添加任何额外字段或说明文字。第一个字符必须是{，最后一个字符必须是}";
            
            messages.add(new AiMessageReq("system", contentSys));
            
            // 构建用户消息
            String userContent = "请分析以下YouTube频道：" + "userId：" + dto.getUserId() + "，link：" + dto.getLink() + "，按要求返回分析评估结果JSON。";
            messages.add(new AiMessageReq("user", userContent));
            params.put("messages", messages);
            
            // 发送请求
            String content = HttpUtil.postAihubmixContentRetry(getAihubmixChatUrl(), params);
            content = StringUtil.trimJson(content);
            if (StringUtils.isNotBlank(content)) {
                JSONObject contentObj = JSONObject.parseObject(content);
                // 设置结果
                scoreVo.setPrimaryScreening(contentObj.getInteger("primaryScreening"));
                scoreVo.setRating(contentObj.getInteger("rating"));
                scoreVo.setExplain(contentObj.getString("explain"));
                scoreVo.setTime(DateUtil.getDateTime());

                // 如果初筛被排除 rating=0
                if (scoreVo.getPrimaryScreening() != null && scoreVo.getPrimaryScreening() == 0) {
                    scoreVo.setRating(0);
                }
                // 保存到缓存
                redisService.set(cacheKey, JSONObject.toJSONString(scoreVo), 1000, TimeUnit.DAYS);
            }
            log.info("博主AI评分完成，花费：{}ms, userId={}, link={}, scoreVo={}", System.currentTimeMillis() - start, dto.getUserId(), dto.getLink(), JSONObject.toJSONString(scoreVo));

        } catch (Exception e) {
            log.error("博主AI评分请求报错！userId={}, link={}", dto.getUserId(), dto.getLink());
            log.info("详细信息：", e);
            
            // 异常时返回默认结果
            scoreVo.setPrimaryScreening(0);
            scoreVo.setRating(0);
            scoreVo.setExplain("AI分析异常，请稍后重试");
        }
        
        return scoreVo;
    }
*/


    /**
     * AI翻译
     * @param dto
     * @return
     */
    @Override
    public AiTranslateVo aiTranslate(AiTranslateDto dto) {
        long start = System.currentTimeMillis();
        // 动态召回PCB术语（数据库+Redis缓存）
        List<TranslationTerm> matchedTerms = Collections.emptyList();
        List<TranslationMemory> matchedMemories = Collections.emptyList();
        // 是否开启术语增强，限制为EQ异常工单
        if (translationEnhancedEnabled && CustomsEnum.LANG_SOURCE_1.getCode().equals(dto.getSource())) {
            try {
                matchedTerms = translationService.recallTerms(dto.getText());
            } catch (Exception e) {
                log.warn("[AiLLMServiceImpl] 术语召回失败，降级为无术语模式", e);
            }
            // 动态召回翻译记忆（中英互译场景）
            try {
                Integer targetLang = dto.getTarget();
                // 仅中英互译时启用TM召回
                if (CustomsEnum.LANG_ZH.getCode().equals(targetLang) || CustomsEnum.LANG_EN.getCode().equals(targetLang)) {
                    matchedMemories = translationService.recallMemory(dto.getText(), targetLang);
                }
            } catch (Exception e) {
                log.warn("[AiLLMServiceImpl] TM召回失败，降级为无TM模式", e);
            }
        } else {
            log.info("[AiLLMServiceImpl] 翻译增强功能已关闭，跳过术语/TM召回");
        }

        // 使用TranslationService动态构建System/User Prompt
        String contentSys = translationService.buildSystemPrompt(dto.getTarget(), matchedTerms, matchedMemories);
        String contentUser = translationService.buildUserPrompt(dto.getText(), dto.getTarget(), dto.getSource());

        String res = "";
        boolean needTranslate = true;
        if (CustomsEnum.LANG_ZH.getCode().equals(dto.getTarget())) {
            // 基本汉字、符号和标点、空白字符、数字
            if (dto.getText() != null && dto.getText().matches(Constants.REGEX_CHINESE_ALL)) {
                res = dto.getText();
                needTranslate = false;
                log.info("AI翻译输入是中文，目标也是中文，无需翻译");
            }
        }
        //查询历史翻译
        if (needTranslate) {
            String historyTranslation = translationService.findHistoryTranslation(dto);
            if(StringUtils.isNotBlank(historyTranslation)){
                needTranslate = false;
                res = historyTranslation;
                log.info("AI翻译完全命中记忆翻译，无需AI翻译");
            }
        }

        if (needTranslate) {
            // 调用AI翻译
            res = translatorProviderFactory.getProvider().translate("gpt-5.2", contentSys, contentUser);
        }
        // 异步记录翻译日志（不阻塞主链路）
        final String finalRes = res;
        final List<TranslationTerm> finalTerms = matchedTerms;
        final List<TranslationMemory> finalMemories = matchedMemories;
        try {
            List<Long> termIds = finalTerms.stream().map(TranslationTerm::getId).filter(Objects::nonNull).collect(Collectors.toList());
            List<Long> memoryIds = finalMemories.stream().map(TranslationMemory::getId).filter(Objects::nonNull).collect(Collectors.toList());
            //添加翻译日志
            translationService.recordTranslationLogAsync(dto.getText(), finalRes, dto.getTarget(), termIds, memoryIds);
            // 增加使用计数
            translationService.incrementTermUsageCount(termIds);
            translationService.incrementMemoryUsageCount(memoryIds);

        } catch (Exception e) {
            log.warn("[AiLLMServiceImpl] 异步记录翻译日志失败", e);
        }

        AiTranslateVo vo = new AiTranslateVo();
        vo.setText(dto.getText());
        vo.setTarget(dto.getTarget());
        vo.setTranslation(res);
        log.info("对接AI翻译完成，花费：{}ms, 命中术语:{}条, 命中TM:{}条, 增强开关:{}", System.currentTimeMillis() - start, matchedTerms.size(), matchedMemories.size(), translationEnhancedEnabled);
        return vo;
    }
}
