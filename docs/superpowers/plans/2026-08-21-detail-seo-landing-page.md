# 落地页 /goods/detail/{id} Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增客户可见的 SEO 落地页 `GET /goods/detail/{id}`，服务端渲染（Thymeleaf）后台生成的元器件资料，并输出 Schema.org `Product` JSON-LD 多语言（en/zh/ja/ru）结构化数据。

**Architecture:** 后台现有 `hq_goods_doc_record` → `GoodsDocService` 提供 `product(id, request)`（从 cookie `lang` 决定语言、按 id 种子生成稳定随机库存/阶梯价）→ 新增 `DetailController`（`@Controller`）渲染 `templates/detail.html`（fastjson 序列化 JSON-LD 进 model）。页面布局对照 `用户端查看页面-seo落地页.jpg`（HQ mall 元器件商城风格）。

**Tech Stack:** Spring Boot 2.1.7 · Thymeleaf（版本随 starter-parent）· Java 11 · MyBatis-Plus 3.5.17 · fastjson 2.0.34（`com.alibaba.fastjson`）· JUnit 4.12 · Mockito 2.23

## Global Constraints

- Java 11（`maven.compiler.source/target=11`）；**构建命令必须先设** `export JAVA_HOME="D:/Program Files/Java/jdk-11.0.13"`
- Spring Boot 2.1.7.RELEASE 父 POM；Thymeleaf starter 不写版本号
- fastjson 用 `com.alibaba.fastjson.JSON`（v2 兼容包名，项目统一）
- 落地页语言固定 `en / zh / ja / ru`，常量 `SUPPORTED_LANGS`（预留可扩展；后台存储的 `zhTw` 不进页面）
- 当前语言存 cookie `lang`（`path=/`，默认 `en`）；服务方法签名 **`ProductVo product(Long id, HttpServletRequest request)`**，lang 从 request cookie 解析
- **不得提交 `src/main/resources/application.yml`**（含真实 API 密钥，用户手动提交）
- 测试风格：JUnit4 + `MockitoJUnitRunner`，沿用 `src/test/java/com/hq/goods/lang/service/impl/GoodsDocServiceImplTest.java`
- `context-path=/doc`；SSR 路由 `/detail/{id}`，REST 保持 `/api/doc/product/{id}`（两者同调 `service.product(id, request)`）

## File Structure

| 文件 | 职责 |
|---|---|
| `pom.xml` | +`spring-boot-starter-thymeleaf` |
| `src/main/java/com/hq/goods/lang/bean/vo/PriceTier.java` | 新增：阶梯价档位 VO |
| `src/main/java/com/hq/goods/lang/bean/vo/ProductVo.java` | 修改：+`description`/`stock`/`prices` |
| `src/main/java/com/hq/goods/lang/service/GoodsDocService.java` | 修改：`product(Long id, HttpServletRequest request)` |
| `src/main/java/com/hq/goods/lang/service/impl/GoodsDocServiceImpl.java` | 修改：cookie 语言解析 + 本地化 + 随机库存/价格 |
| `src/main/java/com/hq/goods/lang/controller/GoodsDocController.java` | 修改：`/api/doc/product/{id}` 注入并传 request |
| `src/main/java/com/hq/goods/lang/controller/DetailController.java` | 新增：SSR 控制器 |
| `src/main/resources/templates/detail.html` | 新增：落地页模板 |
| `src/main/resources/templates/not-found.html` | 新增：404 页 |
| `src/test/java/com/hq/goods/lang/service/impl/GoodsDocServiceImplTest.java` | 修改：product 测试迁移 + 新增用例 |
| `src/test/java/com/hq/goods/lang/controller/DetailControllerTest.java` | 新增：控制器单元测试 |
| `src/test/java/com/hq/goods/lang/controller/DetailTemplateTest.java` | 新增：模板标记冒烟测试 |

---

### Task 1: 依赖 + 数据模型扩展

**Files:**
- Modify: `pom.xml`（在 spring-boot-starter-test 之后插入）
- Create: `src/main/java/com/hq/goods/lang/bean/vo/PriceTier.java`
- Modify: `src/main/java/com/hq/goods/lang/bean/vo/ProductVo.java`

**Interfaces:**
- Produces: `PriceTier{qtyLabel:String, unitPrice:BigDecimal, extPrice:BigDecimal}`；`ProductVo.getDescription()/getStock()/getPrices()`

- [ ] **Step 1: 加 Thymeleaf 依赖**

在 `pom.xml` 第 45 行（`spring-boot-starter-test` 的 `</dependency>`）之后插入：

```xml

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
        </dependency>
```

- [ ] **Step 2: 新建 PriceTier VO**

`src/main/java/com/hq/goods/lang/bean/vo/PriceTier.java`：

```java
package com.hq.goods.lang.bean.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 阶梯价格档位：数量标签 / 单价 / 总价
 */
@Data
public class PriceTier {
    /** 如 1+ / 5+ / 10+ / 100+ */
    private String qtyLabel;
    private BigDecimal unitPrice;
    private BigDecimal extPrice;
}
```

- [ ] **Step 3: ProductVo 增加字段**

`src/main/java/com/hq/goods/lang/bean/vo/ProductVo.java` 在 `datasheetUrl` 字段后追加：

```java
    /** 记录 id（页面「HQ Part #」展示） */
    private Long id;
    /** 本地化描述（按当前语言，默认英文） */
    private String description;
    /** 库存（id 种子随机，同型号稳定） */
    private Integer stock;
    /** 阶梯价格（买越多越便宜） */
    private List<PriceTier> prices;
```

- [ ] **Step 4: 编译验证**

Run: `export JAVA_HOME="D:/Program Files/Java/jdk-11.0.13" && mvn -q compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/java/com/hq/goods/lang/bean/vo/PriceTier.java src/main/java/com/hq/goods/lang/bean/vo/ProductVo.java
git commit -m "feat: 落地页依赖与数据模型（Thymeleaf + ProductVo 本地化/库存/阶梯价）"
```

---

### Task 2: Service 多语言 + 随机库存价格（签名迁移）

**Files:**
- Modify: `src/main/java/com/hq/goods/lang/service/GoodsDocService.java`
- Modify: `src/main/java/com/hq/goods/lang/service/impl/GoodsDocServiceImpl.java`
- Modify: `src/main/java/com/hq/goods/lang/controller/GoodsDocController.java`
- Test: `src/test/java/com/hq/goods/lang/service/impl/GoodsDocServiceImplTest.java`

**Interfaces:**
- Consumes: Task 1 的 `ProductVo`/`PriceTier`
- Produces: `GoodsDocService.product(Long id, HttpServletRequest request)`（从 cookie `lang` 解析语言，默认 en；本地化 `description`；`stock`/`prices` 按 `new Random(id)` 稳定随机、阶梯递减）

- [ ] **Step 1: 改接口签名（写失败测试的前提）**

`GoodsDocService.java`：加 import `javax.servlet.http.HttpServletRequest`；把

```java
    /** 客户页面公开数据 */
    ProductVo product(Long id);
```

改为

```java
    /** 客户页面公开数据（多语言按 cookie lang，默认 en） */
    ProductVo product(Long id, HttpServletRequest request);
```

- [ ] **Step 2: 迁移并新增失败测试**

`src/test/java/com/hq/goods/lang/service/impl/GoodsDocServiceImplTest.java`：

1) 顶部新增 import：
```java
import com.hq.goods.lang.bean.CustomException;
import com.hq.goods.lang.bean.vo.PriceTier;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
```

2) 新增私有辅助方法（放在 `@Before setup()` 之后）：
```java
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
```

3) 把现有 `testProduct()` 中 `ProductVo vo = service.product(1L);` 改为 `ProductVo vo = service.product(1L, requestWithLang(null));`

4) 追加新测试：
```java
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
```

- [ ] **Step 3: 运行测试，确认失败**

Run: `export JAVA_HOME="D:/Program Files/Java/jdk-11.0.13" && mvn -q -Dtest=GoodsDocServiceImplTest test`
Expected: `BUILD FAILURE`（编译错误：`service.product(1L)` 无该方法 / 实现未改）

- [ ] **Step 4: 实现 service**

`GoodsDocServiceImpl.java`：

1) 新增 import：
```java
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Random;
```

2) 类内新增常量（放在 `private static final String MODEL = "gpt-5.5";` 附近）：
```java
    /** 落地页支持语言（预留可扩展） */
    private static final List<String> SUPPORTED_LANGS = Arrays.asList("en", "zh", "ja", "ru");
    private static final String DEFAULT_LANG = "en";
    /** 阶梯档位：起购量 → 单价递减系数 */
    private static final int[] TIER_QTY = {1, 5, 10, 100};
    private static final double[] TIER_FACTOR = {1.00, 0.85, 0.80, 0.62};
```

3) 把现有 `product(Long id)` 方法整体替换为：
```java
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
```

4) `GoodsDocController.java`：给 `/api/doc/product/{id}` 方法加 `HttpServletRequest` 参数并传参（保持 REST 接口与 SSR 数据同源）：
```java
    /** ⑨ 客户页面公开数据（官网 SSR；多语言按 cookie lang） */
    @GetMapping("/product/{id}")
    public String product(@PathVariable Long id, HttpServletRequest request) {
        return ResultBody.success(goodsDocService.product(id, request));
    }
```
顶部加 import `javax.servlet.http.HttpServletRequest;`

- [ ] **Step 5: 运行测试，确认通过**

Run: `export JAVA_HOME="D:/Program Files/Java/jdk-11.0.13" && mvn -q -Dtest=GoodsDocServiceImplTest test`
Expected: `BUILD SUCCESS`（原 9 例 + 新 5 例全部通过）

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/hq/goods/lang/service/GoodsDocService.java src/main/java/com/hq/goods/lang/service/impl/GoodsDocServiceImpl.java src/main/java/com/hq/goods/lang/controller/GoodsDocController.java src/test/java/com/hq/goods/lang/service/impl/GoodsDocServiceImplTest.java
git commit -m "feat: product 接口按 cookie 多语言 + id 种子随机库存/阶梯价"
```

---

### Task 3: DetailController + 404 页（TDD）

**Files:**
- Create: `src/main/java/com/hq/goods/lang/controller/DetailController.java`
- Create: `src/main/resources/templates/not-found.html`
- Test: `src/test/java/com/hq/goods/lang/controller/DetailControllerTest.java`

**Interfaces:**
- Consumes: Task 2 的 `GoodsDocService.product(Long id, HttpServletRequest request)`
- Produces: 视图 `"detail"` / `"not-found"`；model 属性 `vo / lang / langs / langLabels / seoLocal / pageUrl / productJson`

- [ ] **Step 1: 写失败测试**

`src/test/java/com/hq/goods/lang/controller/DetailControllerTest.java`：

```java
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
                .thenReturn(new StringBuffer("http://localhost:8080/goods/detail/1"));
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
                eq("http://localhost:8080/goods/detail/1"));
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
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `export JAVA_HOME="D:/Program Files/Java/jdk-11.0.13" && mvn -q -Dtest=DetailControllerTest test`
Expected: `BUILD FAILURE`（`DetailController` 不存在）

- [ ] **Step 3: 实现 DetailController**

`src/main/java/com/hq/goods/lang/controller/DetailController.java`：

```java
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
```

- [ ] **Step 4: 建 404 页**

`src/main/resources/templates/not-found.html`：

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>404 - Not Found</title>
    <style>
        body { font-family: Arial, sans-serif; background: #fff; color: #333;
               text-align: center; padding: 80px 20px; }
        h1 { color: #d3232a; font-size: 48px; margin-bottom: 12px; }
        p { color: #888; }
    </style>
</head>
<body>
<h1>404</h1>
<p>The product you are looking for does not exist or has been removed.</p>
<p><a th:href="@{/}">&#8592; Back to Home</a></p>
</body>
</html>
```

- [ ] **Step 5: 运行测试，确认通过**

Run: `export JAVA_HOME="D:/Program Files/Java/jdk-11.0.13" && mvn -q -Dtest=DetailControllerTest test`
Expected: `BUILD SUCCESS`（2 例通过）

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/hq/goods/lang/controller/DetailController.java src/main/resources/templates/not-found.html src/test/java/com/hq/goods/lang/controller/DetailControllerTest.java
git commit -m "feat: DetailController SSR + 404 页（JSON-LD 多语言 productJson）"
```

---

### Task 4: detail.html 落地页模板

**Files:**
- Create: `src/main/resources/templates/detail.html`
- Test: `src/test/java/com/hq/goods/lang/controller/DetailTemplateTest.java`

**Interfaces:**
- Consumes: Task 3 的 model 属性 `vo / lang / langs / langLabels / seoLocal / pageUrl / productJson`
- Produces: 完整 SSR 页面

- [ ] **Step 1: 写失败测试（模板标记冒烟）**

`src/test/java/com/hq/goods/lang/controller/DetailTemplateTest.java`：

```java
package com.hq.goods.lang.controller;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

/**
 * 模板冒烟测试：验证 detail.html / not-found.html 存在且含关键标记。
 * 实际渲染效果由人工在浏览器验证。
 */
public class DetailTemplateTest {

    @Test
    public void testDetailTemplateContainsRequiredMarkers() throws Exception {
        File f = new File("src/main/resources/templates/detail.html");
        assertTrue("detail.html 不存在", f.exists());
        String html = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        assertTrue("缺少 JSON-LD script", html.contains("application/ld+json"));
        assertTrue("缺少语言切换", html.contains("langSelect"));
        assertTrue("缺少 canonical", html.contains("rel=\"canonical\""));
        assertTrue("缺少 hreflang x-default", html.contains("x-default"));
        assertTrue("缺少 demo1 图", html.contains("demo1.png"));
        assertTrue("缺少 demo2 图", html.contains("demo2.png"));
        assertTrue("缺少 demo3 图", html.contains("demo3.png"));
        assertTrue("缺少阶梯价渲染", html.contains("vo.prices"));
        assertTrue("缺少参数表渲染", html.contains("vo.parameters"));
    }

    @Test
    public void testNotFoundTemplateExists() {
        assertTrue("not-found.html 不存在",
                new File("src/main/resources/templates/not-found.html").exists());
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `export JAVA_HOME="D:/Program Files/Java/jdk-11.0.13" && mvn -q -Dtest=DetailTemplateTest test`
Expected: `BUILD FAILURE`（`detail.html` 不存在）

- [ ] **Step 3: 写 detail.html**

`src/main/resources/templates/detail.html`（完整文件）：

```html
<!DOCTYPE html>
<html lang="en" th:lang="${lang}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title th:text="${seoLocal != null and seoLocal.title != null ? seoLocal.title : vo.partNumber}">Product</title>
    <meta name="description" th:if="${seoLocal != null}" th:content="${seoLocal.description}">
    <meta name="keywords" th:if="${seoLocal != null}" th:content="${#strings.listJoin(seoLocal.keywords, ',')}">
    <link rel="canonical" th:href="${pageUrl}">
    <link rel="alternate" th:each="l : ${langs}" th:href="${pageUrl}" th:hreflang="${l}">
    <link rel="alternate" hreflang="x-default" th:href="${pageUrl}">
    <script type="application/ld+json" th:utext="${productJson}"></script>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: Arial, "Helvetica Neue", sans-serif; background: #fff; color: #333; }
        a { color: #d3232a; text-decoration: none; }
        /* ---- 顶栏 ---- */
        .top-bar { background: #f5f5f5; font-size: 12px; padding: 4px 24px; display: flex; justify-content: space-between; }
        .header { max-width: 1200px; margin: 0 auto; padding: 12px 24px; display: flex; align-items: center; gap: 20px; }
        .logo { font-size: 22px; font-weight: bold; color: #d3232a; line-height: 1.1; }
        .logo small { display: block; font-size: 10px; color: #999; font-weight: normal; }
        .search { flex: 1; max-width: 520px; position: relative; }
        .search input { width: 100%; padding: 8px 12px; border: 2px solid #d3232a; border-radius: 3px; }
        .search .tags { margin-top: 4px; font-size: 12px; color: #888; }
        .search .tags a { color: #888; margin-right: 10px; }
        .nav { display: flex; gap: 16px; font-size: 14px; }
        .nav a { color: #333; }
        .util { display: flex; align-items: center; gap: 14px; font-size: 13px; }
        .cart { color: #333; }
        /* ---- 面包屑 ---- */
        .breadcrumb { max-width: 1200px; margin: 8px auto; padding: 0 24px; font-size: 13px; color: #888; }
        .breadcrumb a { color: #888; }
        .breadcrumb span { margin: 0 6px; }
        /* ---- 主区 ---- */
        .main { max-width: 1200px; margin: 20px auto; padding: 0 24px; display: flex; gap: 24px; }
        .gallery { width: 300px; }
        .gallery img.main-img { width: 300px; height: 300px; object-fit: contain; border: 1px solid #eee; }
        .gallery .thumbs { display: flex; gap: 8px; margin-top: 8px; }
        .gallery .thumbs img { width: 56px; height: 56px; object-fit: contain; border: 1px solid #ddd; cursor: pointer; }
        .gallery .note { font-size: 12px; color: #999; margin-top: 8px; }
        .detail { flex: 1; }
        .detail h1 { font-size: 22px; margin-bottom: 12px; }
        .fields { list-style: none; font-size: 14px; }
        .fields li { display: flex; padding: 5px 0; border-bottom: 1px dashed #eee; }
        .fields .k { width: 150px; color: #888; }
        .fields .v { flex: 1; }
        .buy-box { width: 340px; border: 1px solid #eee; padding: 16px; }
        .stock { color: #2e7d32; font-weight: bold; margin-bottom: 10px; }
        .buy-row { display: flex; align-items: center; gap: 10px; margin: 8px 0; font-size: 14px; }
        .buy-row input { width: 70px; padding: 6px; }
        .price { font-size: 20px; color: #d3232a; font-weight: bold; margin: 10px 0; }
        .btn { display: inline-block; width: 48%; padding: 10px 0; text-align: center; border: none;
               border-radius: 3px; font-size: 15px; cursor: pointer; }
        .btn.red { background: #d3232a; color: #fff; }
        .btn.orange { background: #ff9800; color: #fff; }
        .min-mult { font-size: 12px; color: #999; margin: 6px 0; }
        /* ---- 表格区 ---- */
        .section { max-width: 1200px; margin: 24px auto; padding: 0 24px; }
        .card { border: 1px solid #eee; margin-top: 12px; }
        .card h3 { font-size: 16px; padding: 12px 16px; background: #fafafa; border-bottom: 1px solid #eee; }
        table { width: 100%; border-collapse: collapse; font-size: 14px; }
        th, td { text-align: left; padding: 10px 16px; border-bottom: 1px solid #f0f0f0; }
        th { background: #fff; color: #888; font-weight: normal; }
        .desc { line-height: 1.7; font-size: 14px; padding: 16px; }
        /* ---- 页脚 ---- */
        .footer { background: #fafafa; border-top: 1px solid #eee; padding: 24px; margin-top: 40px;
                  text-align: center; font-size: 13px; color: #888; }
    </style>
</head>
<body>

<!-- 顶栏 -->
<div class="top-bar">
    <span>support@mall.com</span>
    <div class="util">
        <select id="langSelect" onchange="switchLang(this.value)" title="Language">
            <option th:each="l : ${langs}" th:value="${l}" th:text="${langLabels[l]}" th:selected="${l == lang}"></option>
        </select>
        <span class="cart">&#128722; <span>20</span></span>
    </div>
</div>

<div class="header">
    <div class="logo">HQ mall<small>www.mall.com</small></div>
    <div class="search">
        <input type="text" placeholder="Part#/Keyword">
        <div class="tags">
            <span>Hot: </span><a th:href="@{/}">RP2350A</a><a th:href="@{/}">STM32F103RCT6</a>
            <a th:href="@{/}">RP2040</a><a th:href="@{/}">AR02DTD2001</a>
        </div>
    </div>
    <nav class="nav">
        <a th:href="@{/}">Products</a><a th:href="@{/}">BOM Tool</a><a th:href="@{/}">Request Quote</a>
        <a th:href="@{/}">PCB Service</a><a th:href="@{/}">About Us</a><a th:href="@{/}">Contact Us</a>
        <a th:href="@{/}">Blog</a>
    </nav>
</div>

<!-- 面包屑 -->
<div class="breadcrumb">
    <a th:href="@{/}">Home</a><span>&gt;</span>
    <span th:text="${vo.category}">Category</span>
    <span th:if="${vo.subcategory != null and !vo.subcategory.isEmpty()}">
        <span>&gt;</span><span th:text="${vo.subcategory}">Subcategory</span>
    </span>
    <span>&gt;</span><span th:text="${vo.partNumber}">PartNumber</span>
</div>

<!-- 主区 -->
<div class="main">
    <div class="gallery">
        <img id="mainImage" class="main-img" th:src="@{/images/default/demo1.png}" alt="Product image">
        <div class="thumbs">
            <img th:src="@{/images/default/demo1.png}" onclick="setImage(this.src)" alt="view 1">
            <img th:src="@{/images/default/demo2.png}" onclick="setImage(this.src)" alt="view 2">
            <img th:src="@{/images/default/demo3.png}" onclick="setImage(this.src)" alt="view 3">
        </div>
        <div class="note">Images are for reference only</div>
    </div>

    <div class="detail">
        <h1 th:text="${vo.partNumber}">PartNumber</h1>
        <ul class="fields">
            <li><span class="k">Mfr Part #</span><span class="v" th:text="${vo.partNumber}"></span></li>
            <li><span class="k">Manufacturer</span><span class="v" th:text="${vo.brand}"></span></li>
            <li><span class="k">HQ Part #</span><span class="v" th:text="${vo.id}">-</span></li>
            <li><span class="k">Package</span><span class="v" th:text="${vo.packageType}">-</span></li>
            <li><span class="k">Lead Time</span><span class="v">Ship immediately</span></li>
            <li th:if="${vo.description != null and !vo.description.isEmpty()}">
                <span class="k">Description</span><span class="v" th:text="${vo.description}"></span>
            </li>
            <li><span class="k">Customer #</span>
                <input type="text" maxlength="30" placeholder="Up to 30 characters" style="flex:1;padding:6px;">
            </li>
            <li th:if="${vo.datasheetUrl != null and !vo.datasheetUrl.isEmpty()}">
                <span class="k">Datasheet</span><span class="v"><a th:href="${vo.datasheetUrl}" target="_blank">Download Datasheet</a></span>
            </li>
        </ul>
    </div>

    <div class="buy-box">
        <div class="stock" th:text="${vo.stock + ' In Stock'}">In Stock</div>
        <div class="buy-row"><span>Qty</span><input type="number" value="1" min="1"></div>
        <div class="min-mult">Minimum: 1 &nbsp; Multiple: 1</div>
        <div class="price">
            <span th:if="${vo.prices != null and !vo.prices.isEmpty()}">
                Unit Price: $<span th:text="${vo.prices[0].unitPrice}"></span>
            </span>
        </div>
        <div style="margin-top:10px;">
            <button class="btn red" type="button">Add to Cart</button>
            <button class="btn orange" type="button">Buy Now</button>
        </div>
    </div>
</div>

<!-- 属性表 -->
<div class="section">
    <div class="card">
        <h3>Product Attributes</h3>
        <table>
            <tr><th>Type</th><th>Description</th></tr>
            <tr><td>Product Type</td>
                <td><span th:text="${vo.category}"></span><span th:if="${vo.subcategory != null and !vo.subcategory.isEmpty()}" th:text="' ' + ${vo.subcategory}"></span></td>
            </tr>
            <tr><td>Package / Case</td><td th:text="${vo.packageType}">-</td></tr>
            <tr th:each="prm : ${vo.parameters}">
                <td th:text="${prm.name}"></td>
                <td><span th:text="${prm.value}"></span><span th:if="${prm.unit != null and !prm.unit.isEmpty()}" th:text="' ' + ${prm.unit}"></span></td>
            </tr>
        </table>
    </div>
</div>

<!-- 阶梯价格 -->
<div class="section">
    <div class="card">
        <h3>Pricing (USD)</h3>
        <table>
            <tr><th>Qty</th><th>Unit Price</th><th>Ext Price</th></tr>
            <tr th:each="p : ${vo.prices}">
                <td th:text="${p.qtyLabel}"></td>
                <td th:text="'$ ' + ${p.unitPrice}"></td>
                <td th:text="'$ ' + ${p.extPrice}"></td>
            </tr>
        </table>
    </div>
</div>

<!-- 描述 -->
<div class="section">
    <div class="card">
        <h3>Description</h3>
        <div class="desc" th:text="${vo.description}">Description</div>
    </div>
</div>

<!-- 页脚 -->
<div class="footer">
    HQ mall &copy; 2026 &nbsp;|&nbsp; support@mall.com &nbsp;|&nbsp; All prices in USD, subject to change
</div>

<script>
    function switchLang(code) {
        document.cookie = "lang=" + code + "; path=/; max-age=31536000";
        location.reload();
    }
    function setImage(src) {
        document.getElementById('mainImage').src = src;
    }
</script>

</body>
</html>
```

> 说明：`vo.id` 由 Task 1 新增的 `ProductVo.id` 提供（记录 id，即「HQ Part #」展示值），Task 2 的 `buildProduct` 已 `vo.setId(r.getId())`，模板表达式解析正常。

- [ ] **Step 4: 运行测试，确认通过**

Run: `export JAVA_HOME="D:/Program Files/Java/jdk-11.0.13" && mvn -q -Dtest=DetailTemplateTest test`
Expected: `BUILD SUCCESS`（2 例通过）

- [ ] **Step 5: 编译 + 全量测试**

Run: `export JAVA_HOME="D:/Program Files/Java/jdk-11.0.13" && mvn -q test`
Expected: `BUILD SUCCESS`（全部既有 + 新增测试通过）

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/detail.html src/test/java/com/hq/goods/lang/controller/DetailTemplateTest.java
git commit -m "feat: 落地页 detail.html（SSR + 语言切换 + JSON-LD 多语言）"
```

---

### Task 5: 端到端验证

**Files:** 无新文件；验证与收尾

- [ ] **Step 1: 启动应用做 SSR 冒烟**

Run: `export JAVA_HOME="D:/Program Files/Java/jdk-11.0.13" && mvn -q spring-boot:run`（后台），随后：
```bash
curl -s http://localhost:8080/goods/detail/1 -o /tmp/detail.html && grep -c "application/ld+json" /tmp/detail.html
curl -s -H "Cookie: lang=zh" http://localhost:8080/goods/detail/1 | grep -o "中文描述\|English desc" | head -1
curl -s http://localhost:8080/goods/detail/999 -o /dev/null -w "%{http_code}\n"
```
Expected：第 1 条输出 `1`（含 JSON-LD）；第 2 条输出中文描述（cookie 生效）；第 3 条 `404`。
> 若本地无 id=1 记录，换成库里真实存在的 id；`999` 用不存在 id。

- [ ] **Step 2: 浏览器人工清单（用户执行）**
1. `http://localhost:8080/goods/detail/{id}`：顶栏 / 面包屑 / 主区左图右信息 / 右栏库存价格按钮 / 属性表 / 价格阶梯表 / 描述 / 页脚 均正常
2. 顶栏语言下拉切 中文/日本語/Русский → 整页语言切换（cookie 生效，刷新一致）
3. 查看页面源码：JSON-LD 含 en/zh/ja/ru 四个 `@graph` 节点，`<script type="application/ld+json">` 未转义破坏
4. 不存在 id → 404 页
5. 用 Google Rich Results / schema.org 校验器验证 JSON-LD 无报错

- [ ] **Step 3: 确认 application.yml 未提交**

Run: `git status --short`
Expected：`src/main/resources/application.yml` 仍为 ` M`（未提交），由用户手动提交

---

## Self-Review

- **Spec 覆盖**：§4 后端改动 → Task 1/2/3；§5 布局 → Task 4；§6 JSON-LD → Task 3（buildJsonLd）+ Task 4（模板）；§7 head SEO → Task 4；§8 语言切换 → Task 3（resolveLang）+ Task 4（langSelect/switchLang）；§9 404 → Task 3；§10 测试 → Task 2/3/4；§3 多语言机制（en/zh/ja/ru、cookie、默认 en、回退）→ Task 2。✅
- **占位符**：无 TBD/TODO；Step 3 模板对 `vo.id` 的处置已显式给出修正指令。✅
- **类型一致**：`SUPPORTED_LANGS` 在 service 与 controller 各定义一份（值相同，en/zh/ja/ru）；`product(Long id, HttpServletRequest request)` 签名在接口/实现/controller/测试全部一致；`PriceTier{qtyLabel,unitPrice,extPrice}` 一致。✅
