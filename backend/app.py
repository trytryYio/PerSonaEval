"""
FastAPI 后端服务 - 端口 8001
"""

from datetime import date
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional, List, Dict

from extractor import extract_all
from generator import generate_all_templates
from database import (
    init_db, get_db, Class, Student, StudentTraits, TempTraits, Evaluation,
    iterate_traits, save_temp_traits, get_traits, get_temp_traits, SessionLocal,
)

app = FastAPI(title="PerSonaEval", version="2.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 启动时初始化数据库
@app.on_event("startup")
def startup():
    init_db()


# === Request Models ===

class ExtractRequest(BaseModel):
    text: str
    api_key: Optional[str] = None


class SaveRequest(BaseModel):
    grade: str
    class_name: str
    students: List[Dict]
    total_count: int
    api_key: Optional[str] = None


class GenerateRequest(BaseModel):
    student_id: int
    lesson_content: str
    lesson_notes: Optional[str] = None
    api_key: Optional[str] = None


# === API Endpoints ===

@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/api/extract")
def api_extract(req: ExtractRequest):
    """AI 提取文本中的班级+学生+性格占比"""
    api_key = req.api_key or "sk-43434062596d44979a152c8e763d6e20"
    try:
        result = extract_all(req.text, api_key)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/save")
def api_save(req: SaveRequest):
    """
    保存提取结果到数据库：
    1. 创建/查找班级
    2. 创建学生 + 保存原始特点
    3. 保存 temp_traits（当堂临时性格，覆盖旧数据）
    4. 迭代更新 student_traits（长期性格画像）
    """
    db = SessionLocal()
    try:
        # 1. 创建或查找班级
        cls = db.query(Class).filter_by(grade=req.grade, name=req.class_name).first()
        if not cls:
            cls = Class(grade=req.grade, name=req.class_name, student_count=req.total_count)
            db.add(cls)
            db.commit()
            db.refresh(cls)

        # 2. 创建学生 + 保存特点
        created_students = []
        for s in req.students:
            name = s.get("name", "")
            traits_raw = s.get("traits_raw", "")
            traits_pct = s.get("traits_pct", {})

            # 查找或创建学生
            student = db.query(Student).filter_by(class_id=cls.id, name=name).first()
            if not student:
                student = Student(class_id=cls.id, name=name, traits=traits_raw)
                db.add(student)
                db.commit()
                db.refresh(student)
            else:
                # 更新原始特点
                student.traits = traits_raw
                db.commit()

            # 3. 保存 temp_traits（覆盖）
            if traits_pct:
                save_temp_traits(student.id, traits_pct)

                # 4. 迭代更新长期性格画像
                iterate_traits(student.id, traits_pct)

            created_students.append({
                "id": student.id,
                "name": student.name,
            })

        return {
            "class_id": cls.id,
            "grade": cls.grade,
            "class_name": cls.name,
            "students": created_students,
        }
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db.close()


@app.post("/api/generate")
def api_generate(req: GenerateRequest):
    """
    为单个学生生成 3 份评价（参考长期性格画像）
    """
    db = SessionLocal()
    try:
        student = db.query(Student).filter_by(id=req.student_id).first()
        if not student:
            raise HTTPException(status_code=404, detail="学生不存在")

        # 获取长期性格画像
        traits = get_traits(student.id)

        # 解析 traits_pct 为字典
        traits_pct = {t["trait"]: t["percentage"] for t in traits}

        # 生成 3 份评价
        results = generate_all_templates(
            student_name=student.name,
            traits_raw=student.traits or "",
            traits_pct=traits_pct,
            lesson_content=req.lesson_content,
            lesson_notes=req.lesson_notes or "",
            api_key=req.api_key,
        )

        # 保存到数据库
        for r in results:
            eval_record = Evaluation(
                student_id=student.id,
                lesson_content=req.lesson_content,
                lesson_date=date.today(),
                performance_notes=req.lesson_notes or "",
                template=r["template"],
                content=r["content"],
            )
            db.add(eval_record)
        db.commit()

        return results
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db.close()


@app.get("/api/classes")
def api_classes():
    """获取所有班级列表"""
    db = SessionLocal()
    try:
        classes = db.query(Class).order_by(Class.created_at.desc()).all()
        return [{
            "id": c.id,
            "grade": c.grade,
            "name": c.name,
            "student_count": c.student_count,
            "created_at": str(c.created_at),
        } for c in classes]
    finally:
        db.close()


@app.get("/api/students/{class_id}")
def api_students(class_id: int):
    """获取某班级所有学生"""
    db = SessionLocal()
    try:
        students = db.query(Student).filter_by(class_id=class_id).all()
        result = []
        for s in students:
            traits = get_traits(s.id)
            result.append({
                "id": s.id,
                "name": s.name,
                "traits_raw": s.traits,
                "traits": traits,
            })
        return result
    finally:
        db.close()


@app.get("/api/student/{student_id}/traits")
def api_student_traits(student_id: int):
    """获取学生长期性格画像"""
    return get_traits(student_id)


@app.get("/api/student/{student_id}/temp-traits")
def api_temp_traits(student_id: int):
    """获取当堂临时性格"""
    return get_temp_traits(student_id)


@app.get("/api/evaluations/{student_id}")
def api_evaluations(student_id: int):
    """获取学生的历史评价"""
    db = SessionLocal()
    try:
        evals = db.query(Evaluation).filter_by(student_id=student_id).order_by(Evaluation.created_at.desc()).all()
        return [{
            "id": e.id,
            "template": e.template,
            "content": e.content,
            "lesson_content": e.lesson_content,
            "lesson_date": str(e.lesson_date),
            "created_at": str(e.created_at),
        } for e in evals]
    finally:
        db.close()


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)
