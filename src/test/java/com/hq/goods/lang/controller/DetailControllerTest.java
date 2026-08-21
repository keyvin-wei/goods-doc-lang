package com.hq.goods.lang.controller;

import com.hq.goods.lang.bean.CustomException;
import com.hq.goods.lang.bean.vo.ProductVo;
import com.hq.goods.lang.bean.vo.SeoVo;
import com.hq.goods.lang.service.GoodsDocService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.ui.Model;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class DetailControllerTest {

    @Mock
    private GoodsDocService goodsDocService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private Model model;

    @InjectMocks
    private DetailController controller;

    private ProductVo vo;

    @Before
    public void setup() {
        vo = new ProductVo();
        vo.setPartNumber("RP2350A");
        vo.setBrand("Raspberry Pi");
        vo.setCategory("MCU");
        vo.setDescriptionEn("English");
        vo.setMultilingual(new HashMap<>());
        vo.getMultilingual().put("en", "English");
        vo.getMultilingual().put("zh", "中文");
        vo.setSeo(new HashMap<>());
        vo.getSeo().put("en", new SeoVo());
        vo.setPrices(Collections.emptyList());
        when(request.getRequestURL())
                .thenReturn(new StringBuffer("http://localhost:8080/doc/detail/1"));
        when(request.getCookies()).thenReturn(null);
    }

    @Test
    public void testDetailReturnsViewAndModel() {
        when(goodsDocService.product(1L, request)).thenReturn(vo);
        String view = controller.detail(1L, request, response, model);
        assertEquals("detail", view);
        verify(model).addAttribute(eq("vo"), eq(vo));
        verify(model).addAttribute(eq("lang"), eq("en"));
        verify(model).addAttribute(eq("pageUrl"),
                eq("http://localhost:8080/doc/detail/1"));
        verify(model).addAttribute(eq("productJson"), anyString());
    }

    @Test
    public void testDetailNotFound() {
        when(goodsDocService.product(999L, request))
                .thenThrow(new CustomException(400, "记录不存在"));
        String view = controller.detail(999L, request, response, model);
        assertEquals("not-found", view);
        verify(response).setStatus(404);
    }
}
