import json

def pb_type_to_kotlin(pb_type, options):
    """تبدیل تایپ‌های پاکت‌بیس به کاتلین"""
    if pb_type in ["text", "editor", "url", "email", "date"]:
        return "String"
    elif pb_type == "number":
        return "Double"
    elif pb_type == "bool":
        return "Boolean"
    elif pb_type == "json":
        return "kotlinx.serialization.json.JsonElement"
    elif pb_type == "relation":
        # اگر کاربر فقط بتونه یکی انتخاب کنه میشه استرینگ، وگرنه میشه لیست
        if options.get("maxSelect", 1) == 1:
            return "String"
        else:
            return "List<String>"
    else:
        return "String"

def generate_kotlin_models(json_file_path):
    try:
        with open(json_file_path, 'r', encoding='utf-8') as f:
            collections = json.load(f)
    except FileNotFoundError:
        print("خطا: فایل pb_schema.json پیدا نشد!")
        return

    # کلاس پایه که تمام مدل‌ها ازش ارث‌بری می‌کنن
    kotlin_code = """import kotlinx.serialization.Serializable

@Serializable
open class BaseModel {
    val id: String = ""
    val created: String = ""
    val updated: String = ""
}

"""
    for collection in collections:
        # جداول سیستمی (مثل _superusers) رو نادیده می‌گیریم
        if collection.get("name", "").startswith("_"):
            continue
            
        table_name = collection.get("name", "")
        # تبدیل snake_case به PascalCase برای نام کلاس (مثلا branch_services -> BranchServicesModel)
        class_name = "".join(word.capitalize() for word in table_name.split("_")) + "Model"
        
        fields = []
        schema = collection.get("schema", [])
        
        for field in schema:
            field_name = field.get("name")
            field_type = field.get("type")
            options = field.get("options", {})
            
            # فیلدهای پایه رو رد می‌کنیم چون در BaseModel هستند
            if field_name in ["id", "created", "updated"]:
                continue
                
            kt_type = pb_type_to_kotlin(field_type, options)
            
            # اگر فیلد اجباری نیست، نوع رو Nullable می‌کنیم
            is_required = field.get("required", False)
            if not is_required:
                kt_type += "?"
                
            fields.append(f"    val {field_name}: {kt_type}")
        
        kotlin_code += f"@Serializable\ndata class {class_name}(\n"
        kotlin_code += ",\n".join(fields)
        kotlin_code += "\n) : BaseModel()\n\n"

    # ذخیره خروجی در یک فایل کاتلین
    output_filename = "PocketBaseModels.kt"
    with open(output_filename, 'w', encoding='utf-8') as out_file:
        out_file.write(kotlin_code)
        
    print(f"✅ موفقیت‌آمیز! تمام کلاس‌های کاتلین در فایل '{output_filename}' ساخته شدند.")

if __name__ == "__main__":
    generate_kotlin_models("pb_schema.json")