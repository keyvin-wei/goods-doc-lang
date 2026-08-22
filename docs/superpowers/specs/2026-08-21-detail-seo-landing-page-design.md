# 落地页 /goods/detail/{id} 设计

- 日期：2026-08-21
- 模块：商品文档（goods-doc-lang）
- 关联设计：`2026-08-16-goods-doc-standardization-design.md`（其中第④项 JSON-LD「官网 SSR 自拼」由本项目落地页承接）

## 1. 背景与目标

后台每天把外贸元器件资料（型号解析 → 描述生成 → 多语言 + SEO）沉淀到 `hq_goods_doc_record`。本文档设计一个**前台用户可见的 SEO 落地页**：

- URL：`http://localhost:8080/goods/detail/{id}`（`context-path=/goods`，模板视图 `/detail/{id}`）
- 页面布局参考：`docs/superpowers/diagrams/用户端查看页面-seo落地页.jpg`（HQ mall 风格元器件商城产品详情页）
- 把后台生成的型号资料渲染成 HTML，并输出 **Schema.org `Product` JSON-LD 多语言结构化数据**，为搜索引擎及未来 AI 搜索提供标准产品信息，提高 AI 推荐曝光
- 电商区块（库存/价格/购物车）无真实数据：以 **id 为随机种子生成稳定随机值**，仅展示、不做功能

## 2. 技术方案

- **服务端渲染 Thymeleaf**：`pom.xml` 新增 `spring-boot-starter-thymeleaf`；模板 `src/main/resources/templates/detail.html`
- 新增 **SSR 控制器**（`@Controller`），直接调用 `goodsDocService.product(id, request)`（与现有 `/api/doc/product/{id}` 同一数据源，避免二次 HTTP 调用）
- 静态图片：`src/main/resources/static/images/default/demo1.png / demo2.png / demo3.png`（300×300，用户已放置；静态 URL `/images/default/demo1.png`，页面**写死**引用，不按型号动态化）
- 前端零构建：纯 HTML + 少量内联 JS/CSS

## 3. 多语言机制

**支持语言**（常量 `SUPPORTED_LANGS = ["en","zh","ja","ru"]`，预留可扩展；后台存储的 `zhTw` 保留在库中但页面不使用）：

- 当前语言存 cookie `lang`（`path=/`，有效期 1 年），**默认 `en`**
- 服务方法签名：`ProductVo product(Long id, HttpServletRequest request)`，内部从 request 读 cookie 取 lang，无 cookie 回退 `en`
- **本地化规则**（字段不变，仅取值随语言变化）：
  - `description` = `multilingual[lang]`，无该语言则回退 `multilingual["en"]`
  - head SEO = `seo[lang]`，无则回退 `seo["en"]`
  - `partNumber / brand / category / subcategory / series / packageType / parameters / applications` 保持后台存储的源语言（当前后台只对描述与 SEO 生成多语言）

## 4. 后端改动

1. `pom.xml`：加 `spring-boot-starter-thymeleaf`
2. 新增 VO `PriceTier { qtyLabel, unitPrice, extPrice }`（金额 `BigDecimal`）
3. `ProductVo` 新增字段：
   - `description`（String，本地化后的描述；原 `descriptionEn` 保留）
   - `stock`（int，id 种子随机 1~999）
   - `prices`（List\<PriceTier\>，4 档阶梯价，递减）
4. `GoodsDocService`：新增 `ProductVo product(Long id, HttpServletRequest request)`；现有 `product(id)` 改为委托 `product(id, request)`（controller 注入 request 传入）
5. **随机库存与阶梯价**（id 种子，保证同型号每次刷新一致，避免爬虫与真人看到不同价格）：
   - 基准单价随机（约 `0.50 ~ 50.00` USD），档位递减系数约 `1.00 / 0.85 / 0.80 / 0.62`
   - 档位：`1+ / 5+ / 10+ / 100+`；`extPrice = 档位单价 × 档位起点数量`
6. 新增 `DetailController`（`@Controller`）：
   - `GET /detail/{id}` → 读 cookie lang → `service.product(id, request)` → model 放入 `vo / lang / langs / langName / seoLocal` → 返回视图 `detail`
   - JSON-LD 的 JSON 由 controller 用 **fastjson 序列化** `vo.multilingual / vo.seo / partNumber / brand / 首档价格` 等组装成 `productJson`（String）进 model，模板用 `th:text` 直接输出，**不在模板里手拼 JSON**
   - 记录不存在或已删除 → **404 + 简单错误页**（复用 `requireRecord` 抛异常，由全局异常处理返回 404）

## 5. 页面布局（对照参考图）

| 区块 | 实现 |
|---|---|
| 顶栏 | 写死静态：HQ mall Logo + 搜索框（占位 `Part#/Keyword` + 热门词 RP2350A 等）+ 导航（Products / BOM Tool / Request Quote / PCB Service / About Us / Contact Us / Blog）+ 邮箱 + 购物车图标(20)。**全无功能** |
| 语言切换 | 顶栏右侧（用户图标旁）下拉框：English / 中文 / 日本語 / Русский；onchange → 写 cookie `lang` → `location.reload()` 整页重渲染 |
| 面包屑 | `Home > {category} > {subcategory 有则显示，无则省略} > {partNumber}` |
| 主区左栏 | `images/default/demo1.png / demo2.png / demo3.png` 三图 + 缩略图切换 + 「Images are for reference only」；字段：Mfr Part #=partNumber、Manufacturer=brand、HQ Part #=记录id、Package=packageType、Lead Time=写死 Ship immediately、Description=本地化描述、Customer # 输入框(无功能)、Datasheet 链接(datasheetUrl 有则显示，无则隐藏) |
| 主区右栏 | `{stock} In Stock` + Qty 输入框(默认1) + Minimum 1 / Multiple 1(写死) + Unit Price=$首档 + Ext Price + 红色 **Add to Cart / Buy Now**（存在无功能） |
| 属性表 | 固定行：Product Type=category+subcategory、Package/Case=packageType；**动态行 = `parameters` 列表**（每个元器件属性不同，后台生成时保存，name → value+unit） |
| 价格阶梯表 | `Qty \| Unit Price \| Ext Price`，渲染 `prices` 4 档，递减 |
| 描述区 | 直接展示本地化 `description`（随整页语言刷新，无客户端 tab） |
| 页脚 | 静态公司/联系信息 |
| **JSON-LD** | Schema.org `Product`，`@graph` 数组 = 4 种语言各一个节点（见 §6），**不随 UI 语言变化，始终全量输出** |

## 6. JSON-LD 结构化数据

`<script type="application/ld+json">`，对 `SUPPORTED_LANGS` 每种语言输出一个 Product 节点：

```json
{
  "@context": "https://schema.org",
  "@graph": [
    {
      "@type": "Product",
      "@id": "https://<host>/goods/detail/{id}",
      "name": "{partNumber}",
      "inLanguage": "en",
      "description": "{multilingual[en] 或 descriptionEn}",
      "brand": { "@type": "Brand", "name": "{brand}" },
      "category": "{category} {subcategory}",
      "sku": "{partNumber}",
      "image": "{imageUrl}",
      "offers": {
        "@type": "Offer",
        "price": "{首档 unitPrice}",
        "priceCurrency": "USD",
        "availability": "https://schema.org/InStock",
        "url": "https://<host>/goods/detail/{id}"
      }
    },
    { "zh" 节点, "ja" 节点, "ru" 节点: 同上，description 取 multilingual[lang] }
  ]
}
```

- `name` 用 partNumber（无每语言品名数据）；`description` 取对应语言，缺失回退 en
- 无论当前 UI 语言，JSON-LD 恒输出全部 4 语言（搜索引擎/AI 的目标，与页面语言解耦）
- **绝对 URL（@id / offers.url / canonical）**：由当前 request 推导 `request.getRequestURL()`（scheme://host:port + `/goods/detail/{id}`），无需配置站点域名

## 7. head SEO

- `title / description / keywords`：取 `seo[lang]`，回退 `seo[en]`
- `<link rel="canonical">` = 当前 URL
- `hreflang` alternate：en / zh / ja / ru + `x-default=en`
- `<html lang="{当前语言}">`

## 8. 语言切换交互

```js
// 顶栏下拉 onchange
document.cookie = "lang=" + code + "; path=/; max-age=31536000";
location.reload();
```

服务器按 cookie 重新渲染整页（含 head SEO、正文、面包屑），刷新无闪烁。

## 9. 错误处理

- 记录不存在或已删除 → 404 状态码 + 简单错误提示页（CSS 与主站一致）

## 10. 测试

- 单元测试（Mockito）：
  - `product(id, request)` 各语言取值与 en 回退
  - 无 cookie 默认 en；非法 cookie 值回退 en
  - 同 id 随机库存/价格稳定；价格档位严格递减
- 手工验证：切语言刷新、`/goods/detail/{id}` 各区块渲染、JSON-LD 用 Google Rich Results / schema.org 校验器验证

## 11. 非目标（本次不做）

- 真实电商功能（购物车 / 下单 / 库存 / 价格）
- 参数名、品牌、分类等的多语言（后台未存）
- 商品图按型号动态化（写死 demo 图）
- zhTw 语言支持（保留在库，`SUPPORTED_LANGS` 常量可扩展）
