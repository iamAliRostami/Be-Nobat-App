/// <reference path="../pb_data/types.d.ts" />

// state machine مشابه برای appointment_services.status (سطح هر خط سرویس داخل نوبت)
const APPT_SERVICE_TRANSITIONS = {
    "pending": ["in_progress", "cancelled"],
    "in_progress": ["completed", "cancelled"],
    "completed": [],
    "cancelled": [],
};

onRecordUpdateRequest((e) => {
    let original = e.record.original();
    let oldStatus = original.get("status");
    let newStatus = e.record.get("status");

    if (oldStatus !== newStatus) {
        if (e.hasSuperuserAuth()) {
            e.next();
            return;
        }
        let allowed = APPT_SERVICE_TRANSITIONS[oldStatus] || [];
        if (allowed.indexOf(newStatus) === -1) {
            throw new BadRequestError(
                "تغییر وضعیت سرویس از «" + oldStatus + "» به «" + newStatus + "» مجاز نیست."
            );
        }
    }

    e.next();
}, "appointment_services");
