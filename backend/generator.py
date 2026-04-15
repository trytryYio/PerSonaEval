"""
评价生成模块 - 调用千问 API 生成 3 份不同风格的评价

模板类型：
- 80/20：80% 夸奖 + 20% 委婉提醒
- 65/35：65% 夸奖 + 35% 建设性反馈
- 90/10：90% 鼓励 + 10% 轻微提醒
"""

import httpx
import json
from typing import Dict, List, Optional

DASHSCOPE_API_KEY = "sk-43434062596d44979a152c8e763d6e20"
API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"

# 委婉词替换映射
EUPHEMISMS = {
    "烦人精": "爱思考、问题多",
    "懒": "需要更多动力",
    "不爱作答": "更擅长表达而非书写",
    "烦人": "好奇心强",
    "捣蛋": "精力旺盛",
    "不听话": "有自己想法",
    "差": "还有提升空间",
    "笨": "需要更多引导",
    "慢": "需要更多练习时间",
    "走神": "容易被新鲜事物吸引",
}

TEMPLATES = {
    "80/20": {
        "label": "偏夸奖",
        "instruction": "80% 夸奖，20% 委婉提醒不足。以肯定为主，委婉地提出 1 个小建议。",
    },
    "65/35": {
        "label": "平衡",
        "instruction": "65% 夸奖，35% 建设性反馈。先肯定优点，然后具体指出需要改进的地方，给出可操作的建议。",
    },
    "90/10": {
        "label": "鼓励",
        "instruction": "90% 鼓励，10% 轻微提醒。几乎全是正面评价，不足的地方一笔带过，用希望/期待的语气。",
    },
}


def format_traits(traits: List[Dict] | Dict) -> str:
    """格式化性格画像为自然语言"""
    if isinstance(traits, list):
        parts = [f"{t['trait']} ({t['percentage']}%)" for t in traits]
        return "、".join(parts)
    elif isinstance(traits, dict):
        parts = [f"{k} ({v}%)" for k, v in traits.items()]
        return "、".join(parts)
    return str(traits)


def generate_evaluation(
    student_name: str,
    traits_raw: str,
    traits_pct: Dict,
    lesson_content: str,
    lesson_notes: str = "",
    template: str = "80/20",
    api_key: str = DASHSCOPE_API_KEY,
) -> str:
    """
    生成单个学生的课后评价

    Args:
        student_name: 学生姓名
        traits_raw: 原始特点文本
        traits_pct: 性格占比字典
        lesson_content: 课程内容
        lesson_notes: 当堂表现备注
        template: 模板类型 "80/20", "65/35", "90/10"
        api_key: API Key

    Returns:
        生成的评价文本
    """
    tpl = TEMPLATES.get(template, TEMPLATES["80/20"])
    traits_text = format_traits(traits_pct)

    # 构建委婉替换提示
    euphemism_text = ""
    for bad, good in EUPHEMISMS.items():
        if bad in traits_raw:
            euphemism_text += f'- 把"{bad}"委婉表达为"{good}"\n'

    prompt = f"""你是一位编程培训机构的老师，需要给学生家长发课后反馈。

学生姓名：{student_name}
学生性格特点：{traits_text}
原始描述：{traits_raw}
本节课内容：{lesson_content}
{'当堂表现：' + lesson_notes if lesson_notes else ''}

{tpl['instruction']}

要求：
1. 按照以下格式输出：
{student_name}家长您好，这是{student_name}近期的课程反馈：

课程主题：{lesson_content}

课堂表现：（具体内容）

课后建议：（具体内容）

2. 语气亲切自然，像微信聊天
3. 不要用"展现了""优异""值得肯定"等书面化词语
4. 适当用一些 emoji（🌹、👍、💪、🌟）
5. 内容具体，不要太空泛
6. 总字数 150-250 字
{euphemism_text}
7. 如果有负面特点，用委婉方式表达，不要直接说缺点
"""

    api_key = api_key or DASHSCOPE_API_KEY

    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }

    payload = {
        "model": "qwen-turbo",
        "input": {"messages": [{"role": "user", "content": prompt}]},
        "parameters": {
            "temperature": 0.8,
            "max_tokens": 500,
        },
    }

    try:
        with httpx.Client(timeout=30.0) as client:
            response = client.post(API_URL, headers=headers, json=payload)
            response.raise_for_status()
            data = response.json()
            text = data.get("output", {}).get("text", "")
            return text.strip() if text else "评价生成失败，请重试。"
    except httpx.TimeoutException:
        return "请求超时，请重试。"
    except Exception as e:
        return f"生成失败：{str(e)}"


def generate_all_templates(
    student_name: str,
    traits_raw: str,
    traits_pct: Dict,
    lesson_content: str,
    lesson_notes: str = "",
    api_key: str = DASHSCOPE_API_KEY,
) -> List[Dict]:
    """
    为单个学生生成 3 份不同风格的评价

    Returns:
        [{"template": "80/20", "label": "偏夸奖", "content": "..."}, ...]
    """
    results = []
    for key in ["80/20", "65/35", "90/10"]:
        content = generate_evaluation(
            student_name=student_name,
            traits_raw=traits_raw,
            traits_pct=traits_pct,
            lesson_content=lesson_content,
            lesson_notes=lesson_notes,
            template=key,
            api_key=api_key,
        )
        results.append({
            "template": key,
            "label": TEMPLATES[key]["label"],
            "content": content,
        })
    return results


if __name__ == "__main__":
    # 测试
    results = generate_all_templates(
        student_name="小明",
        traits_raw="活泼好动喜欢上课跳舞，同时还是一个烦人精 老是问来问去不爱作答",
        traits_pct={"活泼好动": 40, "爱提问": 30, "偏重口头表达": 20, "缺乏专注力": 10},
        lesson_content="while 循环",
    )
    for r in results:
        print(f"\n=== {r['template']} ({r['label']}) ===")
        print(r['content'])
