"""
数据库迁移脚本 - 添加新字段
"""

from sqlalchemy import text
from database import engine, SessionLocal

def migrate():
    """执行数据库迁移"""
    db = SessionLocal()
    try:
        # 1. 给 Class 表添加 template_patterns 字段
        print("[1/4] Adding template_patterns to Class table...")
        try:
            db.execute(text(
                "ALTER TABLE class ADD COLUMN template_patterns TEXT"
            ))
            db.commit()
            print("[OK] Class 表添加 template_patterns 字段成功")
        except Exception as e:
            if "Duplicate column name" in str(e):
                print("[SKIP] Class 表已存在 template_patterns 字段，跳过")
            else:
                raise e

        # 2. 给 Evaluation 表添加 is_adopted 字段
        print("[2/4] Adding is_adopted to Evaluation table...")
        try:
            db.execute(text(
                "ALTER TABLE evaluation ADD COLUMN is_adopted TINYINT(1) DEFAULT 1 NOT NULL"
            ))
            db.commit()
            print("[OK] Evaluation 表添加 is_adopted 字段成功")
        except Exception as e:
            if "Duplicate column name" in str(e):
                print("[SKIP] Evaluation 表已存在 is_adopted 字段，跳过")
            else:
                raise e

        # 3. 更新 Student 表的 name 字段长度
        print("[3/4] Extending Student.name to VARCHAR(50)...")
        try:
            db.execute(text(
                "ALTER TABLE student MODIFY name VARCHAR(50)"
            ))
            db.commit()
            print("[OK] Student 表 name 字段扩展到 VARCHAR(50) 成功")
        except Exception as e:
            print(f"[SKIP] Student 表 name 字段修改失败：{e}")

        # 4. 给现有 Evaluation 数据的 is_adopted 设为 True
        print("[4/4] Updating existing evaluations is_adopted status...")
        db.execute(text(
            "UPDATE evaluation SET is_adopted = 1 WHERE is_adopted IS NULL"
        ))
        db.commit()
        print("[OK] 现有评价数据 is_adopted 状态更新完成")

        print("\n=== Migration Completed ===")

    except Exception as e:
        db.rollback()
        print(f"Migration failed: {e}")
        raise
    finally:
        db.close()


if __name__ == "__main__":
    migrate()
