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
    m = re.search(r"([一二三四五六七八九十\d]+) 年级\s*([a-zA-Z\d 一二三四五六七八九十]+) 班", text)
    if m:
        return m.group(1) + "年级", m.group(2) + "班"

    m = re.search(r"([一二三四五六七八九十\d]+) 年\s*([a-zA-Z\d一二三四五六七八九十]+) 班", text)
    if m:
        return m.group(1) + "年级", m.group(2) + "班"

    m = re.search(r"([高初][一二三\d])\s*[(（](\d+)[)）]\s*班", text)
    if m:
        return m.group(1), m.group(2) + "班"

    m = re.search(r"([一二三四五六七八九十\d]+)\s*(?:年级 | 年)\s*([a-zA-Z\d一二三四五六七八九十]+)\s*班", text)
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
    支持 2-4 字姓名
    """
    students = []
    lines = re.split(r"\n+", text.strip())

    # 描述性词汇开头（用于识别名字结束位置）
    DESC_STARTERS = "性很非特比喜活开内文沉稳细粗认积聪懒安敏勇强静杰芳磊洋艳军霞平刚调皮好爱喜厌讨怕愿想会能可足欠多少太最较为更越渐愈愈越加尤其特别相当十分非常挺怪挺较蛮颇甚是确当真确实实在果真正真果然居然竟然忽然突然渐渐慢慢缓缓逐渐时时常常往往每每频频屡屡接连连连忽地猛地骤然陡然霍然顿时光溜溜圆滚滚胖乎乎瘦巴巴瘦伶仃胖墩墩圆咕隆咚黑黝黝白净净红扑扑光灿灿亮堂堂明晃晃雾蒙蒙灰扑扑尘漫漫水灵灵干巴巴湿漉漉潮乎乎汗津津油腻腻脏兮兮乱糟糟嘈杂杂闹哄哄静悄悄寂静静幽幽空荡荡空旷旷宽绰绰紧巴巴松垮垮软绵绵硬邦邦沉甸甸轻飘飘飘悠悠慢腾腾急匆匆急忙忙慌慌张张忐忐忑忑兴冲冲怒冲冲气呼呼乐呵呵笑眯眯笑嘻嘻笑哈哈哭啼啼愁眉苦苦大仇深欢欢喜喜悲悲切切高高兴兴快快乐乐开开心心愉愉快快安安静静吵吵闹闹打打闹闹蹦蹦跳跳跑跑走走停停说说笑笑唱唱跳跳写写画画读读写写背背诵诵练练习习考考试试试测验验做做玩玩吃吃喝喝睡睡觉觉起起床床穿穿衣衣脱脱鞋鞋洗洗澡澡刷刷牙牙梳梳头头洗洗脸脸擦擦手手揉揉眼眼捏捏鼻鼻掏掏耳耳剪剪指指甲甲理理发发刮刮胡胡修修面面整整容容健健身身锻锻炼炼运运动动游游泳泳跑跑步步跳跳绳绳踢踢球球打打球球拍拍球球骑骑车车滑滑冰冰滑滑板板登登山山爬爬山山走走步步散散步步遛遛狗狗溜溜弯弯转转逛逛看看望望想想念念记记挂挂担担心心心心疼疼忧忧虑虑焦焦急急烦烦恼恼生生气气恨怨恨怨悔悔恨憾惋惋惜惜遗遗憾憾可可悯怜悯同情情同同理理心心心相相印印心心心相相息息心心心心心相相印印心心心相相惜惜"

    for line in lines:
        line = line.strip()
        if not line or len(line) < 3:
            continue

        # 跳过年级/班级行
        if re.match(r"^[一二三四五六七八九十\d]+年级", line) or re.match(r"^[高初][一二三\d]", line):
            continue
        if re.search(r"\d+\s*个", line) and not re.match(r"^[\u4e00-\u9fa5]{2,}", line):
            continue

        # 跳过纯数字或符号行
        if re.match(r"^[\d\s\.,;:!?,.。，；：！、]+$", line):
            continue

        # 尝试匹配 4 字姓名
        if len(line) >= 5:
            potential_name_4 = line[:4]
            rest_4 = line[4:]
            if rest_4 and any(c in DESC_STARTERS for c in rest_4[:3] if c):
                students.append({"name": potential_name_4, "traits_raw": rest_4.strip()})
                continue

        # 尝试匹配 3 字姓名
        if len(line) >= 4:
            potential_name_3 = line[:3]
            rest_3 = line[3:]
            if rest_3 and any(c in DESC_STARTERS for c in rest_3[:2] if c):
                students.append({"name": potential_name_3, "traits_raw": rest_3.strip()})
                continue

        # 尝试匹配 2 字姓名
        if len(line) >= 3:
            potential_name_2 = line[:2]
            rest_2 = line[2:]
            if rest_2 and any(c in DESC_STARTERS for c in rest_2[:2] if c):
                students.append({"name": potential_name_2, "traits_raw": rest_2.strip()})
                continue

        # 通用匹配：句首 2-4 个中文字作为名字
        m = re.match(r"^([\u4e00-\u9fa5]{2,4})", line)
        if m:
            candidate = m.group(1)
            rest = line[len(candidate):].strip()
            if rest and len(rest) >= 2:
                students.append({"name": candidate, "traits_raw": rest})

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
  "学生名字": {{"标签 1": 占比 1, "标签 2": 占比 2, ...}},
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
