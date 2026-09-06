import json
import os

def map_pb_type_to_kotlin(pb_type, options):
    """تبدیل تایپ‌های پاکت‌بیس به کاتلین"""
    if pb_type in ["text", "editor", "url", "email", "date"]:
        return "String"
    elif pb_type == "number":
        return "Double" # یا Int
    elif pb_type == "bool":
        return "Boolean"
    elif pb_type == "json":
        return "kotlinx.serialization.json.JsonElement"
    elif pb_type in ["relation", "file", "select"]:
        if options.get("maxSelect", 1) == 1:
            return "String"
        else:
            return "List<String>"
    else:
        return "String"

def snake_to_pascal(snake_str):
    """تبدیل نام جداول به فرمت کلاس‌های کاتلین"""
    components = snake_str.split('_')
    return "".join(x.title() for x in components)

def generate_kotlin_files(schema_file_path, output_dir_name):
    # بررسی وجود فایل جیسون
    if not os.path.exists(schema_file_path):
        print(f"❌ خطا: فایل {schema_file_path} پیدا نشد!")
        return

    # ساخت پوشه خروجی اگر وجود ندارد
    if not os.path.exists(output_dir_name):
        os.makedirs(output_dir_name)

    with open(schema_file_path, 'r', encoding='utf-8') as f:
        collections = json.load(f)

    # 1. ساخت فایل BaseDomain.kt
    base_code = """import kotlinx.serialization.Serializable

@Serializable
open class BaseDomain {
    val id: String = ""
    val created: String = ""
    val updated: String = ""
}
"""
    with open(os.path.join(output_dir_name, "BaseDomain.kt"), 'w', encoding='utf-8') as f:
        f.write(base_code)

    # 2. تولید یک فایل مجزا برای هر جدول
    for col in collections:
        col_name = col.get("name", "")
        
        # جداول سیستمی را رد کن
        if col_name.startswith("_"):
            continue 

        class_name = snake_to_pascal(col_name) + "Domain"
        file_name = f"{class_name}.kt"
        
        fields_code = []
        for field in col.get("schema", []):
            f_name = field.get("name")
            
            # فیلدهای پیش‌فرض را رد کن چون در BaseDomain هستند
            if f_name in ["id", "created", "updated"]:
                continue 

            f_type = field.get("type")
            f_options = field.get("options", {})
            f_required = field.get("required", False)

            kt_type = map_pb_type_to_kotlin(f_type, f_options)
            
            # اگر فیلد اجباری نیست، آن را Nullable کن
            if not f_required:
                kt_type += "?"

            fields_code.append(f"    val {f_name}: {kt_type}")

        # ترکیب و ساخت بدنه فایل کاتلین
        kotlin_code = "import kotlinx.serialization.Serializable\n\n"
        kotlin_code += f"@Serializable\ndata class {class_name}(\n"
        kotlin_code += ",\n".join(fields_code)
        kotlin_code += "\n) : BaseDomain()\n"

        # 3. ذخیره در فایل اختصاصی خودش
        with open(os.path.join(output_dir_name, file_name), 'w', encoding='utf-8') as f:
            f.write(kotlin_code)

    print(f"✅ موفقیت‌آمیز! تمام فایل‌های کاتلین به صورت مجزا در پوشه '{output_dir_name}' ساخته شدند.")

if __name__ == "__main__":
    # نام فایل ورودی و اسم پوشه خروجی
    generate_kotlin_files("pb_schema.json", "KotlinDomains")