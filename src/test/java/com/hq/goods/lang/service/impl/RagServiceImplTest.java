package com.hq.goods.lang.service.impl;

import com.hq.goods.lang.bean.entity.GoodsDocRecord;
import com.hq.goods.lang.bean.vo.RagHit;
import com.hq.goods.lang.dao.GoodsDocRecordDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RagServiceImplTest {

    @Mock
    private GoodsDocRecordDao goodsDocRecordDao;

    @InjectMocks
    private RagServiceImpl ragService;

    private GoodsDocRecord record() {
        GoodsDocRecord r = new GoodsDocRecord();
        r.setPartNumber("STM32F103C8T6");
        r.setBrand("ST");
        r.setCategory("MCU");
        r.setPackageType("LQFP48");
        return r;
    }

    @Test
    public void testScoreAllFields() {
        GoodsDocRecord r = record();
        // 型号4 + 品牌3 + 分类2 + 封装1 = 10
        assertEquals(10, RagServiceImpl.score(r, "STM32F103C8T6 ST MCU LQFP48"));
    }

    @Test
    public void testScoreNoMatch() {
        GoodsDocRecord r = record();
        assertEquals(0, RagServiceImpl.score(r, "qwertyuiopasdf"));
    }

    @Test
    public void testSearchTopK() {
        GoodsDocRecord r1 = record();
        GoodsDocRecord r2 = new GoodsDocRecord();
        r2.setPartNumber("LM358");
        r2.setBrand("TI");
        when(goodsDocRecordDao.selectList(any())).thenReturn(Arrays.asList(r1, r2));

        List<RagHit> hits = ragService.searchTopK("STM32F103C8T6", 3);
        assertEquals(1, hits.size());
        assertEquals("STM32F103C8T6", hits.get(0).getPartNumber());
        assertTrue(hits.get(0).getScore() > 0);
    }

    @Test
    public void testSearchTopKEmpty() {
        when(goodsDocRecordDao.selectList(any())).thenReturn(Collections.emptyList());
        assertTrue(ragService.searchTopK("anything", 3).isEmpty());
    }

    @Test
    public void testSearchTopKBlankQuery() {
        assertTrue(ragService.searchTopK("", 3).isEmpty());
    }

    @Test
    public void testScoreShortBrandNoFalsePositive() {
        // 品牌 "ST" 不应因型号 "STM32..." 前缀含 "st" 而误加分（只加型号4分）
        assertEquals(4, RagServiceImpl.score(record(), "STM32F103C8T6"));
    }

    @Test
    public void testSearchTopKNonPositiveK() {
        assertTrue(ragService.searchTopK("STM32", 0).isEmpty());
    }

    @Test
    public void testSearchTopKNullQuery() {
        assertTrue(ragService.searchTopK(null, 3).isEmpty());
    }
}
