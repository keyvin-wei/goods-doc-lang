package com.hq.goods.lang.controller;

import com.alibaba.fastjson.JSON;
import com.hq.goods.lang.bean.CustomException;
import com.hq.goods.lang.bean.vo.PriceTier;
import com.hq.goods.lang.bean.vo.ProductVo;
import com.hq.goods.lang.bean.vo.SeoVo;
import com.hq.goods.lang.service.GoodsDocService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户落地页（SSR + JSON-LD 多语言）
 */
@Controller
public class DetailController {

    /** 落地页支持语言（预留可扩展） */
    private static final List<String> SUPPORTED_LANGS = Arrays.asList("en", "zh", "ja", "ru");

    private static final Map<String, String> LANG_LABELS = new LinkedHashMap<>();

    static {
        LANG_LABELS.put("en", "English");
        LANG_LABELS.put("zh", "中文");
        LANG_LABELS.put("ja", "日本語");
        LANG_LABELS.put("ru", "Русский");
    }

    @Autowired
    private GoodsDocService goodsDocService;

    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Long id, HttpServletRequest request,
                         HttpServletResponse response, Model model) {
        try {
            ProductVo vo = goodsDocService.product(id, request);
            String lang = resolveLang(request);
            String pageUrl = request.getRequestURL().toString();
            model.addAttribute("vo", vo);
            model.addAttribute("lang", lang);
            model.addAttribute("langs", SUPPORTED_LANGS);
            model.addAttribute("langLabels", LANG_LABELS);
            model.addAttribute("seoLocal", pickSeo(vo, lang));
            model.addAttribute("pageUrl", pageUrl);
            // 转义 </ 防止 </script> 逃逸；th:utext 输出 JSON-LD
            String productJson = JSON.toJSONString(buildJsonLd(vo, pageUrl)).replace("</", "<\\/");
            model.addAttribute("productJson", productJson);
            return "detail";
        } catch (CustomException e) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "not-found";
        }
    }

    private String resolveLang(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return "en";
        }
        for (Cookie c : request.getCookies()) {
            if ("lang".equals(c.getName()) && SUPPORTED_LANGS.contains(c.getValue())) {
                return c.getValue();
            }
        }
        return "en";
    }

    private SeoVo pickSeo(ProductVo vo, String lang) {
        Map<String, SeoVo> seo = vo.getSeo();
        if (seo == null || seo.isEmpty()) {
            return null;
        }
        SeoVo s = seo.get(lang);
        return s != null ? s : seo.getOrDefault("en", seo.values().iterator().next());
    }

    private Map<String, Object> buildJsonLd(ProductVo vo, String pageUrl) {
        String category = StringUtils.isBlank(vo.getSubcategory())
                ? vo.getCategory() : vo.getCategory() + " " + vo.getSubcategory();
        Map<String, String> multi = vo.getMultilingual();
        List<Map<String, Object>> graph = new ArrayList<>();
        for (String lang : SUPPORTED_LANGS) {
            String desc = multi != null ? multi.get(lang) : null;
            if (StringUtils.isBlank(desc)) {
                desc = vo.getDescriptionEn();
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("@type", "Product");
            node.put("@id", pageUrl);
            node.put("name", vo.getPartNumber());
            node.put("inLanguage", lang);
            node.put("description", desc);
            Map<String, Object> brand = new LinkedHashMap<>();
            brand.put("@type", "Brand");
            brand.put("name", vo.getBrand());
            node.put("brand", brand);
            node.put("category", category);
            node.put("sku", vo.getPartNumber());
            if (StringUtils.isNotBlank(vo.getImageUrl())) {
                node.put("image", vo.getImageUrl());
            }
            Map<String, Object> offers = new LinkedHashMap<>();
            offers.put("@type", "Offer");
            BigDecimal price = firstUnitPrice(vo);
            if (price != null) {
                offers.put("price", price.toPlainString());
            }
            offers.put("priceCurrency", "USD");
            offers.put("availability", "https://schema.org/InStock");
            offers.put("url", pageUrl);
            node.put("offers", offers);
            graph.add(node);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("@context", "https://schema.org");
        root.put("@graph", graph);
        return root;
    }

    private BigDecimal firstUnitPrice(ProductVo vo) {
        List<PriceTier> prices = vo.getPrices();
        if (prices != null && !prices.isEmpty() && prices.get(0).getUnitPrice() != null) {
            return prices.get(0).getUnitPrice();
        }
        return null;
    }
}
