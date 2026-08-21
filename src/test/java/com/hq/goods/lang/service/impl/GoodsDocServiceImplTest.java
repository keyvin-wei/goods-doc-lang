package com.hq.goods.lang.service.impl;

import com.hq.goods.lang.bean.CustomException;
import com.hq.goods.lang.bean.vo.PriceTier;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.alibaba.fastjson.JSON;
import com.hq.goods.lang.bean.dto.AiTranslateDto;
import com.hq.goods.lang.bean.dto.GenerateDescReq;
import com.hq.goods.lang.bean.dto.GenerateMultiReq;
import com.hq.goods.lang.bean.dto.ParsePartReq;
import com.hq.goods.lang.bean.dto.ParseTextReq;
import com.hq.goods.lang.bean.dto.SaveReq;
import com.hq.goods.lang.bean.entity.GoodsDocRecord;
import com.hq.goods.lang.bean.vo.AiTranslateVo;
import com.hq.goods.lang.bean.vo.GoodsDocDescVo;
import com.hq.goods.lang.bean.vo.GoodsDocMultiVo;
import com.hq.goods.lang.bean.vo.GoodsDocVo;
import com.hq.goods.lang.bean.vo.ParamItem;
import com.hq.goods.lang.bean.vo.ProductVo;
import com.hq.goods.lang.dao.GoodsDocRecordDao;
import com.hq.goods.lang.service.AiLLMService;
import com.hq.goods.lang.service.RagService;
import com.hq.goods.lang.service.TranslationService;
import com.hq.goods.lang.utils.TranslatorProviderFactory;
import com.hq.goods.lang.utils.TranslatorProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class GoodsDocServiceImplTest {

    @Mock
    private TranslatorProviderFactory translatorProviderFactory;
    @Mock
    private TranslatorProvider translatorProvider;
    @Mock
    private AiLLMService aiLLMService;
    @Mock
    private TranslationService translationService;
    @Mock
    private RagService ragService;
    @Mock
    private GoodsDocRecordDao goodsDocRecordDao;

    @InjectMocks
    private GoodsDocServiceImpl service;

    @Before
    public void setup() {
        when(translatorProviderFactory.getProvider()).thenReturn(translatorProvider);
        when(translationService.recallTerms(anyString())).thenReturn(Collections.emptyList());
        when(ragService.searchTopK(anyString(), anyInt())).thenReturn(Collections.emptyList());
    }

    private HttpServletRequest requestWithLang(String lang) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getCookies()).thenReturn(
                lang == null ? new Cookie[0] : new Cookie[]{new Cookie("lang", lang)});
        return req;
    }

    private GoodsDocRecord baseRecord() {
        GoodsDocRecord r = new GoodsDocRecord();
        r.setId(1L);
        r.setPartNumber("STM32F103C8T6");
        r.setBrand("ST");
        r.setCategory("MCU");
        r.setSubcategory("Cortex-M3");
        r.setPackageType("LQFP48");
        r.setParameters(JSON.toJSONString(Collections.emptyList()));
        r.setApplications(JSON.toJSONString(Collections.emptyList()));
        r.setMultilingual("{\"en\":\"English desc\",\"zh\":\"中文描述\",\"ja\":\"日本語\",\"ru\":\"Русский\"}");
        r.setSeo("{\"en\":{\"title\":\"T\",\"keywords\":[\"a\"],\"description\":\"D\"}}");
        r.setDescriptionEn("English desc");
        return r;
    }

    @Test
    public void testParsePart() {
        String json = "{\"partNumber\":\"STM32F103C8T6\",\"brand\":\"ST\",\"category\":\"MCU\","
                + "\"parameters\":[{\"name\":\"Flash\",\"value\":\"64KB\"}]}";
        when(translatorProvider.translate(anyString(), anyString(), anyString()))
                .thenReturn("```json\n" + json + "\n```");

        ParsePartReq req = new ParsePartReq();
        req.setPartNumber("STM32F103C8T6");
        req.setBrand("ST");
        GoodsDocVo vo = service.parsePart(req);

        assertEquals("STM32F103C8T6", vo.getPartNumber());
        assertEquals("MCU", vo.getCategory());
        assertEquals(1, vo.getParameters().size());
        assertEquals("Flash", vo.getParameters().get(0).getName());
    }

    @Test
    public void testParsePartFallbackBrand() {
        String json = "{\"partNumber\":\"TPS5430\"}";
        when(translatorProvider.translate(anyString(), anyString(), anyString())).thenReturn(json);

        ParsePartReq req = new ParsePartReq();
        req.setPartNumber("TPS5430");
        req.setBrand("TI");
        GoodsDocVo vo = service.parsePart(req);

        assertEquals("TPS5430", vo.getPartNumber());
        assertEquals("TI", vo.getBrand());
    }

    @Test
    public void testParseText() {
        String json = "{\"partNumber\":\"LM358\",\"brand\":\"TI\",\"category\":\"运放\",\"applications\":[\"放大电路\"]}";
        when(translatorProvider.translate(anyString(), anyString(), anyString())).thenReturn(json);

        ParseTextReq req = new ParseTextReq();
        req.setRawText("这是 LM358 双运放，TI 品牌，用于放大电路。");
        GoodsDocVo vo = service.parseText(req);

        assertEquals("LM358", vo.getPartNumber());
        assertEquals(1, vo.getApplications().size());
    }

    @Test
    public void testGenerateDesc() {
        when(translatorProvider.translate(anyString(), anyString(), anyString()))
                .thenReturn("This is a high-performance MCU based on real parameters.");

        GenerateDescReq req = new GenerateDescReq();
        GoodsDocVo vo = new GoodsDocVo();
        vo.setPartNumber("STM32F103C8T6");
        req.setGoodsDoc(vo);
        GoodsDocDescVo desc = service.generateDesc(req);

        assertEquals("This is a high-performance MCU based on real parameters.", desc.getDescription());
    }

    @Test
    public void testGenerateMulti() {
        when(aiLLMService.aiTranslate(any(AiTranslateDto.class))).thenAnswer(inv -> {
            AiTranslateDto dto = inv.getArgument(0);
            AiTranslateVo v = new AiTranslateVo();
            v.setTranslation("tr-" + dto.getTarget());
            return v;
        });
        String seoJson = "{\"en\":{\"title\":\"T\",\"keywords\":[\"k\"],\"description\":\"D\"},"
                + "\"zh\":{\"title\":\"中\",\"keywords\":[\"kw中\"],\"description\":\"描\"},"
                + "\"ja\":{\"title\":\"日\",\"keywords\":[\"kw日\"],\"description\":\"日D\"},"
                + "\"ru\":{\"title\":\"俄\",\"keywords\":[\"kw俄\"],\"description\":\"俄D\"}}";
        when(translatorProvider.translate(anyString(), anyString(), anyString())).thenReturn(seoJson);

        GenerateMultiReq req = new GenerateMultiReq();
        GoodsDocVo vo = new GoodsDocVo();
        vo.setPartNumber("STM32F103C8T6");
        req.setGoodsDoc(vo);
        req.setDescription("English description");
        GoodsDocMultiVo multi = service.generateMulti(req);

        assertEquals("English description", multi.getMultilingual().get("en"));
        // target: 2=中文 3=繁体 4=日 5=俄
        assertEquals("tr-2", multi.getMultilingual().get("zh"));
        assertEquals("tr-4", multi.getMultilingual().get("ja"));
        assertEquals("T", multi.getSeo().get("en").getTitle());
    }

    @Test
    public void testSaveNew() {
        doAnswer(inv -> {
            ((GoodsDocRecord) inv.getArgument(0)).setId(100L);
            return 1;
        }).when(goodsDocRecordDao).insert(any(GoodsDocRecord.class));

        SaveReq req = new SaveReq();
        GoodsDocVo vo = new GoodsDocVo();
        vo.setPartNumber("STM32F103C8T6");
        vo.setParameters(Collections.singletonList(new ParamItem()));
        req.setGoodsDoc(vo);
        req.setMultilingual(new HashMap<>());
        req.setSeo(new HashMap<>());

        Long id = service.save(req);
        assertEquals(Long.valueOf(100L), id);
        verify(goodsDocRecordDao).insert(any(GoodsDocRecord.class));
    }

    @Test
    public void testSaveUpdate() {
        SaveReq req = new SaveReq();
        req.setId(5L);
        GoodsDocVo vo = new GoodsDocVo();
        vo.setPartNumber("LM358");
        req.setGoodsDoc(vo);

        service.save(req);
        verify(goodsDocRecordDao).updateById(any(GoodsDocRecord.class));
    }

    @Test
    public void testProduct() {
        GoodsDocRecord r = new GoodsDocRecord();
        r.setId(1L);
        r.setPartNumber("STM32F103C8T6");
        r.setBrand("ST");
        r.setParameters(JSON.toJSONString(Collections.singletonList(new ParamItem())));
        r.setApplications(JSON.toJSONString(Collections.singletonList("工业控制")));
        r.setMultilingual("{\"en\":\"desc\",\"zh\":\"描述\"}");
        r.setSeo("{\"en\":{\"title\":\"T\",\"keywords\":[\"a\"],\"description\":\"D\"}}");
        r.setDescriptionEn("English desc");
        when(goodsDocRecordDao.selectById(1L)).thenReturn(r);

        ProductVo vo = service.product(1L, requestWithLang(null));
        assertEquals("STM32F103C8T6", vo.getPartNumber());
        assertEquals("desc", vo.getMultilingual().get("en"));
        assertEquals("T", vo.getSeo().get("en").getTitle());
        assertEquals("English desc", vo.getDescriptionEn());
    }

    @Test
    public void testDelete() {
        service.delete(9L);
        verify(goodsDocRecordDao).updateById(any(GoodsDocRecord.class));
    }

    @Test
    public void testProductLangLocalized() {
        when(goodsDocRecordDao.selectById(1L)).thenReturn(baseRecord());
        assertEquals("中文描述", service.product(1L, requestWithLang("zh")).getDescription());
        assertEquals("日本語", service.product(1L, requestWithLang("ja")).getDescription());
        assertEquals("Русский", service.product(1L, requestWithLang("ru")).getDescription());
    }

    @Test
    public void testProductLangFallbackAndDefault() {
        when(goodsDocRecordDao.selectById(1L)).thenReturn(baseRecord());
        // cookie 语言不在 multilingual 中 → 回退 en
        assertEquals("English desc", service.product(1L, requestWithLang("fr")).getDescription());
        // 无 cookie → 默认 en
        assertEquals("English desc", service.product(1L, requestWithLang(null)).getDescription());
    }

    @Test
    public void testProductRandomStable() {
        when(goodsDocRecordDao.selectById(1L)).thenReturn(baseRecord());
        ProductVo a = service.product(1L, requestWithLang("en"));
        ProductVo b = service.product(1L, requestWithLang("en"));
        assertEquals(a.getStock(), b.getStock());
        assertEquals(4, a.getPrices().size());
        assertEquals(a.getPrices().get(0).getUnitPrice(), b.getPrices().get(0).getUnitPrice());
        assertEquals(a.getPrices().get(3).getUnitPrice(), b.getPrices().get(3).getUnitPrice());
    }

    @Test
    public void testProductPricesDecreasing() {
        when(goodsDocRecordDao.selectById(1L)).thenReturn(baseRecord());
        List<PriceTier> prices = service.product(1L, requestWithLang("en")).getPrices();
        assertEquals(4, prices.size());
        assertEquals("1+", prices.get(0).getQtyLabel());
        assertEquals("100+", prices.get(3).getQtyLabel());
        assertTrue(prices.get(0).getUnitPrice().compareTo(prices.get(1).getUnitPrice()) > 0);
        assertTrue(prices.get(1).getUnitPrice().compareTo(prices.get(2).getUnitPrice()) > 0);
        assertTrue(prices.get(2).getUnitPrice().compareTo(prices.get(3).getUnitPrice()) > 0);
        // 总价 = 单价 × 起购量
        assertEquals(prices.get(1).getUnitPrice().multiply(BigDecimal.valueOf(5)),
                prices.get(1).getExtPrice());
    }

    @Test(expected = CustomException.class)
    public void testProductNotFound() {
        when(goodsDocRecordDao.selectById(99L)).thenReturn(null);
        service.product(99L, requestWithLang("en"));
    }
}
