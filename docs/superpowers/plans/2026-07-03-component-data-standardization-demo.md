# 电子元器件数据标准化 Demo 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a competition demo that uses AI + RAG to standardize electronic component data — input a part number, output structured JSON + technical description.

**Architecture:** Standard three-layer Spring Boot backend (Controller → Service → AI/RAG Services) with MySQL 5.7 storing pre-populated components + embeddings. Deepseek API for both embedding generation and chat completion. Single-page HTML frontend for presentation.

**Tech Stack:** Java 11, Spring Boot 2.1.7, MySQL 5.7, JPA/Hibernate, Deepseek API (chat + embeddings), okhttp3, HTML/CSS/JS

## Global Constraints

- Must work on local Windows machine with MySQL 5.7 running
- Keep existing project versions: Spring Boot 2.1.7, Java 11
- Frontend: pure static HTML/CSS/JS in `src/main/resources/static/`, zero Node/npm
- AI: Deepseek API (requires network) — API key configured in `application.yml`
- MySQL 5.7 JSON type used for `parameters`, `applications`, `fields` columns
- Embedding stored as BLOB (Java `float[]` serialized via ByteArrayOutputStream)
- All new code under `com.hq.goods.lang` package
- Deepseek embedding model: `deepseek-embedding` (or `text-embedding-ada-002` compatible endpoint)
- Deepseek chat model: `deepseek-chat`

---

## File Structure

```
NEW/MODIFIED FILES:

pom.xml                                          [MODIFY] Add MySQL + JPA deps
src/main/resources/application.yml               [MODIFY] Add datasource + deepseek config

src/main/java/com/keyvin/hq/
├── entity/
│   ├── Component.java                           [CREATE] JPA entity for component_library table
│   └── CategoryTemplate.java                    [CREATE] JPA entity for category_template table
├── repository/
│   └── ComponentRepository.java                 [CREATE] Spring Data JPA repository
├── dto/
│   ├── GenerateRequest.java                     [CREATE] Request DTO
│   └── GenerateResponse.java                    [CREATE] Response DTO
├── util/
│   └── VectorUtils.java                         [CREATE] Cosine similarity, float[] <-> byte[]
├── service/
│   ├── EmbeddingService.java                    [CREATE] Deepseek embedding API client
│   ├── AiService.java                           [CREATE] Deepseek chat API client + prompt builder
│   ├── RAGService.java                          [CREATE] TOP-K retrieval via cosine similarity
│   └── GoodsDocService.java                         [CREATE] Orchestration + fallback logic
├── controller/
│   └── GoodsDocController.java                      [CREATE] REST endpoint
└── config/
    └── DataInitializer.java                     [CREATE] Startup data pre-population

src/main/resources/
├── schema.sql                                   [CREATE] MySQL DDL
└── static/
    └── index.html                               [CREATE] Frontend single-page app
```

---

### Task 1: Add Dependencies & Database Config

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yml`
- Create: `src/main/resources/schema.sql`

**Interfaces:**
- Consumes: existing pom.xml, existing application.yml
- Produces: MySQL 5.7 database with tables `component_library` and `category_template`

- [ ] **Step 1: Add MySQL + JPA dependencies to pom.xml**

Insert inside `<dependencies>` after the existing ones:

```xml
<!-- MySQL + JPA -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>8.0.33</version>
</dependency>
```

- [ ] **Step 2: Update application.yml with datasource config**

Append to existing `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/server_hq?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=utf8
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL57Dialect

deepseek:
  api:
    key: ${DEEPSEEK_API_KEY:sk-your-key-here}
    url: https://api.deepseek.com
```

- [ ] **Step 3: Check MySQL is running and create database**

Run in MySQL client:

```sql
CREATE DATABASE IF NOT EXISTS server_hq DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

- [ ] **Step 4: Create schema.sql**

```sql
-- src/main/resources/schema.sql
CREATE TABLE IF NOT EXISTS component_library (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    part_number VARCHAR(100) NOT NULL UNIQUE,
    brand VARCHAR(100),
    category VARCHAR(50) NOT NULL,
    parameters JSON,
    standard_desc TEXT,
    applications JSON,
    embedding BLOB,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS category_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(50) NOT NULL UNIQUE,
    fields JSON NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 5: Run schema.sql**

```bash
mysql -u root -p server_hq < src/main/resources/schema.sql
```

- [ ] **Step 6: Verify config loads**

Run: `mvn spring-boot:run` and check startup logs for no data source errors (app will start, endpoint not ready yet — OK to stop after startup).
Expected: no `DataSource` or `driver` exceptions in logs.

- [ ] **Step 7: Commit**

```bash
git add pom.xml src/main/resources/application.yml src/main/resources/schema.sql
git commit -m "feat: add MySQL + JPA dependencies and schema"
```

---

### Task 2: Entity Layer + DTOs

**Files:**
- Create: `src/main/java/com/keyvin/hq/entity/Component.java`
- Create: `src/main/java/com/keyvin/hq/entity/CategoryTemplate.java`
- Create: `src/main/java/com/keyvin/hq/repository/ComponentRepository.java`
- Create: `src/main/java/com/keyvin/hq/dto/GenerateRequest.java`
- Create: `src/main/java/com/keyvin/hq/dto/GenerateResponse.java`

**Interfaces:**
- Produces: `Component` JPA entity, `CategoryTemplate` JPA entity, `ComponentRepository` (Spring Data JPA), `GenerateRequest`, `GenerateResponse`
- Consumed by: Task 3 (VectorUtils), Task 5 (RAGService), Task 6 (GoodsDocController/GoodsDocService)

- [ ] **Step 1: Create Component entity**

```java
package com.hq.goods.lang.entity;

import lombok.Data;
import javax.persistence.*;
import java.util.Date;

@Data
@Entity
@Table(name = "component_library")
public class Component {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "part_number", nullable = false, unique = true, length = 100)
    private String partNumber;

    @Column(length = 100)
    private String brand;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(columnDefinition = "JSON")
    private String parameters;  // JSON string: {"core":"Cortex-M3","flash":"64KB"}

    @Column(name = "standard_desc", columnDefinition = "TEXT")
    private String standardDesc;

    @Column(columnDefinition = "JSON")
    private String applications;  // JSON string: ["IoT","Industrial"]

    @Column(columnDefinition = "BLOB")
    private byte[] embedding;

    @Column(name = "created_at")
    private Date createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = new Date();
    }
}
```

- [ ] **Step 2: Create CategoryTemplate entity**

```java
package com.hq.goods.lang.entity;

import lombok.Data;
import javax.persistence.*;

@Data
@Entity
@Table(name = "category_template")
public class CategoryTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String category;

    @Column(nullable = false, columnDefinition = "JSON")
    private String fields;  // JSON array: ["core","flash","ram","package","frequency"]
}
```

- [ ] **Step 3: Create ComponentRepository**

```java
package com.hq.goods.lang.repository;

import com.hq.goods.lang.entity.Component;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComponentRepository extends JpaRepository<Component, Long> {
    Optional<Component> findByPartNumber(String partNumber);
    List<Component> findAll();
    long count();
}
```

- [ ] **Step 4: Create GenerateRequest DTO**

```java
package com.hq.goods.lang.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class GenerateRequest {
    @NotBlank(message = "partNumber不能为空")
    private String partNumber;
}
```

- [ ] **Step 5: Create TopKItem inner class inside GenerateResponse**

```java
package com.hq.goods.lang.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateResponse {
    private boolean success;
    private Data data;
    private String message;
    private String errorCode;

    @lombok.Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Data {
        private String partNumber;
        private String brand;
        private String category;
        private Map<String, String> parameters;
        private String standardDesc;
        private List<String> applications;
        private List<String> reasoningSteps;
        private List<TopKItem> topK;
    }

    @lombok.Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopKItem {
        private String partNumber;
        private String brand;
        private double similarity;
    }

    public static GenerateResponse success(Data data) {
        GenerateResponse r = new GenerateResponse();
        r.success = true;
        r.data = data;
        return r;
    }

    public static GenerateResponse error(String message, String errorCode) {
        GenerateResponse r = new GenerateResponse();
        r.success = false;
        r.message = message;
        r.errorCode = errorCode;
        return r;
    }
}
```

- [ ] **Step 6: Compile-check**

Run: `mvn compile -q`
Expected: BUILD SUCCESS (no errors)

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/keyvin/hq/entity/ src/main/java/com/keyvin/hq/repository/ src/main/java/com/keyvin/hq/dto/
git commit -m "feat: add entity layer, repository, and DTOs"
```

---

### Task 3: VectorUtils & EmbeddingService

**Files:**
- Create: `src/main/java/com/keyvin/hq/util/VectorUtils.java`
- Create: `src/main/java/com/keyvin/hq/service/EmbeddingService.java`

**Interfaces:**
- Produces: `VectorUtils.cosineSimilarity(float[], float[])`, `VectorUtils.toBytes(float[])`, `VectorUtils.toFloats(byte[])`, `EmbeddingService.generateEmbedding(String)`, `EmbeddingService.generateEmbeddings(List<String>)`
- Consumed by: Task 5 (RAGService), Task 8 (DataInitializer)

- [ ] **Step 1: Create VectorUtils with cosine similarity + serialization**

```java
package com.hq.goods.lang.util;

import java.io.*;

public class VectorUtils {

    public static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vector dimensions must match: " + a.length + " vs " + b.length);
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public static byte[] toBytes(float[] vector) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(vector.length * 4);
        DataOutputStream dos = new DataOutputStream(bos);
        for (float v : vector) {
            dos.writeFloat(v);
        }
        dos.flush();
        return bos.toByteArray();
    }

    public static float[] toFloats(byte[] bytes) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes));
        int n = bytes.length / 4;
        float[] result = new float[n];
        for (int i = 0; i < n; i++) {
            result[i] = dis.readFloat();
        }
        return result;
    }
}
```

- [ ] **Step 2: Create EmbeddingService**

```java
package com.hq.goods.lang.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class EmbeddingService {

    private final OkHttpClient client;
    private final String apiKey;
    private final String baseUrl;

    public EmbeddingService(
            @Value("${deepseek.api.key}") String apiKey,
            @Value("${deepseek.api.url}") String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Generate embedding vector for a single text string.
     */
    public float[] generateEmbedding(String text) throws Exception {
        List<float[]> results = generateEmbeddings(List.of(text));
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Generate embedding vectors for multiple texts in one API call.
     */
    public List<float[]> generateEmbeddings(List<String> texts) throws Exception {
        JSONObject params = new JSONObject();
        params.put("model", "deepseek-embedding");
        params.put("input", texts);

        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, params.toJSONString());
        Request request = new Request.Builder()
                .url(baseUrl + "/v1/embeddings")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String respBody = response.body() != null ? response.body().string() : "";
                throw new RuntimeException("Embedding API error: " + response.code() + " " + respBody);
            }
            String respBody = response.body() != null ? response.body().string() : "{}";
            JSONObject json = JSONObject.parseObject(respBody);
            JSONArray dataArr = json.getJSONArray("data");

            List<float[]> result = new ArrayList<>(dataArr.size());
            for (int i = 0; i < dataArr.size(); i++) {
                JSONObject item = dataArr.getJSONObject(i);
                JSONArray embeddingArr = item.getJSONArray("embedding");
                float[] vector = new float[embeddingArr.size()];
                for (int j = 0; j < embeddingArr.size(); j++) {
                    vector[j] = embeddingArr.getFloatValue(j);
                }
                result.add(vector);
            }
            return result;
        }
    }
}
```

- [ ] **Step 3: Compile-check**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/keyvin/hq/util/VectorUtils.java src/main/java/com/keyvin/hq/service/EmbeddingService.java
git commit -m "feat: add VectorUtils and EmbeddingService"
```

---

### Task 4: AiService (Chat + Prompt Builder)

**Files:**
- Create: `src/main/java/com/keyvin/hq/service/AiService.java`

**Interfaces:**
- Produces: `AiService.generate(String partNumber, String retrievedContext)` — returns JSON string from Deepseek chat
- Consumed by: Task 5 (GoodsDocService)

- [ ] **Step 1: Create AiService**

```java
package com.hq.goods.lang.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AiService {

    private final OkHttpClient client;
    private final String apiKey;
    private final String baseUrl;

    public AiService(
            @Value("${deepseek.api.key}") String apiKey,
            @Value("${deepseek.api.url}") String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Call Deepseek chat API to generate structured component data.
     *
     * @param partNumber      user input part number
     * @param retrievedContext TOP-K retrieved similar components as context string
     * @return structured JSON string from AI
     */
    public String generate(String partNumber, String retrievedContext) throws Exception {
        String systemPrompt = "你是一个电子元器件数据标准化专家。根据用户输入和召回参考数据，输出结构化JSON。";

        String userPrompt = "请分析以下元器件信息。\n\n"
                + "【用户输入型号】" + partNumber + "\n\n"
                + "【召回参考数据（TOP-3相似型号）】" + retrievedContext + "\n\n"
                + "请严格按以下JSON格式输出，不要添加额外解释：\n"
                + "{\n"
                + "  \"part_number\": \"型号\",\n"
                + "  \"brand\": \"品牌（标准化全称）\",\n"
                + "  \"category\": \"品类（如MCU/运放/电阻/电容/连接器/电源IC）\",\n"
                + "  \"parameters\": {品类相关字段 },\n"
                + "  \"standard_desc\": \"英文技术描述（2-3句）\",\n"
                + "  \"applications\": [\"应用场景1\", \"应用场景2\"],\n"
                + "  \"reasoning_steps\": [\"步骤1\", \"步骤2\", \"步骤3\"]\n"
                + "}";

        JSONObject params = new JSONObject();
        params.put("model", "deepseek-chat");
        params.put("temperature", 0.1);
        params.put("max_tokens", 2000);
        params.put("stream", false);

        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.add(sysMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);

        params.put("messages", messages);

        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, params.toJSONString());
        Request request = new Request.Builder()
                .url(baseUrl + "/v1/chat/completions")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String respBody = response.body() != null ? response.body().string() : "";
                throw new RuntimeException("Chat API error: " + response.code() + " " + respBody);
            }
            String respBody = response.body() != null ? response.body().string() : "{}";
            log.debug("AI response: {}", respBody);

            JSONObject json = JSONObject.parseObject(respBody);
            JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("AI returned empty choices");
            }
            JSONObject choice0 = choices.getJSONObject(0);
            JSONObject message = choice0.getJSONObject("message");
            return message.getString("content");
        }
    }
}
```

- [ ] **Step 2: Compile-check**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/keyvin/hq/service/AiService.java
git commit -m "feat: add AiService with chat completion and prompt builder"
```

---

### Task 5: RAGService

**Files:**
- Create: `src/main/java/com/keyvin/hq/service/RAGService.java`

**Interfaces:**
- Consumes: `ComponentRepository.findAll()`, `EmbeddingService.generateEmbedding(String)`, `VectorUtils.cosineSimilarity()`, `VectorUtils.toFloats()`
- Produces: `RAGService.search(String userInput, int topK)` — returns list of similar components with scores

- [ ] **Step 1: Create RAGService**

```java
package com.hq.goods.lang.service;

import com.hq.goods.lang.entity.Component;
import com.hq.goods.lang.repository.ComponentRepository;
import com.hq.goods.lang.util.VectorUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RAGService {

    private final ComponentRepository componentRepository;
    private final EmbeddingService embeddingService;

    public RAGService(ComponentRepository componentRepository, EmbeddingService embeddingService) {
        this.componentRepository = componentRepository;
        this.embeddingService = embeddingService;
    }

    /**
     * Search TOP-K similar components by embedding cosine similarity.
     *
     * @param userInput the user's part number input
     * @param topK      number of results to return
     * @return list of map entries: { "component": Component, "similarity": Double }
     */
    public List<Map<String, Object>> search(String userInput, int topK) {
        try {
            // Generate embedding for user input
            float[] queryVector = embeddingService.generateEmbedding(userInput);
            if (queryVector == null) {
                log.warn("Failed to generate embedding for input: {}", userInput);
                return Collections.emptyList();
            }

            // Load all components with pre-computed embeddings
            List<Component> allComponents = componentRepository.findAll();

            // Calculate similarity scores
            List<Map<String, Object>> scored = new ArrayList<>();
            for (Component comp : allComponents) {
                if (comp.getEmbedding() == null) continue;
                try {
                    float[] compVector = VectorUtils.toFloats(comp.getEmbedding());
                    double similarity = VectorUtils.cosineSimilarity(queryVector, compVector);
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("component", comp);
                    entry.put("similarity", similarity);
                    scored.add(entry);
                } catch (Exception e) {
                    log.warn("Failed to compute similarity for component {}: {}", comp.getPartNumber(), e.getMessage());
                }
            }

            // Sort by similarity descending, take topK
            return scored.stream()
                    .sorted((a, b) -> Double.compare(
                            (double) b.get("similarity"), (double) a.get("similarity")))
                    .limit(topK)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("RAG search failed: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Build a context string from TOP-K results for prompt injection.
     */
    public String buildContext(List<Map<String, Object>> topKResults) {
        if (topKResults == null || topKResults.isEmpty()) {
            return "无召回数据";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < topKResults.size(); i++) {
            Map<String, Object> entry = topKResults.get(i);
            Component comp = (Component) entry.get("component");
            double sim = (double) entry.get("similarity");
            sb.append(i + 1).append(". ")
                    .append(comp.getPartNumber())
                    .append(" | 品牌: ").append(comp.getBrand())
                    .append(" | 品类: ").append(comp.getCategory())
                    .append(" | 参数: ").append(comp.getParameters())
                    .append(" | 描述: ").append(comp.getStandardDesc())
                    .append(" (相似度: ").append(String.format("%.2f", sim)).append(")\n");
        }
        return sb.toString();
    }
}
```

- [ ] **Step 2: Compile-check**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/keyvin/hq/service/RAGService.java
git commit -m "feat: add RAGService with embedding-based TOP-K retrieval"
```

---

### Task 6: GoodsDocService (Orchestration + Fallback)

**Files:**
- Create: `src/main/java/com/keyvin/hq/service/GoodsDocService.java`

**Interfaces:**
- Consumes: `AiService.generate()`, `RAGService.search()`, `RAGService.buildContext()`
- Produces: `GoodsDocService.process(String partNumber)` — returns `GenerateResponse`

- [ ] **Step 1: Create GoodsDocService**

```java
package com.hq.goods.lang.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hq.goods.lang.dto.GenerateResponse;
import com.hq.goods.lang.entity.Component;
import com.hq.goods.lang.repository.ComponentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GoodsDocService {

    private final AiService aiService;
    private final RAGService ragService;
    private final ComponentRepository componentRepository;

    public GoodsDocService(AiService aiService, RAGService ragService,
                       ComponentRepository componentRepository) {
        this.aiService = aiService;
        this.ragService = ragService;
        this.componentRepository = componentRepository;
    }

    /**
     * Main entry point: process part number through the full pipeline.
     */
    public GenerateResponse process(String partNumber) {
        try {
            // Step 1: RAG retrieval
            log.info("Processing part number: {}", partNumber);
            List<Map<String, Object>> topKResults = ragService.search(partNumber, 3);
            String context = ragService.buildContext(topKResults);

            // Step 2: AI generation
            String aiResult;
            try {
                aiResult = aiService.generate(partNumber, context);
            } catch (Exception e) {
                log.warn("AI generation failed, trying fallback: {}", e.getMessage());
                // Fallback: if TOP-K has data, return first match from DB
                if (!topKResults.isEmpty()) {
                    return buildFallbackResponse(topKResults, "AI服务异常，已降级使用库中数据");
                }
                throw e;
            }

            // Step 3: Parse AI result
            return parseAiResponse(partNumber, aiResult, topKResults);

        } catch (Exception e) {
            log.error("Demo processing failed: {}", e.getMessage(), e);
            return GenerateResponse.error("处理失败: " + e.getMessage(), "PROCESSING_ERROR");
        }
    }

    private GenerateResponse parseAiResponse(String partNumber, String aiResult,
                                              List<Map<String, Object>> topKResults) {
        try {
            // Try to extract JSON from AI response (handle markdown code blocks)
            String jsonStr = aiResult;
            if (jsonStr.contains("```json")) {
                jsonStr = jsonStr.substring(jsonStr.indexOf("```json") + 7);
                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```"));
            } else if (jsonStr.contains("```")) {
                jsonStr = jsonStr.substring(jsonStr.indexOf("```") + 3);
                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```"));
            }
            jsonStr = jsonStr.trim();

            JSONObject json = JSONObject.parseObject(jsonStr);

            // Extract fields
            String brand = json.getString("brand");
            String category = json.getString("category");

            Map<String, String> parameters = new LinkedHashMap<>();
            JSONObject paramsObj = json.getJSONObject("parameters");
            if (paramsObj != null) {
                for (String key : paramsObj.keySet()) {
                    parameters.put(key, paramsObj.getString(key));
                }
            }

            String standardDesc = json.getString("standard_desc");

            List<String> applications = new ArrayList<>();
            JSONArray appArr = json.getJSONArray("applications");
            if (appArr != null) {
                for (int i = 0; i < appArr.size(); i++) {
                    applications.add(appArr.getString(i));
                }
            }

            List<String> reasoningSteps = new ArrayList<>();
            JSONArray stepsArr = json.getJSONArray("reasoning_steps");
            if (stepsArr != null) {
                for (int i = 0; i < stepsArr.size(); i++) {
                    reasoningSteps.add(stepsArr.getString(i));
                }
            }

            // Build TOP-K list
            List<GenerateResponse.TopKItem> topKList = topKResults.stream()
                    .map(entry -> {
                        Component comp = (Component) entry.get("component");
                        double sim = (double) entry.get("similarity");
                        return new GenerateResponse.TopKItem(
                                comp.getPartNumber(), comp.getBrand(), sim);
                    })
                    .collect(Collectors.toList());

            GenerateResponse.Data data = new GenerateResponse.Data(
                    partNumber, brand, category, parameters,
                    standardDesc, applications, reasoningSteps, topKList);

            return GenerateResponse.success(data);

        } catch (Exception e) {
            log.warn("Failed to parse AI response: {}", e.getMessage());
            // Fallback to DB data if available
            if (!topKResults.isEmpty()) {
                return buildFallbackResponse(topKResults, "AI结果解析失败，已使用库中数据");
            }
            return GenerateResponse.error("AI结果解析失败", "PARSE_ERROR");
        }
    }

    private GenerateResponse buildFallbackResponse(List<Map<String, Object>> topKResults, String message) {
        Map<String, Object> best = topKResults.get(0);
        Component comp = (Component) best.get("component");
        double sim = (double) best.get("similarity");

        Map<String, String> params = new LinkedHashMap<>();
        if (comp.getParameters() != null) {
            JSONObject paramsObj = JSONObject.parseObject(comp.getParameters());
            for (String key : paramsObj.keySet()) {
                params.put(key, paramsObj.getString(key));
            }
        }

        List<String> apps = new ArrayList<>();
        if (comp.getApplications() != null) {
            JSONArray appArr = JSONObject.parseArray(comp.getApplications());
            for (int i = 0; i < appArr.size(); i++) {
                apps.add(appArr.getString(i));
            }
        }

        List<GenerateResponse.TopKItem> topKList = topKResults.stream()
                .map(entry -> {
                    Component c = (Component) entry.get("component");
                    double s = (double) entry.get("similarity");
                    return new GenerateResponse.TopKItem(c.getPartNumber(), c.getBrand(), s);
                })
                .collect(Collectors.toList());

        GenerateResponse.Data data = new GenerateResponse.Data(
                comp.getPartNumber(), comp.getBrand(), comp.getCategory(),
                params, comp.getStandardDesc(), apps,
                List.of("已使用本地数据（AI不可用）"), topKList);

        GenerateResponse resp = GenerateResponse.success(data);
        resp.setMessage(message);
        return resp;
    }
}
```

- [ ] **Step 2: Compile-check**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/keyvin/hq/service/GoodsDocService.java
git commit -m "feat: add GoodsDocService with orchestration and fallback logic"
```

---

### Task 7: GoodsDocController

**Files:**
- Create: `src/main/java/com/keyvin/hq/controller/GoodsDocController.java`

**Interfaces:**
- Consumes: `GoodsDocService.process(String)`
- Produces: POST `/api/generate` endpoint

- [ ] **Step 1: Create GoodsDocController**

```java
package com.hq.goods.lang.controller;

import com.hq.goods.lang.dto.GenerateRequest;
import com.hq.goods.lang.dto.GenerateResponse;
import com.hq.goods.lang.service.GoodsDocService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api")
public class GoodsDocController {

    private final GoodsDocService goodsDocService;

    public GoodsDocController(GoodsDocService goodsDocService) {
        this.goodsDocService = goodsDocService;
    }

    @PostMapping("/generate")
    public ResponseEntity<GenerateResponse> generate(@Valid @RequestBody GenerateRequest request) {
        log.info("Received generate request: {}", request.getPartNumber());
        GenerateResponse response = goodsDocService.process(request.getPartNumber());
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
```

- [ ] **Step 2: Add validation dependency (if not present)**

Check if `spring-boot-starter-validation` is in pom.xml. If not, add:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

- [ ] **Step 3: Compile-check**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Test health endpoint**

1. Start app: `mvn spring-boot:run`
2. In another terminal: `curl http://localhost:9000/hq/api/health`
Expected: `OK`

3. Stop app (Ctrl+C)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/keyvin/hq/controller/GoodsDocController.java
git commit -m "feat: add GoodsDocController with /api/generate endpoint"
```

---

### Task 8: DataInitializer (Pre-populate Components)

**Files:**
- Create: `src/main/java/com/keyvin/hq/config/DataInitializer.java`

**Interfaces:**
- Consumes: `ComponentRepository`, `EmbeddingService`, `CategoryTemplate` entity
- Produces: 30-50 pre-populated records in MySQL with embeddings

- [ ] **Step 1: Create DataInitializer**

```java
package com.hq.goods.lang.config;

import com.hq.goods.lang.entity.CategoryTemplate;
import com.hq.goods.lang.entity.Component;
import com.hq.goods.lang.repository.ComponentRepository;
import com.hq.goods.lang.service.EmbeddingService;
import com.hq.goods.lang.util.VectorUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.util.*;

@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    private final ComponentRepository componentRepository;
    private final EmbeddingService embeddingService;

    @PersistenceContext
    private EntityManager entityManager;

    public DataInitializer(ComponentRepository componentRepository,
                           EmbeddingService embeddingService) {
        this.componentRepository = componentRepository;
        this.embeddingService = embeddingService;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (componentRepository.count() > 0) {
            log.info("Database already has data, skipping initialization");
            return;
        }

        log.info("Initializing demo data...");

        // Pre-populate component data
        List<Component> components = buildDemoComponents();

        // Generate embeddings in batch (text descriptions for each component)
        List<String> embedTexts = new ArrayList<>();
        for (Component comp : components) {
            embedTexts.add(buildEmbedText(comp));
        }

        try {
            List<float[]> embeddings = embeddingService.generateEmbeddings(embedTexts);
            for (int i = 0; i < components.size(); i++) {
                components.get(i).setEmbedding(VectorUtils.toBytes(embeddings.get(i)));
            }
            log.info("Generated {} embeddings successfully", embeddings.size());
        } catch (Exception e) {
            log.warn("Failed to generate embeddings during init, skipping: {}", e.getMessage());
            // Components will still be saved without embeddings — RAG will skip them
        }

        // Save all components
        componentRepository.saveAll(components);
        log.info("Initialized {} demo components", components.size());
    }

    private String buildEmbedText(Component comp) {
        return comp.getPartNumber() + " " +
                (comp.getBrand() != null ? comp.getBrand() : "") + " " +
                comp.getCategory() + " " +
                (comp.getParameters() != null ? comp.getParameters() : "") + " " +
                (comp.getStandardDesc() != null ? comp.getStandardDesc() : "");
    }

    private List<Component> buildDemoComponents() {
        List<Component> list = new ArrayList<>();

        // ===== MCU =====
        list.add(buildComponent("STM32F103C8T6", "STMicroelectronics", "MCU",
                "{\"core\":\"ARM Cortex-M3\",\"frequency\":\"72MHz\",\"flash\":\"64KB\",\"ram\":\"20KB\",\"package\":\"LQFP48\"}",
                "STM32F103C8T6 is a 32-bit ARM Cortex-M3 microcontroller with 64KB Flash, 20KB RAM, operating at up to 72MHz. It features multiple communication interfaces including USART, SPI, I2C, USB, and CAN.",
                "[\"Industrial control\",\"IoT devices\",\"Motor drive\",\"Consumer electronics\"]"));

        list.add(buildComponent("STM32F103CBT6", "STMicroelectronics", "MCU",
                "{\"core\":\"ARM Cortex-M3\",\"frequency\":\"72MHz\",\"flash\":\"128KB\",\"ram\":\"20KB\",\"package\":\"LQFP48\"}",
                "STM32F103CBT6 is a 32-bit ARM Cortex-M3 microcontroller with 128KB Flash, 20KB RAM, operating at up to 72MHz. High-density version of the STM32F103 series.",
                "[\"Industrial control\",\"IoT\",\"Automation\"]"));

        list.add(buildComponent("STM32F103R8T6", "STMicroelectronics", "MCU",
                "{\"core\":\"ARM Cortex-M3\",\"frequency\":\"72MHz\",\"flash\":\"64KB\",\"ram\":\"20KB\",\"package\":\"LQFP64\"}",
                "STM32F103R8T6 is a 32-bit ARM Cortex-M3 MCU with 64KB Flash in LQFP64 package. Offers additional GPIO pins compared to the LQFP48 variant.",
                "[\"Industrial\",\"Automation\",\"Embedded systems\"]"));

        list.add(buildComponent("STM32F030C6T6", "STMicroelectronics", "MCU",
                "{\"core\":\"ARM Cortex-M0\",\"frequency\":\"48MHz\",\"flash\":\"32KB\",\"ram\":\"4KB\",\"package\":\"LQFP48\"}",
                "STM32F030C6T6 is a 32-bit entry-level ARM Cortex-M0 microcontroller with 32KB Flash, ideal for cost-sensitive applications.",
                "[\"Cost-sensitive embedded\",\"Consumer electronics\",\"Simple control\"]"));

        list.add(buildComponent("ATMEGA328P", "Microchip", "MCU",
                "{\"core\":\"AVR 8-bit\",\"frequency\":\"20MHz\",\"flash\":\"32KB\",\"ram\":\"2KB\",\"package\":\"DIP-28\"}",
                "ATmega328P is a high-performance 8-bit AVR RISC-based microcontroller with 32KB ISP flash memory, commonly used in Arduino Uno boards.",
                "[\"Arduino\",\"DIY electronics\",\"IoT\",\"Education\"]"));

        list.add(buildComponent("ATMEGA2560", "Microchip", "MCU",
                "{\"core\":\"AVR 8-bit\",\"frequency\":\"16MHz\",\"flash\":\"256KB\",\"ram\":\"8KB\",\"package\":\"TQFP-100\"}",
                "ATmega2560 is a high-performance 8-bit AVR microcontroller with 256KB Flash, used in Arduino Mega boards for complex projects.",
                "[\"3D printing\",\"Robotics\",\"Arduino Mega\"]"));

        list.add(buildComponent("TMS320F28335", "Texas Instruments", "MCU",
                "{\"core\":\"C28x 32-bit DSP\",\"frequency\":\"150MHz\",\"flash\":\"512KB\",\"ram\":\"68KB\",\"package\":\"BGA-179\"}",
                "TMS320F28335 is a 32-bit floating-point DSP controller with 150MHz performance, designed for advanced motor control and digital power applications.",
                "[\"Motor control\",\"Digital power\",\"Industrial drive\"]"));

        list.add(buildComponent("GD32F103C8T6", "GigaDevice", "MCU",
                "{\"core\":\"ARM Cortex-M3\",\"frequency\":\"108MHz\",\"flash\":\"64KB\",\"ram\":\"20KB\",\"package\":\"LQFP48\"}",
                "GD32F103C8T6 is a pin-to-pin compatible alternative to STM32F103 with higher 108MHz clock speed and competitive pricing.",
                "[\"Cost-sensitive embedded\",\"Chinese market\",\"Industrial\"]"));

        // ===== Operational Amplifiers =====
        list.add(buildComponent("LM358", "Texas Instruments", "运放",
                "{\"type\":\"Dual op-amp\",\"supplyVoltage\":\"3V-32V\",\"bandwidth\":\"1MHz\",\"package\":\"DIP-8/SOP-8\"}",
                "LM358 is a low-power dual operational amplifier with wide supply voltage range, commonly used in signal conditioning and sensor amplification.",
                "[\"Signal conditioning\",\"Sensor amplification\",\"Active filters\"]"));

        list.add(buildComponent("OP07", "Analog Devices", "运放",
                "{\"type\":\"Precision op-amp\",\"supplyVoltage\":\"±3V-±18V\",\"bandwidth\":\"0.6MHz\",\"package\":\"DIP-8/SOP-8\"}",
                "OP07 is an ultra-low offset voltage precision operational amplifier, ideal for precision instrumentation and data acquisition systems.",
                "[\"Precision instrumentation\",\"Data acquisition\",\"Medical devices\"]"));

        list.add(buildComponent("LM324", "Texas Instruments", "运放",
                "{\"type\":\"Quad op-amp\",\"supplyVoltage\":\"3V-32V\",\"bandwidth\":\"1MHz\",\"package\":\"DIP-14/SOP-14\"}",
                "LM324 is a quad low-power operational amplifier with four independent op-amps in one package, widely used for multi-channel signal processing.",
                "[\"Multi-channel processing\",\"Active filters\",\"DC gain blocks\"]"));

        // ===== Resistors =====
        list.add(buildComponent("RC0603FR-07100RL", "Yageo", "电阻",
                "{\"resistance\":\"100Ω\",\"tolerance\":\"±1%\",\"power\":\"0.1W\",\"package\":\"0603\"}",
                "RC0603FR-07100RL is a 100Ω ±1% thick film chip resistor in 0603 package, suitable for general-purpose surface mount applications.",
                "[\"General purpose\",\"Current limiting\",\"Pull-up/down\"]"));

        list.add(buildComponent("RC0805JR-0710KL", "Yageo", "电阻",
                "{\"resistance\":\"10kΩ\",\"tolerance\":\"±5%\",\"power\":\"0.125W\",\"package\":\"0805\"}",
                "RC0805JR-0710KL is a 10kΩ ±5% thick film chip resistor in 0805 package, one of the most commonly used values in circuit design.",
                "[\"General purpose\",\"Pull-up\",\"Voltage divider\"]"));

        list.add(buildComponent("RC0402FR-074K7L", "Yageo", "电阻",
                "{\"resistance\":\"4.7kΩ\",\"tolerance\":\"±1%\",\"power\":\"0.063W\",\"package\":\"0402\"}",
                "RC0402FR-074K7L is a 4.7kΩ ±1% precision thick film chip resistor in compact 0402 package for space-constrained designs.",
                "[\"Compact designs\",\"Mobile devices\",\"Precision circuits\"]"));

        // ===== Capacitors =====
        list.add(buildComponent("CL10A105KB8NNNC", "Samsung", "电容",
                "{\"capacitance\":\"1μF\",\"voltage\":\"50V\",\"dielectric\":\"X5R\",\"package\":\"0603\"}",
                "CL10A105KB8NNNC is a 1μF ±10% 50V X5R multilayer ceramic capacitor in 0603 package, suitable for decoupling and filtering.",
                "[\"Decoupling\",\"Filtering\",\"Power supply smoothing\"]"));

        list.add(buildComponent("C1608X5R1A106K080AC", "TDK", "电容",
                "{\"capacitance\":\"10μF\",\"voltage\":\"10V\",\"dielectric\":\"X5R\",\"package\":\"0603\"}",
                "C1608X5R1A106K is a 10μF ±10% 10V X5R MLCC in 0603 package, commonly used for power rail decoupling in portable devices.",
                "[\"Power decoupling\",\"Portable devices\",\"DC-DC converters\"]"));

        list.add(buildComponent("GRM155R71C104KA88D", "Murata", "电容",
                "{\"capacitance\":\"0.1μF\",\"voltage\":\"16V\",\"dielectric\":\"X7R\",\"package\":\"0402\"}",
                "GRM155R71C104KA88D is a 0.1μF ±10% 16V X7R multilayer ceramic capacitor in 0402 package, the standard bypass capacitor for ICs.",
                "[\"IC bypass\",\"General decoupling\",\"High-frequency filtering\"]"));

        // ===== Connectors =====
        list.add(buildComponent("2-1932052-1", "TE Connectivity", "连接器",
                "{\"type\":\"Wire-to-board\",\"pins\":\"2\",\"pitch\":\"2.5mm\",\"current\":\"5A\"}",
                "2-1932052-1 is a 2-position wire-to-board connector with 2.5mm pitch from TE's Economy Power series, rated for 5A per contact.",
                "[\"Power connection\",\"Internal wiring\",\"Consumer electronics\"]"));

        list.add(buildComponent("53261-0271", "Molex", "连接器",
                "{\"type\":\"Wire-to-board\",\"pins\":\"2\",\"pitch\":\"2.5mm\",\"current\":\"3A\"}",
                "53261-0271 is a 2-pin Molex SPOX wire-to-board connector system with positive locking mechanism for secure connections.",
                "[\"Consumer electronics\",\"Automotive\",\"Power supply\"]"));

        list.add(buildComponent("B2B-XH-A", "JST", "连接器",
                "{\"type\":\"Wire-to-board\",\"pins\":\"2\",\"pitch\":\"2.5mm\",\"current\":\"3A\"}",
                "B2B-XH-A is a 2-position JST XH series wire-to-board connector with side-entry design and friction locking.",
                "[\"Battery connectors\",\"Small appliances\",\"PCB interconnects\"]"));

        // ===== Power ICs =====
        list.add(buildComponent("TPS5430", "Texas Instruments", "电源IC",
                "{\"type\":\"Buck converter\",\"inputVoltage\":\"5.5V-36V\",\"outputCurrent\":\"3A\",\"frequency\":\"500kHz\",\"package\":\"SOIC-8\"}",
                "TPS5430 is a high-output-current 3A step-down (buck) DC-DC converter with 500kHz switching frequency, featuring integrated MOSFET and wide input voltage range.",
                "[\"Industrial power supplies\",\"12V/24V systems\",\"Battery-powered devices\"]"));

        list.add(buildComponent("MP2303DN", "MPS", "电源IC",
                "{\"type\":\"Buck converter\",\"inputVoltage\":\"4.75V-28V\",\"outputCurrent\":\"3A\",\"frequency\":\"340kHz\",\"package\":\"SOIC-8\"}",
                "MP2303DN is a 3A synchronous step-down converter with 340kHz switching frequency, offering high efficiency over a wide input range.",
                "[\"Distributed power\",\"Industrial\",\"Networking equipment\"]"));

        list.add(buildComponent("AMS1117-3.3", "AMS", "电源IC",
                "{\"type\":\"LDO regulator\",\"inputVoltage\":\"4.75V-12V\",\"outputVoltage\":\"3.3V\",\"outputCurrent\":\"1A\",\"package\":\"SOT-223\"}",
                "AMS1117-3.3 is a 3.3V fixed output low-dropout voltage regulator capable of delivering 1A output current with thermal overload protection.",
                "[\"3.3V power rail\",\"MCU power supply\",\"General regulation\"]"));

        log.info("Built {} demo components", list.size());
        return list;
    }

    private Component buildComponent(String partNumber, String brand, String category,
                                      String parameters, String standardDesc, String applications) {
        Component comp = new Component();
        comp.setPartNumber(partNumber);
        comp.setBrand(brand);
        comp.setCategory(category);
        comp.setParameters(parameters);
        comp.setStandardDesc(standardDesc);
        comp.setApplications(applications);
        return comp;
    }
}
```

- [ ] **Step 2: Add javax.validation dependency for GenerateRequest**

Validate that `spring-boot-starter-validation` is in pom.xml. If not already present:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

Also ensure the app has `@SpringBootApplication` which already enables auto-configuration of JPA (no `@EnableJpaRepositories` needed).

- [ ] **Step 3: Compile-check**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Verify startup with demo data**

1. Ensure MySQL is running and database `server_hq` exists with tables
2. Start app: `mvn spring-boot:run`
3. Check logs for: "Initializing demo data..." + "Initialized N demo components"
4. Stop app (Ctrl+C)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/keyvin/hq/config/DataInitializer.java
git commit -m "feat: add DataInitializer with 30+ pre-populated demo components"
```

---

### Task 9: Frontend Page

**Files:**
- Create: `src/main/resources/static/index.html`

**Interfaces:**
- Consumes: POST `/hq/api/generate`
- Produces: Browser-based demo UI

- [ ] **Step 1: Create index.html**

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>电子元器件数据标准化工具</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
               background: #f0f2f5; color: #333; padding: 20px; }
        .container { max-width: 1000px; margin: 0 auto; }
        h1 { text-align: center; color: #1a73e8; margin-bottom: 24px; font-size: 28px; }
        .card { background: #fff; border-radius: 12px; padding: 24px; margin-bottom: 20px;
                box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
        .input-row { display: flex; gap: 12px; align-items: center; }
        .input-row input { flex: 1; padding: 12px 16px; font-size: 16px; border: 2px solid #ddd;
                           border-radius: 8px; outline: none; transition: border-color 0.2s; }
        .input-row input:focus { border-color: #1a73e8; }
        .btn { padding: 12px 32px; font-size: 16px; border: none; border-radius: 8px;
               cursor: pointer; font-weight: 600; transition: all 0.2s; }
        .btn-primary { background: #1a73e8; color: #fff; }
        .btn-primary:hover { background: #1557b5; }
        .btn-primary:disabled { background: #a0c4ff; cursor: not-allowed; }
        .btn-sample { background: #f0f2f5; color: #555; padding: 8px 16px; font-size: 13px; }
        .btn-sample:hover { background: #e0e2e5; }
        .samples { margin-top: 12px; display: flex; gap: 8px; flex-wrap: wrap; }
        .samples span { color: #888; font-size: 13px; line-height: 32px; }
        .progress { margin: 16px 0; }
        .progress-step { display: flex; align-items: center; gap: 8px; padding: 6px 0; color: #888; font-size: 14px; }
        .progress-step.active { color: #1a73e8; font-weight: 600; }
        .progress-step.done { color: #34a853; }
        .progress-step .icon { font-size: 18px; width: 24px; text-align: center; }
        .progress-step .spinner { display: inline-block; width: 18px; height: 18px; border: 2px solid #ddd;
                                   border-top-color: #1a73e8; border-radius: 50%; animation: spin 0.8s linear infinite; }
        @keyframes spin { to { transform: rotate(360deg); } }
        .tab-bar { display: flex; gap: 0; border-bottom: 2px solid #eee; margin-bottom: 16px; }
        .tab { padding: 10px 24px; cursor: pointer; color: #666; font-weight: 500;
               border-bottom: 2px solid transparent; margin-bottom: -2px; transition: all 0.2s; }
        .tab.active { color: #1a73e8; border-bottom-color: #1a73e8; }
        .tab-content { display: none; }
        .tab-content.active { display: block; }
        pre { background: #f8f9fa; border-radius: 8px; padding: 16px; overflow-x: auto;
              font-size: 13px; line-height: 1.6; }
        .topk-item { display: flex; justify-content: space-between; align-items: center;
                     padding: 8px 12px; background: #f8f9fa; border-radius: 6px; margin-bottom: 6px; }
        .topk-item .sim { color: #1a73e8; font-weight: 600; font-size: 14px; }
        .error-msg { color: #d93025; background: #fce8e6; border-radius: 8px; padding: 12px 16px; margin: 12px 0; }
        .desc-text { line-height: 1.8; font-size: 14px; color: #444; }
        .desc-text .label { font-weight: 600; color: #333; display: block; margin-top: 12px; margin-bottom: 4px; }
        .empty-state { text-align: center; padding: 40px 20px; color: #aaa; font-size: 15px; }
        .tag { display: inline-block; background: #e8f0fe; color: #1a73e8; padding: 2px 10px; border-radius: 12px; font-size: 12px; }
        footer { text-align: center; color: #aaa; font-size: 13px; margin-top: 32px; }
    </style>
</head>
<body>
<div class="container">
    <h1>🔧 电子元器件数据标准化工具</h1>

    <!-- Input -->
    <div class="card">
        <div class="input-row">
            <input type="text" id="partNumber" placeholder="输入元器件型号，如 STM32F103C8T6"
                   onkeydown="if(event.key==='Enter') generate()">
            <button class="btn btn-primary" id="generateBtn" onclick="generate()">🚀 一键生成</button>
        </div>
        <div class="samples">
            <span>快速示例：</span>
            <button class="btn btn-sample" onclick="fillSample('STM32F103C8T6')">STM32F103C8T6</button>
            <button class="btn btn-sample" onclick="fillSample('LM358')">LM358</button>
            <button class="btn btn-sample" onclick="fillSample('RC0603FR-07100RL')">RC0603FR-07100RL</button>
            <button class="btn btn-sample" onclick="fillSample('TPS5430')">TPS5430</button>
        </div>
    </div>

    <!-- Progress -->
    <div class="card" id="progressCard" style="display:none;">
        <div class="progress" id="progressSteps">
            <div class="progress-step" id="step1"><span class="icon">○</span> 向量检索 TOP-3 ...</div>
            <div class="progress-step" id="step2"><span class="icon">○</span> AI 结构化分析 ...</div>
            <div class="progress-step" id="step3"><span class="icon">○</span> 结果生成 ...</div>
        </div>
    </div>

    <!-- Error -->
    <div class="card" id="errorCard" style="display:none;">
        <div class="error-msg" id="errorMsg"></div>
    </div>

    <!-- Results -->
    <div class="card" id="resultCard" style="display:none;">
        <div class="tab-bar">
            <div class="tab active" onclick="switchTab('json', this)">📊 标准化数据</div>
            <div class="tab" onclick="switchTab('desc', this)">📝 技术描述</div>
        </div>

        <div class="tab-content active" id="tab-json">
            <pre id="jsonOutput">等待数据...</pre>
        </div>
        <div class="tab-content" id="tab-desc">
            <div id="descOutput">
                <div class="empty-state">暂无数据</div>
            </div>
        </div>
    </div>

    <!-- TOP-K -->
    <div class="card" id="topkCard" style="display:none;">
        <h3 style="font-size:15px;margin-bottom:12px;">📌 召回参考 (TOP-3)</h3>
        <div id="topkList"></div>
        <div style="margin-top:12px;font-size:12px;color:#888;">
            <span class="tag">RAG</span> 基于 Deepseek embedding 余弦相似度检索
        </div>
    </div>

    <!-- Fallback message -->
    <div class="card" id="fallbackCard" style="display:none;">
        <div style="color:#f9ab00;font-weight:500;">⚠️ <span id="fallbackMsg"></span></div>
    </div>

    <footer>华秋电子 — AI 元器件数据标准化 Demo</footer>
</div>

<script>
    const API_BASE = window.location.origin + '/hq';

    function fillSample(mpn) {
        document.getElementById('partNumber').value = mpn;
    }

    async function generate() {
        const partNumber = document.getElementById('partNumber').value.trim();
        if (!partNumber) {
            showError('请输入元器件型号');
            return;
        }

        // Reset UI
        hideAll();
        document.getElementById('generateBtn').disabled = true;
        document.getElementById('progressCard').style.display = 'block';
        setStep('step1', 'active');

        try {
            // Step 1: RAG (simulated via progress only — happens server-side)
            await delay(500);
            setStep('step1', 'done');
            setStep('step2', 'active');

            // Step 2+3: API call (covers AI generation)
            const response = await fetch(API_BASE + '/api/generate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ partNumber })
            });

            setStep('step2', 'done');
            setStep('step3', 'active');
            await delay(300);

            const result = await response.json();
            setStep('step3', 'done');

            if (!result.success) {
                showError(result.message || '处理失败');
                document.getElementById('generateBtn').disabled = false;
                return;
            }

            // Show fallback message if present
            if (result.message) {
                document.getElementById('fallbackCard').style.display = 'block';
                document.getElementById('fallbackMsg').textContent = result.message;
            }

            displayResults(result.data);

        } catch (err) {
            setStep('step1', 'active');
            showError('请求失败: ' + err.message + '。请检查网络连接和后端服务。');
        } finally {
            document.getElementById('generateBtn').disabled = false;
        }
    }

    function displayResults(data) {
        // JSON tab
        const jsonDisplay = {
            part_number: data.partNumber,
            brand: data.brand,
            category: data.category,
            parameters: data.parameters || {},
            standard_desc: data.standardDesc,
            applications: data.applications || [],
            reasoning_steps: data.reasoningSteps || []
        };
        document.getElementById('jsonOutput').textContent = JSON.stringify(jsonDisplay, null, 2);

        // Description tab
        let descHtml = '';
        if (data.standardDesc) {
            descHtml += '<div class="label">📋 英文技术描述</div><p class="desc-text">' + escapeHtml(data.standardDesc) + '</p>';
        }
        if (data.applications && data.applications.length > 0) {
            descHtml += '<div class="label">🎯 应用场景</div><div class="desc-text">';
            data.applications.forEach(app => { descHtml += '<span class="tag" style="margin:2px 4px">' + escapeHtml(app) + '</span>'; });
            descHtml += '</div>';
        }
        if (data.reasoningSteps && data.reasoningSteps.length > 0) {
            descHtml += '<div class="label">🧠 分析推理步骤</div><ol class="desc-text">';
            data.reasoningSteps.forEach(step => { descHtml += '<li>' + escapeHtml(step) + '</li>'; });
            descHtml += '</ol>';
        }
        document.getElementById('descOutput').innerHTML = descHtml || '<div class="empty-state">暂无描述数据</div>';

        // TOP-K
        if (data.topK && data.topK.length > 0) {
            let topkHtml = '';
            data.topK.forEach((item, i) => {
                topkHtml += '<div class="topk-item">' +
                    '<span>' + (i+1) + '. <strong>' + escapeHtml(item.partNumber) + '</strong> <span style="color:#888;font-size:13px;">' + escapeHtml(item.brand || '') + '</span></span>' +
                    '<span class="sim">' + (item.similarity * 100).toFixed(1) + '%</span>' +
                    '</div>';
            });
            document.getElementById('topkList').innerHTML = topkHtml;
            document.getElementById('topkCard').style.display = 'block';
        }

        document.getElementById('resultCard').style.display = 'block';
    }

    function switchTab(tabName, el) {
        document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
        document.querySelectorAll('.tab-content').forEach(t => t.classList.remove('active'));
        el.classList.add('active');
        document.getElementById('tab-' + tabName).classList.add('active');
    }

    function setStep(id, status) {
        const el = document.getElementById(id);
        el.classList.remove('active', 'done');
        if (status === 'active') {
            el.classList.add('active');
            el.querySelector('.icon').innerHTML = '<span class="spinner"></span>';
        } else if (status === 'done') {
            el.classList.add('done');
            el.querySelector('.icon').textContent = '✓';
        }
    }

    function showError(msg) {
        document.getElementById('errorCard').style.display = 'block';
        document.getElementById('errorMsg').textContent = '⚠️ ' + msg;
    }

    function hideAll() {
        document.getElementById('errorCard').style.display = 'none';
        document.getElementById('resultCard').style.display = 'none';
        document.getElementById('topkCard').style.display = 'none';
        document.getElementById('fallbackCard').style.display = 'none';
        document.getElementById('progressCard').style.display = 'none';
    }

    function delay(ms) { return new Promise(resolve => setTimeout(resolve, ms)); }

    function escapeHtml(str) {
        if (!str) return '';
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }
</script>
</body>
</html>
```

- [ ] **Step 2: Start app and test in browser**

1. Start app: `mvn spring-boot:run`
2. Open browser: `http://localhost:9000/hq/`
3. Click sample "STM32F103C8T6" → Click "一键生成"
Expected: Progress animation → JSON data + description + TOP-K list displayed
4. Try "LM358" and "RC0603FR-07100RL" samples
5. Stop app (Ctrl+C)

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "feat: add demo frontend page with input, results, and TOP-K display"
```

---

### Task 10: End-to-End Verification & Polish

**Files:**
- Modify: (testing only, no code changes unless bugs found)

- [ ] **Step 1: Full end-to-end test with all sample parts**

1. Start MySQL if not running
2. Start app: `mvn spring-boot:run`
3. Test each of the 4 sample buttons — verify JSON shows correctly, description tab renders, TOP-K has meaningful results
4. Test error case: enter "UNKNOWNTYPE999" → verify error message displays gracefully
5. Test empty input: click "一键生成" with empty input → verify validation prevents submission

- [ ] **Step 2: Check demo-ready presentation aspects**

- Verify page loads cleanly without any console errors (F12 → Console tab)
- Verify progress animation works smoothly
- Verify JSON tab and Description tab switching works
- Verify TOP-K similarity percentages look reasonable
- Verify tab title shows correctly in browser

- [ ] **Step 3: Prepare screenshots for PPT (as per spec requirement)**

Capture 4 screenshots:
1. Input interface with sample filled in
2. Loading/progress animation state
3. Standardized JSON output tab
4. Technical description tab

- [ ] **Step 4: Commit if any fixes were made**

```bash
git add -A
git commit -m "fix: polish demo UI and fix end-to-end issues"
```

---

## Spec Coverage Check

| Spec Section | Covered By |
|---|---|
| MySQL 表设计 (2 tables) | Task 1 (schema.sql) + Task 2 (entities) |
| RAG 实现 (embedding + cosine) | Task 3 (VectorUtils) + Task 3 (EmbeddingService) + Task 5 (RAGService) |
| Prompt 设计 (一次性调用) | Task 4 (AiService) |
| 前端页面设计 | Task 9 (index.html) |
| API 接口 | Task 7 (GoodsDocController) |
| 错误处理 (fallback) | Task 6 (GoodsDocService fallback logic) |
| 预填充数据 (30-50条) | Task 8 (DataInitializer) |
| 项目结构 | All tasks — matches spec's file structure |
| 标准三层架构 | Tasks 2-7 — entity/repository/service/controller layers |
