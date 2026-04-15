"""
文本识别模块 - 从自由文本中提取班级、学生、性格占比

输入示例：
"八年级 a 班 5 个学生
小明活泼好动喜欢上课跳舞，同时还是一个烦人精 老是问来问去不爱作答
小红比较安静但是做题很慢
小刚特别聪明就是有点懒"

输出：
{
    "grade": "八年级",
    "class": "a 班",
    "students": [
        {"name": "小明", "traits_raw": "活泼好动喜欢上课跳舞，同时还是一个烦人精...", "traits_pct": {"活泼": 30, "好动": 20, "爱提问": 30, "不爱书写": 20}},
        ...
    ]
}
"""

import re
import json
import httpx
from typing import Dict, List, Optional

DASHSCOPE_API_KEY = "sk-43434062596d44979a152c8e763d6e20"


def extract_grade_and_class(text: str) -> tuple[str, str]:
    """提取年级和班级"""
    m = re.search(r"([一二三四五六七八九十\d]+)年级\s*([a-zA-Z\d一二三四五六七八九十]+)班", text)
    if m:
        return m.group(1) + "年级", m.group(2) + "班"

    m = re.search(r"([一二三四五六七八九十\d]+)年\s*([a-zA-Z\d一二三四五六七八九十]+)班", text)
    if m:
        return m.group(1) + "年级", m.group(2) + "班"

    m = re.search(r"([高初][一二三\d])\s*[(（](\d+)[)）]\s*班", text)
    if m:
        return m.group(1), m.group(2) + "班"

    m = re.search(r"([一二三四五六七八九十\d]+)\s*(?:年级|年)\s*([a-zA-Z\d一二三四五六七八九十]+)\s*班", text)
    if m:
        return m.group(1) + "年级", m.group(2) + "班"

    return "", ""


def extract_count(text: str) -> Optional[int]:
    """提取学生总数"""
    for pattern in [r"(\d+)\s*人", r"(\d+)\s*个学", r"(\d+)\s*个\s*学"]:
        m = re.search(pattern, text)
        if m:
            return int(m.group(1))
    return None


def parse_students_from_text(text: str) -> List[Dict[str, str]]:
    """
    从文本中分割出每个学生描述
    按换行分割，识别句首名字（排除年级/班级行）
    """
    students = []
    lines = re.split(r"\n+", text.strip())

    # 常见中文姓氏
    SURNAMES = set("王李张刘陈杨赵黄周吴徐孙胡朱高林何郭马罗梁宋郑谢韩唐冯于董萧程曹袁邓许傅沈曾彭吕苏卢蒋蔡贾丁魏薛叶阎余潘杜戴夏钟汪田任姜范方石姚谭廖邹熊金陆郝孔白崔康毛邱秦江史顾侯邵孟龙万段雷钱汤尹黎易常武乔贺赖龚文庞樊兰殷施陶翟安颜倪严牛温芦季俞章鲁葛伍韦申尤毕聂丛焦向柳邢路岳齐沿梅莫庄辛管祝左涂谷祁时舒耿牟卜肖詹关苗凌费纪靳盛童欧甄项曲成游阳裴席卫查屈鲍位覃霍翁隋植甘景薄单包司柏宁柯阮桂闵欧阳解强柴华车冉房边")

    for line in lines:
        line = line.strip()
        if not line or len(line) < 3:
            continue

        # 跳过年级/班级行
        if re.match(r"^[一二三四五六七八九十\d]+年级", line) or re.match(r"^[高初][一二三]", line):
            continue
        if re.search(r"\d+\s*个", line) and not re.match(r"^[\u4e00-\u9fa5]{2,3}", line):
            continue

        # 尝试匹配常见姓氏
        name = ""
        if len(line) >= 2 and line[0] in SURNAMES:
            # 2字名：姓+名
            if len(line) >= 3 and line[1] in SURNAMES or True:  # 第二个字也可是名
                name = line[:2]
                desc = line[2:].strip()
                if desc:
                    students.append({"name": name, "traits_raw": desc})
                    continue

        # 通用匹配：句首 2 个中文字作为名字（假设名字后紧跟描述性文字）
        m = re.match(r"^([\u4e00-\u9fa5]{2})(?:性|很|非|特|比|喜|活|开|内|文|沉|细|粗|认|积|聪|懒|安|敏|勇|强|静|杰|芳|磊|洋|艳|军|霞|平|刚|调|皮|好)", line)
        if m:
            name = m.group(1)
            desc = line[len(name):].strip()
            if desc:
                students.append({"name": name, "traits_raw": desc})

    return students


def analyze_traits_with_ai(students: List[Dict], api_key: str = DASHSCOPE_API_KEY) -> List[Dict]:
    """
    调用千问 API，把每个学生的特点转成 JSON 占比格式
    """
    if not students:
        return students

    # 构建所有学生特点的文本
    traits_descriptions = []
    for s in students:
        traits_descriptions.append(f"{s['name']}：{s['traits_raw']}")

    all_traits_text = "\n".join(traits_descriptions)

    prompt = f"""你是一个教育机构的老师。请分析以下学生的性格特点，把每个学生的特点转成百分比格式（总和 100%）。

学生特点描述：
{all_traits_text}

要求：
1. 提取 3-6 个特点标签
2. 正面和负面特点都要提取
3. 负面特点用委婉的标签，例如：
   - "烦人精" → "爱提问"
   - "懒" → "缺乏动力"
   - "不爱作答" → "偏重口头表达"
   - "做题慢" → "需要更多练习"
4. 输出纯 JSON，格式如下：
{{
  "学生名字": {{"标签1": 占比1, "标签2": 占比2, ...}},
  ...
}}

注意：只输出 JSON，不要其他文字。每个学生的百分比总和必须是 100。"""

    api_url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }
    payload = {
        "model": "qwen-turbo",
        "input": {"messages": [{"role": "user", "content": prompt}]},
        "parameters": {"temperature": 0.5, "max_tokens": 500},
    }

    try:
        with httpx.Client(timeout=30.0) as client:
            response = client.post(api_url, headers=headers, json=payload)
            response.raise_for_status()
            data = response.json()
            text = data.get("output", {}).get("text", "")

            # 解析 JSON
            # 清理可能的 markdown 代码块标记
            text = text.strip()
            if text.startswith("```"):
                text = re.sub(r"^```(?:json)?\s*", "", text)
                text = re.sub(r"\s*```$", "", text)

            traits_data = json.loads(text)

            # 合并到学生数据
            for student in students:
                name = student["name"]
                if name in traits_data:
                    student["traits_pct"] = traits_data[name]
                else:
                    student["traits_pct"] = {}

    except Exception as e:
        print(f"AI 性格分析失败：{e}")
        for student in students:
            student["traits_pct"] = {}

    return students


def extract_all(text: str, api_key: str = DASHSCOPE_API_KEY) -> Dict:
    """主函数 - 从原始文本中提取所有信息"""
    grade, class_name = extract_grade_and_class(text)
    total_count = extract_count(text)
    students = parse_students_from_text(text)

    # 调用 AI 分析性格占比
    students = analyze_traits_with_ai(students, api_key)

    return {
        "grade": grade,
        "class": class_name,
        "total_count": total_count or len(students),
        "students": students,
        "raw_text": text,
    }


if __name__ == "__main__":
    test_text = """八年级 a 班 5 个学生
小明活泼好动喜欢上课跳舞，同时还是一个烦人精 老是问来问去不爱作答
小红比较安静但是做题很慢
小刚特别聪明就是有点懒"""

    result = extract_all(test_text)
    print(json.dumps(result, ensure_ascii=False, indent=2))
