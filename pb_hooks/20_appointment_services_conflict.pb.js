/// <reference path="../pb_data/types.d.ts" />

// جلوگیری از تداخل زمانی (double-booking): یک resource (پرسنل/منبع فیزیکی)
// نباید دو appointment_service با بازه‌ی زمانی هم‌پوشان و status غیر از
// cancelled داشته باشه. این فایل بعد از 10_appointment_services_pricing.pb.js
// اجرا می‌شه (به لطف پیشوند عددی نام فایل)، پس در لحظه‌ی اجرا duration واقعی
// (نه مقدار کلاینت) از قبل روی رکورد ست شده.
//
// [fix/production-readiness] دو اصلاح نسبت به نسخه‌ی قبلی:
//
// 1) دامنه‌ی چک: نسخه‌ی قبلی فقط appointment_serviceهای با همان
//    service_assignment_id را می‌دید. اما یک resource واحد می‌تواند چند
//    service_assignment مختلف داشته باشد (مثلاً یک آرایشگر هم «اصلاح مو» و
//    هم «اصلاح ریش» ارائه می‌دهد - دو service_assignment جدا، یک
//    resource_assignment مشترک). با دامنه‌ی قبلی، رزرو هم‌زمان این دو
//    سرویس برای همان شخص تشخیص داده نمی‌شد. حالا چک روی
//    resource_assignment_id (که از طریق رابطه‌ی service_assignment_id به
//    دست می‌آید) انجام می‌شود.
//
// 2) Race Condition (Time-Of-Check-Time-Of-Use): چک قبلی فقط در
//    onRecordCreateRequest/onRecordUpdateRequest انجام می‌شد. این دو رویداد
//    در سطح API و *قبل* از تراکنش واقعی ذخیره‌سازی اجرا می‌شوند؛ اگر دو
//    درخواست هم‌زمان برای یک بازه‌ی یکسان از یک resource برسند، هر دو
//    می‌توانند این کوئری findRecordsByFilter را قبل از این‌که هیچ‌کدام
//    رکوردشان را ذخیره کرده باشند اجرا کنند، هیچ‌کدام تداخلی نبینند، و هر
//    دو با موفقیت ذخیره شوند (یک double-booking واقعی).
//
//    راه‌حل: کل «چک + ذخیره» داخل e.app.runInTransaction انجام می‌شود، و
//    بلافاصله بعد از شروع تراکنش، یک UPDATE واقعی (بی‌اثر از نظر داده، فقط
//    برای گرفتن قفل) روی رکورد resource_assignment اجرا می‌شود. SQLite تنها
//    یک نویسنده‌ی هم‌زمان دارد؛ این UPDATE باعث می‌شود قفل نوشتن همان لحظه
//    گرفته شود، نه فقط در لحظه‌ی INSERT نهایی. در نتیجه اگر دو درخواست
//    هم‌زمان برای همان resource برسند، دومی تا commit شدن تراکنش اول منتظر
//    می‌ماند و SELECT خودش را *بعد* از commit شدن اولی اجرا می‌کند - یعنی
//    تداخل واقعی را می‌بیند و رد می‌شود. برای resourceهای متفاوت هیچ تأثیری
//    روی هم‌زمانی ندارد (فقط رکورد همان resource_assignment قفل می‌شود).

function resolveResourceAssignmentId(app, serviceAssignmentId) {
    if (!serviceAssignmentId) return "";
    try {
        let serviceAssignment = app.findRecordById("service_assignments", serviceAssignmentId);
        return serviceAssignment.getString("resource_assignment_id");
    } catch (err) {
        return "";
    }
}

function checkOverlap(app, record) {
    let serviceAssignmentId = record.get("service_assignment_id");
    let startAtStr = record.get("start_at");
    let duration = Number(record.get("duration")) || 0;

    if (!serviceAssignmentId || !startAtStr || duration <= 0) {
        // اعتبارسنجی پایه (required بودن فیلدها) رو خود PocketBase انجام می‌ده
        return;
    }

    let resourceAssignmentId = resolveResourceAssignmentId(app, serviceAssignmentId);
    if (!resourceAssignmentId) {
        return; // service_assignment نامعتبر؛ اعتبارسنجی دیگری (010_domain_validations) این را می‌گیرد
    }

    let newStart = new Date(startAtStr);
    let newEnd = new Date(newStart.getTime() + duration * 60000);

    // برای اینکه query سنگین نشه، فقط رکوردهای یک بازه‌ی معقول (۱۲ ساعت قبل تا پایان نوبت جدید) رو می‌گیریم
    // و overlap دقیق رو توی جاوااسکریپت چک می‌کنیم (چون فیلتر PocketBase نمی‌تونه start_at + duration رو محاسبه کنه)
    let windowStart = new Date(newStart.getTime() - 12 * 60 * 60000).toISOString();

    let candidates = app.findRecordsByFilter(
        "appointment_services",
        "service_assignment_id.resource_assignment_id = {:ra} && status != 'cancelled' && start_at >= {:from} && start_at <= {:to}",
        "",
        0,
        0,
        {
            "ra": resourceAssignmentId,
            "from": windowStart,
            "to": newEnd.toISOString(),
        }
    );

    for (let i = 0; i < candidates.length; i++) {
        let c = candidates[i];
        if (c.id === record.id) {
            continue; // در حالت update، خود رکورد رو نادیده بگیر
        }
        let cStart = new Date(c.get("start_at"));
        let cDuration = Number(c.get("duration")) || 0;
        let cEnd = new Date(cStart.getTime() + cDuration * 60000);

        // دو بازه تداخل دارن اگر: newStart < cEnd && cStart < newEnd
        if (newStart < cEnd && cStart < newEnd) {
            throw new BadRequestError("این بازه‌ی زمانی برای این پرسنل/منبع قبلاً رزرو شده است.");
        }
    }

    return resourceAssignmentId;
}

function scheduleFieldsChanged(record) {
    let original = record.original();
    return (
        original.get("start_at") !== record.get("start_at") ||
        original.get("duration") !== record.get("duration") ||
        original.get("service_assignment_id") !== record.get("service_assignment_id")
    );
}

// اگر خطا از نوع "database is locked"/"busy" باشه (رقابت هم‌زمان زیاد روی
// SQLite)، به‌جای خطای خام، پیام قابل‌فهم به کلاینت برمی‌گردونه.
function isTransientDbError(err) {
    let msg = String((err && err.message) || err || "").toLowerCase();
    return msg.indexOf("database is locked") !== -1 ||
        msg.indexOf("busy") !== -1 ||
        msg.indexOf("locked") !== -1;
}

// قفل نوشتن SQLite رو همون ابتدای تراکنش می‌گیره (به‌جای این‌که SQLite صبر
// کنه تا اولین INSERT/UPDATE واقعی)، تا پنجره‌ی race بین چک و ذخیره بسته بشه.
function acquireResourceLock(txApp, resourceAssignmentId) {
    if (!resourceAssignmentId) return;
    try {
        txApp.db()
            .newQuery("UPDATE resource_assignments SET updated = updated WHERE id = {:id}")
            .bind({ id: resourceAssignmentId })
            .execute();
    } catch (err) {
        txApp.logger().warn("could not acquire booking write-lock", "error", err);
    }
}

function runOverlapCheckInTransaction(e, needsCheck) {
    let originalApp = e.app;

    try {
        e.app.runInTransaction((txApp) => {
            e.app = txApp;

            if (needsCheck(e.record)) {
                let resourceAssignmentId = resolveResourceAssignmentId(
                    txApp,
                    e.record.get("service_assignment_id")
                );
                acquireResourceLock(txApp, resourceAssignmentId);
                checkOverlap(txApp, e.record);
            }

            e.next();
        });
    } catch (err) {
        if (err instanceof BadRequestError) {
            throw err;
        }
        if (isTransientDbError(err)) {
            throw new BadRequestError("سیستم شلوغ است، لطفاً چند لحظه دیگر دوباره تلاش کنید.");
        }
        throw err;
    } finally {
        e.app = originalApp;
    }
}

onRecordCreateRequest((e) => {
    runOverlapCheckInTransaction(e, () => true);
}, "appointment_services");

onRecordUpdateRequest((e) => {
    runOverlapCheckInTransaction(e, (record) => scheduleFieldsChanged(record));
}, "appointment_services");
