import json
import re

from app.llm.doubao_client import chat
from app.rag.compose_utils import normalize_type_counts

SYSTEM_PROMPT = """你是高校/职业教育试卷组卷助手。用户题库可能包含非常规专业课（如数字电子技术、微机原理、PLC、嵌入式、医学影像等），不要默认中小学场景。
根据用户的自然语言组卷需求，提取结构化条件。
只输出 JSON，不要 markdown，不要解释。字段说明：
- subject: 课程/科目名称（如「数字电子技术」「大学物理」），没有则 null
- grade: 课程层级或适用对象（如「大二」「高职」「期末考」），没有则 null，不要强行填中小学年级
- questionType: 单一题型时使用，枚举 SINGLE_CHOICE/MULTI_CHOICE/TRUE_FALSE/FILL_BLANK/SHORT_ANSWER/CALCULATION/ESSAY，没有则 null
- typeCounts: 多题型比例，对象，键为题型枚举，值为数量；若用户说「5道单选+3道计算」，填 {"SINGLE_CHOICE":5,"CALCULATION":3}，此时 questionType 应为 null
- difficulty: EASY/MEDIUM/HARD，没有则 null
- chapter: 章节、知识点或专题（如「触发器」「ADC采样」），没有则 null
- count: 总题量，默认 10，范围 1~30；若有 typeCounts，count 等于 typeCounts 各项之和
- keywords: 用于向量检索的关键词短语，应包含课程名、核心概念、题型意向

示例输入：数字电子技术 触发器与计数器 8道单选题，中等难度，适合大二期末
示例输出：{"subject":"数字电子技术","grade":"大二","questionType":"SINGLE_CHOICE","typeCounts":null,"difficulty":"MEDIUM","chapter":"触发器与计数器","count":8,"keywords":"数字电子技术 触发器 计数器 单选题"}

示例输入：微机原理 5道单选题 3道计算题 2道简答题
示例输出：{"subject":"微机原理","grade":null,"questionType":null,"typeCounts":{"SINGLE_CHOICE":5,"CALCULATION":3,"SHORT_ANSWER":2},"difficulty":null,"chapter":null,"count":10,"keywords":"微机原理 单选题 计算题 简答题"}

示例输入：微机原理 简答题5道 偏难
示例输出：{"subject":"微机原理","grade":null,"questionType":"SHORT_ANSWER","typeCounts":null,"difficulty":"HARD","chapter":null,"count":5,"keywords":"微机原理 简答题"}"""


def _repair_json(text: str) -> dict:
    text = text.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*", "", text)
        text = re.sub(r"\s*```$", "", text)
    return json.loads(text)


def parse_compose_query(query: str) -> dict:
    raw = chat(
        f"用户需求：{query}",
        system=SYSTEM_PROMPT,
    )
    try:
        data = _repair_json(raw)
    except json.JSONDecodeError as exc:
        raise ValueError(f"无法解析组卷条件: {exc}") from exc

    type_counts = normalize_type_counts(data.get("typeCounts"))
    if type_counts:
        data["typeCounts"] = type_counts
        data["questionType"] = None
        data["count"] = max(1, min(sum(type_counts.values()), 30))
    else:
        data.pop("typeCounts", None)
        count = data.get("count") or 10
        try:
            count = int(count)
        except (TypeError, ValueError):
            count = 10
        data["count"] = max(1, min(count, 30))
    return data
