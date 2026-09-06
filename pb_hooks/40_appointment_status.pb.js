/// <reference path="../pb_data/types.d.ts" />

// state machine برای appointment.status.
// یادآوری: طبق rule های کالکشن appointment، مشتری اصلاً اجازه‌ی تغییر status رو نداره (فقط owner/staff).
// این hook یه لایه‌ی دفاعی دومه که حتی برای owner/staff/superuser هم گذارهای بی‌معنی رو می‌بنده.
const APPOINTMENT_TRANSITIONS = {
    "pending": ["confirmed", "cancelled"],
    "confirmed": ["in_progress", "cancelled", "no_show"],
    "in_progress": ["completed", "cancelled"],
    "completed": [],
    "cancelled": [],
    "no_show": [],
};

onRecordUpdateRequest((e) => {
    let original = e.record.original();
    let oldStatus = original.get("status");
    let newStatus = e.record.get("status");

    if (oldStatus !== newStatus) {
        if (e.hasSuperuserAuth()) {
            // ادمین سیستم (از پنل ادمین) می‌تونه برای اصلاح دستی داده استثنا بزنه
            e.next();
            return;
        }
        let allowed = APPOINTMENT_TRANSITIONS[oldStatus] || [];
        if (allowed.indexOf(newStatus) === -1) {
            throw new BadRequestError(
                "تغییر وضعیت نوبت از «" + oldStatus + "» به «" + newStatus + "» مجاز نیست."
            );
        }
    }

    e.next();
}, "appointment");
