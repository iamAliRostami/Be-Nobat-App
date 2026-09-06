/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_1970990732")

  // [fix/production-readiness]
  // این پنج فیلد به‌صورت خودکار پر می‌شوند:
  //   - start/end/total_price/final_price توسط
  //     pb_hooks/30_appointment_services_aggregate.pb.js پس از افزوده شدن هر
  //     appointment_service بازمحاسبه می‌شوند.
  //   - total_price/discount_amount/final_price همچنین در لحظه‌ی create با
  //     مقدار پیش‌فرض 0 توسط pb_hooks/010_domain_validations.pb.js پر می‌شوند.
  // اما چون در لحظه‌ی ساخت اولیه‌ی appointment (پیش از افزودن هیچ سرویسی)
  // start/end هنوز معنایی ندارند، required=true روی این فیلدها اصلاً اجازه‌ی
  // ساخت appointment جدید را نمی‌داد.
  //
  // توجه: عمداً از collection.fields.getByName(...) + تغییر پراپرتی به‌جای
  // بازسازی Field با id دستی استفاده شده، چون id هاردکدشده ممکنه با id
  // واقعی فیلد در دیتابیس شما یکی نباشه (همون چیزی که باعث خطای
  // «Duplicated or invalid field name» می‌شد).

  const start = collection.fields.getByName("start")
  start.required = false

  const end = collection.fields.getByName("end")
  end.required = false

  const totalPrice = collection.fields.getByName("total_price")
  totalPrice.required = false
  totalPrice.min = 0

  const discountAmount = collection.fields.getByName("discount_amount")
  discountAmount.required = false
  discountAmount.min = 0

  const finalPrice = collection.fields.getByName("final_price")
  finalPrice.required = false
  finalPrice.min = 0

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_1970990732")

  const start = collection.fields.getByName("start")
  start.required = true

  const end = collection.fields.getByName("end")
  end.required = true

  const totalPrice = collection.fields.getByName("total_price")
  totalPrice.required = true
  totalPrice.min = 1

  const discountAmount = collection.fields.getByName("discount_amount")
  discountAmount.required = true
  discountAmount.min = 1

  const finalPrice = collection.fields.getByName("final_price")
  finalPrice.required = true
  finalPrice.min = 1

  return app.save(collection)
})
