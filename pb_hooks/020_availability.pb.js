/// <reference path="../pb_data/types.d.ts" />

// ============================================================================
// Base Availability Route
//
// POST /api/be-nobat/availability/base
//
// [fix/production-readiness] این نسخه، برخلاف نسخه‌ی قبلی، این موارد را هم
// در نظر می‌گیرد (که مهم‌ترین نقصِ کارکردی endpoint قبلی بود: بدون این‌ها
// اسلات‌هایی که قبلاً واقعاً رزرو شده بودند هم به‌عنوان «آزاد» برگردانده
// می‌شدند):
//
// - resource_exceptions (تعطیلی/مرخصی/تعمیرات به‌عنوان بازه‌ی مسدود، و
//   ساعات کاری فوق‌العاده به‌عنوان بازه‌ی اضافه)
// - appointment_services موجود و فعال (غیر cancelled) روی همین resource
//
// هنوز این مورد پیاده‌سازی نشده:
// - امتیازدهی و بهینه‌سازی Gapها (اولویت‌بندی اسلات‌ها بر اساس فاصله‌ی
//   بهینه بین نوبت‌ها). این صرفاً یک بهینه‌سازی UX است، نه یک باگ صحت داده.
//
// open_time و close_time از نوع Text و با فرمت HH:mm هستند.
// ============================================================================

routerAdd(
    "POST",

    "/api/be-nobat/availability/base",

    (e) => {

        // ====================================================================
        // Helper Functions
        // ====================================================================

        function timeToMinute(value) {

            const parts =
                value.split(":");


            if (
                parts.length !== 2
            ) {
                throw new BadRequestError(
                    "فرمت زمان نامعتبر است. زمان باید به صورت HH:mm باشد."
                );
            }


            const hour =
                parseInt(parts[0], 10);

            const minute =
                parseInt(parts[1], 10);


            if (
                isNaN(hour) ||
                isNaN(minute) ||
                hour < 0 ||
                hour > 23 ||
                minute < 0 ||
                minute > 59
            ) {
                throw new BadRequestError(
                    "مقدار زمان نامعتبر است."
                );
            }


            return (
                hour * 60 +
                minute
            );
        }


        function minuteToTime(value) {

            const hour =
                Math.floor(
                    value / 60
                );

            const minute =
                value % 60;


            return (
                (hour < 10 ? "0" : "") +
                hour +
                ":" +
                (minute < 10 ? "0" : "") +
                minute
            );
        }


        function parseLocalDate(value) {

            if (
                !/^\d{4}-\d{2}-\d{2}$/.test(
                    value
                )
            ) {
                throw new BadRequestError(
                    "فرمت تاریخ باید به صورت YYYY-MM-DD باشد."
                );
            }


            const parts =
                value.split("-");


            const year =
                parseInt(
                    parts[0],
                    10
                );

            const month =
                parseInt(
                    parts[1],
                    10
                );

            const day =
                parseInt(
                    parts[2],
                    10
                );


            const date =
                new Date(
                    Date.UTC(
                        year,
                        month - 1,
                        day
                    )
                );


            // جلوگیری از تاریخ‌هایی مثل:
            // 2026-02-31
            if (
                date.getUTCFullYear() !== year ||
                date.getUTCMonth() !== month - 1 ||
                date.getUTCDate() !== day
            ) {
                throw new BadRequestError(
                    "تاریخ واردشده معتبر نیست."
                );
            }


            return date;
        }


        function findRequiredRecord(
            app,
            collectionName,
            id,
            label
        ) {

            if (!id) {
                throw new BadRequestError(
                    label +
                    " الزامی است."
                );
            }


            try {

                return app.findRecordById(
                    collectionName,
                    id
                );

            } catch (err) {

                throw new BadRequestError(
                    label +
                    " پیدا نشد."
                );
            }
        }


        function ensureActiveNotDeleted(
            record,
            label
        ) {

            if (
                record.getString("status") !==
                "active"
            ) {
                throw new BadRequestError(
                    label +
                    " فعال نیست."
                );
            }


            if (
                !record
                    .getDateTime("deleted_at")
                    .isZero()
            ) {
                throw new BadRequestError(
                    label +
                    " حذف شده است."
                );
            }
        }


        // ====================================================================
        // Request Body
        // ====================================================================

        const body =
            e.requestInfo().body;


        const resourceAssignmentId =
            body.resource_assignment_id ||
            "";


        const requestedDate =
            body.date ||
            "";


        const serviceAssignmentIds =
            body.service_assignment_ids ||
            [];


        if (
            resourceAssignmentId === ""
        ) {
            throw new BadRequestError(
                "resource_assignment_id الزامی است."
            );
        }


        if (
            requestedDate === ""
        ) {
            throw new BadRequestError(
                "تاریخ الزامی است."
            );
        }


        if (
            !Array.isArray(
                serviceAssignmentIds
            ) ||
            serviceAssignmentIds.length === 0
        ) {
            throw new BadRequestError(
                "حداقل یک service_assignment_id باید انتخاب شود."
            );
        }


        // ====================================================================
        // جلوگیری از Service Assignment تکراری
        // ====================================================================

        const uniqueServiceAssignmentIds =
            [];


        for (
            let i = 0;
            i < serviceAssignmentIds.length;
            i++
        ) {

            const id =
                serviceAssignmentIds[i];


            if (
                typeof id !== "string" ||
                id === ""
            ) {
                throw new BadRequestError(
                    "شناسه یکی از سرویس‌های انتخاب‌شده معتبر نیست."
                );
            }


            if (
                uniqueServiceAssignmentIds
                    .indexOf(id) !== -1
            ) {
                throw new BadRequestError(
                    "یک سرویس نمی‌تواند بیش از یک‌بار در درخواست تکرار شود."
                );
            }


            uniqueServiceAssignmentIds
                .push(id);
        }


        // ====================================================================
        // Date
        //
        // Convention:
        //
        // 0 = Sunday
        // 1 = Monday
        // ...
        // 6 = Saturday
        //
        // ====================================================================

        const date =
            parseLocalDate(
                requestedDate
            );


        const dayOfWeek =
            date.getUTCDay();


        // ====================================================================
        // Resource Assignment
        // ====================================================================

        const resourceAssignment =
            findRequiredRecord(
                e.app,

                "resource_assignments",

                resourceAssignmentId,

                "تخصیص منبع"
            );


        ensureActiveNotDeleted(
            resourceAssignment,
            "تخصیص منبع"
        );


        const branchId =
            resourceAssignment
                .getString(
                    "branch_id"
                );


        const resourceId =
            resourceAssignment
                .getString(
                    "resource_id"
                );


        // ====================================================================
        // Branch
        // ====================================================================

        const branch =
            findRequiredRecord(
                e.app,

                "branches",

                branchId,

                "شعبه"
            );


        ensureActiveNotDeleted(
            branch,
            "شعبه"
        );


        // ====================================================================
        // Resource
        // ====================================================================

        const resource =
            findRequiredRecord(
                e.app,

                "resources",

                resourceId,

                "منبع"
            );


        ensureActiveNotDeleted(
            resource,
            "منبع"
        );


        // ====================================================================
        // Service Assignments
        // ====================================================================

        const serviceAssignments =
            e.app.findRecordsByIds(
                "service_assignments",
                uniqueServiceAssignmentIds
            );


        if (
            serviceAssignments.length !==
            uniqueServiceAssignmentIds.length
        ) {
            throw new BadRequestError(
                "یک یا چند سرویس انتخاب‌شده وجود ندارند."
            );
        }


        let totalDuration =
            0;


        for (
            let i = 0;
            i < serviceAssignments.length;
            i++
        ) {

            const serviceAssignment =
                serviceAssignments[i];


            // ------------------------------------------------------------
            // همه سرویس‌ها فعلاً باید متعلق به یک ResourceAssignment باشند.
            // ------------------------------------------------------------

            if (
                serviceAssignment
                    .getString(
                        "resource_assignment_id"
                    ) !==
                resourceAssignmentId
            ) {
                throw new BadRequestError(
                    "تمام سرویس‌های انتخاب‌شده باید توسط همان منبع در همان شعبه ارائه شوند."
                );
            }


            ensureActiveNotDeleted(
                serviceAssignment,
                "تخصیص سرویس"
            );


            const branchServiceId =
                serviceAssignment
                    .getString(
                        "branch_service_id"
                    );


            const branchService =
                findRequiredRecord(
                    e.app,

                    "branch_services",

                    branchServiceId,

                    "سرویس شعبه"
                );


            ensureActiveNotDeleted(
                branchService,
                "سرویس شعبه"
            );


            if (
                branchService
                    .getString("branch_id") !==
                branchId
            ) {
                throw new BadRequestError(
                    "یکی از سرویس‌های انتخاب‌شده متعلق به شعبه موردنظر نیست."
                );
            }


            // =================================================================
            // اولویت مدت زمان:
            //
            // service_assignments.duration_override
            //                ↓
            // branch_services.duration
            // =================================================================

            let duration =
                serviceAssignment
                    .getInt(
                        "duration_override"
                    );


            if (
                duration <= 0
            ) {
                duration =
                    branchService
                        .getInt(
                            "duration"
                        );
            }


            if (
                duration <= 0
            ) {
                throw new BadRequestError(
                    "مدت زمان یکی از سرویس‌های انتخاب‌شده معتبر نیست."
                );
            }


            totalDuration +=
                duration;
        }


        // ====================================================================
        // Resource Weekly Availability
        //
        // open_time و close_time از نوع Text هستند.
        // فرمت:
        //
        // HH:mm
        //
        // ====================================================================

        const baseIntervalRows =
            e.app.findRecordsByFilter(

                "resource_availability",

                `
                    resource_assignment_id = {:assignment}
                    &&
                    day_of_week = {:day}
                    &&
                    status = "active"
                `,

                "open_time",

                100,

                0,

                {
                    assignment:
                        resourceAssignmentId,

                    day:
                        dayOfWeek
                }
            );

        const baseIntervals = [];

        for (let i = 0; i < baseIntervalRows.length; i++) {
            const openMinute = timeToMinute(baseIntervalRows[i].getString("open_time"));
            const closeMinute = timeToMinute(baseIntervalRows[i].getString("close_time"));

            if (openMinute >= closeMinute) {
                throw new BadRequestError(
                    "یکی از بازه‌های زمانی منبع معتبر نیست."
                );
            }

            baseIntervals.push({ open: openMinute, close: closeMinute });
        }


        // ====================================================================
        // Interval Helpers
        //
        // اعداد به‌صورت «دقیقه از شروع روز» (0 تا 1440) نگه‌داری می‌شوند.
        // ====================================================================

        function mergeIntervals(list) {
            if (list.length === 0) return [];

            const sorted = list.slice().sort((a, b) => a.open - b.open);
            const merged = [{ open: sorted[0].open, close: sorted[0].close }];

            for (let i = 1; i < sorted.length; i++) {
                const last = merged[merged.length - 1];
                if (sorted[i].open <= last.close) {
                    last.close = Math.max(last.close, sorted[i].close);
                } else {
                    merged.push({ open: sorted[i].open, close: sorted[i].close });
                }
            }

            return merged;
        }

        function subtractIntervals(base, cuts) {
            let result = [];

            for (let i = 0; i < base.length; i++) {
                let segments = [{ open: base[i].open, close: base[i].close }];

                for (let c = 0; c < cuts.length; c++) {
                    const cut = cuts[c];
                    const next = [];

                    for (let s = 0; s < segments.length; s++) {
                        const seg = segments[s];

                        if (cut.close <= seg.open || cut.open >= seg.close) {
                            // بدون هم‌پوشانی
                            next.push(seg);
                            continue;
                        }

                        if (cut.open > seg.open) {
                            next.push({ open: seg.open, close: Math.min(cut.open, seg.close) });
                        }

                        if (cut.close < seg.close) {
                            next.push({ open: Math.max(cut.close, seg.open), close: seg.close });
                        }
                    }

                    segments = next;
                }

                for (let s = 0; s < segments.length; s++) {
                    if (segments[s].close > segments[s].open) {
                        result.push(segments[s]);
                    }
                }
            }

            return result;
        }


        // ====================================================================
        // Resource Exceptions
        //
        // - effect = "unavailable" و status = "enable": بازه‌ی مسدود (تعطیلی،
        //   مرخصی، تعمیرات و ...) که از برنامه‌ی هفتگی کم می‌شود.
        // - effect = "available" و status = "enable": ساعت کاری فوق‌العاده که
        //   حتی اگر روز عادتاً بسته باشد، اضافه می‌شود (مثلاً افتتاحیه یک
        //   روز تعطیل).
        // - resource_assignment_id خالی یعنی استثناء برای کل شعبه است.
        // ====================================================================

        const dayStartMs = date.getTime();
        const dayEndMs = dayStartMs + 24 * 60 * 60000;

        const exceptionRows = e.app.findRecordsByFilter(
            "resource_exceptions",
            `
                (
                    resource_assignment_id = {:assignment}
                    ||
                    (resource_assignment_id = "" && branch_id = {:branch})
                )
                &&
                status = "enable"
                &&
                start_datetime <= {:dayEnd}
                &&
                end_datetime >= {:dayStart}
            `,
            "",
            0,
            0,
            {
                assignment: resourceAssignmentId,
                branch: branchId,
                dayStart: new Date(dayStartMs).toISOString(),
                dayEnd: new Date(dayEndMs).toISOString()
            }
        );

        const blockedIntervals = [];
        const extraOpenIntervals = [];

        for (let i = 0; i < exceptionRows.length; i++) {
            const row = exceptionRows[i];

            const rowStartMs = row.getDateTime("start_datetime").unix() * 1000;
            const rowEndMs = row.getDateTime("end_datetime").unix() * 1000;

            const clippedOpenMs = Math.max(rowStartMs, dayStartMs);
            const clippedCloseMs = Math.min(rowEndMs, dayEndMs);

            if (clippedCloseMs <= clippedOpenMs) {
                continue; // در واقع همپوشانی‌ای با این روز ندارد
            }

            const openMinute = Math.round((clippedOpenMs - dayStartMs) / 60000);
            const closeMinute = Math.round((clippedCloseMs - dayStartMs) / 60000);

            if (row.getString("effect") === "available") {
                extraOpenIntervals.push({ open: openMinute, close: closeMinute });
            } else {
                // پیش‌فرض امن: هر مقدار دیگری (یا "unavailable") مسدودکننده در نظر گرفته می‌شود.
                blockedIntervals.push({ open: openMinute, close: closeMinute });
            }
        }


        // ====================================================================
        // Existing Bookings (جلوگیری از نمایش Slotهای قبلاً رزروشده)
        //
        // appointment_services فعال (غیر cancelled) متعلق به هر service_assignment
        // مربوط به همین resource_assignment، که در بازه‌ی همین روز قرار می‌گیرند.
        // ====================================================================

        const bookedRows = e.app.findRecordsByFilter(
            "appointment_services",
            `
                service_assignment_id.resource_assignment_id = {:assignment}
                &&
                status != "cancelled"
                &&
                start_at < {:dayEnd}
                &&
                start_at >= {:windowStart}
            `,
            "",
            0,
            0,
            {
                assignment: resourceAssignmentId,
                // مدت هیچ appointment_service ای در عمل از 24 ساعت بیشتر نمی‌شود؛
                // یک بازه‌ی امن قبل از شروع روز هم برای نوبت‌هایی که از دیروز
                // «سرریز» شده باشند در نظر گرفته می‌شود.
                windowStart: new Date(dayStartMs - 24 * 60 * 60000).toISOString(),
                dayEnd: new Date(dayEndMs).toISOString()
            }
        );

        const bookedIntervals = [];

        for (let i = 0; i < bookedRows.length; i++) {
            const row = bookedRows[i];

            const startMs = row.getDateTime("start_at").unix() * 1000;
            const durationMinutes = Number(row.get("duration")) || 0;
            const endMs = startMs + durationMinutes * 60000;

            if (endMs <= dayStartMs || startMs >= dayEndMs) {
                continue; // با این روز هم‌پوشانی ندارد
            }

            bookedIntervals.push({
                open: Math.max(0, Math.round((startMs - dayStartMs) / 60000)),
                close: Math.min(24 * 60, Math.round((endMs - dayStartMs) / 60000))
            });
        }


        // ====================================================================
        // ترکیب نهایی:
        // (برنامه‌ی هفتگی ∪ ساعات فوق‌العاده) − (استثناهای مسدودکننده ∪ رزروهای موجود)
        // ====================================================================

        const openIntervals = mergeIntervals(baseIntervals.concat(extraOpenIntervals));

        const workingIntervals = subtractIntervals(
            openIntervals,
            blockedIntervals.concat(bookedIntervals)
        );


        // ====================================================================
        // Resource در این روز هیچ بازه‌ی خالی‌ای ندارد.
        // ====================================================================

        if (workingIntervals.length === 0) {

            return e.json(
                200,
                {
                    date:
                        requestedDate,

                    day_of_week:
                        dayOfWeek,

                    branch_id:
                        branchId,

                    resource_assignment_id:
                        resourceAssignmentId,

                    total_duration:
                        totalDuration,

                    slot_step:
                        5,

                    slots:
                        []
                }
            );
        }


        // ====================================================================
        // Slot Generation
        //
        // STEP_MINUTE فقط دقت زمان شروع Slot است.
        //
        // این مقدار به معنی Duration سرویس نیست.
        // ====================================================================

        const STEP_MINUTE =
            5;


        const slots =
            [];


        for (
            let i = 0;
            i < workingIntervals.length;
            i++
        ) {

            const interval =
                workingIntervals[i];

            const openMinute = interval.open;
            const closeMinute = interval.close;

            for (
                let start =
                    openMinute;

                start + totalDuration <=
                    closeMinute;

                start +=
                    STEP_MINUTE
            ) {

                const end =
                    start +
                    totalDuration;


                slots.push({
                    start_minute:
                        start,

                    end_minute:
                        end,

                    start_time:
                        minuteToTime(
                            start
                        ),

                    end_time:
                        minuteToTime(
                            end
                        )
                });
            }
        }


        // ====================================================================
        // Response
        // ====================================================================

        return e.json(
            200,
            {
                date:
                    requestedDate,

                day_of_week:
                    dayOfWeek,

                branch_id:
                    branchId,

                resource_assignment_id:
                    resourceAssignmentId,

                total_duration:
                    totalDuration,

                slot_step:
                    STEP_MINUTE,

                slots:
                    slots
            }
        );
    },

    $apis.requireAuth(
        "users"
    )
);