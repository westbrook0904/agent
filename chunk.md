> **Document AST Driven Chunk Engine（基于文档树的企业级Chunk切分引擎）**

因为从你的输入来看，你已经不是在做简单 Chunk，而是在做一个面向 RAG 的文档理解与知识单元构建系统。

---

# 1. 设计目标

## 核心问题

传统 Chunk 方案：

```text
PDF
 ↓
Text
 ↓
500 Token切分
 ↓
Embedding
```

存在的问题：

### 结构丢失

```md
# Redis

## 主从复制

内容...
```

变成：

```text
Redis 主从复制 内容...
```

标题层级消失。

---

### 表格失真

二维表：

| 地区 | 1月  | 2月  |
| -- | --- | --- |
| 华东 | 100 | 120 |

变成：

```text
华东 100 120
```

语义关系丢失。

---

### 代码语义缺失

```java
public User query(Long id)
```

单独向量化效果极差。

---

### 图文关系丢失

```text
架构图
↓
说明文字
```

被拆散。

---

# 目标

构建：

```text
Document AST
 ↓
Knowledge Unit
 ↓
Parent-Child
 ↓
Multi-Level Retrieval
```

而不是：

```text
Document
 ↓
Text Chunk
```

---

# 2. 整体架构

```text
                ┌──────────────┐
                │ Document AST │
                └──────┬───────┘
                       │
                       ▼
          ┌────────────────────────┐
          │ Node Strategy Router   │
          └──────────┬─────────────┘
                     │
 ┌───────────┬───────┼─────────┬───────────┐
 ▼           ▼       ▼         ▼           ▼

Paragraph   Table   Code     List      Image

Chunker     Chunker Chunker  Chunker   Chunker

 └───────────┴───────┴─────────┴───────────┘
                     │
                     ▼

         Context Augmentation

                     │
                     ▼

              Chunk Merge

                     │
                     ▼

         Knowledge Unit Builder

                     │
                     ▼

            Parent Builder

                     │
                     ▼

           Summary Builder

                     │
                     ▼

           Embedding Storage
```

---

# 3. AST模型

解析阶段输出：

```java
Document
├── Heading
├── Paragraph
├── Table
├── CodeBlock
├── OrderedList
├── Image
├── Formula
└── Quote
```

---

同时维护：

```java
PathContext
```

例如：

```text
Redis
 └── 主从复制
      └── 增量同步
```

形成：

```text
Redis > 主从复制 > 增量同步
```

作为全局 Metadata。

---

# 4. Node Routing

不同节点采用不同 Chunk 策略。

## Paragraph

适用：

```text
说明文
制度文档
设计文档
论文
```

策略：

```text
Structure Chunk
+
Semantic Chunk
+
Sliding Window
```

输出：

```json
{
  "type":"paragraph",
  "path":"Redis > 主从复制"
}
```

---

## Ordered List

保持整体。

例如：

```text
1. 创建订单
2. 扣减库存
3. 支付
```

作为一个流程知识单元。

输出：

```json
{
  "type":"workflow"
}
```

---

## Table

采用多策略。

---

### 小表

条件：

```text
≤ 10行
≤ 10列
```

保留完整Markdown。

```json
{
  "type":"table",
  "table":"..."
}
```

---

### 中等表

采用 Row Linearization。

例如：

```text
地区=华东
1月=100
2月=120
```

生成：

```text
华东地区1月销售额100，2月销售额120
```

---

### 超大表

例如：

```text
5000行订单
```

采用：

```text
Row Group Chunk
```

每：

```text
50~100行
```

一个 Chunk。

---

### Metadata

保留：

```json
{
  "row":"华东",
  "column":"2月"
}
```

方便后续过滤检索。

---

## Code

根据文件规模选择。

---

### 小文件

```text
<2000 Token
```

整个文件保留。

---

### 大文件

建立：

```text
File
 └── Class
      └── Method
```

层级结构。

---

检索：

```text
Method Recall
 ↓
Class Expansion
 ↓
File Expansion
```

---

Metadata：

```json
{
  "language":"java",
  "class":"UserService",
  "method":"query"
}
```

---

## Image

采用：

```text
OCR
+
Caption
+
Layout Context
```

生成：

```text
图片类型：系统架构图

内容：

订单中心
↓
库存中心
↓
支付中心
```

作为独立 Chunk。

---

# 5. Context Augmentation

这是提升召回质量最重要的环节之一。

---

原始代码：

```java
public User query(Long id)
```

Embedding效果差。

---

增强后：

```text
文档路径：

Redis > 主从复制

代码示例：

public User query(Long id)
```

---

表格：

```text
Redis > 配置参数

参数：
repl-backlog-size

默认值：
1MB
```

---

最终所有 Chunk 都带：

```json
{
  "path":"Redis > 配置参数"
}
```

---

# 6. Chunk Merge

目标：

避免碎片化。

---

例如：

```text
Paragraph1
Paragraph2
Paragraph3
```

都属于：

```text
Redis > 主从复制
```

则自动合并。

---

规则：

```text
同一路径
+
同类型
+
长度 < MergeThreshold
```

例如：

```text
800 Token
```

---

生成：

```text
Redis > 主从复制

PSYNC...

全量同步...

增量同步...
```

---

# 7. Knowledge Unit Builder

核心创新点。

---

传统：

```text
Chunk = 文本块
```

---

升级：

```text
Chunk = 知识单元
```

例如：

```text
Redis主从复制
```

形成：

```json
{
  "topic":"Redis主从复制",

  "keywords":[
      "PSYNC",
      "全量同步",
      "增量同步"
  ],

  "content":"..."
}
```

---

对于流程：

```json
{
  "topic":"订单创建流程",
  "steps":[
      "创建订单",
      "扣减库存",
      "支付"
  ]
}
```

---

# 8. Parent-Child构建

## Child

用于召回。

大小：

```text
300~500 Token
```

---

## Parent

用于回答补充上下文。

大小：

```text
1000~2000 Token
```

---

结构：

```text
Parent
├── Child1
├── Child2
├── Child3
└── Child4
```

---

Metadata：

```json
{
  "parent_id":"p001"
}
```

---

# 9. Summary Layer

对 Parent 生成摘要。

---

例如：

```text
Redis主从复制机制包括：

PSYNC
全量同步
增量同步
backlog
```

生成：

```text
Redis主从复制总体机制说明。
```

---

长度：

```text
100~300 Token
```

---

作用：

快速召回。

---

# 10. 多层索引结构

最终存储：

```text
Summary Layer

Parent Layer

Child Layer
```

---

Summary：

```text
100~300 token
```

用于粗召回。

---

Parent：

```text
1000~2000 token
```

用于章节理解。

---

Child：

```text
300~500 token
```

用于精准命中。

---

# 11. 检索链路

推荐采用：

```text
Query

 ↓

Summary Recall

 ↓

Parent Recall

 ↓

Child Recall

 ↓

Rerank

 ↓

Context Assemble

 ↓

LLM
```

---

# 12. 可扩展设计

未来新增：

```text
Formula
Mermaid
SQL
JSON
YAML
OpenAPI
Excel
PPT
```

无需修改主流程。

仅新增：

```java
ChunkStrategy
```

实现即可。

---

# 最终落地建议（适合企业知识库）

如果让我给一个生产环境推荐配置：

```yaml
parent_chunk_size: 1500

child_chunk_size: 400

overlap: 80

merge_threshold: 800

small_table_row_limit: 10

large_table_batch_size: 50

summary_size: 200
```

并采用：

```text
AST
 ↓
Node Strategy Routing
 ↓
Context Augmentation
 ↓
Chunk Merge
 ↓
Knowledge Unit Builder
 ↓
Parent Builder
 ↓
Summary Builder
 ↓
Multi-Level Retrieval
```

这套方案兼顾了结构化文档（Markdown、Confluence、飞书）、复杂 PDF（MinerU、Docling）、代码仓库（CodeRAG）和企业制度文档，属于目前比较接近企业级 Agentic RAG 的通用架构。
