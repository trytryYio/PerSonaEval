"""
评价生成模块 - 调用千问 API 生成课后评价

使用模型：Qwen-Turbo (够用、经济)
API: DashScope
"""

import httpx
import os
from typing import Dict, Optional


def get_api_key(user_provided: Optional[str] = None) -> str:
    """获取 API Key，优先使用用户提供的，其次环境变量"""
    if user_provided:
        return user_provided

    api_key = os.environ.get("DASHSCOPE_API_KEY", "")
    if not api_key:
        # 默认测试 key（用户可在前端输入自己的）
        api_key = "sk-43434062596d44979a152c8e763d6e20"

    return api_key


def generate_evaluation(
    student: Dict,
    lesson_content: str,
    grade: str = "",
    class_name: str = "",
    api_key: Optional[str] = None,
) -> str:
    """
    生成单个学生的课后评价

    输出格式仿照老师发微信的风格：
    XXX 家长您好，这是 XXX 近期的课程反馈：
    课程主题：xxx
    课堂表现：xxx
    课后建议：xxx
    """
    api_key = get_api_key(api_key)
    api_url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"

    # 构建 Prompt - 严格按照用户提供的样例风格
    name = student.get("name", "学生")
    personality = student.get("personality", "")
    notes = student.get("notes", "")

    # 构建学生特点描述
    traits_parts = []
    if personality:
        traits_parts.append(f"性格特点：{personality}")
    if notes:
        traits_parts.append(f"课堂表现：{notes}")
    traits_text = "\n".join(traits_parts) if traits_parts else "暂无特别说明"

    prompt = f"""你是一位编程培训机构的老师，需要给学生家长发课后反馈。

学生姓名：{name}
本节课内容：{lesson_content}
{traits_text}

请按照以下格式写一份课后反馈，语气亲切自然，像微信聊天一样：

{name}家长您好，这是{name}近期的课程反馈：

课程主题：{lesson_content}

课堂表现：（结合学生性格和课堂表现，写一段具体、真诚的评价，指出孩子的优点和需要改进的地方，语气像真实老师写的，不要用套话）

课后建议：（给家长 1-2 条具体的建议，帮助孩子更好地学习）

要求：
1. 语气亲切，像真实老师跟家长聊天
2. 不要用"展现了""优异""值得肯定"等书面化词语
3. 适当用一些语气词和 emoji（🌹、👍、💪）
4. 内容具体，不要太空泛
5. 总字数 100-200 字
"""

    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }

    payload = {
        "model": "qwen-turbo",
        "input": {"messages": [{"role": "user", "content": prompt}]},
        "parameters": {
            "temperature": 0.8,
            "max_tokens": 400,
        },
    }

    try:
        with httpx.Client(timeout=30.0) as client:
            response = client.post(api_url, headers=headers, json=payload)
            response.raise_for_status()
            data = response.json()

            output = data.get("output", {})
            text = output.get("text", "")

            return text.strip() if text else "评价生成失败，请重试。"

    except httpx.TimeoutException:
        return "请求超时，请重试。"
    except Exception as e:
        return f"生成失败：{str(e)}"


def batch_generate(
    students: list[Dict],
    lesson_content: str,
    grade: str = "",
    class_name: str = "",
    api_key: Optional[str] = None,
) -> list[Dict]:
    """
    批量生成评价

    Returns:
        [{"name": xxx, "evaluation": xxx}, ...]
    """
    results = []

    for student in students:
        evaluation = generate_evaluation(
            student=student,
            lesson_content=lesson_content,
            grade=grade,
            class_name=class_name,
            api_key=api_key,
        )
        results.append({
            "name": student.get("name", ""),
            "evaluation": evaluation,
        })

    return results


if __name__ == "__main__":
    # 测试
    test_student = {
        "name": "张三",
        "personality": "活泼开朗",
        "notes": "上课积极发言，但有时走神",
    }

    result = generate_evaluation(
        student=test_student,
        lesson_content="分数的加减法",
        grade="一年级",
        class_name="三班",
    )

    print(f"生成的评价：{result}")
