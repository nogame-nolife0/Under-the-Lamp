# Under-the-Lamp
灯下卷 = Word 智能入库 + 题库管理 + 自然语言 RAG 组卷 + Word 导出。

三层架构：

Vue3 前端
Spring Boot 业务后端
Python Agent（LangGraph 解析 Word + Chroma RAG 组卷 + 豆包大模型）
需要哪些「数据库 / 存储」
存储	    用途	                     要不要单独装
MySQL 8   用户、题库、试卷、导入批次   ✅ 要装
Redis     登录 Token                 ✅ 要装
ChromaDB  题目向量（智能组卷）        ❌ 不用单独装，Agent 本地文件
本地目录   上传 Word、图片、导出卷     自动创建 ~/paper-data/
怎么启动（顺序）
1. MySQL 执行 SQL：00 → 01 → 02 → 04
2. 配 application-local.yml（MySQL + Redis）
3. python-agent：cp .env.example .env，pip install，python run.py  → :8001
4. 后端：mvn spring-boot:run  → :8080
5. 前端：npm install && npm run dev  → :5173
首次使用：注册 → 导入 Word → 同步向量库 → 智能组卷。
