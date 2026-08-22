# 外贸商品信息标准化与多语言 SEO 智能生成系统 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 goods-doc-lang（Spring Boot 2.1.7 + MyBatis-Plus）中实现外贸元器件资料的后台工作台（解析/英文描述/多语言/SEO/保存/历史）与 9 个 `/api/doc` 接口，前端用 Vue3 CDN 静态页承载。

**Architecture:** 新增 `GoodsDocController → GoodsDocService`（编排）→ 复用 `TranslatorProviderFactory`（LLM）、`AiLLMService.aiTranslate`（多语言）、`TranslationService.recallTerms`（术语约束）、新增 `RagService`（简单 LIKE 召回，预留 ES）。保存写入新表 `hq_goods_doc_record`。客户页面经公开接口 `GET /api/doc/product/{id}` 消费数据（SSR，不在本项目）。

**Tech Stack:** Java 11, Spring Boot 2.1.7, MyBatis-Plus 3.5.17, MySQL, fastjson 2, Vue 3 (CDN), JUnit 4 + Mockito 2。

---

## 环境与构建注意事项（所有 Maven 命令都必须使用）

> ⚠️ 项目 Lombok 1.18.22 不兼容 JDK 21，**编译/测试/运行必须用 JDK 11（Dragonwell）**：
> `E:\Java\java11-dragonwell-11.0.31.27`
>
> 每个 Maven 命令统一前缀：`JAVA_HOME="E:\Java\java11-dragonwell-11.0.31.27" mvn ...`
>
> 测试框架：项目 starter-test 含 **JUnit 4.12**（`org.junit.Test`）与 **Mockito 2.23**（`org.mockito.junit.MockitoJUnitRunner`），**不要用 JUnit 5**。

## 文件结构

**新增：**
- `src/main/java/com/hq/goods/lang/bean/entity/GoodsDocRecord.java` — 记录实体
- `src/main/java/com/hq/goods/lang/dao/GoodsDocRecordDao.java` — BaseMapper
- `src/main/java/com/hq/goods/lang/config/MybatisPlusConfig.java` — 分页拦截器
- `src/main/java/com/hq/goods/lang/bean/vo/` 下 10 个 VO：`ParamItem/RagHit/SeoVo/GoodsDocVo/GoodsDocDescVo/GoodsDocMultiVo/RecordVo/GoodsDocRecordVo/ProductVo/PageResult`
- `src/main/java/com/hq/goods/lang/bean/dto/` 下 5 个 DTO：`ParsePartReq/ParseTextReq/GenerateDescReq/GenerateMultiReq/SaveReq`
- `src/main/java/com/hq/goods/lang/utils/GoodsDocParseUtil.java` — LLM JSON 提取/映射（纯静态，可测）
- `src/main/java/com/hq/goods/lang/service/RagService.java` + `service/impl/RagServiceImpl.java`
- 测试：`GoodsDocParseUtilTest` / `RagServiceImplTest` / `GoodsDocServiceImplTest`
- `src/main/resources/static/images/default/` — 默认素材目录（业务员自维护，本次仅建目录）

**修改：**
- `src/main/java/com/hq/goods/lang/service/GoodsDocService.java` — 重写接口
- `src/main/java/com/hq/goods/lang/service/impl/GoodsDocServiceImpl.java` — 重写实现
- `src/main/java/com/hq/goods/lang/controller/GoodsDocController.java` — 从空类改写为 9 个接口
- `src/main/resources/static/index.html` — 重写为 Vue3 三视图
- `src/main/java/com/hq/goods/lang/sql/goods-lang.sql` — 追加 `hq_goods_doc_record` DDL

---

## Task 1: 建表 DDL

**Files:**
- Modify: `src/main/java/com/hq/goods/lang/sql/goods-lang.sql`（当前为空文件）

- [ ] **Step 1: 向 `goods-lang.sql` 追加建表语句**

将以下 SQL 追加到 `src/main/java/com/hq/goods/lang/sql/goods-lang.sql` 末尾：

```sql
-- 外贸商品文档记录表
CREATE TABLE IF NOT EXISTS `hq_goods_doc_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `part_number` VARCHAR(100) DEFAULT NULL COMMENT '型号',
  `brand` VARCHAR(100) DEFAULT NULL COMMENT '品牌',
  `category` VARCHAR(100) DEFAULT NULL COMMENT '分类',
  `subcategory` VARCHAR(100) DEFAULT NULL COMMENT '子分类',
  `series` VARCHAR(100) DEFAULT NULL COMMENT '系列',
  `package` VARCHAR(100) DEFAULT NULL COMMENT '封装',
  `mounting_type` VARCHAR(50) DEFAULT NULL COMMENT '安装类型',
  `pin_count` INT DEFAULT NULL COMMENT '引脚数',
  `dimensions` VARCHAR(100) DEFAULT NULL COMMENT '尺寸',
  `parameters` TEXT COMMENT '动态参数JSON',
  `operating_temp` VARCHAR(100) DEFAULT NULL COMMENT '工作温度范围',
  `storage_temp` VARCHAR(100) DEFAULT NULL COMMENT '存储温度',
  `grade` VARCHAR(50) DEFAULT NULL COMMENT '质量等级',
  `rohs` VARCHAR(50) DEFAULT NULL COMMENT 'RoHS/环保',
  `packaging` VARCHAR(50) DEFAULT NULL COMMENT '包装方式',
  `moq` VARCHAR(50) DEFAULT NULL COMMENT '最小起订量',
  `unit` VARCHAR(50) DEFAULT NULL COMMENT '单位',
  `hs_code` VARCHAR(50) DEFAULT NULL COMMENT '海关编码',
  `lead_time` VARCHAR(50) DEFAULT NULL COMMENT '交期',
  `price_range` VARCHAR(100) DEFAULT NULL COMMENT '价格区间',
  `availability` VARCHAR(50) DEFAULT NULL COMMENT '供货状态',
  `datasheet_url` VARCHAR(500) DEFAULT NULL COMMENT '数据手册URL',
  `image_url` VARCHAR(500) DEFAULT NULL COMMENT '图片URL',
  `applications` TEXT COMMENT '应用领域JSON数组',
  `description_en` TEXT COMMENT '英文标准描述',
  `multilingual` TEXT COMMENT '多语言描述JSON',
  `seo` TEXT COMMENT 'SEO JSON',
  `raw_input` TEXT COMMENT '原始输入',
  `source_type` TINYINT DEFAULT NULL COMMENT '1=型号解析 2=文本解析',
  `status` TINYINT DEFAULT 0 COMMENT '状态',
  `creator` VARCHAR(50) DEFAULT NULL COMMENT '创建人',
  `updater` VARCHAR(50) DEFAULT NULL COMMENT '修改人',
  `delete_status` TINYINT DEFAULT 0 COMMENT '0有效 1删除',
  `c_time` DATETIME DEFAULT NULL COMMENT '创建时间',
  `u_time` DATETIME DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_part_number` (`part_number`),
  KEY `idx_brand` (`brand`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外贸商品文档记录';
```

- [ ] **Step 2: 提交**

```bash
cd "E:\workspace\goods-doc-lang"
git add src/main/java/com/hq/goods/lang/sql/goods-lang.sql
git commit -m "feat: 新增 hq_goods_doc_record 建表DDL"
```

> 📌 后续手工端到端前，需在 `doclang` 库执行此 DDL（本任务仅落文件，不连库）。

---

## Task 2: MyBatis-Plus 分页配置

项目当前没有分页拦截器，`selectPage` 不会真正分页，需新增配置。

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/hq/goods/lang/config/MybatisPlusConfig.java`

> ⚠️ MyBatis-Plus 3.5.9+ 将 `PaginationInnerInterceptor` 拆到独立模块 `mybatis-plus-jsqlparser`，必须显式引入，否则分页插件编译报"找不到符号"。已验证 Maven Central 存在 `com.baomidou:mybatis-plus-jsqlparser:3.5.17`。

- [ ] **Step 1: 在 `pom.xml` 添加 mybatis-plus-jsqlparser 依赖**

在 `pom.xml` 的 `<dependencies>` 内、`mybatis-plus-boot-starter` 依赖之后加入：

```xml
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-jsqlparser</artifactId>
            <version>3.5.17</version>
        </dependency>
```

- [ ] **Step 2: 新建配置类**

```java
package com.hq.goods.lang.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：注册分页插件
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd "E:\workspace\goods-doc-lang"
JAVA_HOME="E:\Java\java11-dragonwell-11.0.31.27" mvn -q compile
```

Expected: 无输出（BUILD SUCCESS）。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/hq/goods/lang/config/MybatisPlusConfig.java
git commit -m "feat: 注册 MyBatis-Plus 分页插件"
```

---

## Task 3: 记录实体 GoodsDocRecord

**Files:**
- Create: `src/main/java/com/hq/goods/lang/bean/entity/GoodsDocRecord.java`

- [ ] **Step 1: 新建实体**

```java
package com.hq.goods.lang.bean.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 外贸商品文档记录实体
 */
@Getter
@Setter
@TableName("hq_goods_doc_record")
public class GoodsDocRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String partNumber;
    private String brand;
    private String category;
    private String subcategory;
    private String series;

    /** package 是 Java 关键字，用 @TableField 映射 package 列 */
    @TableField("package")
    private String packageType;

    private String mountingType;
    private Integer pinCount;
    private String dimensions;

    /** 动态参数 JSON 字符串 */
    private String parameters;

    private String operatingTemp;
    private String storageTemp;
    private String grade;
    private String rohs;
    private String packaging;
    private String moq;
    private String unit;
    private String hsCode;
    private String leadTime;
    private String priceRange;
    private String availability;
    private String datasheetUrl;
    private String imageUrl;

    /** 应用领域 JSON 数组字符串 */
    private String applications;

    private String descriptionEn;

    /** 多语言描述 JSON：{en,zh,zhTw,ja,ru} */
    private String multilingual;

    /** SEO JSON：{zh,en,ja,ru} */
    private String seo;

    private String rawInput;
    private Integer sourceType;
    private Integer status;
    private String creator;
    private String updater;
    private Integer deleteStatus;
    private LocalDateTime cTime;
    private LocalDateTime uTime;
}
```

- [ ] **Step 2: 编译验证**

```bash
JAVA_HOME="E:\Java\java11-dragonwell-11.0.31.27" mvn -q compile
```

Expected: 无输出（BUILD SUCCESS）。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/hq/goods/lang/bean/entity/GoodsDocRecord.java
git commit -m "feat: 新增商品文档记录实体"
```

---

## Task 4: 记录 DAO

**Files:**
- Create: `src/main/java/com/hq/goods/lang/dao/GoodsDocRecordDao.java`

- [ ] **Step 1: 新建 DAO**

```java
package com.hq.goods.lang.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hq.goods.lang.bean.entity.GoodsDocRecord;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

/**
 * 外贸商品文档记录 Mapper
 */
@Mapper
@Repository
public interface GoodsDocRecordDao extends BaseMapper<GoodsDocRecord> {
}
```

- [ ] **Step 2: 编译验证**

```bash
JAVA_HOME="E:\Java\java11-dragonwell-11.0.31.27" mvn -q compile
```

Expected: 无输出（BUILD SUCCESS）。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/hq/goods/lang/dao/GoodsDocRecordDao.java
git commit -m "feat: 新增商品文档记录 DAO"
```

---

## Task 5: VO 类（10 个）

**Files:**
- Create: `src/main/java/com/hq/goods/lang/bean/vo/ParamItem.java`
- Create: `src/main/java/com/hq/goods/lang/bean/vo/RagHit.java`
- Create: `src/main/java/com/hq/goods/lang/bean/vo/SeoVo.java`
- Create: `src/main/java/com/hq/goods/lang/bean/vo/GoodsDocVo.java`
- Create: `src/main/java/com/hq/goods/lang/bean/vo/GoodsDocDescVo.java`
- Create: `src/main/java/com/hq/goods/lang/bean/vo/GoodsDocMultiVo.java`
- Create: `src/main/java/com/hq/goods/lang/bean/vo/RecordVo.java`
- Create: `src/main/java/com/hq/goods/lang/bean/vo/GoodsDocRecordVo.java`
- Create: `src/main/java/com/hq/goods/lang/bean/vo/ProductVo.java`
- Create: `src/main/java/com/hq/goods/lang/bean/vo/PageResult.java`

- [ ] **Step 1: 新建 10 个 VO 文件（内容如下）**

`ParamItem.java`:
```java
package com.hq.goods.lang.bean.vo;

import lombok.Data;

/**
 * 动态参数项：参数名 / 数值 / 单位
 */
@Data
public class ParamItem {
    private String name;
    private String value;
    private String unit;
}
```

`RagHit.java`:
```java
package com.hq.goods.lang.bean.vo;

import lombok.Data;

/**
 * RAG 召回命中项
 */
@Data
public class RagHit {
    private String partNumber;
    private String brand;
    private Integer score;

    public RagHit() {
    }

    public RagHit(String partNumber, String brand, Integer score) {
        this.partNumber = partNumber;
        this.brand = brand;
        this.score = score;
    }
}
```

`SeoVo.java`:
```java
package com.hq.goods.lang.bean.vo;

import lombok.Data;

import java.util.List;

/**
 * SEO 内容：Title / Keywords / Description
 */
@Data
public class SeoVo {
    private String title;
    private List<String> keywords;
    private String description;
}
```

`GoodsDocVo.java`:
```java
package com.hq.goods.lang.bean.vo;

import lombok.Data;

import java.util.List;

/**
 * 元器件基本资料（页面可编辑表单模型）
 */
@Data
public class GoodsDocVo {
    private String partNumber;
    private String brand;
    private String category;
    private String subcategory;
    private String series;
    private String packageType;
    private String mountingType;
    private Integer pinCount;
    private String dimensions;
    private List<ParamItem> parameters;
    private String operatingTemp;
    private String storageTemp;
    private String grade;
    private String rohs;
    private String packaging;
    private String moq;
    private String unit;
    private String hsCode;
    private String leadTime;
    private String priceRange;
    private String availability;
    private String datasheetUrl;
    private String imageUrl;
    private List<String> applications;
    private String descriptionEn;
    private String rawInput;
    private List<RagHit> topK;
}
```

`GoodsDocDescVo.java`:
```java
package com.hq.goods.lang.bean.vo;

import lombok.Data;

/**
 * 英文描述生成结果
 */
@Data
public class GoodsDocDescVo {
    private String description;

    public GoodsDocDescVo() {
    }

    public GoodsDocDescVo(String description) {
        this.description = description;
    }
}
```

`GoodsDocMultiVo.java`:
```java
package com.hq.goods.lang.bean.vo;

import lombok.Data;

import java.util.Map;

/**
 * 多语言 + SEO 生成结果
 */
@Data
public class GoodsDocMultiVo {
    /** 语言码 → 描述文本：en/zh/zhTw/ja/ru */
    private Map<String, String> multilingual;
    /** 语言码 → SEO：zh/en/ja/ru */
    private Map<String, SeoVo> seo;
}
```

`RecordVo.java`:
```java
package com.hq.goods.lang.bean.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 历史列表行
 */
@Data
public class RecordVo {
    private Long id;
    private String partNumber;
    private String brand;
    private String category;
    private String packageType;
    private LocalDateTime cTime;
}
```

`GoodsDocRecordVo.java`:
```java
package com.hq.goods.lang.bean.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 后台详情（编辑回填）
 */
@Data
public class GoodsDocRecordVo {
    private Long id;
    private GoodsDocVo basic;
    private Map<String, String> multilingual;
    private Map<String, SeoVo> seo;
    private Integer sourceType;
    private LocalDateTime cTime;
    private LocalDateTime uTime;
}
```

`ProductVo.java`:
```java
package com.hq.goods.lang.bean.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 客户页面（官网 SSR）消费的数据
 */
@Data
public class ProductVo {
    private String partNumber;
    private String brand;
    private String category;
    private String subcategory;
    private String series;
    private String packageType;
    private List<ParamItem> parameters;
    private String descriptionEn;
    private List<String> applications;
    private Map<String, String> multilingual;
    private Map<String, SeoVo> seo;
    private String imageUrl;
    private String datasheetUrl;
}
```

`PageResult.java`:
```java
package com.hq.goods.lang.bean.vo;

import lombok.Data;

import java.util.List;

/**
 * 通用分页结果
 */
@Data
public class PageResult<T> {
    private long total;
    private List<T> list;

    public PageResult() {
    }

    public PageResult(long total, List<T> list) {
        this.total = total;
        this.list = list;
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
JAVA_HOME="E:\Java\java11-dragonwell-11.0.31.27" mvn -q compile
```

Expected: 无输出（BUILD SUCCESS）。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/hq/goods/lang/bean/vo/
git commit -m "feat: 新增商品文档 VO"
```

---

## Task 6: DTO 类（5 个）

**Files:**
- Create: `src/main/java/com/hq/goods/lang/bean/dto/ParsePartReq.java`
- Create: `src/main/java/com/hq/goods/lang/bean/dto/ParseTextReq.java`
- Create: `src/main/java/com/hq/goods/lang/bean/dto/GenerateDescReq.java`
- Create: `src/main/java/com/hq/goods/lang/bean/dto/GenerateMultiReq.java`
- Create: `src/main/java/com/hq/goods/lang/bean/dto/SaveReq.java`

- [ ] **Step 1: 新建 5 个 DTO 文件（内容如下）**

`ParsePartReq.java`:
```java
package com.hq.goods.lang.bean.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 型号+品牌解析请求
 */
@Data
public class ParsePartReq {
    @NotBlank(message = "型号不能为空")
    private String partNumber;

    private String brand;
}
```

`ParseTextReq.java`:
```java
package com.hq.goods.lang.bean.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 描述文本解析请求
 */
@Data
public class ParseTextReq {
    @NotBlank(message = "描述文本不能为空")
    private String rawText;
}
```

`GenerateDescReq.java`:
```java
package com.hq.goods.lang.bean.dto;

import com.hq.goods.lang.bean.vo.GoodsDocVo;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 英文描述生成请求
 */
@Data
public class GenerateDescReq {
    @NotNull(message = "基本资料不能为空")
    private GoodsDocVo goodsDoc;
}
```

`GenerateMultiReq.java`:
```java
package com.hq.goods.lang.bean.dto;

import com.hq.goods.lang.bean.vo.GoodsDocVo;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 多语言 + SEO 生成请求
 */
@Data
public class GenerateMultiReq {
    @NotNull(message = "基本资料不能为空")
    private GoodsDocVo goodsDoc;

    private String description;
}
```

`SaveReq.java`:
```java
package com.hq.goods.lang.bean.dto;

import com.hq.goods.lang.bean.vo.GoodsDocVo;
import com.hq.goods.lang.bean.vo.SeoVo;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * 保存请求（无 id 新增 / 有 id 更新）
 */
@Data
public class SaveReq {
    private Long id;

    @NotNull(message = "基本资料不能为空")
    private GoodsDocVo goodsDoc;

    /** 语言码 → 描述文本：en/zh/zhTw/ja/ru */
    private Map<String, String> multilingual;

    /** 语言码 → SEO：zh/en/ja/ru */
    private Map<String, SeoVo> seo;

    /** 1=型号解析 2=文本解析 */
    private Integer sourceType;
}
```

- [ ] **Step 2: 编译验证**

```bash
JAVA_HOME="E:\Java\java11-dragonwell-11.0.31.27" mvn -q compile
```

Expected: 无输出（BUILD SUCCESS）。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/hq/goods/lang/bean/dto/
git commit -m "feat: 新增商品文档 DTO"
```

---

## Task 7: LLM JSON 解析工具（TDD）

**Files:**
- Create: `src/main/java/com/hq/goods/lang/utils/GoodsDocParseUtil.java`
- Test: `src/test/java/com/hq/goods/lang/GoodsDocParseUtilTest.java`

- [ ] **Step 1: 先写失败测试**

`src/test/java/com/hq/goods/lang/GoodsDocParseUtilTest.java`:
```java
package com.hq.goods.lang;

import com.hq.goods.lang.bean.CustomException;
import com.hq.goods.lang.bean.vo.GoodsDocVo;
import com.hq.goods.lang.bean.vo.SeoVo;
import com.hq.goods.lang.utils.GoodsDocParseUtil;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GoodsDocParseUtilTest {

    @Test
    public void testExtractJsonWithFence() {
        String raw = "```json\n{\"partNumber\":\"A\"}\n```";
        assertEquals("{\"partNumber\":\"A\"}", GoodsDocParseUtil.extractJson(raw));
    }

    @Test
    public void testExtractJsonWithPrefixText() {
        String raw = "以下是结果：{\"partNumber\":\"A\"} 完毕";
        assertEquals("{\"partNumber\":\"A\"}", GoodsDocParseUtil.extractJson(raw));
    }

    @Test(expected = CustomException.class)
    public void testExtractJsonNoJson() {
        GoodsDocParseUtil.extractJson("没有 JSON");
    }

    @Test
    public void testToGoodsDocVo() {
        String json = "{\"partNumber\":\"STM32F103C8T6\",\"brand\":\"ST\",\"category\":\"MCU\","
                + "\"parameters\":[{\"name\":\"Flash\",\"value\":\"64KB\"}],"
                + "\"applications\":[\"工业控制\",\"消费电子\"]}";
        GoodsDocVo vo = GoodsDocParseUtil.toGoodsDocVo(json);
        assertEquals("STM32F103C8T6", vo.getPartNumber());
        assertEquals("MCU", vo.getCategory());
        assertEquals(1, vo.getParameters().size());
        assertEquals("Flash", vo.getParameters().get(0).getName());
        assertEquals("64KB", vo.getParameters().get(0).getValue());
        assertEquals(2, vo.getApplications().size());
    }

    @Test
    public void testToGoodsDocVoMissingFields() {
        GoodsDocVo vo = GoodsDocParseUtil.toGoodsDocVo("{\"partNumber\":\"A\"}");
        assertEquals("A", vo.getPartNumber());
        assertNull(vo.getBrand());
        assertNull(vo.getParameters());
    }

    @Test
    public void testParseSeo() {
        String json = "{\"en\":{\"title\":\"T\",\"keywords\":[\"k1\",\"k2\"],\"description\":\"D\"},"
                + "\"zh\":{\"title\":\"中\",\"keywords\":[\"关键词\"],\"description\":\"描述\"}}";
        Map<String, SeoVo> seo = GoodsDocParseUtil.parseSeo(json);
        assertEquals(2, seo.size());
        assertEquals("T", seo.get("en").getTitle());
        assertEquals(2, seo.get("en").getKeywords().size());
        assertEquals("描述", seo.get("zh").getDescription());
    }

    @Test
    public void testToGoodsDocVoMalformed() {
        GoodsDocVo vo = GoodsDocParseUtil.toGoodsDocVo("不是JSON{{{");
        assertNotNull(vo);
        assertNull(vo.getPartNumber());
    }

    @Test
    public void testToGoodsDocVoBlank() {
        GoodsDocVo vo = GoodsDocParseUtil.toGoodsDocVo("");
        assertNotNull(vo);
        assertNull(vo.getPartNumber());
    }

    @Test
    public void testToGoodsDocVoPinCountSafe() {
        assertEquals(Integer.valueOf(64), GoodsDocParseUtil.toGoodsDocVo("{\"pinCount\":64}").getPinCount());
        assertEquals(Integer.valueOf(64), GoodsDocParseUtil.toGoodsDocVo("{\"pinCount\":\"64\"}").getPinCount());
        assertNull(GoodsDocParseUtil.toGoodsDocVo("{\"pinCount\":\"64 pins\"}").getPinCount());
    }

    @Test
    public void testToGoodsDocVoTypeDeviation() {
        assertNull(GoodsDocParseUtil.toGoodsDocVo("{\"parameters\":{\"name\":\"Flash\",\"value\":\"64KB\"}}").getParameters());
        assertNull(GoodsDocParseUtil.toGoodsDocVo("{\"parameters\":[\"Flash\",\"64KB\"]}").getParameters());
    }

    @Test
    public void testValidateJson() {
        GoodsDocParseUtil.validateJson("```json\n{\"partNumber\":\"A\"}\n```");
        GoodsDocParseUtil.validateJson("以下是结果：{\"partNumber\":\"A\"} 完毕");
    }

    @Test(expected = CustomException.class)
    public void testValidateJsonInvalid() {
        GoodsDocParseUtil.validateJson("完全没有JSON");
    }

    @Test
    public void testIsValidJson() {
        assertTrue(GoodsDocParseUtil.isValidJson("{\"a\":1}"));
        assertFalse(GoodsDocParseUtil.isValidJson("not json"));
        assertFalse(GoodsDocParseUtil.isValidJson(""));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd "E:\workspace\goods-doc-lang"
JAVA_HOME="E:\Java\java11-dragonwell-11.0.31.27" mvn -q test -Dtest=GoodsDocParseUtilTest
```

Expected: FAIL（编译失败：找不到 `GoodsDocParseUtil`）。

- [ ] **Step 3: 实现工具类**

`src/main/java/com/hq/goods/lang/utils/GoodsDocParseUtil.java`:
```java
package com.hq.goods.lang.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hq.goods.lang.bean.CustomException;
import com.hq.goods.lang.bean.ResponseEnum;
import com.hq.goods.lang.bean.vo.GoodsDocVo;
import com.hq.goods.lang.bean.vo.ParamItem;
import com.hq.goods.lang.bean.vo.SeoVo;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 输出 JSON 提取与结构化映射工具
 */
public final class GoodsDocParseUtil {

    private GoodsDocParseUtil() {
    }

    /**
     * 从 LLM 输出中提取 JSON（自动去掉 markdown 代码块围栏与前后杂文本）
     */
    public static String extractJson(String raw) {
        if (StringUtils.isBlank(raw)) {
            throw new CustomException(ResponseEnum.INNER_SERVER_ERROR.getCode(), "AI 输出为空");
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            start = raw.indexOf('[');
            end = raw.lastIndexOf(']');
            if (start < 0 || end <= start) {
                throw new CustomException(ResponseEnum.INNER_SERVER_ERROR.getCode(), "AI 输出未包含有效 JSON");
            }
        }
        return raw.substring(start, end + 1).trim();
    }

    /**
     * 校验 LLM 原始输出提取后是合法 JSON，非法则抛 CustomException（供服务层重试判定）
     */
    public static void validateJson(String raw) {
        String json = extractJson(raw);
        if (!isValidJson(json)) {
            throw new CustomException(ResponseEnum.INNER_SERVER_ERROR.getCode(), "AI 输出未包含有效 JSON");
        }
    }

    /**
     * 判断给定字符串是否为合法 JSON
     */
    public static boolean isValidJson(String json) {
        if (StringUtils.isBlank(json)) {
            return false;
        }
        try {
            return JSON.parse(json) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * LLM 解析结果 JSON → GoodsDocVo（容错：字段缺失/类型偏离不报错，解析失败返回空 VO）
     */
    public static GoodsDocVo toGoodsDocVo(String json) {
        GoodsDocVo vo = new GoodsDocVo();
        if (StringUtils.isBlank(json)) {
            return vo;
        }
        JSONObject obj;
        try {
            obj = JSON.parseObject(json);
        } catch (Exception e) {
            return vo;
        }
        if (obj == null) {
            return vo;
        }
        vo.setPartNumber(obj.getString("partNumber"));
        vo.setBrand(obj.getString("brand"));
        vo.setCategory(obj.getString("category"));
        vo.setSubcategory(obj.getString("subcategory"));
        vo.setSeries(obj.getString("series"));
        vo.setPackageType(obj.getString("packageType"));
        vo.setMountingType(obj.getString("mountingType"));
        vo.setPinCount(toIntSafe(obj.get("pinCount")));
        vo.setDimensions(obj.getString("dimensions"));
        vo.setOperatingTemp(obj.getString("operatingTemp"));
        vo.setStorageTemp(obj.getString("storageTemp"));
        vo.setGrade(obj.getString("grade"));
        vo.setRohs(obj.getString("rohs"));
        vo.setPackaging(obj.getString("packaging"));
        vo.setMoq(obj.getString("moq"));
        vo.setUnit(obj.getString("unit"));
        vo.setHsCode(obj.getString("hsCode"));
        vo.setLeadTime(obj.getString("leadTime"));
        vo.setPriceRange(obj.getString("priceRange"));
        vo.setAvailability(obj.getString("availability"));
        vo.setDatasheetUrl(obj.getString("datasheetUrl"));
        vo.setImageUrl(obj.getString("imageUrl"));

        Object paramsRaw = obj.get("parameters");
        if (paramsRaw instanceof JSONArray) {
            JSONArray params = (JSONArray) paramsRaw;
            List<ParamItem> list = new ArrayList<>();
            for (int i = 0; i < params.size(); i++) {
                Object po = params.get(i);
                if (!(po instanceof JSONObject)) {
                    continue;
                }
                JSONObject p = (JSONObject) po;
                ParamItem item = new ParamItem();
                item.setName(p.getString("name"));
                item.setValue(p.getString("value"));
                item.setUnit(p.getString("unit"));
                list.add(item);
            }
            if (!list.isEmpty()) {
                vo.setParameters(list);
            }
        }

        Object appsRaw = obj.get("applications");
        if (appsRaw instanceof JSONArray) {
            JSONArray apps = (JSONArray) appsRaw;
            List<String> appList = new ArrayList<>();
            for (int i = 0; i < apps.size(); i++) {
                Object a = apps.get(i);
                if (a instanceof String && StringUtils.isNotBlank((String) a)) {
                    appList.add((String) a);
                }
            }
            vo.setApplications(appList);
        }
        return vo;
    }

    /**
     * SEO JSON → Map&lt;语言码, SeoVo&gt;（zh/en/ja/ru，容错：解析失败返回空 Map）
     */
    public static Map<String, SeoVo> parseSeo(String json) {
        Map<String, SeoVo> result = new LinkedHashMap<>();
        if (StringUtils.isBlank(json)) {
            return result;
        }
        JSONObject obj;
        try {
            obj = JSON.parseObject(json);
        } catch (Exception e) {
            return result;
        }
        if (obj == null) {
            return result;
        }
        for (String key : obj.keySet()) {
            Object so = obj.get(key);
            if (!(so instanceof JSONObject)) {
                continue;
            }
            JSONObject s = (JSONObject) so;
            SeoVo seo = new SeoVo();
            seo.setTitle(s.getString("title"));
            seo.setDescription(s.getString("description"));
            Object kwRaw = s.get("keywords");
            if (kwRaw instanceof JSONArray) {
                JSONArray kw = (JSONArray) kwRaw;
                List<String> keywords = new ArrayList<>();
                for (int i = 0; i < kw.size(); i++) {
                    Object k = kw.get(i);
                    if (k instanceof String && StringUtils.isNotBlank((String) k)) {
                        keywords.add((String) k);
                    }
                }
                seo.setKeywords(keywords);
            }
            result.put(key, seo);
        }
        return result;
    }

    /**
     * 安全转 int：null/非数字返回 null，数字字符串/数值直接解析
     */
    private static Integer toIntSafe(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        String s = String.valueOf(o).trim();
        if (StringUtils.isBlank(s)) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
JAVA_HOME="E:\Java\java11-dragonwell-11.0.31.27" mvn -q test -Dtest=GoodsDocParseUtilTest
```

Expected: PASS（6 个测试全部通过）。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/hq/goods/lang/utils/GoodsDocParseUtil.java src/test/java/com/hq/goods/lang/GoodsDocParseUtilTest.java
git commit -m "feat: 商品文档 LLM JSON 解析工具 + 单测"
```

---

## Task 8: RAG 服务（简单实现，预留 ES）

**Files:**
- Create: `src/main/java/com/hq/goods/lang/service/RagService.java`
- Create: `src/main/java/com/hq/goods/lang/service/impl/RagServiceImpl.java`
- Test: `src/test/java/com/hq/goods/lang/service/impl/RagServiceImplTest.java`

- [ ] **Step 1: 先写失败测试**

`src/test/java/com/hq/goods/lang/service/impl/RagServiceImplTest.java`:
```java
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
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd "E:\workspace\goods-doc-lang"
JAVA_HOME="E:\Java\java11-dragonwell-11.0.31.27" mvn -q test -Dtest=RagServiceImplTest
```

Expected: FAIL（编译失败：找不到 `RagService`/`RagServiceImpl`）。

- [ ] **Step 3: 实现接口与实现类**

`src/main/java/com/hq/goods/lang/service/RagService.java`:
```java
package com.hq.goods.lang.service;

import com.hq.goods.lang.bean.vo.RagHit;

import java.util.List;

/**
 * RAG 检索服务。
 * 当前为简单实现（MySQL LIKE 计分）；后续 ES 部署后新增 ES 向量检索实现（相同签名），
 * 通过配置切换 Bean，调用方不变。
 */
public interface RagService {

    /**
     * 相似商品 TOP-K 召回
     *
     * @param query 查询文本
     * @param k     top-k 数量
     */
    List<RagHit> searchTopK(String query, int k);
}
```

`src/main/java/com/hq/goods/lang/service/impl/RagServiceImpl.java`:
```java
package com.hq.goods.lang.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hq.goods.lang.bean.entity.GoodsDocRecord;
import com.hq.goods.lang.bean.vo.RagHit;
import com.hq.goods.lang.dao.GoodsDocRecordDao;
import com.hq.goods.lang.service.RagService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 简单实现：从 hq_goods_doc_record 按 型号/品牌/分类/封装 子串匹配计分，取 TOP-K。
 * 注：当前为全表扫描 + 内存计分，适用于中小数据量；ES 部署后替换为向量检索实现（接口不变）。
 */
@Service
public class RagServiceImpl implements RagService {

    @Autowired
    private GoodsDocRecordDao goodsDocRecordDao;

    @Override
    public List<RagHit> searchTopK(String query, int k) {
        if (StringUtils.isBlank(query) || k <= 0) {
            return Collections.emptyList();
        }
        // 仅投影计分所需列，避免加载 TEXT 大字段
        List<GoodsDocRecord> records = goodsDocRecordDao.selectList(
                new QueryWrapper<GoodsDocRecord>()
                        .select("part_number", "brand", "category", "package")
                        .eq("delete_status", 0));
        if (CollectionUtils.isEmpty(records)) {
            return Collections.emptyList();
        }
        return records.stream()
                .map(r -> new RagHit(r.getPartNumber(), r.getBrand(), score(r, query)))
                .filter(h -> h.getScore() > 0)
                .sorted(Comparator.comparingInt(RagHit::getScore).reversed())
                .limit(k)
                .collect(Collectors.toList());
    }

    /**
     * 简单计分：型号4 / 品牌3 / 分类2 / 封装1（双向子串匹配）
     */
    static int score(GoodsDocRecord r, String query) {
        if (r == null || StringUtils.isBlank(query)) {
            return 0;
        }
        String q = query.toLowerCase(Locale.ROOT);
        int s = 0;
        if (hit(r.getPartNumber(), q)) {
            s += 4;
        }
        if (hit(r.getBrand(), q)) {
            s += 3;
        }
        if (hit(r.getCategory(), q)) {
            s += 2;
        }
        if (hit(r.getPackageType(), q)) {
            s += 1;
        }
        return s;
    }

    private static boolean hit(String field, String q) {
        if (StringUtils.isBlank(field)) {
            return false;
        }
        String f = field.toLowerCase(Locale.ROOT);
        if (f.length() < 3) {
            // 短字段（如品牌 "ST"/"TI"）：仅当查询完整包含该字段作为独立词或字段包含查询时命中，避免任意 2 字符子串误加分
            return f.contains(q) || (" " + q + " ").contains(" " + f + " ");
        }
        return q.contains(f) || f.contains(q);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
JAVA_HOME="E:\Java\java11-dragonwell-11.0.31.27" mvn -q test -Dtest=RagServiceImplTest
```

Expected: PASS（5 个测试全部通过）。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/hq/goods/lang/service/RagService.java src/main/java/com/hq/goods/lang/service/impl/RagServiceImpl.java src/test/java/com/hq/goods/lang/service/impl/RagServiceImplTest.java
git commit -m "feat: RAG 简单实现（LIKE 计分，预留 ES）+ 单测"
```

---

## Task 9: 商品文档服务接口（重写）

**Files:**
- Modify: `src/main/java/com/hq/goods/lang/service/GoodsDocService.java`

- [ ] **Step 1: 用新接口整体替换旧文件**

`src/main/java/com/hq/goods/lang/service/GoodsDocService.java`（整体覆盖旧内容）:
```java
package com.hq.goods.lang.service;

import com.hq.goods.lang.bean.dto.GenerateDescReq;
import com.hq.goods.lang.bean.dto.GenerateMultiReq;
import com.hq.goods.lang.bean.dto.ParsePartReq;
import com.hq.goods.lang.bean.dto.ParseTextReq;
import com.hq.goods.lang.bean.dto.SaveReq;
import com.hq.goods.lang.bean.vo.GoodsDocDescVo;
import com.hq.goods.lang.bean.vo.GoodsDocMultiVo;
import com.hq.goods.lang.bean.vo.GoodsDocRecordVo;
import com.hq.goods.lang.bean.vo.GoodsDocVo;
import com.hq.goods.lang.bean.vo.PageResult;
import com.hq.goods.lang.bean.vo.ProductVo;
import com.hq.goods.lang.bean.vo.RecordVo;

/**
 * 外贸商品文档编排服务
 */
public interface GoodsDocService {

    /** 型号+品牌解析 */
    GoodsDocVo parsePart(ParsePartReq req);

    /** 描述文本解析 */
    GoodsDocVo parseText(ParseTextReq req);

    /** 生成英文标准描述 */
    GoodsDocDescVo generateDesc(GenerateDescReq req);

    /** 生成多语言 + SEO */
    GoodsDocMultiVo generateMulti(GenerateMultiReq req);

    /** 保存（无 id 新增 / 有 id 更新） */
    Long save(SaveReq req);

    /** 历史分页列表 */
    PageResult<RecordVo> list(int page, int size);

    /** 后台详情 */
    GoodsDocRecordVo detail(Long id);

    /** 逻辑删除 */
    void delete(Long id);

    /** 客户页面公开数据 */
    ProductVo product(Long id);
}
```

- [ ] **Step 2: 编译验证**

```bash
cd "E:\workspace\goods-doc-lang"
JAVA_HOME="E:\Java\java11-dragonwell-11.0.31.27" mvn -q compile
```

Expected: 无输出（BUILD SUCCESS）。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/hq/goods/lang/service/GoodsDocService.java
git commit -m "feat: 重写商品文档服务接口"
```

---

## Task 10: 商品文档服务实现（TDD）

**Files:**
- Modify: `src/main/java/com/hq/goods/lang/service/impl/GoodsDocServiceImpl.java`
- Test: `src/test/java/com/hq/goods/lang/service/impl/GoodsDocServiceImplTest.java`

- [ ] **Step 1: 先写失败测试**

`src/test/java/com/hq/goods/lang/service/impl/GoodsDocServiceImplTest.java`:
```java
package com.hq.goods.lang.service.impl;

import com.alibaba.fastjson.JSON;
import com.hq.goods.lang.bean.dto.AiTranslateDto;
import com.hq.goods.lang.bean.dto.GenerateDescReq;
import com.hq.goods.lang.bean.dto.GenerateMultiReq;
import com.hq.goods.lang.bean.dto.ParsePartReq;
import com.hq.goods.lang.bean.dto.ParseTextReq;
import com.hq.goods.lang.bean.dto.SaveReq;
import com.hq.goods.lang.bean.entity.GoodsDocRecord;
import com.hq.goods.lang.bean.vo.AiTranslateVo;
import com.hq.goods.lang.bean.vo.GoodsDocDescVo;
import com.hq.goods.lang.bean.vo.GoodsDocMultiVo;
import com.hq.goods.lang.bean.vo.GoodsDocVo;
import com.hq.goods.lang.bean.vo.ParamItem;
import com.hq.goods.lang.bean.vo.ProductVo;
import com.hq.goods.lang.dao.GoodsDocRecordDao;
import com.hq.goods.lang.service.AiLLMService;
import com.hq.goods.lang.service.RagService;
import com.hq.goods.lang.service.TranslationService;
import com.hq.goods.lang.utils.TranslatorProviderFactory;
import com.hq.goods.lang.utils.TranslatorProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class GoodsDocServiceImplTest {

    @Mock
    private TranslatorProviderFactory translatorProviderFactory;
    @Mock
    private TranslatorProvider translatorProvider;
    @Mock
    private AiLLMService aiLLMService;
    @Mock
    private TranslationService translationService;
    @Mock
    private RagService ragService;
    @Mock
    private GoodsDocRecordDao goodsDocRecordDao;

    @InjectMocks
    private GoodsDocServiceImpl service;

    @Before
    public void setup() {
        when(translatorProviderFactory.getProvider()).thenReturn(translatorProvider);
        when(translationService.recallTerms(anyString())).thenReturn(Collections.emptyList());
        when(ragService.searchTopK(anyString(), anyInt())).thenReturn(Collections.emptyList());
    }

    @Test
    public void testParsePart() {
        String json = "{\"partNumber\":\"STM32F103C8T6\",\"brand\":\"ST\",\"category\":\"MCU\","
                + "\"parameters\":[{\"name\":\"Flash\",\"value\":\"64KB\"}]}";
        when(translatorProvider.translate(anyString(), anyString(), anyString()))
                .thenReturn("```json\n" + json + "\n```");

        ParsePartReq req = new ParsePartReq();
        req.setPartNumber("STM32F103C8T6");
        req.setBrand("ST");
        GoodsDocVo vo = service.parsePart(req);

        assertEquals("STM32F103C8T6", vo.getPartNumber());
        assertEquals("MCU", vo.getCategory());
        assertEquals(1, vo.getParameters().size());
        assertEquals("Flash", vo.getParameters().get(0).getName());
    }

    @Test
    public void testParsePartFallbackBrand() {
        String json = "{\"partNumber\":\"TPS5430\"}";
        when(translatorProvider.translate(anyString(), anyString(), anyString())).thenReturn(json);

        ParsePartReq req = new ParsePartReq();
        req.setPartNumber("TPS5430");
        req.setBrand("TI");
        GoodsDocVo vo = service.parsePart(req);

        assertEquals("TPS5430", vo.getPartNumber());
        assertEquals("TI", vo.getBrand());
    }

    @Test
    public void testParseText() {
        String json = "{\"partNumber\":\"LM358\",\"brand\":\"TI\",\"category\":\"运放\",\"applications\":[\"放大电路\"]}";
        when(translatorProvider.translate(anyString(), anyString(), anyString())).thenReturn(json);

        ParseTextReq req = new ParseTextReq();
        req.setRawText("这是 LM358 双运放，TI 品牌，用于放大电路。");
        GoodsDocVo vo = service.parseText(req);

        assertEquals("LM358", vo.getPartNumber());
        assertEquals(1, vo.getApplications().size());
    }

    @Test
    public void testGenerateDesc() {
        when(translatorProvider.translate(anyString(), anyString(), anyString()))
                .thenReturn("This is a high-performance MCU based on real parameters.");

        GenerateDescReq req = new GenerateDescReq();
        GoodsDocVo vo = new GoodsDocVo();
        vo.setPartNumber("STM32F103C8T6");
        req.setGoodsDoc(vo);
        GoodsDocDescVo desc = service.generateDesc(req);

        assertEquals("This is a high-performance MCU based on real parameters.", desc.getDescription());
    }

    @Test
    public void testGenerateMulti() {
        when(aiLLMService.aiTranslate(any(AiTranslateDto.class))).thenAnswer(inv -> {
            AiTranslateDto dto = inv.getArgument(0);
            AiTranslateVo v = new AiTranslateVo();
            v.setTranslation("tr-" + dto.getTarget());
            return v;
        });
        String seoJson = "{\"en\":{\"title\":\"T\",\"keywords\":[\"k\"],\"description\":\"D\"},"
                + "\"zh\":{\"title\":\"中\",\"keywords\":[\"kw中\"],\"description\":\"描\"},"
                + "\"ja\":{\"title\":\"日\",\"keywords\":[\"kw日\"],\"description\":\"日D\"},"
                + "\"ru\":{\"title\":\"俄\",\"keywords\":[\"kw俄\"],\"description\":\"俄D\"}}";
        when(translatorProvider.translate(anyString(), anyString(), anyString())).thenReturn(seoJson);

        GenerateMultiReq req = new GenerateMultiReq();
        GoodsDocVo vo = new GoodsDocVo();
        vo.setPartNumber("STM32F103C8T6");
        req.setGoodsDoc(vo);
        req.setDescription("English description");
        GoodsDocMultiVo multi = service.generateMulti(req);

        assertEquals("English description", multi.getMultilingual().get("en"));
        // target: 2=中文 3=繁体 4=日 5=俄
        assertEquals("tr-2", multi.getMultilingual().get("zh"));
        assertEquals("tr-4", multi.getMultilingual().get("ja"));
        assertEquals("T", multi.getSeo().get("en").getTitle());
    }

    @Test
    public void testSaveNew() {
        doAnswer(inv -> {
            ((GoodsDocRecord) inv.getArgument(0)).setId(100L);
            return 1;
        }).when(goodsDocRecordDao).insert(any(GoodsDocRecord.class));

        SaveReq req = new SaveReq();
        GoodsDocVo vo = new GoodsDocVo();
        vo.setPartNumber("STM32F103C8T6");
        vo.setParameters(Collections.singletonList(new ParamItem()));
        req.setGoodsDoc(vo);
        req.setMultilingual(new HashMap<>());
        req.setSeo(new HashMap<>());

        Long id = service.save(req);
        assertEquals(Long.valueOf(100L), id);
        verify(goodsDocRecordDao).insert(any(GoodsDocRecord.class));
    }

    @Test
    public void testSaveUpdate() {
        SaveReq req = new SaveReq();
        req.setId(5L);
        GoodsDocVo vo = new GoodsDocVo();
        vo.setPartNumber("LM358");
        req.setGoodsDoc(vo);

        service.save(req);
        verify(goodsDocRecordDao).updateById(any(GoodsDocRecord.class));
    }

    @Test
    public void testProduct() {
        GoodsDocRecord r = new GoodsDocRecord();
        r.setId(1L);
        r.setPartNumber("STM32F103C8T6");
        r.setBrand("ST");
        r.setParameters(JSON.toJSONString(Collections.singletonList(new ParamItem())));
        r.setApplications(JSON.toJSONString(Collections.singletonList("工业控制")));
        r.setMultilingual("{\"en\":\"desc\",\"zh\":\"描述\"}");
        r.setSeo("{\"en\":{\"title\":\"T\",\"keywords\":[\"a\"],\"description\":\"D\"}}");
        r.setDescriptionEn("English desc");
        when(goodsDocRecordDao.selectById(1L)).thenReturn(r);

        ProductVo vo = service.product(1L);
        assertEquals("STM32F103C8T6", vo.getPartNumber());
        assertEquals("desc", vo.getMultilingual().get("en"));
        assertEquals("T", vo.getSeo().get("en").getTitle());
        assertEquals("English desc", vo.getDescriptionEn());
    }

    @Test
    public void testDelete() {
        service.delete(9L);
        verify(goodsDocRecordDao).updateById(any(GoodsDocRecord.class));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd "E:\workspace\goods-doc-lang"
JAVA_HOME="E:\Java\java11-dragonwell-11.0.31.27" mvn -q test -Dtest=GoodsDocServiceImplTest
```

Expected: FAIL（编译失败：`GoodsDocServiceImpl` 没有这些方法）。

- [ ] **Step 3: 实现服务（整体覆盖旧实现）**

`src/main/java/com/hq/goods/lang/service/impl/GoodsDocServiceImpl.java`（整体覆盖旧内容）:

```java
package com.hq.goods.lang.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hq.goods.lang.bean.CustomException;
import com.hq.goods.lang.bean.CustomsEnum;
import com.hq.goods.lang.bean.ResponseEnum;
import com.hq.goods.lang.bean.dto.AiTranslateDto;
import com.hq.goods.lang.bean.dto.GenerateDescReq;
import com.hq.goods.lang.bean.dto.GenerateMultiReq;
import com.hq.goods.lang.bean.dto.ParsePartReq;
import com.hq.goods.lang.bean.dto.ParseTextReq;
import com.hq.goods.lang.bean.dto.SaveReq;
import com.hq.goods.lang.bean.entity.GoodsDocRecord;
import com.hq.goods.lang.bean.entity.TranslationTerm;
import com.hq.goods.lang.bean.vo.GoodsDocDescVo;
import com.hq.goods.lang.bean.vo.GoodsDocMultiVo;
import com.hq.goods.lang.bean.vo.GoodsDocRecordVo;
import com.hq.goods.lang.bean.vo.GoodsDocVo;
import com.hq.goods.lang.bean.vo.PageResult;
import com.hq.goods.lang.bean.vo.ParamItem;
import com.hq.goods.lang.bean.vo.ProductVo;
import com.hq.goods.lang.bean.vo.RagHit;
import com.hq.goods.lang.bean.vo.RecordVo;
import com.hq.goods.lang.bean.vo.SeoVo;
import com.hq.goods.lang.dao.GoodsDocRecordDao;
import com.hq.goods.lang.service.AiLLMService;
import com.hq.goods.lang.service.GoodsDocService;
import com.hq.goods.lang.service.RagService;
import com.hq.goods.lang.service.TranslationService;
import com.hq.goods.lang.utils.GoodsDocParseUtil;
import com.hq.goods.lang.utils.TranslatorProviderFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 外贸商品文档编排服务实现
 */
@Slf4j
@Service
public class GoodsDocServiceImpl implements GoodsDocService {

    private static final String MODEL = "gpt-5.5";
    private static final int RAG_TOP_K = 3;

    @Autowired
    private TranslatorProviderFactory translatorProviderFactory;
    @Autowired
    private AiLLMService aiLLMService;
    @Autowired
    private TranslationService translationService;
    @Autowired
    private RagService ragService;
    @Autowired
    private GoodsDocRecordDao goodsDocRecordDao;

    // ---------- 解析 ----------

    @Override
    public GoodsDocVo parsePart(ParsePartReq req) {
        String query = StringUtils.defaultString(req.getPartNumber())
                + " " + StringUtils.defaultString(req.getBrand());
        return doParse(query.trim(), req.getPartNumber(), req.getBrand());
    }

    @Override
    public GoodsDocVo parseText(ParseTextReq req) {
        return doParse(req.getRawText(), null, null);
    }

    private GoodsDocVo doParse(String query, String partNumber, String brand) {
        List<RagHit> topK = ragService.searchTopK(query, RAG_TOP_K);
        List<TranslationTerm> terms = translationService.recallTerms(query);
        String sys = buildParseSystemPrompt();
        String user = buildParseUserPrompt(query, topK, terms);
        String res = callLlmWithRetry(sys, user);
        GoodsDocVo vo = GoodsDocParseUtil.toGoodsDocVo(GoodsDocParseUtil.extractJson(res));
        if (StringUtils.isBlank(vo.getPartNumber())) {
            vo.setPartNumber(partNumber);
        }
        if (StringUtils.isBlank(vo.getBrand())) {
            vo.setBrand(brand);
        }
        vo.setTopK(topK);
        vo.setRawInput(query);
        return vo;
    }

    // ---------- 英文描述 ----------

    @Override
    public GoodsDocDescVo generateDesc(GenerateDescReq req) {
        GoodsDocVo vo = req.getGoodsDoc();
        List<TranslationTerm> terms = translationService.recallTerms(buildQueryFromVo(vo));
        String sys = buildDescSystemPrompt();
        String user = buildDescUserPrompt(vo, terms);
        String res = callLlm(sys, user);
        return new GoodsDocDescVo(res);
    }

    // ---------- 多语言 + SEO ----------

    @Override
    public GoodsDocMultiVo generateMulti(GenerateMultiReq req) {
        GoodsDocVo vo = req.getGoodsDoc();
        String description = StringUtils.defaultString(req.getDescription());

        Map<String, String> multilingual = new LinkedHashMap<>();
        multilingual.put("en", description);
        multilingual.put("zh", translate(description, CustomsEnum.LANG_ZH.getCode()));
        multilingual.put("zhTw", translate(description, CustomsEnum.LANG_ZH_TW.getCode()));
        multilingual.put("ja", translate(description, CustomsEnum.LANG_JP.getCode()));
        multilingual.put("ru", translate(description, CustomsEnum.LANG_RU.getCode()));

        Map<String, SeoVo> seo = generateSeo(vo, description);

        GoodsDocMultiVo result = new GoodsDocMultiVo();
        result.setMultilingual(multilingual);
        result.setSeo(seo);
        return result;
    }

    private String translate(String text, Integer target) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        try {
            AiTranslateDto dto = new AiTranslateDto();
            dto.setText(text);
            dto.setTarget(target);
            dto.setSource(0);
            return aiLLMService.aiTranslate(dto).getTranslation();
        } catch (Exception e) {
            log.warn("[GoodsDoc] 翻译失败 target={}", target, e);
            return "";
        }
    }

    private Map<String, SeoVo> generateSeo(GoodsDocVo vo, String description) {
        List<TranslationTerm> terms = translationService.recallTerms(buildQueryFromVo(vo));
        String sys = buildSeoSystemPrompt();
        String user = buildSeoUserPrompt(vo, description, terms);
        try {
            String res = callLlm(sys, user);
            return GoodsDocParseUtil.parseSeo(GoodsDocParseUtil.extractJson(res));
        } catch (Exception e) {
            log.warn("[GoodsDoc] SEO 生成失败，返回空", e);
            return Collections.emptyMap();
        }
    }

    // ---------- 保存 / 列表 / 详情 / 删除 / 产品 ----------

    @Override
    public Long save(SaveReq req) {
        GoodsDocVo vo = req.getGoodsDoc();
        if (vo == null) {
            throw new CustomException(ResponseEnum.PARAMETER_ERROR.getCode(), "基本资料不能为空");
        }
        GoodsDocRecord record = toRecord(req);
        if (record.getId() == null) {
            record.setDeleteStatus(0);
            record.setStatus(0);
            record.setCTime(LocalDateTime.now());
            goodsDocRecordDao.insert(record);
        } else {
            record.setUTime(LocalDateTime.now());
            goodsDocRecordDao.updateById(record);
        }
        return record.getId();
    }

    @Override
    public PageResult<RecordVo> list(int page, int size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 100) {
            size = 20;
        }
        Page<GoodsDocRecord> p = new Page<>(page, size);
        goodsDocRecordDao.selectPage(p,
                new QueryWrapper<GoodsDocRecord>().eq("delete_status", 0).orderByDesc("c_time"));
        List<RecordVo> list = p.getRecords().stream().map(this::toRecordVo).collect(Collectors.toList());
        return new PageResult<>(p.getTotal(), list);
    }

    @Override
    public GoodsDocRecordVo detail(Long id) {
        GoodsDocRecord r = requireRecord(id);
        GoodsDocRecordVo vo = new GoodsDocRecordVo();
        vo.setId(r.getId());
        vo.setBasic(toGoodsDocVo(r));
        vo.setMultilingual(parseJsonMap(r.getMultilingual(), String.class));
        vo.setSeo(parseJsonMap(r.getSeo(), SeoVo.class));
        vo.setSourceType(r.getSourceType());
        vo.setCTime(r.getCTime());
        vo.setUTime(r.getUTime());
        return vo;
    }

    @Override
    public void delete(Long id) {
        GoodsDocRecord r = new GoodsDocRecord();
        r.setId(id);
        r.setDeleteStatus(1);
        goodsDocRecordDao.updateById(r);
    }

    @Override
    public ProductVo product(Long id) {
        GoodsDocRecord r = requireRecord(id);
        ProductVo vo = new ProductVo();
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
        return vo;
    }

    private GoodsDocRecord requireRecord(Long id) {
        GoodsDocRecord r = goodsDocRecordDao.selectById(id);
        if (r == null || Integer.valueOf(1).equals(r.getDeleteStatus())) {
            throw new CustomException(ResponseEnum.PARAMETER_ERROR.getCode(), "记录不存在");
        }
        return r;
    }

    // ---------- LLM 调用 ----------

    private String callLlm(String sys, String user) {
        String res = translatorProviderFactory.getProvider().translate(MODEL, sys, user);
        if (StringUtils.isBlank(res)) {
            throw new CustomException(ResponseEnum.INNER_SERVER_ERROR.getCode(), "AI 生成结果为空，请重试");
        }
        return res.trim();
    }

    private String callLlmWithRetry(String sys, String user) {
        String res = callLlm(sys, user);
        try {
            GoodsDocParseUtil.validateJson(res);
        } catch (Exception e) {
            log.warn("[GoodsDoc] AI 输出解析失败，重试一次");
            res = callLlm(sys, user);
            GoodsDocParseUtil.validateJson(res);
        }
        return res;
    }

    // ---------- Prompt 构建 ----------

    private String buildParseSystemPrompt() {
        return "你是电子元器件领域的资深专家。请把用户提供的元器件信息解析为统一 JSON，严格按以下字段输出（无法识别的字段返回空字符串或空数组，不要编造）：\n"
                + "{\n"
                + "  \"partNumber\": \"型号\", \"brand\": \"品牌\", \"category\": \"分类\", \"subcategory\": \"子分类\", \"series\": \"系列\",\n"
                + "  \"packageType\": \"封装\", \"mountingType\": \"安装类型\", \"pinCount\": 0, \"dimensions\": \"尺寸\",\n"
                + "  \"parameters\": [{\"name\": \"参数名\", \"value\": \"数值\", \"unit\": \"单位\"}],\n"
                + "  \"operatingTemp\": \"工作温度范围\", \"storageTemp\": \"存储温度\", \"grade\": \"质量等级\", \"rohs\": \"RoHS/环保\",\n"
                + "  \"packaging\": \"包装方式\", \"moq\": \"最小起订量\", \"unit\": \"单位\", \"hsCode\": \"海关编码\",\n"
                + "  \"leadTime\": \"交期\", \"priceRange\": \"价格区间\", \"availability\": \"供货状态\",\n"
                + "  \"applications\": [\"应用领域\"]\n"
                + "}\n"
                + "规则：1.只输出 JSON，不要解释、前后缀或 markdown 代码块；"
                + "2.parameters 为键值数组，按品类提取关键参数（如阻值、容值、耐压、电流、频率、精度、内核、Flash、RAM 等）；"
                + "3.无法识别的字段返回空字符串或空数组。";
    }

    private String buildParseUserPrompt(String query, List<RagHit> topK, List<TranslationTerm> terms) {
        StringBuilder sb = new StringBuilder();
        sb.append("请解析以下元器件信息并输出 JSON：\n\n原始输入：\n").append(query).append("\n");
        if (topK != null && !topK.isEmpty()) {
            sb.append("\n相似型号参考（辅助判断分类与参数格式，仅供参考）：\n");
            for (int i = 0; i < topK.size(); i++) {
                RagHit h = topK.get(i);
                sb.append(i + 1).append(". ").append(h.getPartNumber())
                        .append(" | ").append(StringUtils.defaultString(h.getBrand())).append("\n");
            }
        }
        String termsSection = buildTermSection(terms);
        if (!termsSection.isEmpty()) {
            sb.append("\n").append(termsSection);
        }
        return sb.toString();
    }

    private String buildDescSystemPrompt() {
        return "你是电子元器件行业的海外英文产品描述撰写专家。请根据提供的元器件结构化参数，撰写一段专业、真实、适合海外英文网站展示的产品描述（2-4 句）。必须基于真实参数，不得编造未提供的参数。全英文输出，直接给出描述正文，不要标题或解释。";
    }

    private String buildDescUserPrompt(GoodsDocVo vo, List<TranslationTerm> terms) {
        StringBuilder sb = new StringBuilder("元器件信息：\n");
        appendVoFields(sb, vo);
        sb.append("关键参数：").append(renderParams(vo.getParameters())).append("\n");
        sb.append("应用领域：").append(vo.getApplications() == null ? ""
                : String.join(", ", vo.getApplications())).append("\n");
        String termsSection = buildTermSection(terms);
        if (!termsSection.isEmpty()) {
            sb.append("\n").append(termsSection);
        }
        sb.append("\n请生成英文产品描述。");
        return sb.toString();
    }

    private String buildSeoSystemPrompt() {
        return "你是电子元器件跨境电商 SEO 专家。请根据元器件信息生成 简体中文(zh)、英文(en)、日文(ja)、俄文(ru) 四种语言的 SEO 内容。严格只输出以下 JSON，不要任何其他内容：\n"
                + "{\"zh\": {\"title\": \"...\", \"keywords\": [\"...\"], \"description\": \"...\"},\n"
                + " \"en\": {\"title\": \"...\", \"keywords\": [\"...\"], \"description\": \"...\"},\n"
                + " \"ja\": {\"title\": \"...\", \"keywords\": [\"...\"], \"description\": \"...\"},\n"
                + " \"ru\": {\"title\": \"...\", \"keywords\": [\"...\"], \"description\": \"...\"}}\n"
                + "要求：title 30-60 字符；keywords 5-10 个，包含型号、品牌、品类词；description 50-160 字符，含型号与关键卖点。";
    }

    private String buildSeoUserPrompt(GoodsDocVo vo, String description, List<TranslationTerm> terms) {
        StringBuilder sb = new StringBuilder("元器件信息：\n");
        appendVoFields(sb, vo);
        sb.append("关键参数：").append(renderParams(vo.getParameters())).append("\n");
        sb.append("英文描述：").append(StringUtils.defaultString(description)).append("\n");
        String termsSection = buildTermSection(terms);
        if (!termsSection.isEmpty()) {
            sb.append("\n").append(termsSection);
        }
        sb.append("\n请生成四种语言的 SEO JSON。");
        return sb.toString();
    }

    private void appendVoFields(StringBuilder sb, GoodsDocVo vo) {
        if (vo == null) {
            return;
        }
        appendField(sb, "型号", vo.getPartNumber());
        appendField(sb, "品牌", vo.getBrand());
        appendField(sb, "分类", vo.getCategory());
        appendField(sb, "子分类", vo.getSubcategory());
        appendField(sb, "系列", vo.getSeries());
        appendField(sb, "封装", vo.getPackageType());
        appendField(sb, "安装类型", vo.getMountingType());
        appendField(sb, "引脚数", vo.getPinCount() == null ? null : String.valueOf(vo.getPinCount()));
        appendField(sb, "尺寸", vo.getDimensions());
        appendField(sb, "工作温度", vo.getOperatingTemp());
        appendField(sb, "存储温度", vo.getStorageTemp());
        appendField(sb, "质量等级", vo.getGrade());
        appendField(sb, "RoHS", vo.getRohs());
        appendField(sb, "包装方式", vo.getPackaging());
        appendField(sb, "MOQ", vo.getMoq());
        appendField(sb, "单位", vo.getUnit());
        appendField(sb, "海关编码", vo.getHsCode());
        appendField(sb, "交期", vo.getLeadTime());
        appendField(sb, "价格区间", vo.getPriceRange());
        appendField(sb, "供货状态", vo.getAvailability());
    }

    private void appendField(StringBuilder sb, String label, String value) {
        if (StringUtils.isNotBlank(value)) {
            sb.append(label).append("：").append(value).append("\n");
        }
    }

    private String buildTermSection(List<TranslationTerm> terms) {
        if (terms == null || terms.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("术语对照（术语命名遵循以下对照，不得使用其他译法）：\n");
        for (TranslationTerm t : terms) {
            if (StringUtils.isBlank(t.getCn()) || StringUtils.isBlank(t.getEn())) {
                continue;
            }
            sb.append(t.getCn()).append("=").append(t.getEn()).append(" | ");
        }
        return sb.toString();
    }

    private String renderParams(List<ParamItem> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        return params.stream()
                .filter(p -> p != null && StringUtils.isNotBlank(p.getName()))
                .map(p -> p.getName() + ": " + StringUtils.defaultString(p.getValue())
                        + " " + StringUtils.defaultString(p.getUnit()))
                .collect(Collectors.joining("; "));
    }

    private String buildQueryFromVo(GoodsDocVo vo) {
        if (vo == null) {
            return "";
        }
        return StringUtils.defaultString(vo.getPartNumber()) + " "
                + StringUtils.defaultString(vo.getBrand()) + " "
                + StringUtils.defaultString(vo.getCategory()) + " "
                + StringUtils.defaultString(vo.getPackageType());
    }

    // ---------- 实体/VO 映射 ----------

    private GoodsDocRecord toRecord(SaveReq req) {
        GoodsDocVo vo = req.getGoodsDoc();
        GoodsDocRecord r = new GoodsDocRecord();
        r.setId(req.getId());
        r.setPartNumber(vo.getPartNumber());
        r.setBrand(vo.getBrand());
        r.setCategory(vo.getCategory());
        r.setSubcategory(vo.getSubcategory());
        r.setSeries(vo.getSeries());
        r.setPackageType(vo.getPackageType());
        r.setMountingType(vo.getMountingType());
        r.setPinCount(vo.getPinCount());
        r.setDimensions(vo.getDimensions());
        r.setParameters(vo.getParameters() == null ? null : JSON.toJSONString(vo.getParameters()));
        r.setOperatingTemp(vo.getOperatingTemp());
        r.setStorageTemp(vo.getStorageTemp());
        r.setGrade(vo.getGrade());
        r.setRohs(vo.getRohs());
        r.setPackaging(vo.getPackaging());
        r.setMoq(vo.getMoq());
        r.setUnit(vo.getUnit());
        r.setHsCode(vo.getHsCode());
        r.setLeadTime(vo.getLeadTime());
        r.setPriceRange(vo.getPriceRange());
        r.setAvailability(vo.getAvailability());
        r.setDatasheetUrl(vo.getDatasheetUrl());
        r.setImageUrl(vo.getImageUrl());
        r.setApplications(vo.getApplications() == null ? null : JSON.toJSONString(vo.getApplications()));
        r.setDescriptionEn(vo.getDescriptionEn());
        r.setMultilingual(req.getMultilingual() == null ? null : JSON.toJSONString(req.getMultilingual()));
        r.setSeo(req.getSeo() == null ? null : JSON.toJSONString(req.getSeo()));
        r.setRawInput(vo.getRawInput());
        r.setSourceType(req.getSourceType());
        return r;
    }

    private RecordVo toRecordVo(GoodsDocRecord r) {
        RecordVo vo = new RecordVo();
        vo.setId(r.getId());
        vo.setPartNumber(r.getPartNumber());
        vo.setBrand(r.getBrand());
        vo.setCategory(r.getCategory());
        vo.setPackageType(r.getPackageType());
        vo.setCTime(r.getCTime());
        return vo;
    }

    private GoodsDocVo toGoodsDocVo(GoodsDocRecord r) {
        GoodsDocVo vo = new GoodsDocVo();
        vo.setPartNumber(r.getPartNumber());
        vo.setBrand(r.getBrand());
        vo.setCategory(r.getCategory());
        vo.setSubcategory(r.getSubcategory());
        vo.setSeries(r.getSeries());
        vo.setPackageType(r.getPackageType());
        vo.setMountingType(r.getMountingType());
        vo.setPinCount(r.getPinCount());
        vo.setDimensions(r.getDimensions());
        vo.setParameters(parseJsonArray(r.getParameters(), ParamItem.class));
        vo.setOperatingTemp(r.getOperatingTemp());
        vo.setStorageTemp(r.getStorageTemp());
        vo.setGrade(r.getGrade());
        vo.setRohs(r.getRohs());
        vo.setPackaging(r.getPackaging());
        vo.setMoq(r.getMoq());
        vo.setUnit(r.getUnit());
        vo.setHsCode(r.getHsCode());
        vo.setLeadTime(r.getLeadTime());
        vo.setPriceRange(r.getPriceRange());
        vo.setAvailability(r.getAvailability());
        vo.setDatasheetUrl(r.getDatasheetUrl());
        vo.setImageUrl(r.getImageUrl());
        vo.setApplications(parseJsonArray(r.getApplications(), String.class));
        vo.setDescriptionEn(r.getDescriptionEn());
        vo.setRawInput(r.getRawInput());
        return vo;
    }

    private <T> List<T> parseJsonArray(String json, Class<T> clazz) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyList();
        }
        List<T> list = JSON.parseArray(json, clazz);
        return list == null ? Collections.emptyList() : list;
    }

    private <V> Map<String, V> parseJsonMap(String json, Class<V> clazz) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyMap();
        }
        JSONObject obj = JSON.parseObject(json);
        if (obj == null) {
            return Collections.emptyMap();
        }
        Map<String, V> result = new HashMap<>();
        for (String key : obj.keySet()) {
            String item = obj.getString(key);
            if (StringUtils.isBlank(item)) {
                continue;
            }
            result.put(key, JSON.parseObject(item, clazz));
        }
        return result;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
JAVA_HOME="E:\Java\java11-dragonwell-11.0.31.27" mvn -q test -Dtest=GoodsDocServiceImplTest
```

Expected: PASS（11 个测试全部通过）。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/hq/goods/lang/service/impl/GoodsDocServiceImpl.java src/test/java/com/hq/goods/lang/service/impl/GoodsDocServiceImplTest.java
git commit -m "feat: 商品文档服务编排实现 + 单测"
```

---

## Task 11: 控制器（9 个接口）

**Files:**
- Modify: `src/main/java/com/hq/goods/lang/controller/GoodsDocController.java`

- [ ] **Step 1: 用完整控制器整体覆盖旧空类**

`src/main/java/com/hq/goods/lang/controller/GoodsDocController.java`:

```java
package com.hq.goods.lang.controller;

import com.hq.goods.lang.bean.ResultBody;
import com.hq.goods.lang.bean.dto.GenerateDescReq;
import com.hq.goods.lang.bean.dto.GenerateMultiReq;
import com.hq.goods.lang.bean.dto.ParsePartReq;
import com.hq.goods.lang.bean.dto.ParseTextReq;
import com.hq.goods.lang.bean.dto.SaveReq;
import com.hq.goods.lang.service.GoodsDocService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 外贸商品文档接口
 */
@RestController
@RequestMapping("/api/doc")
public class GoodsDocController {

    @Autowired
    private GoodsDocService goodsDocService;

    /** ① 型号+品牌解析 */
    @PostMapping("/parsePart")
    public String parsePart(@RequestBody @Valid ParsePartReq req) {
        return ResultBody.success(goodsDocService.parsePart(req));
    }

    /** ② 描述文本解析 */
    @PostMapping("/parseText")
    public String parseText(@RequestBody @Valid ParseTextReq req) {
        return ResultBody.success(goodsDocService.parseText(req));
    }

    /** ③ 英文标准描述生成 */
    @PostMapping("/generateDesc")
    public String generateDesc(@RequestBody @Valid GenerateDescReq req) {
        return ResultBody.success(goodsDocService.generateDesc(req));
    }

    /** ④ 多语言 + SEO 生成 */
    @PostMapping("/generateMulti")
    public String generateMulti(@RequestBody @Valid GenerateMultiReq req) {
        return ResultBody.success(goodsDocService.generateMulti(req));
    }

    /** ⑤ 保存（新增/更新） */
    @PostMapping("/save")
    public String save(@RequestBody @Valid SaveReq req) {
        return ResultBody.success(goodsDocService.save(req));
    }

    /** ⑥ 历史分页列表 */
    @GetMapping("/list")
    public String list(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "20") int size) {
        return ResultBody.success(goodsDocService.list(page, size));
    }

    /** ⑦ 后台详情 */
    @GetMapping("/detail")
    public String detail(@RequestParam Long id) {
        return ResultBody.success(goodsDocService.detail(id));
    }

    /** ⑧ 逻辑删除 */
    @DeleteMapping("/delete")
    public String delete(@RequestParam Long id) {
        goodsDocService.delete(id);
        return ResultBody.success();
    }

    /** ⑨ 客户页面公开数据（官网 SSR） */
    @GetMapping("/product/{id}")
    public String product(@PathVariable Long id) {
        return ResultBody.success(goodsDocService.product(id));
    }
}
```

- [ ] **Step 2: 全量编译 + 运行全部测试**

```bash
cd "E:\workspace\goods-doc-lang"
JAVA_HOME="E:\Java\java11-dragonwell-11.0.31.27" mvn -q test
```

Expected: PASS（`GoodsDocParseUtilTest` + `RagServiceImplTest` + `GoodsDocServiceImplTest` 全部通过；`StringTest` 无 `@Test` 不执行）。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/hq/goods/lang/controller/GoodsDocController.java
git commit -m "feat: 商品文档 9 个接口"
```

---

## Task 12: 前端页面（Vue3 CDN，三视图）

**Files:**
- Modify: `src/main/resources/static/index.html`（整体重写）
- Create: `src/main/resources/static/images/default/README.txt`（默认素材目录占位）

- [ ] **Step 1: 新建默认素材目录**

```bash
mkdir -p "E:\workspace\goods-doc-lang\src\main\resources\static\images\default"
printf '在此目录放置默认图片/PDF（如 product-default.png），供业务员手动维护 imageUrl/datasheetUrl 时引用。\n' > "E:\workspace\goods-doc-lang\src\main\resources\static\images\default\README.txt"
```

- [ ] **Step 2: 整体重写 `index.html`（内容如下）**

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>外贸商品信息标准化与多语言 SEO 系统</title>
<script src="https://unpkg.com/vue@3/dist/vue.global.prod.js"></script>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f0f2f5; color: #333; }
  .container { max-width: 1100px; margin: 0 auto; padding: 20px; }
  nav { display: flex; gap: 8px; margin-bottom: 16px; }
  nav button { padding: 8px 20px; border: none; border-radius: 6px; background: #fff; color: #555; cursor: pointer; font-size: 14px; }
  nav button.active { background: #1a73e8; color: #fff; }
  h1 { text-align: center; color: #1a73e8; font-size: 22px; margin-bottom: 4px; }
  .subtitle { text-align: center; color: #888; font-size: 13px; margin-bottom: 20px; }
  .card { background: #fff; border-radius: 10px; padding: 20px; margin-bottom: 16px; box-shadow: 0 1px 6px rgba(0,0,0,0.08); }
  .card h3 { font-size: 15px; color: #333; margin-bottom: 12px; border-left: 3px solid #1a73e8; padding-left: 8px; }
  .row { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
  input, textarea, select { padding: 8px 12px; border: 1px solid #ddd; border-radius: 6px; font-size: 14px; outline: none; }
  input:focus, textarea:focus { border-color: #1a73e8; }
  input[type=text], select { min-width: 130px; }
  textarea { width: 100%; min-height: 80px; resize: vertical; margin-top: 8px; }
  button { padding: 8px 18px; border: none; border-radius: 6px; background: #1a73e8; color: #fff; cursor: pointer; font-size: 14px; }
  button:hover { background: #1557b5; }
  button:disabled { background: #a0c4ff; cursor: not-allowed; }
  button.secondary { background: #f0f2f5; color: #555; }
  button.danger { background: #d93025; }
  .grid3 { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 10px; }
  .field label { display: block; font-size: 12px; color: #888; margin-bottom: 4px; }
  .field input, .field select { width: 100%; }
  .param-row { display: flex; gap: 8px; align-items: center; margin-bottom: 6px; }
  .param-row input { flex: 1; }
  .lang-card { background: #f8f9fa; border-radius: 8px; padding: 12px; margin-bottom: 10px; }
  .lang-card h4 { font-size: 13px; color: #1a73e8; margin-bottom: 6px; }
  .lang-card textarea { min-height: 70px; }
  .seo-card { background: #f8f9fa; border-radius: 8px; padding: 12px; margin-bottom: 10px; }
  .seo-card h4 { font-size: 13px; color: #1a73e8; margin-bottom: 6px; }
  .seo-card input { width: 100%; }
  table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 8px; overflow: hidden; }
  th, td { padding: 10px 12px; text-align: left; font-size: 13px; border-bottom: 1px solid #eee; }
  th { background: #f8f9fa; color: #555; }
  .tag { display: inline-block; background: #e8f0fe; color: #1a73e8; padding: 2px 10px; border-radius: 12px; font-size: 12px; margin: 2px; }
  .topk { display: flex; gap: 10px; flex-wrap: wrap; }
  .topk-item { background: #f8f9fa; border-radius: 6px; padding: 6px 10px; font-size: 12px; }
  .error { color: #d93025; background: #fce8e6; border-radius: 6px; padding: 10px 14px; margin-bottom: 12px; }
  .success { color: #188038; background: #e6f4ea; border-radius: 6px; padding: 10px 14px; margin-bottom: 12px; }
  .pagination { display: flex; gap: 8px; align-items: center; margin-top: 12px; }
  .muted { color: #888; font-size: 12px; }
  .label { font-weight: 600; color: #333; display: block; margin: 8px 0 4px; font-size: 13px; }
  .kv { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 8px 16px; font-size: 13px; }
  .kv div b { color: #666; font-weight: 500; }
  .banner { background: #fef7e0; border: 1px solid #f9c74f; color: #8a6d1a; border-radius: 6px; padding: 8px 14px; margin-bottom: 12px; font-size: 13px; }
</style>
</head>
<body>
<div id="app">
  <div class="container">
    <h1>🌐 外贸商品信息标准化与多语言 SEO 系统</h1>
    <p class="subtitle">AI 解析 · 英文描述 · 多语言 · SEO · 保存审核</p>

    <nav>
      <button :class="{active: view==='workbench'}" @click="switchView('workbench')">工作台</button>
      <button :class="{active: view==='list'}" @click="goList()">历史记录</button>
    </nav>

    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="success" class="success">{{ success }}</div>
    <div v-if="editingId" class="banner">正在编辑记录 #{{ editingId }}，保存后更新该记录。 <a href="javascript:;" @click="cancelEdit()" style="color:#1a73e8">取消编辑</a></div>

    <!-- ============ 工作台 ============ -->
    <template v-if="view==='workbench'">
      <div class="card">
        <h3>① 输入与解析</h3>
        <div class="row">
          <input type="text" v-model="partNumber" placeholder="型号，如 STM32F103C8T6" @keyup.enter="parsePart">
          <input type="text" v-model="brand" placeholder="品牌（可选）">
          <button @click="parsePart" :disabled="loading">{{ loading ? '解析中...' : '解析' }}</button>
        </div>
        <textarea v-model="rawText" placeholder="粘贴大段描述（含型号/品牌/属性等），点「解析描述」提取结构化字段"></textarea>
        <div class="row" style="margin-top:8px;">
          <button class="secondary" @click="parseText" :disabled="loading">{{ loading ? '解析中...' : '解析描述' }}</button>
        </div>
        <div v-if="goodsDoc && goodsDoc.topK && goodsDoc.topK.length" style="margin-top:12px;">
          <div class="muted" style="margin-bottom:6px;">🔎 RAG 召回参考（TOP-K）</div>
          <div class="topk">
            <span v-for="(h, i) in goodsDoc.topK" :key="h.partNumber + i" class="topk-item">{{ h.partNumber }} <span class="muted">{{ h.brand || '' }} · {{ h.score }}分</span></span>
          </div>
        </div>
      </div>

      <div class="card" v-if="goodsDoc">
        <h3>② 基本资料</h3>
        <div class="grid3">
          <div class="field"><label>型号 *</label><input v-model="goodsDoc.partNumber" placeholder="型号"></div>
          <div class="field"><label>品牌</label><input v-model="goodsDoc.brand" placeholder="品牌"></div>
          <div class="field"><label>分类</label><input v-model="goodsDoc.category" placeholder="分类"></div>
          <div class="field"><label>子分类</label><input v-model="goodsDoc.subcategory" placeholder="子分类"></div>
          <div class="field"><label>系列</label><input v-model="goodsDoc.series" placeholder="系列"></div>
          <div class="field"><label>封装</label><input v-model="goodsDoc.packageType" placeholder="封装"></div>
          <div class="field"><label>安装类型</label><input v-model="goodsDoc.mountingType" placeholder="SMD/THT"></div>
          <div class="field"><label>引脚数</label><input v-model="goodsDoc.pinCount" placeholder="引脚数"></div>
          <div class="field"><label>尺寸</label><input v-model="goodsDoc.dimensions" placeholder="尺寸"></div>
          <div class="field"><label>工作温度</label><input v-model="goodsDoc.operatingTemp" placeholder="如 -40~+85℃"></div>
          <div class="field"><label>存储温度</label><input v-model="goodsDoc.storageTemp" placeholder="存储温度"></div>
          <div class="field"><label>质量等级</label><input v-model="goodsDoc.grade" placeholder="质量等级"></div>
          <div class="field"><label>RoHS</label><input v-model="goodsDoc.rohs" placeholder="RoHS/环保"></div>
          <div class="field"><label>包装方式</label><input v-model="goodsDoc.packaging" placeholder="编带/托盘/管装"></div>
          <div class="field"><label>MOQ</label><input v-model="goodsDoc.moq" placeholder="最小起订量"></div>
          <div class="field"><label>单位</label><input v-model="goodsDoc.unit" placeholder="pcs/盘"></div>
          <div class="field"><label>海关编码</label><input v-model="goodsDoc.hsCode" placeholder="海关编码"></div>
          <div class="field"><label>交期</label><input v-model="goodsDoc.leadTime" placeholder="交期"></div>
          <div class="field"><label>价格区间</label><input v-model="goodsDoc.priceRange" placeholder="价格区间"></div>
          <div class="field"><label>供货状态</label><input v-model="goodsDoc.availability" placeholder="现货/订货"></div>
          <div class="field"><label>数据手册URL</label><input v-model="goodsDoc.datasheetUrl" placeholder="业务员维护"></div>
          <div class="field"><label>图片URL</label><input v-model="goodsDoc.imageUrl" placeholder="业务员维护"></div>
        </div>
        <div style="margin-top:12px;">
          <span class="label">参数（动态增删）</span>
          <div v-for="(p, i) in goodsDoc.parameters" :key="i" class="param-row">
            <input v-model="p.name" placeholder="参数名">
            <input v-model="p.value" placeholder="数值">
            <input v-model="p.unit" placeholder="单位">
            <button class="secondary" @click="removeParam(i)">删除</button>
          </div>
          <button class="secondary" @click="addParam">+ 添加参数</button>
          <span class="label">应用领域</span>
          <div class="row">
            <input type="text" v-model="newApp" placeholder="如：消费电子" style="flex:1" @keyup.enter="addApp">
            <button class="secondary" @click="addApp">添加</button>
          </div>
          <div class="row" style="margin-top:6px;">
            <span v-for="(a, i) in goodsDoc.applications" :key="i" class="tag">{{ a }} <span style="cursor:pointer" @click="goodsDoc.applications.splice(i,1)">×</span></span>
          </div>
        </div>
        <div style="margin-top:14px;">
          <button @click="generateDesc" :disabled="loading">{{ loading ? '生成中...' : '③ 生成英文描述' }}</button>
        </div>
        <div v-if="goodsDoc.descriptionEn" style="margin-top:12px;">
          <span class="label">英文描述（可编辑）</span>
          <textarea v-model="goodsDoc.descriptionEn"></textarea>
        </div>
      </div>

      <div class="card" v-if="goodsDoc && goodsDoc.descriptionEn">
        <h3>④ 多语言 + SEO 生成</h3>
        <button @click="generateMulti" :disabled="loading">{{ loading ? '生成中...' : '生成多语言 + SEO' }}</button>
        <div v-if="multiResult">
          <div style="margin-top:14px;">
            <span class="label">多语言描述（可编辑）</span>
            <div v-for="(label, code) in langLabels" :key="code" class="lang-card" v-if="multiResult.multilingual && multiResult.multilingual[code]">
              <h4>{{ label }}</h4>
              <textarea v-model="multiResult.multilingual[code]"></textarea>
            </div>
          </div>
          <div style="margin-top:14px;">
            <span class="label">SEO（中/英/日/俄）</span>
            <div v-for="(label, code) in seoLabels" :key="code" class="seo-card" v-if="multiResult.seo && multiResult.seo[code]">
              <h4>{{ label }} <button class="secondary copy" @click="copySeo(code)">复制</button></h4>
              <div class="field"><label>Title</label><input v-model="multiResult.seo[code].title"></div>
              <div class="field" style="margin-top:6px;"><label>Keywords（逗号分隔）</label><input v-model="multiResult.seo[code].keywordsText"></div>
              <div class="field" style="margin-top:6px;"><label>Description</label><textarea v-model="multiResult.seo[code].description"></textarea></div>
            </div>
          </div>
        </div>
      </div>

      <div class="card" v-if="goodsDoc">
        <button @click="save" :disabled="loading">{{ loading ? '保存中...' : editingId ? '保存修改' : '保存到历史' }}</button>
      </div>
    </template>

    <!-- ============ 历史列表 ============ -->
    <template v-if="view==='list'">
      <div class="card">
        <h3>历史记录</h3>
        <table>
          <thead><tr><th>ID</th><th>型号</th><th>品牌</th><th>分类</th><th>封装</th><th>创建时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="r in records" :key="r.id">
              <td>{{ r.id }}</td><td>{{ r.partNumber }}</td><td>{{ r.brand }}</td><td>{{ r.category }}</td><td>{{ r.packageType }}</td><td>{{ r.cTime }}</td>
              <td>
                <button class="secondary" @click="viewDetail(r.id)">查看</button>
                <button class="secondary" @click="editRecord(r.id)">编辑</button>
                <button class="danger" @click="delRecord(r.id)">删除</button>
              </td>
            </tr>
            <tr v-if="!records.length"><td colspan="7" style="text-align:center;color:#aaa;">暂无记录</td></tr>
          </tbody>
        </table>
        <div class="pagination">
          <button class="secondary" @click="changePage(page - 1)" :disabled="page <= 1">上一页</button>
          <span>第 {{ page }} 页 / 共 {{ totalPages }} 页（共 {{ total }} 条）</span>
          <button class="secondary" @click="changePage(page + 1)" :disabled="page >= totalPages">下一页</button>
        </div>
      </div>
    </template>

    <!-- ============ 只读详情 ============ -->
    <template v-if="view==='detail' && detail">
      <div class="card">
        <h3>记录 #{{ detail.id }} 详情</h3>
        <button class="secondary" @click="editRecord(detail.id)" style="margin-bottom:12px;">编辑</button>
        <div class="kv">
          <div v-if="detail.basic.partNumber"><b>型号：</b>{{ detail.basic.partNumber }}</div>
          <div v-if="detail.basic.brand"><b>品牌：</b>{{ detail.basic.brand }}</div>
          <div v-if="detail.basic.category"><b>分类：</b>{{ detail.basic.category }}</div>
          <div v-if="detail.basic.subcategory"><b>子分类：</b>{{ detail.basic.subcategory }}</div>
          <div v-if="detail.basic.series"><b>系列：</b>{{ detail.basic.series }}</div>
          <div v-if="detail.basic.packageType"><b>封装：</b>{{ detail.basic.packageType }}</div>
          <div v-if="detail.basic.mountingType"><b>安装类型：</b>{{ detail.basic.mountingType }}</div>
          <div v-if="detail.basic.pinCount"><b>引脚数：</b>{{ detail.basic.pinCount }}</div>
          <div v-if="detail.basic.dimensions"><b>尺寸：</b>{{ detail.basic.dimensions }}</div>
          <div v-if="detail.basic.operatingTemp"><b>工作温度：</b>{{ detail.basic.operatingTemp }}</div>
          <div v-if="detail.basic.storageTemp"><b>存储温度：</b>{{ detail.basic.storageTemp }}</div>
          <div v-if="detail.basic.grade"><b>质量等级：</b>{{ detail.basic.grade }}</div>
          <div v-if="detail.basic.rohs"><b>RoHS：</b>{{ detail.basic.rohs }}</div>
          <div v-if="detail.basic.packaging"><b>包装方式：</b>{{ detail.basic.packaging }}</div>
          <div v-if="detail.basic.moq"><b>MOQ：</b>{{ detail.basic.moq }}</div>
          <div v-if="detail.basic.unit"><b>单位：</b>{{ detail.basic.unit }}</div>
          <div v-if="detail.basic.hsCode"><b>海关编码：</b>{{ detail.basic.hsCode }}</div>
          <div v-if="detail.basic.leadTime"><b>交期：</b>{{ detail.basic.leadTime }}</div>
          <div v-if="detail.basic.priceRange"><b>价格区间：</b>{{ detail.basic.priceRange }}</div>
          <div v-if="detail.basic.availability"><b>供货状态：</b>{{ detail.basic.availability }}</div>
          <div v-if="detail.basic.datasheetUrl"><b>数据手册：</b>{{ detail.basic.datasheetUrl }}</div>
          <div v-if="detail.basic.imageUrl"><b>图片：</b>{{ detail.basic.imageUrl }}</div>
        </div>
        <template v-if="detail.basic.parameters && detail.basic.parameters.length">
          <span class="label">参数</span>
          <table><thead><tr><th>参数名</th><th>数值</th><th>单位</th></tr></thead>
          <tbody><tr v-for="(p, i) in detail.basic.parameters" :key="i"><td>{{ p.name }}</td><td>{{ p.value }}</td><td>{{ p.unit }}</td></tr></tbody></table>
        </template>
        <template v-if="detail.basic.applications && detail.basic.applications.length">
          <span class="label">应用领域</span>
          <div class="row"><span v-for="(a, i) in detail.basic.applications" :key="i" class="tag">{{ a }}</span></div>
        </template>
        <div v-if="detail.basic.descriptionEn">
          <span class="label">英文描述</span>
          <pre style="white-space:pre-wrap;background:#f8f9fa;border-radius:6px;padding:12px;font-size:13px;">{{ detail.basic.descriptionEn }}</pre>
        </div>
        <template v-if="detail.multilingual">
          <span class="label">多语言描述</span>
          <div v-for="(label, code) in langLabels" :key="code" class="lang-card" v-if="detail.multilingual[code]">
            <h4>{{ label }}</h4><div style="font-size:13px;white-space:pre-wrap;">{{ detail.multilingual[code] }}</div>
          </div>
        </template>
        <template v-if="detail.seo">
          <span class="label">SEO（中/英/日/俄）</span>
          <div v-for="(label, code) in seoLabels" :key="code" class="seo-card" v-if="detail.seo[code]">
            <h4>{{ label }}</h4>
            <div class="muted">Title</div><div style="font-size:13px;">{{ detail.seo[code].title }}</div>
            <div class="muted">Keywords</div><div style="font-size:13px;">{{ (detail.seo[code].keywords || []).join(', ') }}</div>
            <div class="muted">Description</div><div style="font-size:13px;">{{ detail.seo[code].description }}</div>
          </div>
        </template>
      </div>
    </template>
  </div>
</div>

<script>
const { createApp } = Vue;
const API = '/goods/api/doc';

createApp({
  data() {
    return {
      view: 'workbench',
      loading: false, error: '', success: '',
      partNumber: '', brand: '', rawText: '',
      goodsDoc: null, multiResult: null, newApp: '',
      editingId: null,
      records: [], page: 1, size: 10, total: 0, totalPages: 0,
      detail: null,
      langLabels: { en: '🇬🇧 English', zh: '🇨🇳 中文', zhTw: '🇨🇳 繁体中文', ja: '🇯🇵 日本語', ru: '🇷🇺 Русский' },
      seoLabels: { zh: '中文 SEO', en: 'English SEO', ja: '日本語 SEO', ru: 'Русский SEO' }
    };
  },
  methods: {
    clearMsg() { this.error = ''; this.success = ''; },
    async api(path, method, body) {
      const opt = { method: method || 'GET', headers: { 'Content-Type': 'application/json' } };
      if (body !== undefined) opt.body = JSON.stringify(body);
      const resp = await fetch(API + path, opt);
      const result = await resp.json();
      if (result.code !== 200) throw new Error(result.message || '请求失败');
      return result.body;
    },
    wrap(fn) {
      this.clearMsg();
      return fn().catch(e => { this.error = e.message || '请求失败'; this.loading = false; });
    },
    switchView(v) { this.view = v; this.clearMsg(); },
    goList() { this.page = 1; this.switchView('list'); this.loadList(); },

    normalizeVo(vo) {
      if (!vo.parameters) vo.parameters = [];
      if (!vo.applications) vo.applications = [];
      if (!vo.topK) vo.topK = [];
      return vo;
    },
    async parsePart() {
      if (!this.partNumber.trim()) { this.error = '请输入型号'; return; }
      this.loading = true;
      await this.wrap(async () => {
        const vo = await this.api('/parsePart', 'POST', { partNumber: this.partNumber.trim(), brand: this.brand.trim() });
        this.goodsDoc = this.normalizeVo(vo);
        this.multiResult = null; this.editingId = null;
        this.success = '解析完成，请核对基本资料';
      });
      this.loading = false;
    },
    async parseText() {
      if (!this.rawText.trim()) { this.error = '请输入描述文本'; return; }
      this.loading = true;
      await this.wrap(async () => {
        const vo = await this.api('/parseText', 'POST', { rawText: this.rawText.trim() });
        this.goodsDoc = this.normalizeVo(vo);
        this.multiResult = null; this.editingId = null;
        this.success = '解析完成，已覆盖页面字段，请核对';
      });
      this.loading = false;
    },
    async generateDesc() {
      this.loading = true;
      await this.wrap(async () => {
        const body = await this.api('/generateDesc', 'POST', { goodsDoc: this.goodsDoc });
        this.goodsDoc.descriptionEn = body.description;
        this.success = '英文描述已生成';
      });
      this.loading = false;
    },
    async generateMulti() {
      this.loading = true;
      await this.wrap(async () => {
        const body = await this.api('/generateMulti', 'POST', { goodsDoc: this.goodsDoc, description: this.goodsDoc.descriptionEn });
        if (body.seo) {
          Object.values(body.seo).forEach(s => { s.keywordsText = (s.keywords || []).join(', '); });
        }
        this.multiResult = body;
        this.success = '多语言 + SEO 已生成，请核对';
      });
      this.loading = false;
    },
    addParam() { this.goodsDoc.parameters.push({ name: '', value: '', unit: '' }); },
    removeParam(i) { this.goodsDoc.parameters.splice(i, 1); },
    addApp() {
      const v = (this.newApp || '').trim();
      if (v) { this.goodsDoc.applications.push(v); this.newApp = ''; }
    },
    async save() {
      this.loading = true;
      await this.wrap(async () => {
        const seo = {};
        if (this.multiResult && this.multiResult.seo) {
          Object.keys(this.multiResult.seo).forEach(code => {
            const s = this.multiResult.seo[code];
            seo[code] = {
              title: s.title || '',
              keywords: (s.keywordsText || '').split(/[,，]/).map(x => x.trim()).filter(Boolean),
              description: s.description || ''
            };
          });
        }
        const payload = {
          id: this.editingId,
          goodsDoc: this.goodsDoc,
          multilingual: this.multiResult ? this.multiResult.multilingual : null,
          seo: Object.keys(seo).length ? seo : null,
          sourceType: this.sourceType
        };
        const id = await this.api('/save', 'POST', payload);
        this.success = this.editingId ? '已更新记录 #' + id : '已保存记录 #' + id;
        this.editingId = null;
        this.rawText = '';
      });
      this.loading = false;
    },

    async loadList() {
      await this.wrap(async () => {
        const body = await this.api('/list?page=' + this.page + '&size=' + this.size);
        this.records = body.list || [];
        this.total = body.total || 0;
        this.totalPages = Math.ceil(this.total / this.size) || 1;
      });
    },
    changePage(p) { if (p < 1) return; this.page = p; this.loadList(); },
    async viewDetail(id) {
      await this.wrap(async () => {
        this.detail = await this.api('/detail?id=' + id);
        this.view = 'detail';
      });
    },
    async editRecord(id) {
      await this.wrap(async () => {
        const d = await this.api('/detail?id=' + id);
        this.goodsDoc = this.normalizeVo(d.basic);
        const seo = d.seo || {};
        Object.values(seo).forEach(s => { s.keywordsText = (s.keywords || []).join(', '); });
        this.multiResult = { multilingual: d.multilingual || {}, seo };
        this.sourceType = d.sourceType;
        this.editingId = d.id;
        this.view = 'workbench';
        this.success = '已加载记录 #' + id + '，可修改后保存';
      });
    },
    cancelEdit() { this.editingId = null; this.multiResult = null; this.goodsDoc = null; this.rawText = ''; },
    async delRecord(id) {
      if (!confirm('确认删除记录 #' + id + '？')) return;
      await this.wrap(async () => {
        await this.api('/delete?id=' + id, 'DELETE');
        this.success = '已删除记录 #' + id;
        this.loadList();
      });
    },
    async copySeo(code) {
      const s = this.multiResult.seo[code];
      const text = 'Title: ' + s.title + '\nKeywords: ' + (s.keywordsText || '') + '\nDescription: ' + s.description;
      try {
        await navigator.clipboard.writeText(text);
        this.success = '已复制 ' + code + ' SEO';
      } catch (e) {
        this.error = '复制失败，请手动复制';
      }
    }
  }
}).mount('#app');
</script>
</body>
</html>
```

- [ ] **Step 3: 编译打包（静态资源进入 jar）**

```bash
cd "E:\workspace\goods-doc-lang"
JAVA_HOME="E:\Java\java11-dragonwell-11.0.31.27" mvn -q -DskipTests package
```

Expected: 无输出（BUILD SUCCESS），`target/goods-doc-lang-1.0-SNAPSHOT.jar` 生成，`static/index.html` 已打入。

- [ ] **Step 4: 提交**

```bash
git add src/main/resources/static/
git commit -m "feat: 重写前端为 Vue3 三视图（工作台/历史/详情）"
```

---

## Task 13: 手工端到端验证

**前置条件：**
- MySQL 已启动，`doclang` 库存在；在 `doclang` 库执行 Task 1 的 `hq_goods_doc_record` DDL
- `application.yml` 中 AI Key 已配置（`hq.lang.ai.aihubmixKey` 等）；翻译相关表已存在

- [ ] **Step 1: 启动应用（JDK 11）**

```bash
cd "E:\workspace\goods-doc-lang"
JAVA_HOME="E:\Java\java11-dragonwell-11.0.31.27" mvn -q spring-boot:run
```

Expected: 日志出现 `startup success！`，监听 8080。

- [ ] **Step 2: 打开工作台页面**

浏览器访问 `http://localhost:8080/goods/`。

Expected: 显示页面标题"外贸商品信息标准化与多语言 SEO 系统"，三个导航：工作台 / 历史记录。

- [ ] **Step 3: 验证解析**

工作台输入型号 `STM32F103C8T6`，点「解析」。

Expected: 基本资料表单回填型号/品牌/分类/封装等字段（字段由 AI 识别，未识别留空）；若库里有相似记录则显示"RAG 召回参考"。

- [ ] **Step 4: 验证描述与多语言**

点「③ 生成英文描述」→ 描述框填入英文描述。再点「④ 生成多语言 + SEO」→ 显示 5 语言描述卡片 + 4 语言 SEO 卡片。

Expected: AI Key 有效时正常生成；翻译失败时该语言为空、英文保留。

- [ ] **Step 5: 验证保存与列表**

点「保存到历史」→ 提示"已保存记录 #N"；切到「历史记录」标签看到该行；点「查看」见只读详情；点「编辑」回到工作台表单，改一个字段再「保存修改」→ 提示"已更新记录 #N"。

- [ ] **Step 6: 验证公开接口**

浏览器访问 `http://localhost:8080/goods/api/doc/product/{id}`（用刚保存的 id）。

Expected: 返回 `{code:200, body:{partNumber, multilingual, seo, ...}}`，数据与页面一致。

- [ ] **Step 7: 验证删除**

列表中点「删除」→ 行消失；刷新 `product/{id}` 返回 `code:400`（记录不存在）。

---

## Self-Review 结论

**1. 规格覆盖核对：**
- 能力① 解析 → Task 10 `parsePart/parseText` + Task 8 RAG ✅
- 能力② 英文描述 → Task 10 `generateDesc` ✅
- 能力③ SEO（中/英/日/俄）→ Task 10 `generateSeo` ✅
- 能力④ JSON-LD → 客户 SSR 消费 `product/{id}` 自拼（不落库），Task 10 `product` + Task 13 Step 6 验证 ✅
- 能力⑤ 术语约束/多语言 → Task 10 `recallTerms` 注入 + `aiTranslate` 翻译 ✅
- 保存/历史/编辑 → Task 10 `save/list/detail/delete` ✅
- 9 接口 → Task 11 ✅；Vue3 三视图 → Task 12 ✅；DDL → Task 1 ✅；分页 → Task 2 ✅

**2. 占位符扫描：** 全部步骤含完整代码与命令，无 TBD/TODO。

**3. 类型一致性：** `GoodsDocVo` 含 `description/rawInput/topK` 贯穿 Task 5/9/10；`SeoVo.keywordsText` 仅前端 UI 字段（服务端为 `keywords:List<String>`，save 时由前端拆回）；`packageType ↔ package` 列映射一致。
