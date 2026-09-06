/// <reference path="../pb_data/types.d.ts" />

// این hook قبل از ذخیره‌ی هر appointment_service جدید اجرا می‌شه.
// هدف: هیچ‌وقت به price/duration ای که کلاینت فرستاده اعتماد نکن؛
// همیشه از روی service_assignment و branch_service (که owner تعیین کرده) دوباره محاسبه کن.
// همچنین مطمئن می‌شه resource انتخابی واقعاً توی همون شعبه‌ی appointment فعاله.
onRecordCreateRequest((e) => {
    let serviceAssignmentId = e.record.get("service_assignment_id");
    let appointmentId = e.record.get("appointment_id");

    if (!serviceAssignmentId || !appointmentId) {
        throw new BadRequestError("service_assignment_id و appointment_id الزامی هستند.");
    }

    let serviceAssignment;
    try {
        serviceAssignment = e.app.findRecordById("service_assignments", serviceAssignmentId);
    } catch (err) {
        throw new BadRequestError("service assignment انتخاب‌شده پیدا نشد.");
    }

    if (serviceAssignment.get("status") !== "active") {
        throw new BadRequestError("این سرویس/منبع در حال حاضر غیرفعال است.");
    }

    let branchService;
    try {
        branchService = e.app.findRecordById("branch_services", serviceAssignment.get("branch_service_id"));
    } catch (err) {
        throw new BadRequestError("branch service مرتبط پیدا نشد.");
    }

    if (branchService.get("status") !== "active") {
        throw new BadRequestError("این سرویس در این شعبه غیرفعال است.");
    }

    let resourceAssignment;
    try {
        resourceAssignment = e.app.findRecordById("resource_assignments", serviceAssignment.get("resource_assignment_id"));
    } catch (err) {
        throw new BadRequestError("resource assignment مرتبط پیدا نشد.");
    }

    let appointment;
    try {
        appointment = e.app.findRecordById("appointment", appointmentId);
    } catch (err) {
        throw new BadRequestError("appointment مرتبط پیدا نشد.");
    }

    // چک کن resource واقعاً همون شعبه‌ی appointment رو پوشش می‌ده
    if (resourceAssignment.get("branch_id") !== appointment.get("branch_id")) {
        throw new BadRequestError("این منبع/پرسنل در شعبه‌ی این نوبت فعالیت نمی‌کند.");
    }

    // قیمت/مدت نهایی: اگر override در سطح service_assignment ست شده باشه همون، وگرنه مقدار پایه‌ی branch_service.
    // نکته: چون price_override/duration_override فیلد optional عددی هستن (نه nullable واقعی)،
    // مقدار 0 به‌عنوان «تنظیم نشده» در نظر گرفته می‌شه. اگر لازم شد override واقعاً صفر باشه (سرویس رایگان)،
    // پیشنهاد می‌شه یه فیلد بولی جدا مثل has_price_override اضافه بشه.
    let priceOverride = Number(serviceAssignment.get("price_override")) || 0;
    let durationOverride = Number(serviceAssignment.get("duration_override")) || 0;

    let finalPrice = priceOverride > 0 ? priceOverride : Number(branchService.get("price"));
    let finalDuration = durationOverride > 0 ? durationOverride : Number(branchService.get("duration"));

    e.record.set("price", finalPrice);
    e.record.set("duration", finalDuration);

    e.next();
}, "appointment_services");
