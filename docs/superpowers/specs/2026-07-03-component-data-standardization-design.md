# 电子元器件数据标准化与技术描述生成工具 — 设计规格

> 比赛 Demo 设计文档
> 日期: 2026-07-03

## 一、概述

构建一个 AI + RAG 驱动的电子元器件数据标准化工具，比赛中演示从"非结构化元器件型号输入"到"标准化 JSON + 英文技术描述输出"的全链路过程。

### 核心目标
- 可本地运行的演示系统（Spring Boot + 单页前端）
- 输入型号 → AI 结构化输出
- RAG 增强：embedding 余弦相似度召回 TOP-3，注入 Prompt 提升准确性
- 现场演示流畅，5 分钟内讲完完整链路

## 二、系统架构

标准三层架构：

```
前端 (static/index.html) → GoodsDocController → GoodsDocService → AI Service (Deepseek API)
                                                              → RAG Service (MySQL 5.7 + embedding)
```

### 分层职责

| 层次 | 组件 | 职责 |
|------|------|------|
| 表现层 | index.html | 输入型号、展示结果、展示召回列表 |
| 控制层 | GoodsDocController | 接收请求、校验参数、返回响应 |
| 服务层 | GoodsDocService | 编排流程：向量化 → RAG 召回 → AI 生成 |
| AI 层 | AiService | Deepseek chat API 调用 |
| AI 层 | EmbeddingService | Deepseek embedding API 调用 |
| AI 层 | RAGService | 加载向量 → 余弦相似度 → TOP-K |
| 数据层 | ComponentRepository | MySQL 数据访问 |
| 基础设施 | DataInitializer | 启动时预填充 30-50 条热门型号数据 |

## 三、核心流程

1. 用户输入型号，点击生成
2. EmbeddingService 调用 Deepseek embedding API，将用户输入转向量
3. RAGService 从 MySQL 加载所有预存向量，余弦相似度计算，取 TOP-3
4. AiService 构建 Prompt（用户输入 + TOP-3 上下文），调用 Deepseek chat API
5. 一次性返回结构化的 JSON（标准化数据 + 技术描述 + 推理链 + 召回列表）
6. 前端分区域展示结果

## 四、数据存储

### 表结构

**component_library** — 元器件主表
- `id` BIGINT PK AUTO_INCREMENT
- `part_number` VARCHAR(100) UNIQUE
- `brand` VARCHAR(100)
- `category` VARCHAR(50) INDEX
- `parameters` JSON（品类相关参数字段）
- `standard_desc` TEXT
- `applications` JSON
- `embedding` BLOB（float[] 序列化）
- `created_at` DATETIME

**category_template** — 品类字段模板
- `id` BIGINT PK AUTO_INCREMENT
- `category` VARCHAR(50)
- `fields` JSON（如 MCU → ["core","flash","ram","package","frequency"]）

### 预填充数据

共 30-50 条，覆盖 6 个品类：
- MCU（ST/Microchip/TI/NXP/GD，~15条）
- 运放（TI/Analog Devices/ST，~5条）
- 电阻（Yageo/Rohm，~5条）
- 电容（Murata/Samsung，~5条）
- 连接器（TE/Molex，~5条）
- 电源IC（TI/MPS，~5条）

Embedding 在启动时通过 DataInitializer 批量生成并落库。

## 五、RAG 实现

### Embedding 生成策略
- **存储用 embedding**：基于"型号+品牌+品类+关键参数"的丰富文本生成
- **查询用 embedding**：基于用户输入的简短文本生成
- **API**：Deepseek embeddings API（支持批量）

### 相似度计算
```java
cosineSimilarity(float[] a, float[] b) = dot(a,b) / (norm(a) * norm(b))
```
Demo 数据量小（≤50条），全表遍历计算即可。

### 为何 Demo 可行
- 50 条数据 × 全表遍历 = 毫秒级
- 生产环境（1000万+）需改用 Milvus / ES 向量插件，不在本次 Demo 范围

## 六、AI Prompt 设计

一次性调用，通过 System Prompt + 注入的 TOP-K 上下文，让 AI 直接输出结构化 JSON。

- model: deepseek-chat
- temperature: 0.1（保证输出一致性）
- 输出含 reasoning_steps 字段供前端展示处理过程

## 七、前端设计

单页 HTML + CSS + JS，Spring Boot 托管于 `src/main/resources/static/index.html`。

### 页面区域
1. 输入区：型号输入框 + "一键生成"按钮 + 示例快捷填充
2. 处理进度：步骤动画（向量检索 → AI分析 → 完成）
3. 结果区：Tab 切换（标准化JSON / 技术描述+应用场景）
4. 召回参考区：TOP-3 相似型号及相似度

## 八、后端 API

**POST /api/generate**
- Request: `{ "partNumber": "STM32F103C8T6" }`
- Response: `{ success, data: { partNumber, brand, category, parameters, standardDesc, applications, reasoningSteps, topK } }`
- Error: `{ success: false, message, errorCode }`

### 错误处理

| 场景 | 处理 |
|------|------|
| 输入为空 | 400 参数校验 |
| AI 超时 | 捕获超时，返回友好提示 |
| AI 异常 | 降级：若 TOP-K 命中，直接返回库中数据 |
| Embedding 失败 | 跳过 RAG，直接调 AI（纯 LLM 回退） |
| 无匹配型号 | 返回空结构化数据 |

## 九、项目结构

```
src/main/java/com/keyvin/hq/
├── controller/GoodsDocController.java
├── service/{GoodsDocService, AiService, EmbeddingService, RAGService}.java
├── repository/ComponentRepository.java
├── entity/{Component, CategoryTemplate}.java
├── dto/{GenerateRequest, GenerateResponse}.java
├── config/DataInitializer.java
└── util/VectorUtils.java
src/main/resources/
├── static/index.html
└── application.yml
```

## 十、非功能性约束

- 演示环境：本地 Windows 机器
- Java 11 + Spring Boot 2.1.7（保持现有项目版本）
- MySQL 5.7
- 所有外部依赖：Deepseek API（需网络）
- 前端纯静态，无 Node/npm 依赖
