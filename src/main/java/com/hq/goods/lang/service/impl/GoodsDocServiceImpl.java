package com.hq.goods.lang.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hq.goods.lang.bean.CustomException;
import com.hq.goods.lang.bean.CustomsEnum;
import com.hq.goods.lang.bean.ResponseEnum;
import com.hq.goods.lang.bean.dto.AiTranslateDto;
import com.hq.goods.lang.bean.dto.GenerateDescReq;
import com.hq.goods.lang.bean.dto.GenerateMultiReq;
import com.hq.goods.lang.bean.dto.ParsePartReq;
import com.hq.goods.lang.bean.dto.ParseTextReq;
import com.hq.goods.lang.bean.dto.SaveReq;
import com.hq.goods.lang.bean.entity.GoodsDocRecord;
import com.hq.goods.lang.bean.entity.TranslationTerm;
import com.hq.goods.lang.bean.vo.GoodsDocDescVo;
import com.hq.goods.lang.bean.vo.GoodsDocMultiVo;
import com.hq.goods.lang.bean.vo.GoodsDocRecordVo;
import com.hq.goods.lang.bean.vo.GoodsDocVo;
import com.hq.goods.lang.bean.vo.PageResult;
import com.hq.goods.lang.bean.vo.ParamItem;
import com.hq.goods.lang.bean.vo.PriceTier;
import com.hq.goods.lang.bean.vo.ProductVo;
import com.hq.goods.lang.bean.vo.RagHit;
import com.hq.goods.lang.bean.vo.RecordVo;
import com.hq.goods.lang.bean.vo.SeoVo;
import com.hq.goods.lang.dao.GoodsDocRecordDao;
import com.hq.goods.lang.service.AiLLMService;
import com.hq.goods.lang.service.GoodsDocService;
import com.hq.goods.lang.service.RagService;
import com.hq.goods.lang.service.TranslationService;
import com.hq.goods.lang.utils.GoodsDocParseUtil;
import com.hq.goods.lang.utils.TranslatorProviderFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 外贸商品文档编排服务实现
 */
@Slf4j
@Service
public class GoodsDocServiceImpl implements GoodsDocService {

    private static final String MODEL = "gpt-5.5";
    private static final int RAG_TOP_K = 3;
    /** 落地页支持语言（预留可扩展） */
    private static final List<String> SUPPORTED_LANGS = Arrays.asList("en", "zh", "ja", "ru");
    private static final String DEFAULT_LANG = "en";
    /** 阶梯档位：起购量 → 单价递减系数 */
    private static final int[] TIER_QTY = {1, 5, 10, 100};
    private static final double[] TIER_FACTOR = {1.00, 0.85, 0.80, 0.62};

    @Autowired
    private TranslatorProviderFactory translatorProviderFactory;
    @Autowired
    private AiLLMService aiLLMService;
    @Autowired
    private TranslationService translationService;
    @Autowired
    private RagService ragService;
    @Autowired
    private GoodsDocRecordDao goodsDocRecordDao;

    // ---------- 解析 ----------

    @Override
    public GoodsDocVo parsePart(ParsePartReq req) {
        String query = StringUtils.defaultString(req.getPartNumber())
                + " " + StringUtils.defaultString(req.getBrand());
        return doParse(query.trim(), req.getPartNumber(), req.getBrand());
    }

    @Override
    public GoodsDocVo parseText(ParseTextReq req) {
        return doParse(req.getRawText(), null, null);
    }

    private GoodsDocVo doParse(String query, String partNumber, String brand) {
        List<RagHit> topK = ragService.searchTopK(query, RAG_TOP_K);
        List<TranslationTerm> terms = translationService.recallTerms(query);
        String sys = buildParseSystemPrompt();
        String user = buildParseUserPrompt(query, topK, terms);
        String res = callLlmWithRetry(sys, user);
        GoodsDocVo vo = GoodsDocParseUtil.toGoodsDocVo(GoodsDocParseUtil.extractJson(res));
        if (StringUtils.isBlank(vo.getPartNumber())) {
            vo.setPartNumber(partNumber);
        }
        if (StringUtils.isBlank(vo.getBrand())) {
            vo.setBrand(brand);
        }
        vo.setTopK(topK);
        vo.setRawInput(query);
        return vo;
    }

    // ---------- 英文描述 ----------

    @Override
    public GoodsDocDescVo generateDesc(GenerateDescReq req) {
        GoodsDocVo vo = req.getGoodsDoc();
        List<TranslationTerm> terms = translationService.recallTerms(buildQueryFromVo(vo));
        String sys = buildDescSystemPrompt();
        String user = buildDescUserPrompt(vo, terms);
        String res = callLlm(sys, user);
        return new GoodsDocDescVo(res);
    }

    // ---------- 多语言 + SEO ----------

    @Override
    public GoodsDocMultiVo generateMulti(GenerateMultiReq req) {
        GoodsDocVo vo = req.getGoodsDoc();
        String description = StringUtils.defaultString(req.getDescription());

        Map<String, String> multilingual = new LinkedHashMap<>();
        multilingual.put("en", description);
        multilingual.put("zh", translate(description, CustomsEnum.LANG_ZH.getCode()));
        multilingual.put("zhTw", translate(description, CustomsEnum.LANG_ZH_TW.getCode()));
        multilingual.put("ja", translate(description, CustomsEnum.LANG_JP.getCode()));
        multilingual.put("ru", translate(description, CustomsEnum.LANG_RU.getCode()));

        Map<String, SeoVo> seo = generateSeo(vo, description);

        GoodsDocMultiVo result = new GoodsDocMultiVo();
        result.setMultilingual(multilingual);
        result.setSeo(seo);
        return result;
    }

    private String translate(String text, Integer target) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        try {
            AiTranslateDto dto = new AiTranslateDto();
            dto.setText(text);
            dto.setTarget(target);
            dto.setSource(0);
            return aiLLMService.aiTranslate(dto).getTranslation();
        } catch (Exception e) {
            log.warn("[GoodsDoc] 翻译失败 target={}", target, e);
            return "";
        }
    }

    private Map<String, SeoVo> generateSeo(GoodsDocVo vo, String description) {
        List<TranslationTerm> terms = translationService.recallTerms(buildQueryFromVo(vo));
        String sys = buildSeoSystemPrompt();
        String user = buildSeoUserPrompt(vo, description, terms);
        try {
            String res = callLlm(sys, user);
            return GoodsDocParseUtil.parseSeo(GoodsDocParseUtil.extractJson(res));
        } catch (Exception e) {
            log.warn("[GoodsDoc] SEO 生成失败，返回空", e);
            return Collections.emptyMap();
        }
    }

    // ---------- 保存 / 列表 / 详情 / 删除 / 产品 ----------

    @Override
    public Long save(SaveReq req) {
        GoodsDocVo vo = req.getGoodsDoc();
        if (vo == null) {
            throw new CustomException(ResponseEnum.PARAMETER_ERROR.getCode(), "基本资料不能为空");
        }
        GoodsDocRecord record = toRecord(req);
        if (record.getId() == null) {
            record.setDeleteStatus(0);
            record.setStatus(0);
            record.setCTime(LocalDateTime.now());
            goodsDocRecordDao.insert(record);
        } else {
            record.setUTime(LocalDateTime.now());
            goodsDocRecordDao.updateById(record);
        }
        return record.getId();
    }

    @Override
    public PageResult<RecordVo> list(int page, int size, String partNumber) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 100) {
            size = 20;
        }
        QueryWrapper<GoodsDocRecord> qw = new QueryWrapper<GoodsDocRecord>().eq("delete_status", 0);
        if (StringUtils.isNotBlank(partNumber)) {
            qw.eq("part_number", partNumber.trim());
        }
        qw.orderByDesc("id");
        Page<GoodsDocRecord> p = new Page<>(page, size);
        goodsDocRecordDao.selectPage(p, qw);
        List<RecordVo> list = p.getRecords().stream().map(this::toRecordVo).collect(Collectors.toList());
        return new PageResult<>(p.getTotal(), list);
    }

    @Override
    public GoodsDocRecordVo detail(Long id) {
        GoodsDocRecord r = requireRecord(id);
        GoodsDocRecordVo vo = new GoodsDocRecordVo();
        vo.setId(r.getId());
        vo.setBasic(toGoodsDocVo(r));
        vo.setMultilingual(parseJsonMap(r.getMultilingual(), String.class));
        vo.setSeo(parseJsonMap(r.getSeo(), SeoVo.class));
        vo.setSourceType(r.getSourceType());
        vo.setCTime(r.getCTime());
        vo.setUTime(r.getUTime());
        return vo;
    }

    @Override
    public void delete(Long id) {
        GoodsDocRecord r = new GoodsDocRecord();
        r.setId(id);
        r.setDeleteStatus(1);
        goodsDocRecordDao.updateById(r);
    }

    @Override
    public ProductVo product(Long id, HttpServletRequest request) {
        return buildProduct(id, resolveLang(request));
    }

    /** 从 cookie 解析当前语言，无 cookie 或非法值回退默认 en */
    String resolveLang(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return DEFAULT_LANG;
        }
        for (Cookie c : request.getCookies()) {
            if ("lang".equals(c.getName()) && SUPPORTED_LANGS.contains(c.getValue())) {
                return c.getValue();
            }
        }
        return DEFAULT_LANG;
    }

    private ProductVo buildProduct(Long id, String lang) {
        GoodsDocRecord r = requireRecord(id);
        ProductVo vo = new ProductVo();
        vo.setId(r.getId());
        vo.setPartNumber(r.getPartNumber());
        vo.setBrand(r.getBrand());
        vo.setCategory(r.getCategory());
        vo.setSubcategory(r.getSubcategory());
        vo.setSeries(r.getSeries());
        vo.setPackageType(r.getPackageType());
        vo.setParameters(parseJsonArray(r.getParameters(), ParamItem.class));
        vo.setDescriptionEn(r.getDescriptionEn());
        vo.setApplications(parseJsonArray(r.getApplications(), String.class));
        vo.setMultilingual(parseJsonMap(r.getMultilingual(), String.class));
        vo.setSeo(parseJsonMap(r.getSeo(), SeoVo.class));
        vo.setImageUrl(r.getImageUrl());
        vo.setDatasheetUrl(r.getDatasheetUrl());
        // 本地化描述：multilingual[lang] 无则回退英文
        Map<String, String> multi = vo.getMultilingual();
        String desc = multi != null ? multi.get(lang) : null;
        vo.setDescription(StringUtils.isBlank(desc) ? r.getDescriptionEn() : desc);
        // 随机库存与阶梯价（id 为种子，同型号每次刷新稳定）
        Random rnd = new Random(id);
        vo.setStock(1 + rnd.nextInt(999));
        vo.setPrices(buildPrices(rnd));
        return vo;
    }

    private List<PriceTier> buildPrices(Random rnd) {
        int baseCents = 50 + rnd.nextInt(4950);            // 0.50 ~ 50.00 USD
        BigDecimal base = BigDecimal.valueOf(baseCents, 2);
        List<PriceTier> list = new ArrayList<>(TIER_QTY.length);
        for (int i = 0; i < TIER_QTY.length; i++) {
            BigDecimal unit = base.multiply(BigDecimal.valueOf(TIER_FACTOR[i]))
                    .setScale(4, RoundingMode.HALF_UP);
            BigDecimal ext = unit.multiply(BigDecimal.valueOf(TIER_QTY[i]))
                    .setScale(4, RoundingMode.HALF_UP);
            PriceTier t = new PriceTier();
            t.setQtyLabel(TIER_QTY[i] + "+");
            t.setUnitPrice(unit);
            t.setExtPrice(ext);
            list.add(t);
        }
        return list;
    }

    private GoodsDocRecord requireRecord(Long id) {
        GoodsDocRecord r = goodsDocRecordDao.selectById(id);
        if (r == null || Integer.valueOf(1).equals(r.getDeleteStatus())) {
            throw new CustomException(ResponseEnum.PARAMETER_ERROR.getCode(), "记录不存在");
        }
        return r;
    }

    // ---------- LLM 调用 ----------

    private String callLlm(String sys, String user) {
        String res = translatorProviderFactory.getProvider().translate(MODEL, sys, user);
        if (StringUtils.isBlank(res)) {
            throw new CustomException(ResponseEnum.INNER_SERVER_ERROR.getCode(), "AI 生成结果为空，请重试");
        }
        return res.trim();
    }

    private String callLlmWithRetry(String sys, String user) {
        String res = callLlm(sys, user);
        try {
            GoodsDocParseUtil.validateJson(res);
        } catch (Exception e) {
            log.warn("[GoodsDoc] AI 输出解析失败，重试一次");
            res = callLlm(sys, user);
            GoodsDocParseUtil.validateJson(res);
        }
        return res;
    }

    // ---------- Prompt 构建 ----------

    private String buildParseSystemPrompt() {
        return "你是电子元器件领域的资深专家。请把用户提供的元器件信息解析为统一 JSON，严格按以下字段输出（无法识别的字段返回空字符串或空数组，不要编造）：\n"
                + "{\n"
                + "  \"partNumber\": \"型号\", \"brand\": \"品牌\", \"category\": \"分类\", \"subcategory\": \"子分类\", \"series\": \"系列\",\n"
                + "  \"packageType\": \"封装\", \"mountingType\": \"安装类型\", \"pinCount\": 0, \"dimensions\": \"尺寸\",\n"
                + "  \"parameters\": [{\"name\": \"参数名\", \"value\": \"数值\", \"unit\": \"单位\"}],\n"
                + "  \"operatingTemp\": \"工作温度范围\", \"storageTemp\": \"存储温度\", \"grade\": \"质量等级\", \"rohs\": \"RoHS/环保\",\n"
                + "  \"packaging\": \"包装方式\", \"moq\": \"最小起订量\", \"unit\": \"单位\", \"hsCode\": \"海关编码\",\n"
                + "  \"leadTime\": \"交期\", \"priceRange\": \"价格区间\", \"availability\": \"供货状态\",\n"
                + "  \"applications\": [\"应用领域\"]\n"
                + "}\n"
                + "规则：1.只输出 JSON，不要解释、前后缀或 markdown 代码块；"
                + "2.parameters 为键值数组，按品类提取关键参数（如阻值、容值、耐压、电流、频率、精度、内核、Flash、RAM 等）；"
                + "3.无法识别的字段返回空字符串或空数组。";
    }

    private String buildParseUserPrompt(String query, List<RagHit> topK, List<TranslationTerm> terms) {
        StringBuilder sb = new StringBuilder();
        sb.append("请解析以下元器件信息并输出 JSON：\n\n原始输入：\n").append(query).append("\n");
        if (topK != null && !topK.isEmpty()) {
            sb.append("\n相似型号参考（辅助判断分类与参数格式，仅供参考）：\n");
            for (int i = 0; i < topK.size(); i++) {
                RagHit h = topK.get(i);
                sb.append(i + 1).append(". ").append(h.getPartNumber())
                        .append(" | ").append(StringUtils.defaultString(h.getBrand())).append("\n");
            }
        }
        String termsSection = buildTermSection(terms);
        if (!termsSection.isEmpty()) {
            sb.append("\n").append(termsSection);
        }
        return sb.toString();
    }

    private String buildDescSystemPrompt() {
        return "你是电子元器件行业的海外英文产品描述撰写专家。请根据提供的元器件结构化参数，撰写一段专业、真实、适合海外英文网站展示的产品描述（2-4 句）。必须基于真实参数，不得编造未提供的参数。全英文输出，直接给出描述正文，不要标题或解释。";
    }

    private String buildDescUserPrompt(GoodsDocVo vo, List<TranslationTerm> terms) {
        StringBuilder sb = new StringBuilder("元器件信息：\n");
        appendVoFields(sb, vo);
        sb.append("关键参数：").append(renderParams(vo.getParameters())).append("\n");
        sb.append("应用领域：").append(vo.getApplications() == null ? ""
                : String.join(", ", vo.getApplications())).append("\n");
        String termsSection = buildTermSection(terms);
        if (!termsSection.isEmpty()) {
            sb.append("\n").append(termsSection);
        }
        sb.append("\n请生成英文产品描述。");
        return sb.toString();
    }

    private String buildSeoSystemPrompt() {
        return "你是电子元器件跨境电商 SEO 专家。请根据元器件信息生成 简体中文(zh)、英文(en)、日文(ja)、俄文(ru) 四种语言的 SEO 内容。严格只输出以下 JSON，不要任何其他内容：\n"
                + "{\"zh\": {\"title\": \"...\", \"keywords\": [\"...\"], \"description\": \"...\"},\n"
                + " \"en\": {\"title\": \"...\", \"keywords\": [\"...\"], \"description\": \"...\"},\n"
                + " \"ja\": {\"title\": \"...\", \"keywords\": [\"...\"], \"description\": \"...\"},\n"
                + " \"ru\": {\"title\": \"...\", \"keywords\": [\"...\"], \"description\": \"...\"}}\n"
                + "要求：title 30-60 字符；keywords 5-10 个，包含型号、品牌、品类词；description 50-160 字符，含型号与关键卖点。";
    }

    private String buildSeoUserPrompt(GoodsDocVo vo, String description, List<TranslationTerm> terms) {
        StringBuilder sb = new StringBuilder("元器件信息：\n");
        appendVoFields(sb, vo);
        sb.append("关键参数：").append(renderParams(vo.getParameters())).append("\n");
        sb.append("英文描述：").append(StringUtils.defaultString(description)).append("\n");
        String termsSection = buildTermSection(terms);
        if (!termsSection.isEmpty()) {
            sb.append("\n").append(termsSection);
        }
        sb.append("\n请生成四种语言的 SEO JSON。");
        return sb.toString();
    }

    private void appendVoFields(StringBuilder sb, GoodsDocVo vo) {
        if (vo == null) {
            return;
        }
        appendField(sb, "型号", vo.getPartNumber());
        appendField(sb, "品牌", vo.getBrand());
        appendField(sb, "分类", vo.getCategory());
        appendField(sb, "子分类", vo.getSubcategory());
        appendField(sb, "系列", vo.getSeries());
        appendField(sb, "封装", vo.getPackageType());
        appendField(sb, "安装类型", vo.getMountingType());
        appendField(sb, "引脚数", vo.getPinCount() == null ? null : String.valueOf(vo.getPinCount()));
        appendField(sb, "尺寸", vo.getDimensions());
        appendField(sb, "工作温度", vo.getOperatingTemp());
        appendField(sb, "存储温度", vo.getStorageTemp());
        appendField(sb, "质量等级", vo.getGrade());
        appendField(sb, "RoHS", vo.getRohs());
        appendField(sb, "包装方式", vo.getPackaging());
        appendField(sb, "MOQ", vo.getMoq());
        appendField(sb, "单位", vo.getUnit());
        appendField(sb, "海关编码", vo.getHsCode());
        appendField(sb, "交期", vo.getLeadTime());
        appendField(sb, "价格区间", vo.getPriceRange());
        appendField(sb, "供货状态", vo.getAvailability());
    }

    private void appendField(StringBuilder sb, String label, String value) {
        if (StringUtils.isNotBlank(value)) {
            sb.append(label).append("：").append(value).append("\n");
        }
    }

    private String buildTermSection(List<TranslationTerm> terms) {
        if (terms == null || terms.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("术语对照（术语命名遵循以下对照，不得使用其他译法）：\n");
        for (TranslationTerm t : terms) {
            if (StringUtils.isBlank(t.getCn()) || StringUtils.isBlank(t.getEn())) {
                continue;
            }
            sb.append(t.getCn()).append("=").append(t.getEn()).append(" | ");
        }
        return sb.toString();
    }

    private String renderParams(List<ParamItem> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        return params.stream()
                .filter(p -> p != null && StringUtils.isNotBlank(p.getName()))
                .map(p -> p.getName() + ": " + StringUtils.defaultString(p.getValue())
                        + " " + StringUtils.defaultString(p.getUnit()))
                .collect(Collectors.joining("; "));
    }

    private String buildQueryFromVo(GoodsDocVo vo) {
        if (vo == null) {
            return "";
        }
        return StringUtils.defaultString(vo.getPartNumber()) + " "
                + StringUtils.defaultString(vo.getBrand()) + " "
                + StringUtils.defaultString(vo.getCategory()) + " "
                + StringUtils.defaultString(vo.getPackageType());
    }

    // ---------- 实体/VO 映射 ----------

    private GoodsDocRecord toRecord(SaveReq req) {
        GoodsDocVo vo = req.getGoodsDoc();
        GoodsDocRecord r = new GoodsDocRecord();
        r.setId(req.getId());
        r.setPartNumber(vo.getPartNumber());
        r.setBrand(vo.getBrand());
        r.setCategory(vo.getCategory());
        r.setSubcategory(vo.getSubcategory());
        r.setSeries(vo.getSeries());
        r.setPackageType(vo.getPackageType());
        r.setMountingType(vo.getMountingType());
        r.setPinCount(vo.getPinCount());
        r.setDimensions(vo.getDimensions());
        r.setParameters(vo.getParameters() == null ? null : JSON.toJSONString(vo.getParameters()));
        r.setOperatingTemp(vo.getOperatingTemp());
        r.setStorageTemp(vo.getStorageTemp());
        r.setGrade(vo.getGrade());
        r.setRohs(vo.getRohs());
        r.setPackaging(vo.getPackaging());
        r.setMoq(vo.getMoq());
        r.setUnit(vo.getUnit());
        r.setHsCode(vo.getHsCode());
        r.setLeadTime(vo.getLeadTime());
        r.setPriceRange(vo.getPriceRange());
        r.setAvailability(vo.getAvailability());
        r.setDatasheetUrl(vo.getDatasheetUrl());
        r.setImageUrl(vo.getImageUrl());
        r.setApplications(vo.getApplications() == null ? null : JSON.toJSONString(vo.getApplications()));
        r.setDescriptionEn(vo.getDescriptionEn());
        r.setMultilingual(req.getMultilingual() == null ? null : JSON.toJSONString(req.getMultilingual()));
        r.setSeo(req.getSeo() == null ? null : JSON.toJSONString(req.getSeo()));
        r.setRawInput(vo.getRawInput());
        r.setSourceType(req.getSourceType());
        return r;
    }

    private RecordVo toRecordVo(GoodsDocRecord r) {
        RecordVo vo = new RecordVo();
        vo.setId(r.getId());
        vo.setPartNumber(r.getPartNumber());
        vo.setBrand(r.getBrand());
        vo.setCategory(r.getCategory());
        vo.setPackageType(r.getPackageType());
        vo.setCTime(r.getCTime());
        return vo;
    }

    private GoodsDocVo toGoodsDocVo(GoodsDocRecord r) {
        GoodsDocVo vo = new GoodsDocVo();
        vo.setPartNumber(r.getPartNumber());
        vo.setBrand(r.getBrand());
        vo.setCategory(r.getCategory());
        vo.setSubcategory(r.getSubcategory());
        vo.setSeries(r.getSeries());
        vo.setPackageType(r.getPackageType());
        vo.setMountingType(r.getMountingType());
        vo.setPinCount(r.getPinCount());
        vo.setDimensions(r.getDimensions());
        vo.setParameters(parseJsonArray(r.getParameters(), ParamItem.class));
        vo.setOperatingTemp(r.getOperatingTemp());
        vo.setStorageTemp(r.getStorageTemp());
        vo.setGrade(r.getGrade());
        vo.setRohs(r.getRohs());
        vo.setPackaging(r.getPackaging());
        vo.setMoq(r.getMoq());
        vo.setUnit(r.getUnit());
        vo.setHsCode(r.getHsCode());
        vo.setLeadTime(r.getLeadTime());
        vo.setPriceRange(r.getPriceRange());
        vo.setAvailability(r.getAvailability());
        vo.setDatasheetUrl(r.getDatasheetUrl());
        vo.setImageUrl(r.getImageUrl());
        vo.setApplications(parseJsonArray(r.getApplications(), String.class));
        vo.setDescriptionEn(r.getDescriptionEn());
        vo.setRawInput(r.getRawInput());
        return vo;
    }

    private <T> List<T> parseJsonArray(String json, Class<T> clazz) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyList();
        }
        List<T> list = JSON.parseArray(json, clazz);
        return list == null ? Collections.emptyList() : list;
    }

    private <V> Map<String, V> parseJsonMap(String json, Class<V> clazz) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyMap();
        }
        JSONObject obj = JSON.parseObject(json);
        if (obj == null) {
            return Collections.emptyMap();
        }
        Map<String, V> result = new HashMap<>();
        for (String key : obj.keySet()) {
            String item = obj.getString(key);
            if (StringUtils.isBlank(item)) {
                continue;
            }
            if (clazz == String.class) {
                result.put(key, (V) item);
            } else {
                result.put(key, JSON.parseObject(item, clazz));
            }
        }
        return result;
    }
}
