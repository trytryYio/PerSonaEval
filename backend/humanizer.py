"""
去 AI 痕迹处理模块 - 让生成的评价更像人写的

参考 humanizer 项目策略，同时保留用户需要的格式结构
"""

import re
from typing import Dict, List


# AI 常用词 → 更自然的替换
AI_WORD_REPLACEMENTS = {
    "展现出了": "表现了",
    "展示出": "表现出",
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
    "非常": "特别",
    "进一步": "再",
    "提升": "提高",
    "培养": "养成",
    "良好的": "好的",
    "该学生": "孩子",
    "该同学": "孩子",
    "综上所述": "",
    "总体而言": "总的来说",
}


def replace_ai_words(text: str) -> str:
    """替换 AI 常用词汇"""
    result = text
    for ai_word, human_word in AI_WORD_REPLACEMENTS.items():
        result = result.replace(ai_word, human_word)
    return result


def humanize(text: str) -> str:
    """
    主函数 - 将 AI 生成的文本转换为更自然的表达

    注意：保留 "课程主题"、"课堂表现"、"课后建议" 等标题格式
    """
    if not text:
        return text

    result = text

    # 1. 替换 AI 词汇
    result = replace_ai_words(result)

    # 2. 清理多余空格
    result = re.sub(r"\s+", "", result)

    # 3. 恢复标题格式的空格/换行
    result = result.replace("课程主题：", "\n课程主题：")
    result = result.replace("课堂表现：", "\n课堂表现：")
    result = result.replace("课后建议：", "\n课后建议：")

    # 4. 清理多余换行
    result = re.sub(r"\n{3,}", "\n\n", result)

    return result.strip()


def humanize_batch(evaluations: List[Dict]) -> List[Dict]:
    """批量处理评价"""
    results = []
    for item in evaluations:
        original = item.get("evaluation", "")
        humanized = humanize(original)
        results.append({
            "name": item.get("name", ""),
            "original": original,
            "humanized": humanized,
        })
    return results


if __name__ == "__main__":
    ai_text = "亦铭家长您好，这是亦铭近期的课程反馈：课程主题：while 循环。课堂表现：亦铭展现出了优异的学习态度。课后建议：希望该学生能够进一步提升自己。"
    result = humanize(ai_text)
    print(f"原文：{ai_text}")
    print(f"人性化后：{result}")
