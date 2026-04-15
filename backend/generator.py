"""
评价生成模块 - 调用千问 API 生成 3 份不同风格的评价

集成 humanizer skill 规则消除 AI 写作痕迹。

模板类型：
- 80/20：80% 夸奖 + 20% 委婉提醒
- 65/35：65% 夸奖 + 35% 建设性反馈
- 90/10：90% 鼓励 + 10% 轻微提醒
"""

import httpx
import re
from typing import Dict, List, Optional

DASHSCOPE_API_KEY = "sk-43434062596d44979a152c8e763d6e20"
API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"

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

# Humanizer: AI vocabulary words to avoid
AI_VOCAB = [
    "展现了", "展示出", "彰显了", "体现了其", "优异", "卓越", "显著",
    "积极态度", "展现出了", "具有良好的", "值得肯定", "有待加强",
    "需要改进", "建议加强", "较为", "十分", "进一步", "提升",
    "培养", "良好的", "至关重要", "不可或缺的", "深远", "丰富多彩",
    "不可或缺", "举足轻重", "意味深长", "引人入胜", "丰富多彩",
    "不仅...更...", "不仅...还...", "不仅...也...",
    "stand as", "serve as", "testament", "pivotal", "underscore",
    "showcase", "foster", "delve", "intricate", "tapestry", "landscape",
]


def format_traits(traits: List[Dict] | Dict) -> str:
    """格式化性格画像为自然语言"""
    if isinstance(traits, list):
        parts = [f"{t['trait']} ({t['percentage']}%)" for t in traits]
        return "、".join(parts)
    elif isinstance(traits, dict):
        parts = [f"{k}: {v}%" for k, v in traits.items()]
        return "，".join(parts)
    return str(traits)


def post_humanize(text: str) -> str:
    """
    后处理：应用 humanizer skill 规则清除 AI 痕迹
    """
    if not text:
        return text

    result = text

    # 1. 替换 AI 常用词为简单表达
    replacements = {
        "展现了": "表现了",
        "展示出": "表现出",
        "彰显了": "说明了",
        "优异": "很好",
        "卓越": "不错",
        "显著": "明显",
        "积极态度": "劲头",
        "展现出了": "表现出了",
        "具有良好的": "有不错的",
        "值得肯定": "值得表扬",
        "有待加强": "还要加油",
        "需要改进": "需要注意",
        "建议加强": "可以多",
        "较为": "比较",
        "十分": "很",
        "进一步": "再",
        "提升": "提高",
        "培养": "养成",
        "良好的": "好的",
        "至关重要": "很重要",
        "不可或缺的": "不可少的",
        "不仅": "不但",
        "此外": "另外",
        "总而言之": "总的来说",
        "综上所述": "",
        "总体而言": "总的来说",
    }
    for ai_word, human_word in replacements.items():
        result = result.replace(ai_word, human_word)

    # 2. 去除 em dash 过度使用
    result = result.replace("——", "，")
    result = result.replace("—", "，")

    # 3. 去除 curly quotes
    result = result.replace(""", '"').replace(""", '"')
    result = result.replace("'", "'").replace("'", "'")

    # 4. 去除 emoji (老师微信消息可以有一些，但不要太多)
    # 保留少量 emoji，去掉过多的
    emoji_pattern = re.compile(
        "["
        "\U0001F600-\U0001F64F"
        "\U0001F300-\U0001F5FF"
        "\U0001F680-\U0001F6FF"
        "\U0001F1E0-\U0001F1FF"
        "\U00002702-\U000027B0"
        "]+",
        flags=re.UNICODE,
    )
    # 统计 emoji 数量
    emojis = emoji_pattern.findall(result)
    if len(emojis) > 3:
        # 只保留前 2 个
        emoji_list = emojis[:2]
        result = emoji_pattern.sub("", result)
        # 在末尾加回 1-2 个
        if emoji_list:
            result = result.rstrip("。.!！") + emoji_list[0] + "。"

    # 5. 清理多余空格和换行
    result = re.sub(r" {2,}", " ", result)
    result = re.sub(r"\n{3,}", "\n\n", result)

    # 6. 去除 "It's not just X, it's Y" 类型的否定式平行结构
    result = re.sub(r"不仅仅是(.+?)，更是", "主要是", result)
    result = re.sub(r"不仅是(.+?)，也是", "既是", result)
    result = re.sub(r"不只是(.+?)，还", "既", result)

    # 7. 去除三段式排比
    # "学习认真，作业完成及时，发言积极" -> "学习挺认真的，作业也完成得及时"
    # 这个比较难用正则完美处理，至少清理过度工整的逗号分隔

    # 8. 清理句末多余语气
    result = re.sub(r"希望(该)?学生", "希望孩子", result)
    result = re.sub(r"希望同学", "希望孩子", result)
    result = result.replace("能够", "能")
    result = result.replace("继续保持", "保持住")

    # 9. 确保使用简单动词 (is/are -> 是/有，避免 serves as/stands as 类)
    result = result.replace("作为", "是")
    result = result.replace(" serves as ", "是")
    result = result.replace("代表着", "是")
    result = result.replace("意味着", "说明")

    return result.strip()


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
    """
    api_key = api_key or DASHSCOPE_API_KEY
    tpl = TEMPLATES.get(template, TEMPLATES["80/20"])
    traits_text = format_traits(traits_pct)

    prompt = f"""你是一位培训机构的老师，需要给学生家长发课后反馈。请用平常跟人微信聊天的语气写。

学生：{student_name}
性格特点：{traits_text}
原始描述：{traits_raw}
课程内容：{lesson_content}
{'当堂表现：' + lesson_notes if lesson_notes else ''}

{tpl['instruction']}

按这个格式写：

{student_name}家长您好，这是{student_name}近期的课程反馈：

课程主题：{lesson_content}

课堂表现：（写一段话）

课后建议：（写 1-2 条具体建议）

重要写作要求（必须遵守）：
1. 用平常聊天的语气，像发微信一样自然
2. 不要用这些词：展现了、展示出、彰显了、优异、卓越、显著、积极态度、值得肯定、有待加强、需要改进、建议加强、较为、十分、进一步、提升、培养、至关重要、不可或缺、不可或缺、举足轻重、意味深长、引人入胜、丰富多彩、此外、总而言之、综上所述、总体而言
3. 不要用"不仅...更..."、"不仅...还..."这类结构
4. 不要用破折号——
5. 不要用"作为"代替"是"，直接说"是"
6. 内容要具体，提到学生实际表现
7. 总字数 100-200 字
8. 负面特点要委婉表达，比如"烦人精"说成"爱思考"，"懒"说成"需要更多动力"
9. emoji 最多用 1-2 个，放在句末
10. 写完后自己检查一遍，把上面禁止的词都替换掉
"""

    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }

    payload = {
        "model": "qwen-turbo",
        "input": {"messages": [{"role": "user", "content": prompt}]},
        "parameters": {
            "temperature": 0.7,
            "max_tokens": 500,
        },
    }

    try:
        with httpx.Client(timeout=30.0) as client:
            response = client.post(API_URL, headers=headers, json=payload)
            response.raise_for_status()
            data = response.json()
            text = data.get("output", {}).get("text", "")
            if text:
                # 应用 humanizer 后处理
                text = post_humanize(text)
                return text.strip()
            return "评价生成失败，请重试。"
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
    """为单个学生生成 3 份不同风格的评价"""
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
    results = generate_all_templates(
        student_name="小明",
        traits_raw="活泼好动喜欢上课跳舞，同时还是一个烦人精 老是问来问去不爱作答",
        traits_pct={"活泼好动": 40, "爱提问": 30, "偏重口头表达": 20, "缺乏专注力": 10},
        lesson_content="while 循环",
        lesson_notes="今天上课一直问问题，但不太愿意动手写代码",
    )
    for r in results:
        print(f"\n=== {r['template']} ({r['label']}) ===")
        print(r["content"])
