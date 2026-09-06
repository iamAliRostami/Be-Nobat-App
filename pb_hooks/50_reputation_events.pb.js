/// <reference path="../pb_data/types.d.ts" />

// بعد از اینکه appointment.status به completed / no_show / cancelled تغییر کرد،
// یک رکورد reputation_events برای مشتری (client) به‌صورت خودکار ساخته می‌شه.
function createReputationEvent(app, subjectUserId, appointmentId, sourceUserId, businessId, type, score) {
    if (!subjectUserId) return;
    try {
        let collection = app.findCollectionByNameOrId("reputation_events");
        let record = new Record(collection);
        record.set("subject_user_id", subjectUserId);
        record.set("appointment_id", appointmentId);
        record.set("source_user_id", sourceUserId || "");
        record.set("business_id", businessId || "");
        record.set("type", type);
        record.set("score", score);
        app.save(record);
    } catch (err) {
        app.logger().error("Failed to create reputation event", "error", err);
    }
}

onRecordAfterUpdateSuccess((e) => {
    let original = e.record.original();
    let oldStatus = original.get("status");
    let newStatus = e.record.get("status");

    if (oldStatus === newStatus) {
        e.next();
        return;
    }

    let clientId = e.record.get("client_user_id");
    let actorId = e.auth ? e.auth.id : "";
    let branchId = e.record.get("branch_id");
    let businessId = "";
    try {
        let branch = e.app.findRecordById("branches", branchId);
        businessId = branch.get("business_id");
    } catch (err) {
        // اگر branch پیدا نشد، بدون business_id ادامه بده
    }

    if (newStatus === "completed") {
        createReputationEvent(e.app, clientId, e.record.id, actorId, businessId, "appointment_completed", 5);
    } else if (newStatus === "no_show") {
        createReputationEvent(e.app, clientId, e.record.id, actorId, businessId, "no_show", -10);
    } else if (newStatus === "cancelled") {
        // لغو توسط کسب‌وکار/پرسنل نباید اعتبار مشتری را کاهش دهد. فقط وقتی
        // خود مشتری عامل درخواست است قانون جریمه‌ی زمانی اعمال می‌شود.
        if (actorId !== clientId) {
            createReputationEvent(e.app, clientId, e.record.id, actorId, businessId, "appointment_cancelled", 0);
            e.next();
            return;
        }

        // اگر کمتر از ۲۴ ساعت به شروع نوبت کنسل شده، جریمه‌ی بیشتری در نظر گرفته می‌شه
        let start = new Date(e.record.get("start"));
        let now = new Date();
        let hoursLeft = (start.getTime() - now.getTime()) / (1000 * 60 * 60);
        if (hoursLeft < 24) {
            createReputationEvent(e.app, clientId, e.record.id, actorId, businessId, "late_cancellation", -5);
        } else {
            createReputationEvent(e.app, clientId, e.record.id, actorId, businessId, "cancelled_on_time", 0);
        }
    }

    e.next();
}, "appointment");
