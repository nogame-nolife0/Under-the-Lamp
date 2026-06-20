# API Contract v1

> 试卷出题系统统一接口契约  
> 版本：v1.0 | 日期：2026-06-11  
> 三方：Vue3 前端 ↔ Spring Boot 后端 ↔ Python FastAPI Agent

---

## 1. 服务与端口

| 服务 | 地址 | 说明 |
|------|------|------|
| Vue 前端 | `http://localhost:5173` | 开发环境 |
| Spring Boot | `http://localhost:8080` | 业务中枢，前缀 `/api` |
| Python Agent | `http://localhost:8001` | 仅 Java 内部调用 |
| MySQL | `localhost:3306/paper-generator-system` | 结构化数据 |
| Redis | `192.168.167.130:6379` | 缓存与会话 |
| Chroma | Python 本地目录 | 向量库（二期智能组卷，MVP 可不启） |

---

## 2. 公共约定

### 2.1 统一返回体（Vue ↔ Java）

```json
{
  "code": 200,
  "msg": "success",
  "data": {}
}
```

| code | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |
| 503 | Agent 服务不可用 |

### 2.2 业务错误码（bizCode，放在 msg 或 data 中说明）

| bizCode | msg 示例 | 说明 |
|---------|----------|------|
| 40001 | Word 格式不支持 | 文件无法解析 |
| 40002 | 文件大小超限 | 默认最大 50MB |
| 40003 | 解析结果为空 | Agent 未识别到题目 |
| 40004 | 批次状态不允许此操作 | 如已 CONFIRMED 不可再编辑 |
| 40005 | 题目数量不足 | 组卷时题库不够 |
| 50001 | Agent 服务调用超时 | 默认超时 120s |
| 50002 | Agent 解析失败 | Python 返回错误 |
| 50003 | 大模型调用失败 | LLM 异常 |

### 2.3 枚举（与数据库字段完全一致）

```
QuestionType:
  SINGLE_CHOICE | MULTI_CHOICE | TRUE_FALSE | FILL_BLANK |
  SHORT_ANSWER | CALCULATION | ESSAY | UNKNOWN

Difficulty:
  EASY | MEDIUM | HARD

CategoryType:
  SUBJECT | GRADE | CHAPTER | TAG

ImportBatchStatus:
  PARSING | DRAFT | CONFIRMING | CONFIRMED | FAILED | PARTIAL

ImportItemStatus:
  PENDING | ACCEPTED | REJECTED | EDITED

PaperType:
  HOMEWORK | EXAM | EXAMPLE

ComposeMode:
  MANUAL | SMART

PaperStatus:
  DRAFT | COMPLETED

ExportType:
  STUDENT | TEACHER

EmbedStatus:
  PENDING | SYNCED | FAILED
```

### 2.4 命名规范

| 项 | 规范 |
|----|------|
| URL 路径 | 小写 + 中划线，如 `/api/import/batches` |
| JSON 字段 | 小驼峰：`batchUuid`、`confidenceScore` |
| 数据库字段 | 下划线：`batch_uuid` |
| 时间格式 | `yyyy-MM-dd'T'HH:mm:ss`，如 `2026-06-11T10:00:00` |
| 分页参数 | `pageNum`（从1开始）、`pageSize`（默认10） |
| 分页响应 | `{ list, total, pageNum, pageSize }` |

### 2.5 文件传输（Java → Python）

统一使用**服务器临时文件路径**，不使用 multipart 二次转发：

```json
{
  "batchId": "550e8400-e29b-41d4-a716-446655440000",
  "filePath": "D:/paper-data/uploads/2026/06/xxx.docx",
  "subject": "数学",
  "grade": "初二"
}
```

---

## 3. Java 对外接口（Vue 调用）

基础路径：`http://localhost:8080/api`

### 3.1 健康检查

**GET** `/health`

响应：
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "service": "backend",
    "status": "ok"
  }
}
```

---

### 3.2 分类模块

**GET** `/categories?type=SUBJECT`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | string | 否 | SUBJECT / GRADE / CHAPTER / TAG |
| parentId | long | 否 | 父级ID |

响应 data：
```json
[
  {
    "id": 1,
    "name": "数学",
    "type": "SUBJECT",
    "parentId": 0,
    "sortOrder": 1
  }
]
```

---

### 3.3 导入模块（第一期核心）

#### POST `/import/upload`

`Content-Type: multipart/form-data`

| 字段 | 类型 | 必填 |
|------|------|------|
| file | file | 是 |
| subject | string | 否 |
| grade | string | 否 |

响应 data：
```json
{
  "batchUuid": "550e8400-e29b-41d4-a716-446655440000",
  "status": "DRAFT",
  "fileName": "期末复习.docx",
  "totalCount": 15,
  "needsReviewCount": 5,
  "acceptedCount": 0,
  "rejectedCount": 0,
  "items": []
}
```

> `items` 结构与 `ImportItemVO` 相同，见 3.3.2。

#### GET `/import/batches/{batchUuid}`

响应 data：批次详情（含 status、计数）。

#### GET `/import/batches/{batchUuid}/items`

| 参数 | 说明 |
|------|------|
| status | 可选，筛选 PENDING / ACCEPTED 等 |
| minConfidence | 可选，最低置信度 |

响应 data：`ImportItemVO[]`

#### ImportItemVO 结构

```json
{
  "id": 1,
  "batchId": 10,
  "seqNo": 1,
  "stemRaw": "1. 已知...",
  "answerRaw": "解：...",
  "stemHtml": null,
  "answerHtml": null,
  "analysisHtml": null,
  "options": ["A. ...", "B. ..."],
  "questionType": "SHORT_ANSWER",
  "difficulty": "MEDIUM",
  "subject": "数学",
  "grade": "初二",
  "chapter": "导数",
  "knowledgePoints": ["导数", "单调性"],
  "confidenceScore": 0.82,
  "warnings": ["答案可能来自卷末答案区"],
  "images": [{ "placeholder": "img_001", "position": "stem" }],
  "status": "PENDING"
}
```

#### PUT `/import/items/{id}`

请求体（部分更新）：
```json
{
  "stemHtml": "<p>题干</p>",
  "answerHtml": "<p>答案</p>",
  "questionType": "SHORT_ANSWER",
  "difficulty": "MEDIUM",
  "chapter": "导数"
}
```

#### POST `/import/items/{id}/accept`

接受单题，status → ACCEPTED。

#### POST `/import/items/{id}/reject`

拒绝单题，status → REJECTED。

#### POST `/import/batches/{batchUuid}/confirm`

确认入库：将 ACCEPTED / EDITED 的草稿写入 `question` 表。

响应 data：
```json
{
  "batchUuid": "uuid",
  "status": "CONFIRMED",
  "importedCount": 12,
  "skippedCount": 3
}
```

---

### 3.4 题库模块

#### GET `/questions`

| 参数 | 说明 |
|------|------|
| pageNum | 页码，默认 1 |
| pageSize | 每页条数，默认 10 |
| subject | 学科 |
| grade | 年级 |
| questionType | 题型 |
| difficulty | 难度 |
| chapter | 章节 |
| keyword | 题干关键词 |

响应 data：
```json
{
  "list": [],
  "total": 100,
  "pageNum": 1,
  "pageSize": 10
}
```

#### GET `/questions/{id}`

返回完整题目（含 answer、analysis），用于组卷右侧答案预览。

#### PUT `/questions/{id}`

编辑正式题库题目。

#### DELETE `/questions/{id}`

归档题目，status → ARCHIVED。

---

### 3.5 组卷模块（第二期）

#### POST `/papers`

手动组卷。

请求体：
```json
{
  "title": "初二数学期中复习",
  "paperType": "EXAM",
  "subject": "数学",
  "grade": "初二",
  "durationMinutes": 90,
  "composeMode": "MANUAL",
  "questions": [
    { "questionId": 101, "sortOrder": 1, "score": 10.0 },
    { "questionId": 102, "sortOrder": 2, "score": 10.0 }
  ]
}
```

#### POST `/papers/smart`

自然语言智能组卷（依赖 RAG + Chroma）。

请求体：
```json
{
  "title": "一元一次方程练习",
  "query": "初二数学一元一次方程10道选择题，中等难度",
  "paperType": "HOMEWORK",
  "totalScore": 100
}
```

#### GET `/papers/{id}`

试卷详情（含题目列表）。

#### POST `/papers/{id}/export`

| 参数 | 说明 |
|------|------|
| exportType | STUDENT / TEACHER |

响应：文件流 `application/vnd.openxmlformats-officedocument.wordprocessingml.document`

---

## 4. Java 内部接口（Python Agent）

基础路径：`http://localhost:8001`

Python 返回体与 Java 统一，使用相同 `{ code, msg, data }` 结构。

### 4.1 GET `/health`

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "status": "ok",
    "version": "1.0.0"
  }
}
```

---

### 4.2 POST `/api/v1/parse-word`（第一期必做）

请求：
```json
{
  "batchId": "550e8400-e29b-41d4-a716-446655440000",
  "filePath": "D:/paper-data/uploads/xxx.docx",
  "subject": "数学",
  "grade": "初二",
  "hints": {
    "answerSectionKeywords": ["参考答案", "答案解析"]
  }
}
```

响应 data：
```json
{
  "batchId": "550e8400-e29b-41d4-a716-446655440000",
  "items": [
    {
      "seqNo": 1,
      "stemRaw": "1. 已知函数 f(x)=...",
      "answerRaw": "解：...",
      "analysisRaw": "本题考查...",
      "options": ["A. ...", "B. ..."],
      "questionType": "SHORT_ANSWER",
      "difficulty": "MEDIUM",
      "chapter": "导数",
      "knowledgePoints": ["导数", "单调性"],
      "confidenceScore": 0.82,
      "warnings": ["答案可能来自卷末答案区"],
      "images": [{ "placeholder": "img_001", "position": "stem" }],
      "agentMeta": {
        "answerSource": "INLINE",
        "segmentMethod": "llm"
      }
    }
  ],
  "parseSummary": {
    "total": 15,
    "highConfidence": 10,
    "needsReview": 5
  }
}
```

---

### 4.3 POST `/api/v1/rag/search`（第二期，需 Chroma）

请求：
```json
{
  "query": "初二数学一元一次方程10道选择题，中等难度",
  "subject": "数学",
  "grade": "初二",
  "limit": 10
}
```

响应 data：
```json
{
  "questionIds": [101, 102, 103],
  "matchedCount": 10,
  "composeCondition": {
    "questionType": "SINGLE_CHOICE",
    "difficulty": "MEDIUM",
    "chapter": "一元一次方程"
  }
}
```

---

### 4.4 POST `/api/v1/embed/batch`（第二期，需 Chroma）

题目确认入库后，Java 异步调用，写入向量库。

请求：
```json
{
  "items": [
    {
      "questionId": 101,
      "stem": "题干文本（用于向量化）",
      "subject": "数学",
      "grade": "初二",
      "questionType": "SINGLE_CHOICE",
      "difficulty": "MEDIUM",
      "knowledgePoints": ["一元一次方程"]
    }
  ]
}
```

响应 data：
```json
{
  "successCount": 10,
  "failedIds": []
}
```

---

## 5. 字段映射表

### Python parse-word items → import_item 表

| Python JSON | 数据库字段 | Java 字段 |
|-------------|-----------|-----------|
| seqNo | seq_no | seqNo |
| stemRaw | stem_raw | stemRaw |
| answerRaw | answer_raw | answerRaw |
| analysisRaw | — | analysisRaw（确认时→analysis_html） |
| options | options_json | options |
| questionType | question_type | questionType |
| difficulty | difficulty | difficulty |
| chapter | chapter | chapter |
| knowledgePoints | knowledge_points | knowledgePoints |
| confidenceScore | confidence_score | confidenceScore |
| warnings | warnings | warnings |
| images | images_json | images |
| agentMeta | agent_meta | agentMeta |

### import_item 确认入库 → question 表

| import_item | question |
|-------------|----------|
| stem_html / stem_raw | stem |
| answer_html / answer_raw | answer |
| analysis_html | analysis |
| options_json | options_json |
| question_type | question_type |
| difficulty | difficulty |
| subject / grade / chapter | 同名字段 |
| knowledge_points | knowledge_points |
| confidence_score | confidence_snapshot |
| images_json | images_json |
| batch_id | source_batch_id |
| id | source_item_id |

---

## 6. Redis Key 规范

| Key 模式 | 类型 | 过期 | 说明 |
|----------|------|------|------|
| `paper:questions:page:{hash}` | String | 30min | 题库分页缓存 |
| `paper:compose:session:{sessionId}` | List | 2h | 组卷勾选题目ID |
| `paper:import:task:{batchUuid}` | String | 24h | 导入任务状态 |

---

## 7. 变更记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-11 | 初版：parse-word + 导入 + 题库；RAG/embed 标注第二期 |
