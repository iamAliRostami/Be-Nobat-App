/// <reference path="../pb_data/types.d.ts" />

// بعد از هر create/update/delete روی appointment_services، appointment والدش بازمحاسبه می‌شه:
//   start        = زودترین start_at بین سرویس‌های فعال (غیر cancelled)
//   end          = دیرترین (start_at + duration)
//   total_price  = مجموع price سرویس‌های فعال
//   final_price  = total_price - discount_amount (کف صفر)
function recalcAppointment(app, appointmentId) {
    if (!appointmentId) return;

    let services;
    try {
        services = app.findRecordsByFilter(
            "appointment_services",
            "appointment_id = {:id} && status != 'cancelled'",
            "start_at",
            0,
            0,
            { "id": appointmentId }
        );
    } catch (err) {
        return;
    }

    let appointment;
    try {
        appointment = app.findRecordById("appointment", appointmentId);
    } catch (err) {
        return;
    }

    let discount = Number(appointment.get("discount_amount")) || 0;

    if (services.length === 0) {
        // همه‌ی سرویس‌ها کنسل شدن یا حذف شدن؛ فقط قیمت‌ها صفر می‌شن (start/end دست‌نخورده می‌مونه)
        appointment.set("total_price", 0);
        appointment.set("final_price", 0);
        app.save(appointment);
        return;
    }

    let minStart = null;
    let maxEnd = null;
    let total = 0;

    for (let i = 0; i < services.length; i++) {
        let s = services[i];
        let start = new Date(s.get("start_at"));
        let duration = Number(s.get("duration")) || 0;
        let end = new Date(start.getTime() + duration * 60000);
        let price = Number(s.get("price")) || 0;

        total += price;
        if (minStart === null || start < minStart) minStart = start;
        if (maxEnd === null || end > maxEnd) maxEnd = end;
    }

    appointment.set("start", minStart.toISOString());
    appointment.set("end", maxEnd.toISOString());
    appointment.set("total_price", total);
    appointment.set("final_price", Math.max(total - discount, 0));

    app.save(appointment);
}

onRecordAfterCreateSuccess((e) => {
    recalcAppointment(e.app, e.record.get("appointment_id"));
    e.next();
}, "appointment_services");

onRecordAfterUpdateSuccess((e) => {
    recalcAppointment(e.app, e.record.get("appointment_id"));
    e.next();
}, "appointment_services");

onRecordAfterDeleteSuccess((e) => {
    recalcAppointment(e.app, e.record.get("appointment_id"));
    e.next();
}, "appointment_services");
