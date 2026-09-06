/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_2301922722")

  // [fix/production-readiness]
  // notifications.status شامل مقدار "pending" است، یعنی یک نوتیفیکیشن باید
  // بتواند قبل از ارسال واقعی (پیش از این‌که sent_at معنایی داشته باشد) ساخته
  // شود. required=true روی sent_at این حالت را غیرممکن می‌کرد.
  const sentAt = collection.fields.getByName("sent_at")
  sentAt.required = false

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_2301922722")

  const sentAt = collection.fields.getByName("sent_at")
  sentAt.required = true

  return app.save(collection)
})
