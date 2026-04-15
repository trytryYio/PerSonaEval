"""
文本识别模块 - 从原始文本中提取班级、学生姓名、性格特点等信息
"""

import re
from typing import Dict, List, Optional


# 常见中文非姓名单词
EXCLUDE_WORDS = {
    "一年级", "二年级", "三年级", "四年级", "五年级", "六年级",
    "一班", "二班", "三班", "四班", "五班", "六班", "七班", "八班",
    "性格", "活泼", "开朗", "内向", "认真", "积极",
    "上课", "发言", "表现", "老师", "学生", "孩子",
    "一共", "共有", "同学", "帮助", "喜欢", "比较",
    "但是", "积极", "发言", "他们", "我们", "你们",
}


def extract_grade_and_class(text: str) -> tuple[str, str]:
    """提取年级和班级"""
    # 一年级三班 / 1 年级 3 班 (无空格或任意空格)
    m = re.search(r"([一二三四五六七八九十\d]+)年级\s*([一二三四五六七八九十\d]+)班", text)
    if m:
        return m.group(1) + "年级", m.group(2) + "班"

    # 一年级 三班 (有空格)
    m = re.search(r"([一二三四五六七八九十\d]+)年\s*([一二三四五六七八九十\d]+)班", text)
    if m:
        return m.group(1) + "年级", m.group(2) + "班"

    # 高二 (3) 班
    m = re.search(r"([高初][一二三\d])\s*[(（](\d+)[)）]\s*班", text)
    if m:
        return m.group(1), m.group(2) + "班"

    # 三年级 2 班
    m = re.search(r"([一二三四五六七八九十\d]+)\s*(?:年级|年)\s*([一二三四五六七八九十\d]+)\s*班", text)
    if m:
        return m.group(1) + "年级", m.group(2) + "班"

    return "", ""


def extract_count(text: str) -> Optional[int]:
    """提取学生总数"""
    patterns = [
        r"(\d+)\s*人",
        r"一共\s*(\d+)",
        r"共\s*(\d+)\s*个",
        r"(\d+)\s*个学",
    ]
    for pattern in patterns:
        m = re.search(pattern, text)
        if m:
            return int(m.group(1))
    return None


def extract_student_names(text: str) -> List[str]:
    """
    提取学生姓名列表
    支持多种格式：
    - 有张三、李四、王五
    - 学生包括：小明、小红
    - 今天上课的有：亦铭、泓诺
    """
    names = []

    # 模式 1: 有 xxx、xxx、xxx
    m = re.search(r"有\s*([\u4e00-\u9fa5\s、，,]+?)(?:\.{3}|一共 | 共|。|\d+个)", text)
    if m:
        names_str = m.group(1).strip()
        raw_names = re.split(r"[、，,\s]+", names_str)
        for raw_name in raw_names:
            name = raw_name.strip()
            if name and len(name) >= 2 and len(name) <= 3 and name not in EXCLUDE_WORDS:
                names.append(name)
        return names

    # 模式 2: 学生包括/上课的有：xxx、xxx
    m = re.search(r"(?:学生包括 | 上课的有 | 今天.*有 | 名单 | 包括)[:：]\s*([\u4e00-\u9fa5\s、，,]+?)(?:\.{3}|一共 | 共|。|\d+个)", text)
    if m:
        names_str = m.group(1).strip()
        raw_names = re.split(r"[、，,\s]+", names_str)
        for raw_name in raw_names:
            name = raw_name.strip()
            if name and len(name) >= 2 and len(name) <= 3 and name not in EXCLUDE_WORDS:
                names.append(name)
        return names

    return names


def extract_student_details(text: str) -> List[Dict[str, str]]:
    """提取每个学生的详细信息（姓名、性格、表现备注）"""
    students = {}
    personality_kw = ["活泼", "开朗", "内向", "文静", "外向", "沉稳", "细心", "粗心", "认真", "积极", "走神"]
    sep_kw_list = ["性格", "上课", "比较", "很", "非常", "特别", "喜欢", "状态", "积极", "开朗", "认真"]

    # 先处理 "有 XXX...一共/共..." 或 "学生包括/上课的有：XXX...一共/共..."
    for pattern in [
        r"有\s*([\u4e00-\u9fa5\s、，,]+?)(?:\.{3}|一共 | 共)",
        r"(?:学生包括 | 上课的有 | 今天.*有 | 名单 | 包括)[:：]\s*([\u4e00-\u9fa5\s、，,]+?)(?:\.{3}|一共 | 共|。)",
    ]:
        m = re.search(pattern, text)
        if m:
            segment = m.group(1)
            parts = re.split(r"[、，,]+", segment)
            for part in parts:
                part = part.strip()
                if not part or len(part) < 4:
                    continue
                # 找分隔点来分割名字和描述
                name = ""
                desc = ""
                for kw in sep_kw_list:
                    idx = part.find(kw)
                    if idx >= 2 and idx <= 3:
                        name = part[:idx]
                        desc = part[idx:]
                        break
                if not name:
                    # 没有分隔词，尝试前 2-3 个字作为名字
                    name_match = re.match(r"^([\u4e00-\u9fa5]{2,3})", part)
                    if name_match:
                        name = name_match.group(1)
                        desc = part[len(name):]
                if name and name not in EXCLUDE_WORDS and len(name) <= 3:
                    if name not in students:
                        students[name] = {"name": name, "personality": "", "notes": ""}
                    if desc:
                        if "性格" in desc:
                            personality = desc.split("性格")[1].split("上课")[0].split("喜欢")[0].split("状态")[0].strip()
                            students[name]["personality"] = personality
                        if "上课" in desc:
                            notes = "上课" + desc.split("上课")[1].strip()
                            students[name]["notes"] = notes
                        if "状态" in desc:
                            notes = desc.split("状态")[1].strip()
                            students[name]["notes"] = notes
                        if any(kw in desc for kw in personality_kw) and not students[name]["personality"]:
                            students[name]["personality"] = desc

    # 模式：单独行的 "XXX 性格 XXX"
    for m in re.finditer(r"\n?\s*([\u4e00-\u9fa5]{2,3}) 性格 ([\u4e00-\u9fa5]+?)(?:[,，..!。]|上课 | 喜欢|\s|$)", text):
        name = m.group(1).strip()
        personality = m.group(2).strip()
        if name not in EXCLUDE_WORDS and len(name) <= 3:
            if name not in students:
                students[name] = {"name": name, "personality": "", "notes": ""}
            students[name]["personality"] = personality

    # 模式：单独行的 "XXX 比较/很 XXX"
    for m in re.finditer(r"\n?\s*([\u4e00-\u9fa5]{2,3})\s*(?:比较 | 很 | 非常 | 特别) ([\u4e00-\u9fa5]+?)(?:[,，..!。]|\s|$)", text):
        name = m.group(1).strip()
        desc = m.group(2).strip()
        if name not in EXCLUDE_WORDS and len(name) <= 3 and any(kw in desc for kw in personality_kw):
            if name not in students:
                students[name] = {"name": name, "personality": "", "notes": ""}
            if not students[name]["personality"]:
                students[name]["personality"] = desc

    # 模式：XXX...上课 xxx
    for m in re.finditer(r"([\u4e00-\u9fa5]{2,3}).*?(上课 [\u4e00-\u9fa5]+?)(?:[,，..!。]|\s|$)", text):
        name = m.group(1).strip()
        notes = m.group(2).strip()
        if name not in EXCLUDE_WORDS and len(name) <= 3:
            if name not in students:
                students[name] = {"name": name, "personality": "", "notes": ""}
            if not students[name]["notes"]:
                students[name]["notes"] = notes

    return list(students.values())


def extract_all(text: str) -> Dict:
    """主函数 - 从原始文本中提取所有信息"""
    grade, class_name = extract_grade_and_class(text)
    detailed_students = extract_student_details(text)
    total_count = extract_count(text)

    # 如果没提取到详细学生信息，尝试只提取姓名
    if not detailed_students:
        names = extract_student_names(text)
        detailed_students = [{"name": n, "personality": "", "notes": ""} for n in names]

    result = {
        "grade": grade,
        "class": class_name,
        "total_count": total_count or len(detailed_students),
        "students": detailed_students,
        "raw_text": text,
    }

    return result


if __name__ == "__main__":
    import json

    test_text = (
        "一年级三班 54 人 有张三、李四、王五、赵六、孙七...一共 54 个\n"
        "张三性格活泼，上课积极发言\n"
        "李四比较内向，但很认真\n"
        "王五性格开朗，喜欢帮助同学"
    )

    result = extract_all(test_text)
    print(json.dumps(result, ensure_ascii=False, indent=2))
