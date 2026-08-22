# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

AI 驱动的外贸商品信息标准化与多语言 SEO 系统（华秋电子）。Spring Boot 2.1.7 / Java 11 / MyBatis-Plus 3.5.17，Thymeleaf 落地页 + Vue3 CDN 工作台，`server.servlet.context-path=/goods`。

## 命令

### 构建
- **必须用 JDK 11**：`JAVA_HOME="E:\Java\java11-dragonwell-11.0.31.27"`（系统默认 JDK 21 会让 Lombok 1.18.22 编译失败）。
- `mvn clean package` 打包；`mvn compile` 仅编译。

### 测试（JUnit 4，无 junit-jupiter）
- `mvn test` 跑全部测试。
- `mvn -Dtest=GoodsDocServiceImplTest test` 跑单个测试类。
- 风格：`@RunWith(MockitoJUnitRunner.class)` + `org.junit.Test` + Mockito `@Mock`/`@InjectMocks`。
- **测试从简**：保持单元测试短小快速，不写冗长/慢的全链路测试。

### 运行
- `mvn spring-boot:run`（或 `java -jar target/goods-doc-lang-1.0-SNAPSHOT.jar`）。
- 依赖本地 MySQL `doclang` 库（建表 SQL 在 `src/main/java/com/hq/goods/lang/sql/goods-lang.sql`）。
- **验证完必须停止服务**（Ctrl+C / 结束进程），否则占用 8080 端口，用户手动启动会冲突。

## 架构

### 分层（`com.hq.goods.lang`）
- `controller/` — `GoodsDocController`（`/api/doc` REST）+ `DetailController`（`/detail/{id}` SSR 落地页）。
- `service/` + `service/impl/` — 接口与实现分离，编排逻辑集中在 `GoodsDocServiceImpl`。
- `dao/` — MyBatis-Plus `BaseMapper` 接口，由 `DocLangApplication` 上 `@MapperScan("com.hq.goods.lang.dao")` 扫描。
- `bean/` — `entity`（DB 表）、`dto`（请求体）、`vo`（响应体）三类分目录。
- `utils/` — `GoodsDocParseUtil`（LLM JSON 容错解析）、`TranslatorProvider`/`TranslatorProviderFactory`（AI provider 抽象）、`HttpUtil`。
- `config/` — 拦截器、全局异常处理、MyBatis-Plus 分页、Swagger。
- word、excel、pdf、ppt文档操作（查看/新增/修改/删除）都是在E:\workspace\doc文件夹下面操作，方便用户查看。

### 编排数据流（`GoodsDocService` 是核心）
9 个接口（前缀 `/api/doc`）：`parsePart` / `parseText`（解析）、`generateDesc`（英文描述）、`generateMulti`（多语言+SEO）、`save`（无 id 新增 / 有 id 更新）、`list`（分页）、`detail`、`delete`（逻辑删除）、`product/{id}`（客户页公开数据）。

解析链路：RAG `searchTopK` + 术语召回 `recallTerms` → LLM 返回 JSON → `GoodsDocParseUtil` 容错 → `GoodsDocVo`。描述走同一 provider；多语言用 `AiLLMService.aiTranslate`（英/中/繁/日/俄，单语失败置空不阻断）；SEO 4 语言一次 LLM。

### 数据层约定（易踩坑）
- **`mybatis-plus.mapper-locations: classpath*:mappers/*.xml`**：自定义 SQL 在复数 `mappers/` 目录，MyBatis-Plus 默认只扫单数 `mapper/**`，不配则 `selectAllValid` 等全部 `BindingException`。
- 复杂字段以 **JSON 字符串**存列（`parameters`、`applications`、`multilingual`、`seo`），用 fastjson 序列化；`seo` 形如 `{"en":{"title":..,"keywords":[..],"description":..}}`。
- 表名前缀 `hq_`（如 `hq_translation_term`）；实体包 `com.hq.goods.lang.bean.entity`。
- `RagService` 当前是 MySQL LIKE 计分实现，接口签名预留了 ES 向量检索扩展（换 Bean 即可，调用方不变）。

### 前端
- `static/index.html` — Vue3 CDN 无构建，三视图（工作台 / 历史列表 / 只读详情），字段与 `GoodsDocVo` 对齐；`SeoVo.keywords`（List）↔ 页面 `keywordsText`（字符串）互转。
- `templates/detail.html` — Thymeleaf SSR 落地页，后端注入多语言 JSON-LD（`DetailController.buildJsonLd` 生成，转义 `</` 防 `</script>` 逃逸）。
- 落地页语言由 cookie `lang` 决定（`en`/`zh`/`ja`/`ru`），默认 `en`，缺失回退 `en`。

## 开发规范（用户约定）

- **直接执行、免确认**：计划和编码期间连续执行，非危险操作不再逐项确认。
- **提交到 main**：快速模式，TDD（写测试 → 实现 → 自审 → 提交），跳过逐任务外部双审。
- **发现真实 bug 先征询再修复**。
- **中文沟通**；代码风格与现有代码保持一致（命名、注释密度、结构）。
- **API Key 只留本地**：`application.yml` 里 `aihubmixKey`/`deepseekKey` 为本地真实值，git 历史中保持 `xxx` 占位符，改此文件不要提交真实 Key。

## Skill 调用约定

遇到下列场景优先调用对应 Skill（用 `Skill` 工具，名字以实际可用列表为准）：

| 场景 | Skill |
|------|-------|
| 文档操作处理（Word/PDF/PPT/Excel 创建、读取、编辑） | `docx` / `pdf` / `pptx` / `xlsx` / `doc-coauthoring` |
| 创建、修改、优化 Skill | `skill-creator` |
| 前端设计（视觉方向、排版、去模板化） | `frontend-design` |
| 前端 UI/UX 设计（组件、设计系统、配色、响应式） | `ui-ux-pro-max` |
| 突破瓶颈、卡壳/反复失败时施压自检 | `pua` |
| 发现/安装新 Skill（"有没有能 X 的 skill"） | `find-skills` |
| 新增需求时先暴流程、澄清意图 | `brainstorming`（superpowers 流程，先用 `using-superpowers`） |

### 触发规则
- **新增需求 / 新功能 / 改行为**：先走 `brainstorming` 澄清意图与设计，再动手实现。
- **前端页面或 UI 改动**：先 `frontend-design` + `ui-ux-pro-max` 定视觉方向，再写代码。
- **文档类产出**（报告/Word/PDF/表格/PPT）：按上表用对应文档 Skill。
- **卡壳 / 反复失败 / 要求施压**：走 `pua` 结构化排障 + 证据优先自检。