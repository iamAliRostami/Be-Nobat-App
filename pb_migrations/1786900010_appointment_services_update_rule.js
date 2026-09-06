/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
  const collection = app.findCollectionByNameOrId("pbc_1262204345")

  // [fix/production-readiness]
  // قبلاً updateRule این کالکشن خالی (null) بود، یعنی فقط superuser می‌توانست
  // آن را آپدیت کند. اما pb_hooks/41_appointment_services_status.pb.js و
  // pb_hooks/20_appointment_services_conflict.pb.js صراحتاً برای کاربران
  // عادی (غیر superuser) طراحی شده‌اند (نگاه کنید به e.hasSuperuserAuth()
  // در آن فایل‌ها) - یعنی بدون این Rule، صاحب کسب‌وکار/پرسنل هیچ‌وقت
  // نمی‌توانستند از طریق API واقعی وضعیت یک appointment_service را عوض کنند.
  //
  // این Rule اجازه‌ی آپدیت را به دو گروه می‌دهد:
  //   1) صاحب کسب‌وکاری که این نوبت متعلق به یکی از شعبه‌هایش است.
  //   2) کاربریِ که خودِ resource (پرسنل) این appointment_service است.
  // و تغییر مستقیم appointment_id/service_assignment_id/price/duration را
  // برای هر دو گروه ممنوع می‌کند (این مقادیر فقط باید توسط
  // pb_hooks/10_appointment_services_pricing.pb.js در لحظه‌ی create تعیین
  // شوند). status/note/order/start_at قابل تغییرند (reschedule یا انتقال
  // نوبت هم از همین مسیر و با چک تداخل در 20_ کنترل می‌شود).
  unmarshal({
    "updateRule": "(\n  appointment_id.branch_id.business_id.owner_user_id = @request.auth.id\n  ||\n  service_assignment_id.resource_assignment_id.resource_id.user_id = @request.auth.id\n)\n&&\n@request.body.appointment_id:changed = false\n&&\n@request.body.service_assignment_id:changed = false\n&&\n@request.body.price:changed = false\n&&\n@request.body.duration:changed = false"
  }, collection)

  return app.save(collection)
}, (app) => {
  const collection = app.findCollectionByNameOrId("pbc_1262204345")

  unmarshal({
    "updateRule": null
  }, collection)

  return app.save(collection)
})
