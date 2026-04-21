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


class ClassCreateUpdate(BaseModel):
    grade: str
    name: str
    student_count: Optional[int] = None


class StudentCreateUpdate(BaseModel):
    class_id: int
    name: str
    traits: Optional[str] = None


class TraitCreateUpdate(BaseModel):
    trait: str
    percentage: int


class BatchGenerateRequest(BaseModel):
    class_id: int
    student_ids: Optional[List[int]] = None  # None 表示全班
    lesson_content: str
    lesson_notes_global: Optional[str] = None  # 全班整体表现
    student_notes: Optional[Dict[int, str]] = None  # {student_id: "当堂表现"}
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
            "is_adopted": bool(e.is_adopted),
            "created_at": str(e.created_at),
        } for e in evals]
    finally:
        db.close()


@app.post("/api/evaluation/{eval_id}/adopt")
def api_adopt_evaluation(eval_id: int):
    """标记评价为已采纳（同时取消该学生同模板的其他采纳）"""
    db = SessionLocal()
    try:
        ev = db.query(Evaluation).filter_by(id=eval_id).first()
        if not ev:
            raise HTTPException(status_code=404, detail="评价不存在")

        # 取消该学生同模板的其他采纳
        db.query(Evaluation).filter_by(
            student_id=ev.student_id,
            template=ev.template,
        ).update({"is_adopted": 0})

        # 标记当前为已采纳
        ev.is_adopted = 1
        db.commit()

        return {"id": ev.id, "is_adopted": True}
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db.close()


# === CRUD Endpoints ===

@app.post("/api/class")
def api_create_class(req: ClassCreateUpdate):
    """创建班级"""
    db = SessionLocal()
    try:
        # 检查是否已存在
        existing = db.query(Class).filter_by(grade=req.grade, name=req.name).first()
        if existing:
            raise HTTPException(status_code=400, detail="班级已存在")

        cls = Class(grade=req.grade, name=req.name, student_count=req.student_count or 0)
        db.add(cls)
        db.commit()
        db.refresh(cls)
        return {"id": cls.id, "grade": cls.grade, "name": cls.name, "student_count": cls.student_count}
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db.close()


@app.put("/api/class/{class_id}")
def api_update_class(class_id: int, req: ClassCreateUpdate):
    """更新班级"""
    db = SessionLocal()
    try:
        cls = db.query(Class).filter_by(id=class_id).first()
        if not cls:
            raise HTTPException(status_code=404, detail="班级不存在")

        cls.grade = req.grade
        cls.name = req.name
        if req.student_count is not None:
            cls.student_count = req.student_count
        db.commit()
        db.refresh(cls)
        return {"id": cls.id, "grade": cls.grade, "name": cls.name, "student_count": cls.student_count}
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db.close()


@app.delete("/api/class/{class_id}")
def api_delete_class(class_id: int):
    """删除班级"""
    db = SessionLocal()
    try:
        cls = db.query(Class).filter_by(id=class_id).first()
        if not cls:
            raise HTTPException(status_code=404, detail="班级不存在")

        db.delete(cls)
        db.commit()
        return {"message": "删除成功"}
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db.close()


@app.post("/api/student")
def api_create_student(req: StudentCreateUpdate):
    """创建学生"""
    db = SessionLocal()
    try:
        # 检查班级是否存在
        cls = db.query(Class).filter_by(id=req.class_id).first()
        if not cls:
            raise HTTPException(status_code=404, detail="班级不存在")

        # 检查名字是否重复
        existing = db.query(Student).filter_by(class_id=req.class_id, name=req.name).first()
        if existing:
            raise HTTPException(status_code=400, detail="该班级已有此学生")

        student = Student(class_id=req.class_id, name=req.name, traits=req.traits or "")
        db.add(student)
        db.commit()
        db.refresh(student)
        return {"id": student.id, "name": student.name, "class_id": student.class_id}
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db.close()


@app.put("/api/student/{student_id}")
def api_update_student(student_id: int, req: StudentCreateUpdate):
    """更新学生"""
    db = SessionLocal()
    try:
        student = db.query(Student).filter_by(id=student_id).first()
        if not student:
            raise HTTPException(status_code=404, detail="学生不存在")

        student.class_id = req.class_id
        student.name = req.name
        student.traits = req.traits or ""
        db.commit()
        db.refresh(student)
        return {"id": student.id, "name": student.name, "class_id": student.class_id}
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db.close()


@app.delete("/api/student/{student_id}")
def api_delete_student(student_id: int):
    """删除学生"""
    db = SessionLocal()
    try:
        student = db.query(Student).filter_by(id=student_id).first()
        if not student:
            raise HTTPException(status_code=404, detail="学生不存在")

        db.delete(student)
        db.commit()
        return {"message": "删除成功"}
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db.close()


@app.post("/api/trait/{student_id}")
def api_create_trait(student_id: int, req: TraitCreateUpdate):
    """添加性格特征"""
    db = SessionLocal()
    try:
        student = db.query(Student).filter_by(id=student_id).first()
        if not student:
            raise HTTPException(status_code=404, detail="学生不存在")

        # 使用 iterate_traits 添加新特征
        traits_dict = {req.trait: req.percentage}
        iterate_traits(student_id, traits_dict)

        return get_traits(student_id)
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db.close()


@app.put("/api/trait/{student_id}/{trait_name}")
def api_update_trait(student_id: int, trait_name: str, req: TraitCreateUpdate):
    """更新性格特征"""
    db = SessionLocal()
    try:
        student = db.query(Student).filter_by(id=student_id).first()
        if not student:
            raise HTTPException(status_code=404, detail="学生不存在")

        # 删除旧特征，添加新特征
        old_trait = db.query(StudentTraits).filter_by(student_id=student_id, trait=trait_name).first()
        if old_trait:
            db.delete(old_trait)

        # 添加新特征
        new_trait = StudentTraits(student_id=student_id, trait=req.trait, percentage=req.percentage)
        db.add(new_trait)
        db.commit()

        return get_traits(student_id)
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db.close()


@app.delete("/api/trait/{student_id}/{trait_name}")
def api_delete_trait(student_id: int, trait_name: str):
    """删除性格特征"""
    db = SessionLocal()
    try:
        student = db.query(Student).filter_by(id=student_id).first()
        if not student:
            raise HTTPException(status_code=404, detail="学生不存在")

        trait = db.query(StudentTraits).filter_by(student_id=student_id, trait=trait_name).first()
        if not trait:
            raise HTTPException(status_code=404, detail="特征不存在")

        db.delete(trait)
        db.commit()
        return {"message": "删除成功"}
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db.close()


@app.post("/api/batch-generate")
def api_batch_generate(req: BatchGenerateRequest):
    """批量生成评价 - 一次性为全班学生生成 3 份不同风格的评价"""
    from generator import generate_batch_evaluations

    db = SessionLocal()
    try:
        # 获取学生列表
        if req.student_ids:
            students = db.query(Student).filter(Student.id.in_(req.student_ids)).all()
        else:
            students = db.query(Student).filter_by(class_id=req.class_id).all()

        if not students:
            raise HTTPException(status_code=404, detail="没有学生")

        # 构建学生数据：包含性格特点和当堂表现
        student_notes = req.student_notes or {}
        students_data = []
        for student in students:
            traits = get_traits(student.id)
            traits_pct = {t["trait"]: t["percentage"] for t in traits}
            notes = student_notes.get(str(student.id), "") or student_notes.get(student.id, "")
            students_data.append({
                "name": student.name,
                "traits_pct": traits_pct,
                "traits_raw": student.traits or "",
                "lesson_notes": notes,
            })

        # 调用批量生成函数（一次 AI 请求生成所有学生）
        batch_results = generate_batch_evaluations(
            lesson_content=req.lesson_content,
            students=students_data,
            lesson_notes_global=req.lesson_notes_global or "",
            api_key=req.api_key,
        )

        # 保存到数据库
        results = []
        for idx, (student, batch_result) in enumerate(zip(students, batch_results)):
            try:
                notes = students_data[idx]["lesson_notes"]
                for ev in batch_result.get("evaluations", []):
                    record = Evaluation(
                        student_id=student.id,
                        lesson_content=req.lesson_content,
                        lesson_date=date.today(),
                        performance_notes=notes,
                        template=ev["template"],
                        content=ev["content"],
                    )
                    db.add(record)

                results.append({
                    "student_id": student.id,
                    "student_name": batch_result["student_name"],
                    "evaluations": batch_result.get("evaluations", []),
                    "error": batch_result.get("error"),
                })
            except Exception as e:
                results.append({
                    "student_id": student.id,
                    "student_name": batch_result["student_name"],
                    "evaluations": [],
                    "error": str(e),
                })

        db.commit()
        return {"results": results}
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db.close()


@app.delete("/api/evaluation/{eval_id}")
def api_delete_evaluation(eval_id: int):
    """删除单条评价"""
    db = SessionLocal()
    try:
        ev = db.query(Evaluation).filter_by(id=eval_id).first()
        if not ev:
            raise HTTPException(status_code=404, detail="评价不存在")

        db.delete(ev)
        db.commit()
        return {"message": "删除成功"}
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db.close()


@app.get("/api/students/search")
def api_search_students(q: str):
    """模糊搜索学生"""
    db = SessionLocal()
    try:
        students = db.query(Student).filter(Student.name.like(f"%{q}%")).limit(50).all()
        result = []
        for s in students:
            cls = db.query(Class).filter_by(id=s.class_id).first()
            traits = get_traits(s.id)
            result.append({
                "id": s.id,
                "name": s.name,
                "class_id": s.class_id,
                "class_name": f"{cls.grade}{cls.name}" if cls else "",
                "traits_raw": s.traits,
                "traits": traits,
            })
        return result
    finally:
        db.close()


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)
