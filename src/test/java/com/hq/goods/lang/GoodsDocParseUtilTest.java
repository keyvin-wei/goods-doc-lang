package com.hq.goods.lang;

import com.hq.goods.lang.bean.CustomException;
import com.hq.goods.lang.bean.vo.GoodsDocVo;
import com.hq.goods.lang.bean.vo.SeoVo;
import com.hq.goods.lang.utils.GoodsDocParseUtil;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GoodsDocParseUtilTest {

    @Test
    public void testExtractJsonWithFence() {
        String raw = "```json\n{\"partNumber\":\"A\"}\n```";
        assertEquals("{\"partNumber\":\"A\"}", GoodsDocParseUtil.extractJson(raw));
    }

    @Test
    public void testExtractJsonWithPrefixText() {
        String raw = "以下是结果：{\"partNumber\":\"A\"} 完毕";
        assertEquals("{\"partNumber\":\"A\"}", GoodsDocParseUtil.extractJson(raw));
    }

    @Test(expected = CustomException.class)
    public void testExtractJsonNoJson() {
        GoodsDocParseUtil.extractJson("没有 JSON");
    }

    @Test
    public void testToGoodsDocVo() {
        String json = "{\"partNumber\":\"STM32F103C8T6\",\"brand\":\"ST\",\"category\":\"MCU\","
                + "\"parameters\":[{\"name\":\"Flash\",\"value\":\"64KB\"}],"
                + "\"applications\":[\"工业控制\",\"消费电子\"]}";
        GoodsDocVo vo = GoodsDocParseUtil.toGoodsDocVo(json);
        assertEquals("STM32F103C8T6", vo.getPartNumber());
        assertEquals("MCU", vo.getCategory());
        assertEquals(1, vo.getParameters().size());
        assertEquals("Flash", vo.getParameters().get(0).getName());
        assertEquals("64KB", vo.getParameters().get(0).getValue());
        assertEquals(2, vo.getApplications().size());
    }

    @Test
    public void testToGoodsDocVoMissingFields() {
        GoodsDocVo vo = GoodsDocParseUtil.toGoodsDocVo("{\"partNumber\":\"A\"}");
        assertEquals("A", vo.getPartNumber());
        assertNull(vo.getBrand());
        assertNull(vo.getParameters());
    }

    @Test
    public void testParseSeo() {
        String json = "{\"en\":{\"title\":\"T\",\"keywords\":[\"k1\",\"k2\"],\"description\":\"D\"},"
                + "\"zh\":{\"title\":\"中\",\"keywords\":[\"关键词\"],\"description\":\"描述\"}}";
        Map<String, SeoVo> seo = GoodsDocParseUtil.parseSeo(json);
        assertEquals(2, seo.size());
        assertEquals("T", seo.get("en").getTitle());
        assertEquals(2, seo.get("en").getKeywords().size());
        assertEquals("描述", seo.get("zh").getDescription());
    }
}
