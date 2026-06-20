# UI 重设计方案 — 试卷出题系统前端

> 纯 UI 层改造，不改任何 API 调用、路由、store 逻辑。粉色主色调保持不变。

---

## 一、全局 CSS 基础

### 文件：`src/styles/variables.css`

新增/修改以下 CSS 变量：

```css
/* 圆角 */
--radius-card: 12px;
--radius-lg: 14px;

/* 阴影 — 卡片默认有阴影 */
--shadow-card: 0 2px 12px rgba(45, 36, 39, 0.05);
--shadow-card-hover: 0 6px 24px rgba(45, 36, 39, 0.10);
--shadow-lg: 0 12px 40px rgba(45, 36, 39, 0.12);

/* 统计卡片渐变色 */
--color-stat-1: linear-gradient(135deg, #fdf2f4, #fce1e7);
--color-stat-2: linear-gradient(135deg, #edf7f3, #d9f0e6);
--color-stat-3: linear-gradient(135deg, #fef7f1, #fdf2f4);
--color-stat-4: linear-gradient(135deg, #f5f0fa, #ede0f5);
```

### 文件：`src/styles/transitions.css`

新增动画：

- `card-in` — 卡片入场：从下方 20px + 透明 → 原位不透明，`0.45s ease-out`，支持 `animation-delay: calc(var(--i) * 0.08s)` stagger
- `fade-slide-in` / `fade-slide-out` — 面板滑入滑出（利用 `max-height` 过渡，用于底部预览面板）
- `count-up` — 数字跳动（CSS counter 或 JS 驱动）
- `pulse-dot` — 状态圆点脉冲（用于上传区文件指示器）

### 文件：`src/styles/element-overrides.css`

```css
/* 卡片：默认阴影 + 大圆角 + hover 上浮 */
.el-card {
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(45, 36, 39, 0.05);
  transition: box-shadow 0.3s ease, transform 0.2s ease;
}
.el-card:hover {
  box-shadow: 0 6px 24px rgba(45, 36, 39, 0.10);
  transform: translateY(-1px);
}

/* 按钮：去掉 scale 变换 */
.el-button:hover { transform: none; }
.el-button:active { transform: none; }

/* 表格行过渡 */
.el-table__body tr {
  transition: background-color 0.2s ease;
}

/* 输入框 focus 发光 */
.el-input__wrapper {
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}
```

---

## 二、主布局 — 侧边栏 & Header

### 文件：`src/layout/MainLayout.vue`

**侧边栏改动：**
- 收起宽度 68px，展开宽度 240px
- Logo 收起时：粉色渐变圆形 + 首字（"试"），展开时：完整标题 + 副标题
- 活动菜单项增加 `::before` 竖条指示器（`transition: top 0.3s cubic-bezier(0.4,0,0.2,1)`）
- 菜单 hover 背景改为微渐变
- 底部增加用户信息区：头像占位圆 + 用户名
- 退出按钮移到底部用户区
- 折叠按钮移到 logo 行右侧

**Header 改动：**
- 高度 56px
- 左侧：面包屑导航 + 当前页面标题
- 右侧：退出按钮（用户名已移到侧边栏）
- 背景：`rgba(255,255,255,0.85)` + `backdrop-filter: blur(8px)`
- 边框改为内阴影

**导入提示条：** 移到内容区顶部作为独立卡片，加 `fade-slide` 入场动画。

---

## 三、登录/注册页

### 文件：`src/views/auth/Login.vue`、`Register.vue`

- 背景增加装饰元素：右上角粉色半圆光晕（CSS radial-gradient），左下角淡色波浪
- 卡片从 `el-card` 改为自定义 div，圆角 16px，柔和大阴影
- 标题区域加 emoji 图标
- 输入框圆角加大，focus 时边框发光
- 登录按钮全宽渐变 `#e8738a → #d7657c`，hover 加深 + 微弱 box-shadow 发光
- 卡片下方三个特性图标行（📄 Word导入 · 🔒 数据安全 · ⚡ 智能组卷），增强品牌感
- 页面加载：卡片弹入动画（spring easing, 0.5s），特性图标依次淡入（stagger 0.1s）

---

## 四、题库导入页

### 文件：`src/views/import/Upload.vue`

**布局改为双栏 7:5：**

**左栏 — 主表单：**
- 课程名称输入框加大
- 拖拽上传区圆角加大，边框虚线呼吸动画
- 拖拽区 hover 边框颜色渐变 + 背景色过渡
- 有文件时增加微弱脉冲动画
- 开始解析按钮全宽渐变，loading 时显示动态步骤

**右栏 — 信息面板（三个区块）：**
1. 待确认批次卡片（粉色左边框，点击直达确认页）
2. 导入说明（简洁列表）
3. 统计概览（当前题库总量、课程数）

移除原来的三个 `el-alert` 堆叠。

---

## 五、导入确认页

### 文件：`src/views/import/Confirm.vue`

**摘要条改造：** 横向彩色渐变条，文件名 + 统计数字大号字体，课程名和操作按钮收进右侧。

**布局调整：** 左右比例 5:7

**左侧 — 候选题目列表：**
- 表格顶部增加快速搜索输入框
- 表格行 hover 显示快捷操作按钮（接受/拒绝浮动出现）

**右侧 — 题目详情/编辑：**
- 题干预览和答案预览改为可折叠面板（`el-collapse`），默认展开
- 题型/难度/题号横向三列 inline
- 操作按钮组底部固定

**动画：** 接受/拒绝后该行滑出消失，全部入库完成勾选动画。

---

## 六、题库列表页

### 文件：`src/views/question/List.vue`

**新增顶部统计卡片行（4个）：**

| 卡片 | 渐变色 | 内容 |
|------|--------|------|
| 总题目 | 粉 | 总题目数 + "↑12 本周" |
| 已同步 | 绿 | 同步数 + "88% 同步率" |
| 单选题 | 暖黄 | 单选题数 + "47% 占比" |
| 困难题 | 淡紫 | 困难题数 + "12% 占比" |

**筛选栏+操作收进一个卡片**，内部用分割线分隔。

**表格：** 向量库状态用渐变色圆点+文字替代 tag。

**动画：** 统计数字从 0 滚动到目标值，卡片依次入场。

---

## 七、手动组卷页

### 文件：`src/views/paper/Compose.vue`

**布局：** 左右比例 14:10 → 7:5

**左侧题库：** 筛选栏精简为一行 inline。

**右侧改为上下结构：**
- 上半：试卷元信息（紧凑一行）→ 已选题按题型分组
- 题型分组标题用彩色横条（单选题=粉色底，计算题=蓝色底等）
- 下半：总分 + 操作按钮

**底部新增预览面板：** 可折叠，点击题目滑出，高度 40vh，内部滚动。默认收起。

**动画：** 添加/移除题目时列表项滑入/滑出，底部面板 `translateY` 过渡，总分变化弹跳。

---

## 八、智能组卷页

### 文件：`src/views/paper/SmartCompose.vue`

**布局：** 左右比例 14:10 → 7:5

**左侧：**
- 表单去掉 label-width，改为顶部 placeholder 标签式
- textarea 加大到 5 行
- 示例改为圆角 chips 标签
- 课程+总分+时长紧凑一行
- 检索按钮全宽渐变
- 解析条件用折叠面板（检索后自动展开）

**右侧：**
- 顶部统计概览条：3 个小卡片（题量、总分、时长）
- 已匹配题目表格
- 检索结果摘要 + 章节分布
- 操作按钮组

**移除**右侧内置预览区，改为点击题目行 popover 预览。

**动画：** 检索 loading 波浪效果，匹配列表骨架屏，统计数字跳动。

---

## 九、历史试卷页

### 文件：`src/views/paper/History.vue`

**新增顶部统计卡片行（4个）：** 总试卷/已完成/本月新建/今日导出（粉、绿、暖黄、淡紫配色）。

**筛选栏+操作收进一个卡片。**

**表格改动：**
- 状态用彩色圆点+文字替换 tag
- 导出按钮合并为下拉按钮（展开学生版/教师版）
- 试卷名称列加粗

**动画：** 统计数字滚动，批量删除行滑出，导出 loading 图标旋转。

---

## 十、共享组件

### QuestionEditDialog (`src/components/QuestionEditDialog.vue`)

- 弹窗宽度 900px，圆角 14px
- 预览面板改为实线边框+微渐变背景
- 底部按钮右对齐，主按钮渐变

### RichContent (`src/components/RichContent.vue`)

- 行高 1.9
- 图片圆角 10px
- 图片占位标记加大 + 脉冲动画
- LaTeX 公式块增加左侧装饰条
- 答案预览区绿色背景微调

### 空状态统一

所有 `el-empty`：`image-size="80"`，描述文字用 placeholder 色。

---

## 不改动的范围

- 所有 API 调用（`@/api/*`）
- 路由配置（`router/index.js`）
- Store 逻辑（`stores/*`）
- 业务逻辑函数
- 组件 props / emit 定义
- RichContent 的渲染逻辑（只改样式）

## 注意事项

- 统计卡片只展示现有 API 已返回的数据（如 `total`、列表长度等），不新增 API 请求
- 如果某个统计值当前 API 不提供（如"本周新增"），该卡片改为展示其他已有数据，或保持占位
- 底部预览面板（手动组卷页）是现有预览区的重新布局，不新增数据获取逻辑
- 所有动画均为纯 CSS，不进 JS 逻辑
