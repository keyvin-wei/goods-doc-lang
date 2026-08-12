# 电子元器件数据标准化与技术描述生成系统 — 架构图与核心流程

> 用于演讲展示
> 所有图使用 Mermaid 语法，可在 Markdown 编辑器中直接渲染

---

## 一、系统架构图

```mermaid
graph TB
    subgraph 前端层["前端层 (Browser)"]
        UI["index.html<br/>单页应用"]
    end

    subgraph 控制层["控制层 (Spring Boot)"]
        GC["GoodsDocController<br/>POST /api/generate"]
        HC["Health Check<br/>GET /api/health"]
    end

    subgraph 服务层["服务层"]
        GS["GoodsDocService<br/>流程编排 + 降级兜底"]
    end

    subgraph AI层["AI / RAG 层"]
        AS["AiService<br/>Deepseek Chat API"]
        ES["EmbeddingService<br/>Deepseek Embedding API"]
        RS["RAGService<br/>余弦相似度 TOP-K"]
    end

    subgraph 数据层["数据层"]
        CR["ComponentRepository<br/>JPA Repository"]
        DB[("MySQL 5.7<br/>component_library")]
    end

    subgraph 外部API["外部 API"]
        DSAPI["Deepseek API<br/>api.deepseek.com"]
    end

    subgraph 初始化器["启动时初始化"]
        DI["DataInitializer<br/>CommandLineRunner<br/>30条预填数据 + Embedding"]
    end

    UI -->|"HTTP POST<br/>{partNumber}"| GC
    UI -->|"HTTP GET"| HC
    GC -->|"调用"| GS
    GS -->|"搜索 TOP-3"| RS
    GS -->|"AI 生成"| AS
    GS -.->|"AI 失败<br/>降级取库"| CR
    RS -->|"查询所有/相似度计算"| CR
    RS -->|"生成用户输入向量"| ES
    AS -->|"POST /v1/chat/completions"| DSAPI
    ES -->|"POST /v1/embeddings"| DSAPI
    CR -->|"读写"| DB
    DI -->|"启动时填充"| CR
    DI -->|"批量生成 Embedding"| ES

    style 前端层 fill:#e3f2fd,stroke:#1565c0
    style 控制层 fill:#c8e6c9,stroke:#2e7d32
    style 服务层 fill:#fff9c4,stroke:#f9a825
    style AI层 fill:#f3e5f5,stroke:#7b1fa2
    style 数据层 fill:#ffe0b2,stroke:#e65100
    style 外部API fill:#ffcdd2,stroke:#c62828
    style 初始化器 fill:#e0f7fa,stroke:#00838f
```

---

## 二、核心流水线流程图

```mermaid
flowchart TD
    START(["用户输入型号"])
    VALIDATE{"输入为空?"}
    ERR_EMPTY["提示: 请输入型号"]
    GEN_EMBED["EmbeddingService<br/>生成输入向量"]
    LOAD_VECTORS["加载所有预存向量<br/>(ComponentRepository.findAll)"]
    COMPUTE_SIM["余弦相似度计算<br/>逐条比对"]
    TOP3["取 TOP-3 召回结果"]
    BUILD_CONTEXT["构建 Prompt 上下文<br/>(型号+品牌+参数+描述)"]
    BUILD_PROMPT["构建 AI Prompt<br/>(标准化专家 System +<br/>输入型号 + 召回 Context)"]
    CALL_AI["调用 Deepseek Chat<br/>model=deepseek-chat<br/>temperature=0.1"]
    AI_SUCCESS{"HTTP 调用成功?"}
    PARSE_JSON["提取 Markdown Code Block<br/>解析 JSON"]
    PARSE_SUCCESS{"JSON 解析成功?"}
    BUILD_RESPONSE["构建 GenerateResponse<br/>成功数据结构"]
    FALLBACK["降级: 从数据库<br/>直接构建响应<br/>(带 Warning 信息)"]
    ERROR["返回: 处理失败<br/>PROCESSING_ERROR"]

    START --> VALIDATE
    VALIDATE -->|是| ERR_EMPTY
    VALIDATE -->|否| GEN_EMBED
    GEN_EMBED --> LOAD_VECTORS
    LOAD_VECTORS --> COMPUTE_SIM
    COMPUTE_SIM --> TOP3
    TOP3 --> BUILD_CONTEXT
    BUILD_CONTEXT --> BUILD_PROMPT
    BUILD_PROMPT --> CALL_AI
    CALL_AI --> AI_SUCCESS
    AI_SUCCESS -->|是| PARSE_JSON
    AI_SUCCESS -->|否| FALLBACK
    PARSE_JSON --> PARSE_SUCCESS
    PARSE_SUCCESS -->|是| BUILD_RESPONSE
    PARSE_SUCCESS -->|否| FALLBACK
    FALLBACK --> BUILD_RESPONSE
    BUILD_RESPONSE --> RETURN_RESULT["返回给前端展示"]

    style START fill:#c8e6c9,stroke:#2e7d32
    style ERR_EMPTY fill:#ffcdd2,stroke:#c62828
    style CALL_AI fill:#f3e5f5,stroke:#7b1fa2
    style FALLBACK fill:#fff9c4,stroke:#f9a825,stroke-dasharray: 5 5
    style ERROR fill:#ffcdd2,stroke:#c62828
    style RETURN_RESULT fill:#c8e6c9,stroke:#2e7d32
```

---

## 三、RAG 检索详情图（时序图）

```mermaid
sequenceDiagram
    actor User as 用户
    participant UI as 前端页面
    participant GC as GoodsDocController
    participant GS as GoodsDocService
    participant ES as EmbeddingService
    participant RS as RAGService
    participant DB as MySQL
    participant AS as AiService
    participant AI as Deepseek API

    User->>UI: 输入型号<br/>STM32F103C8T6
    UI->>GC: POST /api/generate
    GC->>GS: process("STM32F103C8T6")

    Note over GS: Step 1: RAG 检索
    GS->>RS: search(input, topK=3)
    RS->>ES: generateEmbedding(input)
    ES->>AI: POST /v1/embeddings
    AI-->>ES: float[1536]
    ES-->>RS: queryVector
    RS->>DB: findAll() 加载所有预存向量
    DB-->>RS: List<Component>
    Note over RS: 内存中计算余弦相似度
    RS-->>GS: TOP-3 结果

    Note over GS: Step 2: AI 生成
    GS->>GS: buildContext()<br/>拼接 TOP-3 为文本
    GS->>AS: generate(input, context)
    AS->>AI: POST /v1/chat/completions
    AI-->>AS: 结构化 JSON
    AS-->>GS: JSON 文本

    Note over GS: Step 3: 解析 & 返回
    GS->>GS: parseAiResponse()<br/>提取 JSON → 构建 Response
    GS-->>GC: GenerateResponse

    alt AI 失败
        GS->>GS: buildFallbackResponse()<br/>从 TOP-1 构建数据
        Note over GS: message: "AI服务异常，已降级"
    end

    alt JSON 解析失败
        GS->>GS: buildFallbackResponse()<br/>从 TOP-1 构建数据
        Note over GS: message: "AI结果解析失败"
    end

    GC-->>UI: 200 OK + JSON
    UI->>UI: 展示结果
```

---

## 四、数据模型 ER 图

```mermaid
erDiagram
    COMPONENT_LIBRARY {
        bigint id PK "自增主键"
        varchar part_number UK "元器件型号"
        varchar brand "品牌"
        varchar category "品类(MCU/运放/电阻/电容/连接器/电源IC)"
        json parameters "品参数(JSON)"
        text standard_desc "英文技术描述"
        json applications "应用场景(JSON数组)"
        blob embedding "浮点向量(BLOB)"
        datetime created_at "创建时间"
    }

    COMPONENT_LIBRARY ||--o{ TOP_K_RESULT : "相似度比较"

    TOP_K_RESULT {
        string part_number
        string brand
        float similarity
    }

    GENERATE_REQUEST {
        string partNumber
    }

    GENERATE_RESPONSE {
        bool success
        string message
        string errorCode
        Data data
    }

    DATA {
        string partNumber
        string brand
        string category
        map parameters
        string standardDesc
        list applications
        list reasoningSteps
        list topK
    }
```

---

## 五、部署架构图

```mermaid
graph LR
    subgraph 本地Windows["本地 Windows 机器"]
        subgraph MySQLSrv["MySQL 5.7<br/>端口: 33066"]
            DB[(nextpcb<br/>component_library)]
        end
        subgraph JavaApp["Spring Boot 2.1.7<br/>端口: 9000"]
            SB["Application.jar<br/>context-path: /hq"]
        end
        subgraph Browser["浏览器"]
            UI["http://localhost:9000/hq/"]
        end
    end

    subgraph Internet["互联网"]
        DSAPI["Deepseek API<br/>api.deepseek.com"]
    end

    UI -->|"HTTP"| SB
    SB -->|"JDBC"| MySQLSrv
    SB -->|"HTTPS"| DSAPI

    style 本地Windows fill:#e3f2fd,stroke:#1565c0
    style Internet fill:#ffcdd2,stroke:#c62828
```

---

## 六、前端页面布局图

```mermaid
graph TD
    subgraph 页面["页面布局"]
        TITLE["🔧 电子元器件数据标准化工具"]
        INPUT["输入框 + 🚀 一键生成按钮"]
        SAMPLES["快速示例: STM32F103C8T6 | LM358 | RC0603FR-07100RL | TPS5430"]
        PROGRESS["○ 向量检索 TOP-3 ...<br/>○ AI 结构化分析 ...<br/>○ 结果生成 ..."]
        RESULT["📊 标准化数据 | 📝 技术描述 (Tab)"]
        TOPK["📌 召回参考 (TOP-3)<br/>1. STM32F103C8T6 88.5%<br/>2. GD32F103C8T6 72.1%<br/>3. STM32F103CBT6 65.3%"]
        FOOTER["华秋电子 — AI 元器件数据标准化系统"]

        TITLE --> INPUT
        INPUT --> SAMPLES
        INPUT --> PROGRESS
        PROGRESS --> RESULT
        RESULT --> TOPK
        TOPK --> FOOTER
    end

    style 页面 fill:#f0f2f5,stroke:#333
    style TITLE fill:#1a73e8,color:#fff
    style INPUT fill:#fff,stroke:#ddd
    style RESULT fill:#fff,stroke:#ddd
    style TOPK fill:#fff,stroke:#ddd
    style FOOTER fill:#f0f2f5
```
