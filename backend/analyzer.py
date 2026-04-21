"""
模板分析器 - AI 自动发现评价中的可复用模式
"""

import json
import re
import httpx
from typing import List, Dict

DASHSCOPE_API_KEY = "sk-43434062596d44979a152c8e763d6e20"


def analyze_template_patterns(evaluations: List[str], api_key: str = DASHSCOPE_API_KEY) -> Dict:
    """
    分析多个评价内容，找出可复用的模板模式

    Args:
        evaluations: 评价内容列表
        api_key: Dashscope API key

    Returns:
        包含可复用模式的字典，例如：
        {
            "common_structures": ["开头句式", "过渡句", "结尾句式"],
            "replaceable_parts": [
                {"pattern": "{学生姓名}", "description": "学生名字"},
                {"pattern": "{具体表现}", "description": "课堂具体表现"},
                {"pattern": "{性格特点}", "description": "性格特点描述"}
            ],
            "suggested_templates": ["模板 1", "模板 2"]
        }
    """
    if not evaluations:
        return {"common_structures": [], "replaceable_parts": [], "suggested_templates": []}

    # 构建分析文本
    eval_text = "\n\n---\n\n".join(evaluations[:10])  # 限制最多分析 10 条

    prompt = f"""你是一个教育机构的文案分析专家。请分析以下学生评价内容，找出可以复用的模板模式。

评价内容：
{eval_text}

请分析：
1. 找出共同的句式结构（开头、过渡、结尾）
2. 找出可以被替代的部分（用占位符标记，如{学生姓名}、{具体表现}等）
3. 基于分析结果，生成 2-3 个通用模板

输出纯 JSON 格式：
{{
    "common_structures": {{
        "opening": "常见的开头句式模式",
        "transition": "常见的过渡句模式",
        "closing": "常见的结尾句式模式"
    }},
    "replaceable_parts": [
        {{"placeholder": "{{学生姓名}}", "description": "学生的名字", "example": "小明"}}，
        {{"placeholder": "{{性格特点}}", "description": "学生的性格特点", "example": "活泼好动"}}，
        {{"placeholder": "{{课堂表现}}", "description": "在课堂上的具体表现", "example": "积极举手发言"}}，
        {{"placeholder": "{{建议改进}}", "description": "需要改进的方面", "example": "提高专注力"}}
    ],
    "suggested_templates": [
        "模板 1：开头 + {{学生姓名}}+{{性格特点}}+ 过渡 + {{课堂表现}}+ 结尾 + {{建议改进}}",
        "模板 2：..."
    ]
}}

注意：只输出 JSON，不要其他文字。"""

    api_url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }
    payload = {
        "model": "qwen-turbo",
        "input": {"messages": [{"role": "user", "content": prompt}]},
        "parameters": {"temperature": 0.3, "max_tokens": 1000},
    }

    try:
        with httpx.Client(timeout=30.0) as client:
            response = client.post(api_url, headers=headers, json=payload)
            response.raise_for_status()
            data = response.json()
            text = data.get("output", {}).get("text", "")

            # 清理可能的 markdown 代码块标记
            text = text.strip()
            if text.startswith("```"):
                text = re.sub(r"^```(?:json)?\s*", "", text)
                text = re.sub(r"\s*```$", "", text)

            result = json.loads(text)
            return result

    except Exception as e:
        print(f"AI 模板分析失败：{e}")
        # 返回基础模式
        return {
            "common_structures": {
                "opening": "该生在本节课中...",
                "transition": "在学习过程中，",
                "closing": "希望在今后的学习中继续保持/改进..."
            },
            "replaceable_parts": [
                {"placeholder": "{学生姓名}", "description": "学生的名字", "example": ""},
                {"placeholder": "{性格特点}", "description": "学生的性格特点", "example": ""},
                {"placeholder": "{课堂表现}", "description": "在课堂上的具体表现", "example": ""}
            ],
            "suggested_templates": ["{学生姓名} 在课堂上 {课堂表现}，展现了{性格特点}。"]
        }


if __name__ == "__main__":
    # 测试
    test_evaluations = [
        "小明在本节课中表现优异，他活泼好动，积极举手发言，展现了很强的表达能力。但是在书写方面还需要加强。",
        "小红上课很认真，虽然速度较慢，但是正确率很高。她文静的性格让她能够专注于细节。",
        "小刚特别聪明，就是有点懒，不爱写作业。口头表达能力强，但书面表达需要提升。"
    ]

    result = analyze_template_patterns(test_evaluations)
    print(json.dumps(result, ensure_ascii=False, indent=2))
