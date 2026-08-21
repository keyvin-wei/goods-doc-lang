# 外贸商品信息标准化与多语言 SEO 智能生成系统 — 设计规格

> 日期: 2026-08-16
> 关联旧稿: `2026-07-03-component-data-standardization-design.md`（比赛 Demo 版，本稿为其产品化演进）

## 一、背景与目标

华秋电子海外业务涉及大量电子元器件产品，上线需维护产品基础信息、技术参数、英文产品描述、SEO Title/Keywords/Description、多语言资料等内容。人工处理耗时、重复、专业表达难统一。

本系统将 AI 能力引入元器件产品资料处理流程：**Datasheet/产品信息 → AI 解析 → 元器件信息标准化 → 产品内容生成 → SEO 内容生成**，减少重复人工工作，提高海外商品资料生产效率。

### 核心目标
- 后台业务员工作台：解析 → 审核修改基本资料 → 生成英文描述 → 生成多语言+SEO → 保存
- 保存后的多语言/SEO 数据经公开接口供**官网服务端渲染**（SSR），实时渲染 SEO 元数据与 JSON-LD，提高搜索引擎/AI 识别率
- 复用现有翻译流程、元器件术语库、翻译日志；RAG 先简单实现、预留 ES 向量检索扩展

## 二、本轮范围

### 本轮实现
| 能力 | 说明 |
|------|------|
| ① 元器件信息智能解析 | 型号+品牌 或 大段描述文本 → 结构化字段（含 RAG TOP-K 参考） |
| ② 产品描述智能生成 | 基于结构化字段生成英文标准描述 |
| ③ SEO 内容智能生成 | 中/英/日/俄 各一套 Title/Keywords/Description |
| ⑤ 行业术语与多语言 | 复用术语库召回约束生成；多语言描述(英/中/繁/日/俄)走现有翻译流程 |
| 数据持久化 | 保存（含审核修改）、历史列表、编辑再保存 |
| 公开数据接口 | `GET /api/doc/product/{id}` 供官网 SSR 渲染 JSON-LD |

### 预留后续
- ④ JSON-LD：不在后台生成/存储，由官网 SSR 页面基于 `product/{id}` 返回的多语言/SEO 数据实时渲染（Schema.org Product）
- PDF/Datasheet 上传解析（OCR，Tess4J 已引入）
- RAG 升级为 ES 向量检索 TOP-K
- 图片/PDF 资料：AI 不生成，业务员手工维护，默认放 `static/` 下，URL 存表

## 三、架构

```
后台前端  static/index.html（Vue3 CDN，无构建、无 SSR）
  工作台 / 历史列表 / 后台详情(编辑)        ← 本项目三视图
        │ fetch
        ▼
GoodsDocController  /api/doc/*
        │
GoodsDocServiceImpl（编排 解析→描述→多语言→保存）
   ├─ TranslatorProviderFactory.translate()   复用：所有 LLM 生成（解析/描述/SEO）
   ├─ AiLLMService.aiTranslate()              复用：多语言翻译（中/繁/日/俄，含术语+TM+日志）
   ├─ TranslationService.recallTerms()        复用：术语召回注入生成 Prompt
   ├─ RagService.searchTopK()                 新增：RAG（当前 MySQL LIKE 简单实现，预留 ES）
   └─ GoodsDocRecordDao（MyBatis-Plus，新表 hq_goods_doc_record）

客户页面（现有官网，SSR，不在本项目内）
  服务端打开型号页 → GET /api/doc/product/{id} → 渲染 SEO meta + JSON-LD 进 HTML → 展示
```

分层：控制层 `GoodsDocController` → 服务层 `GoodsDocService`（编排）→ AI/数据层（复用现有翻译、LLM Provider、术语库；新增 RAG、记录 DAO）。响应包裹沿用 `ResultBody {code,message,body}`，异常处理沿用 `GlobalExceptionHandler` + `CustomException`。

## 四、接口契约（9 个）

统一前缀 `/api/doc`（配合 `context-path=/goods`，完整路径 `/goods/api/doc/*`），响应均为 `ResultBody`。

| # | 方法 | 路径 | 请求 | 响应 body | 用途 |
|---|------|------|------|-----------|------|
| 1 | POST | `/parsePart` | `{partNumber, brand}` | `GoodsDocVo`（含 `topK`） | 型号+品牌解析，回填覆盖基本资料 |
| 2 | POST | `/parseText` | `{rawText}` | `GoodsDocVo`（含 `topK`） | 大段描述解析提取字段 |
| 3 | POST | `/generateDesc` | `GoodsDocVo` | `{description}` | 生成英文标准描述 |
| 4 | POST | `/generateMulti` | `{GoodsDocVo + description}` | `{multilingual, seo}` | 多语言翻译(5) + SEO(中/英/日/俄) |
| 5 | POST | `/save` | `SaveReq`（无 id 新增 / 有 id 更新） | `{id}` | 业务员确认后保存 |
| 6 | GET | `/list?page=&size=` | — | `{total, list:[RecordVo]}` | 历史分页列表 |
| 7 | GET | `/detail?id=` | — | `GoodsDocRecordVo` | 后台详情（编辑回填） |
| 8 | DELETE | `/delete?id=` | — | — | 逻辑删除 |
| 9 | GET | `/product/{id}` | — | `ProductVo`（多语言+SEO+基本资料） | 公开接口，官网 SSR 用 |

**请求/响应对象：**
- `GoodsDocVo`：基本资料全部字段（见第六节字段清单）+ `topK`
- `SaveReq`：`GoodsDocVo` + `multilingual` + `seo` + `id?` + `sourceType`
- `ProductVo`：`partNumber/brand/category/package/parameters/descriptionEn/applications/multilingual/seo/imageUrl/datasheetUrl`

## 五、数据流（五能力落地）

### ① 智能解析 `parsePart` / `parseText`
1. 参数校验（`partNumber+brand` 或 `rawText` 至少其一）
2. `RagService.searchTopK(查询文本, 3)` 召回相似型号作为 few-shot 参考
3. 构建 Prompt：系统提示（电子元器件领域专家、输出严格 JSON schema）+ 用户内容（输入 + TOP-K 参考样例），术语召回结果一并注入
4. `TranslatorProvider.translate()` 调用 → 剥离代码块 → fastjson 解析 → `GoodsDocVo`（AI 仅填能识别的字段，其余留空）
5. 返回 `GoodsDocVo` + `topK`，前端**覆盖**表单字段

### ② 英文描述生成 `generateDesc`
- 基于 `GoodsDocVo` 结构化字段 + 术语召回结果，Prompt 生成英文标准描述（适合海外网站），返回 `description`

### ③ 多语言 + SEO `generateMulti`
- **多语言描述**：英文描述经 `AiLLMService.aiTranslate(text, target=2/3/4/5, source=0)` 并行翻成 中/繁/日/俄（复用现有术语+TM+日志），英文本体保留
- **SEO**：一个 LLM 调用，基于结构化字段 + 英文描述，返回 JSON `{zh,en,ja,ru} × {title, keywords[], description}`

### ④ JSON-LD（官网 SSR，不在后台生成）
- 后台只保证 `product/{id}` 返回的多语言/SEO 数据完整；官网服务端据此按 Schema.org Product 拼装并注入 `<script type="application/ld+json">`，实时渲染，提升 AI/搜索引擎识别率

### ⑤ 术语约束
- 解析/描述/SEO 生成前 `TranslationService.recallTerms()` 召回中英术语映射注入 Prompt；翻译天然走现有术语库流程

## 六、数据模型

### 表 `hq_goods_doc_record`（新表）

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGINT PK AUTO | 主键 |
| part_number | VARCHAR(100) | 型号 |
| brand | VARCHAR(100) | 品牌 |
| category | VARCHAR(100) | 分类 |
| subcategory | VARCHAR(100) | 子分类 |
| series | VARCHAR(100) | 系列 |
| package | VARCHAR(100) | 封装 |
| mounting_type | VARCHAR(50) | 安装类型 SMD/THT |
| pin_count | INT | 引脚数 |
| dimensions | VARCHAR(100) | 尺寸 |
| parameters | TEXT | 动态参数 JSON：`[{name,value,unit}]` |
| operating_temp | VARCHAR(100) | 工作温度范围 |
| storage_temp | VARCHAR(100) | 存储温度 |
| grade | VARCHAR(50) | 质量等级 |
| rohs | VARCHAR(50) | RoHS/环保 |
| packaging | VARCHAR(50) | 包装方式（编带/托盘/管装） |
| moq | VARCHAR(50) | 最小起订量 |
| unit | VARCHAR(50) | 单位 |
| hs_code | VARCHAR(50) | 海关编码 |
| lead_time | VARCHAR(50) | 交期 |
| price_range | VARCHAR(100) | 价格区间 |
| availability | VARCHAR(50) | 供货状态 |
| datasheet_url | VARCHAR(500) | 数据手册（业务员维护） |
| image_url | VARCHAR(500) | 图片（业务员维护） |
| applications | TEXT | 应用领域 JSON 数组 |
| description_en | TEXT | 英文标准描述 |
| multilingual | TEXT | JSON：`{en,zh,zhTw,ja,ru}` 描述 |
| seo | TEXT | JSON：`{zh,en,ja,ru}×{title,keywords[],description}` |
| raw_input | TEXT | 原始输入 |
| source_type | TINYINT | 1=型号解析 2=文本解析 |
| status | TINYINT | 状态 |
| creator / updater | VARCHAR(50) | 创建/修改人 |
| delete_status | TINYINT | 0有效 1删除，默认0 |
| c_time / u_time | DATETIME | 创建/更新时间 |

### 页面商品字段（基本资料，可编辑）
① 标识：型号/品牌/分类/子分类/系列
② 物理：封装/安装类型/引脚数/尺寸
③ 电气：`parameters` 动态键值列表（AI 按品类提取，可增删行）
④ 环境质量：工作温度/存储温度/质量等级/RoHS
⑤ 商业：包装方式/MOQ/单位/海关编码/交期/价格区间/供货状态
⑥ 资料：数据手册 URL/图片 URL（**AI 不生成，业务员维护**，默认用 `static/` 下默认图/PDF）/应用领域
⑦ 其他：英文描述、RAG 召回参考 `topK`

## 七、RAG 简单实现（预留 ES）

- 接口：`RagService` → `List<RagHit> searchTopK(String query, int k)`；`RagHit {partNumber, brand, score}`
- **当前实现**：从 `hq_goods_doc_record` 按 型号/品牌/分类/封装 做 MySQL LIKE 匹配，按匹配命中数简单计分排序取 TOP-K（数据源即已保存记录）
- **后续**：ES 部署后新增 ES 向量检索实现（相同接口签名），配置切换 Bean，调用方不变
- 解析时注入 TOP-K 作为 few-shot 参考并返回给页面展示

## 八、前端页面（Vue3 CDN，`static/index.html`）

单文件、无构建；访问 `http://服务器:8080/goods/`（勿用 `file://` 打开，否则 fetch 跨域失败）。三视图客户端切换：

1. **工作台**
   - 型号+品牌 输入框 + [解析]
   - 大段描述 文本框 + [解析]
   - 基本资料表单（全部字段可编辑，`parameters`/`applications` 动态行增删）
   - [生成英文描述] → 填入描述框
   - [多语言生成] → 多语言(5) + SEO(中英日俄) 展示区（可编辑、可复制）
   - [保存] → 落库跳转列表
2. **历史列表**：分页表格 + 查看/编辑/删除
3. **后台详情**：Vue 异步加载 `/detail` 数据 → 只读展示/编辑模式 → 再保存

## 九、错误处理

- 复用 `GlobalExceptionHandler`（`CustomException` 业务异常、参数校验、通用 500）
- LLM 返回非 JSON / 解析失败 → 重试一次 → `CustomException`（友好提示）
- 翻译失败不阻断主流程：该语言置空 + warn 日志，保留英文
- 前端：`code!=200` 时展示 `message`；生成中按钮禁用、展示加载态

## 十、测试

- 单测：fastjson 容错解析、`GoodsDocVo` 构建、RagService 简单计分、SaveReq 新增/更新分支
- Service 层测试：mock `TranslatorProvider`，验证 Prompt 组装与 VO 输出
- 手工端到端：解析→描述→多语言→保存→列表→编辑再保存

## 十一、项目结构（新增/修改）

```
src/main/java/com/hq/goods/lang/
├── controller/GoodsDocController.java         修改（空实现 → 9 个接口）
├── service/GoodsDocService.java               修改（编排接口）
├── service/impl/GoodsDocServiceImpl.java      修改（实现）
├── service/RagService.java                    新增
├── service/impl/RagServiceImpl.java           新增（LIKE 简单实现，预留 ES）
├── bean/entity/GoodsDocRecord.java            新增
├── dao/GoodsDocRecordDao.java                 新增（BaseMapper）
├── bean/dto/{ParsePartReq, ParseTextReq, GenerateDescReq, GenerateMultiReq, SaveReq}.java   新增
├── bean/vo/{GoodsDocVo, GoodsDocRecordVo, RecordVo, ProductVo, RagHit}.java                 新增
src/main/resources/
├── static/index.html                          重写（Vue3 CDN 三视图）
├── static/images/default/*                    新增（默认图/PDF，业务员自维护素材）
├── mappers/GoodsDocRecordMapper.xml           新增（如需自定义 SQL）
└── sql/goods-lang.sql                         补充 hq_goods_doc_record DDL
```

## 十二、后续扩展
- PDF/Datasheet 上传 + OCR 解析（Tess4J 已引入）
- RAG 切 ES 向量检索 TOP-K
- SEO 多语言扩展、JSON-LD 由后端按需生成接口（当前由官网 SSR 自拼）
- 保存记录对接实际上架流程 / 审核流