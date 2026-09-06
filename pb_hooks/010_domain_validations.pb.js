/// <reference path="../pb_data/types.d.ts" />

// ============================================================================
// [fix/production-readiness]
//
// این فایل جایگزین دو فایل قبلی شده: 010_domain_validations.pb.js (نسخه‌ی قدیمی)
// و validator.pb.js. آن دو فایل روی یک‌سری کالکشن مشترک (service_category،
// services، branch_services، resource_assignments، service_assignments،
// resource_availability، resource_exceptions، role_permissions،
// user_system_roles، branch_membership، favorites) هر دو onRecordValidate
// جداگانه ثبت می‌کردند؛ یعنی هر بار ذخیره، هر دو چک (تکراری و گاهی
// ناسازگار) اجرا می‌شدند. مشکلات مشخص‌شده در validator.pb.js:
//
//   1) چک هم‌پوشانی resource_availability از e.record.getDateTime() روی
//      فیلدهای open_time/close_time استفاده می‌کرد، در حالی که این فیلدها
//      طبق schema فعلی از نوع text (فرمت HH:mm) هستند. یعنی آن چک همیشه
//      DateTime صفر می‌گرفت و در عمل هیچ‌وقت خطا نمی‌داد (کد مرده).
//   2) چک appointment ایجاب می‌کرد start/end همیشه پر باشند؛ اما طبق طراحی
//      فعلی (نگاه کنید به 30_appointment_services_aggregate.pb.js) این
//      مقادیر ابتدا خالی‌اند و بعد از اضافه شدن اولین appointment_service
//      محاسبه می‌شوند. این چک ساخت هر appointment جدید را می‌بست.
//   3) چک appointment_services ایجاب می‌کرد start_at داخل بازه‌ی
//      [appointment.start, appointment.end) باشد؛ چون در لحظه‌ی افزودن
//      اولین appointment_service این بازه هنوز خالی/صفر است، این چک همیشه
//      خطا می‌داد و اصلاً اجازه‌ی ساخت هیچ appointment_service ای را
//      نمی‌داد — یعنی کل فرآیند رزرو را می‌بست.
//
// در نتیجه این دو مورد آخر حذف شدند (و منطق درست جایگزینشان، که در فایل‌های
// 10_/20_/30_ پیاده‌سازی شده، دست‌نخورده باقی مانده) و مورد اول با استفاده
// از parsing درستِ HH:mm بازنویسی شد. بقیه‌ی چک‌های واقعاً مفید
// validator.pb.js (تشخیص حلقه در دسته‌بندی‌ها، اعتبارسنجی discounts،
// reputation_events، محدوده‌ی رتبه‌ی reviews، انقضای user_system_roles و
// محدوده‌ی تاریخ resource_exceptions) به همین‌جا منتقل شدند تا فقط یک
// نسخه‌ی واحد و بدون تداخل از هر قانون وجود داشته باشد.
// ============================================================================

// ============================================================================
// توابع کمکی
// ============================================================================

function timeToMinute(value) {
    const parts = value.split(":");

    if (parts.length !== 2) {
        throw new BadRequestError(
            "فرمت زمان نامعتبر است. زمان باید به صورت HH:mm باشد."
        );
    }

    const hour = parseInt(parts[0], 10);
    const minute = parseInt(parts[1], 10);

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

    return hour * 60 + minute;
}


// ============================================================================
// SERVICES
//
// سرویس و دسته‌بندی آن باید متعلق به یک کسب‌وکار باشند.
// ============================================================================

onRecordValidate((e) => {
    const businessId = e.record.getString("business_id");
    const categoryId = e.record.getString("category_id");

    if (categoryId !== "") {
        const category = e.app.findRecordById(
            "service_category",
            categoryId
        );

        if (
            category.getString("business_id") !==
            businessId
        ) {
            throw new BadRequestError(
                "سرویس و دسته‌بندی انتخاب‌شده باید متعلق به یک کسب‌وکار باشند."
            );
        }
    }

    e.next();
}, "services");


// ============================================================================
// SERVICE CATEGORY
//
// - دسته‌بندی والد باید متعلق به همان کسب‌وکار باشد.
// - یک دسته‌بندی نمی‌تواند والد خودش باشد.
// - درخت دسته‌بندی نباید حلقه (cycle) داشته باشد.
// ============================================================================

onRecordValidate((e) => {
    const businessId = e.record.getString("business_id");
    const parentId = e.record.getString("parent_id");

    if (parentId !== "") {

        if (parentId === e.record.id) {
            throw new BadRequestError(
                "یک دسته‌بندی نمی‌تواند والد خودش باشد."
            );
        }

        const parent = e.app.findRecordById(
            "service_category",
            parentId
        );

        if (
            parent.getString("business_id") !==
            businessId
        ) {
            throw new BadRequestError(
                "دسته‌بندی والد باید متعلق به همان کسب‌وکار باشد."
            );
        }

        // ------------------------------------------------------------
        // تشخیص Cycle در درخت دسته‌بندی‌ها:
        // با دنبال کردن زنجیره‌ی parent_id از parent به بالا، اگر به
        // خود این رکورد برسیم یعنی یک حلقه ایجاد شده.
        // ------------------------------------------------------------
        let current = parent;
        let depth = 0;

        while (current && current.getString("parent_id") !== "") {
            depth++;

            if (depth > 100) {
                throw new BadRequestError(
                    "درخت دسته‌بندی خیلی عمیق است یا حلقه دارد."
                );
            }

            const nextParentId = current.getString("parent_id");

            if (nextParentId === e.record.id) {
                throw new BadRequestError(
                    "درخت دسته‌بندی نمی‌تواند حلقه داشته باشد."
                );
            }

            current = e.app.findRecordById("service_category", nextParentId);
        }
    }

    e.next();
}, "service_category");


// ============================================================================
// BRANCH SERVICES
//
// - شعبه و سرویس باید متعلق به یک کسب‌وکار باشند.
// - قیمت نباید منفی باشد.
// - مدت زمان باید بزرگ‌تر از صفر باشد.
// ============================================================================

onRecordValidate((e) => {
    const branchId = e.record.getString("branch_id");
    const serviceId = e.record.getString("service_id");

    const branch = e.app.findRecordById(
        "branches",
        branchId
    );

    const service = e.app.findRecordById(
        "services",
        serviceId
    );

    if (
        branch.getString("business_id") !==
        service.getString("business_id")
    ) {
        throw new BadRequestError(
            "شعبه و سرویس انتخاب‌شده باید متعلق به یک کسب‌وکار باشند."
        );
    }

    if (e.record.getFloat("price") < 0) {
        throw new BadRequestError(
            "قیمت سرویس شعبه نمی‌تواند منفی باشد."
        );
    }

    if (e.record.getInt("duration") <= 0) {
        throw new BadRequestError(
            "مدت زمان سرویس شعبه باید بزرگ‌تر از صفر باشد."
        );
    }

    e.next();
}, "branch_services");


// ============================================================================
// RESOURCE ASSIGNMENTS
//
// تاریخ پایان نباید قبل از تاریخ شروع باشد.
// ============================================================================

onRecordValidate((e) => {
    const start = e.record.getDateTime("start_date");
    const end = e.record.getDateTime("end_date");

    if (
        !start.isZero() &&
        !end.isZero() &&
        end.before(start)
    ) {
        throw new BadRequestError(
            "تاریخ پایان تخصیص منبع نمی‌تواند قبل از تاریخ شروع باشد."
        );
    }

    e.next();
}, "resource_assignments");


// ============================================================================
// SERVICE ASSIGNMENTS
//
// - سرویس شعبه و تخصیص منبع باید متعلق به یک شعبه باشند.
// - price_override/duration_override نباید منفی باشند (مقدار 0 یعنی
//   «تنظیم نشده»؛ نگاه کنید به pb_hooks/10_appointment_services_pricing.pb.js).
// ============================================================================

onRecordValidate((e) => {
    const branchServiceId =
        e.record.getString("branch_service_id");

    const resourceAssignmentId =
        e.record.getString("resource_assignment_id");

    const branchService = e.app.findRecordById(
        "branch_services",
        branchServiceId
    );

    const resourceAssignment = e.app.findRecordById(
        "resource_assignments",
        resourceAssignmentId
    );

    if (
        branchService.getString("branch_id") !==
        resourceAssignment.getString("branch_id")
    ) {
        throw new BadRequestError(
            "سرویس و منبع انتخاب‌شده باید متعلق به یک شعبه باشند."
        );
    }

    if (e.record.getInt("duration_override") < 0) {
        throw new BadRequestError(
            "duration_override نمی‌تواند منفی باشد."
        );
    }

    if (e.record.getFloat("price_override") < 0) {
        throw new BadRequestError(
            "price_override نمی‌تواند منفی باشد."
        );
    }

    e.next();
}, "service_assignments");


// ============================================================================
// RESOURCE AVAILABILITY
//
// ساختار:
// resource_assignment_id, day_of_week, open_time, close_time, status
// (open_time/close_time از نوع text با فرمت HH:mm هستند)
//
// قوانین:
// 1. روز هفته باید بین 0 و 6 باشد.
// 2. ساعت پایان باید بعد از ساعت شروع باشد.
// 3. بازه‌های فعال یک منبع در یک روز نباید هم‌پوشانی داشته باشند.
// 4. یک Resource (پرسنل/منبع فیزیکی) ممکن است چند resource_assignment در
//    شعبه‌های مختلف داشته باشد؛ بازه‌های فعال این‌ها هم نباید در یک روز با
//    هم تداخل داشته باشند (یک پرسنل نمی‌تواند هم‌زمان در دو شعبه کار کند).
// ============================================================================

onRecordValidate((e) => {
    const assignmentId =
        e.record.getString("resource_assignment_id");

    const dayOfWeek =
        e.record.getInt("day_of_week");

    const openTime =
        e.record.getString("open_time");

    const closeTime =
        e.record.getString("close_time");

    const status =
        e.record.getString("status");

    if (
        dayOfWeek < 0 ||
        dayOfWeek > 6
    ) {
        throw new BadRequestError(
            "مقدار روز هفته باید بین 0 و 6 باشد."
        );
    }

    const openMinute = timeToMinute(openTime);
    const closeMinute = timeToMinute(closeTime);

    if (openMinute >= closeMinute) {
        throw new BadRequestError(
            "ساعت پایان باید بعد از ساعت شروع باشد."
        );
    }

    // بازه غیرفعال نباید باعث ایجاد تداخل شود.
    if (status === "active") {

        const existingIntervals = e.app.findRecordsByFilter(
            "resource_availability",
            `
                resource_assignment_id = {:assignment}
                &&
                day_of_week = {:day}
                &&
                status = "active"
                &&
                id != {:currentId}
            `,
            "open_time",
            100,
            0,
            {
                assignment: assignmentId,
                day: dayOfWeek,
                currentId: e.record.id || ""
            }
        );

        for (let i = 0; i < existingIntervals.length; i++) {
            const existingOpen = timeToMinute(existingIntervals[i].getString("open_time"));
            const existingClose = timeToMinute(existingIntervals[i].getString("close_time"));

            const hasOverlap =
                openMinute < existingClose &&
                closeMinute > existingOpen;

            if (hasOverlap) {
                throw new BadRequestError(
                    "این بازه زمانی با یکی از بازه‌های موجود منبع تداخل دارد."
                );
            }
        }

        // ------------------------------------------------------------
        // تداخل با resource_assignmentهای دیگرِ همین resource (مثلاً در
        // شعبه‌ای دیگر).
        // ------------------------------------------------------------
        const currentAssignment = e.app.findRecordById(
            "resource_assignments",
            assignmentId
        );

        const resourceId = currentAssignment.getString("resource_id");

        const sameResourceAssignments = e.app.findRecordsByFilter(
            "resource_assignments",
            "resource_id = {:resource} && id != {:currentAssignment}",
            "",
            0,
            0,
            {
                resource: resourceId,
                currentAssignment: assignmentId
            }
        );

        for (let a = 0; a < sameResourceAssignments.length; a++) {
            const otherAssignmentId = sameResourceAssignments[a].id;

            const otherIntervals = e.app.findRecordsByFilter(
                "resource_availability",
                `
                    resource_assignment_id = {:assignment}
                    &&
                    day_of_week = {:day}
                    &&
                    status = "active"
                `,
                "",
                0,
                0,
                {
                    assignment: otherAssignmentId,
                    day: dayOfWeek
                }
            );

            for (let b = 0; b < otherIntervals.length; b++) {
                const otherOpen = timeToMinute(otherIntervals[b].getString("open_time"));
                const otherClose = timeToMinute(otherIntervals[b].getString("close_time"));

                const hasCrossOverlap =
                    openMinute < otherClose &&
                    closeMinute > otherOpen;

                if (hasCrossOverlap) {
                    throw new BadRequestError(
                        "این منبع در همین بازه‌ی زمانی برای شعبه/تخصیص دیگری هم برنامه‌ی فعال دارد."
                    );
                }
            }
        }
    }

    e.next();

}, "resource_availability");


// ============================================================================
// RESOURCE EXCEPTIONS
//
// 1. زمان پایان باید بعد از زمان شروع باشد.
// 2. اگر resource_assignment_id مشخص شده باشد، باید متعلق به همان شعبه باشد
//    و در محدوده‌ی تاریخ start_date/end_date همان assignment قرار بگیرد.
// ============================================================================

onRecordValidate((e) => {

    const branchId = e.record.getString("branch_id");
    const assignmentId = e.record.getString("resource_assignment_id");
    const start = e.record.getDateTime("start_datetime");
    const end = e.record.getDateTime("end_datetime");

    if (
        !start.isZero() &&
        !end.isZero() &&
        !start.before(end)
    ) {
        throw new BadRequestError(
            "زمان پایان استثناء باید بعد از زمان شروع باشد."
        );
    }

    if (assignmentId !== "") {

        const assignment = e.app.findRecordById(
            "resource_assignments",
            assignmentId
        );

        if (
            assignment.getString("branch_id") !==
            branchId
        ) {
            throw new BadRequestError(
                "منبع انتخاب‌شده متعلق به شعبه انتخاب‌شده نیست."
            );
        }

        const assignmentStart = assignment.getDateTime("start_date");
        const assignmentEnd = assignment.getDateTime("end_date");

        if (
            !assignmentStart.isZero() &&
            !start.isZero() &&
            start.unix() < assignmentStart.unix()
        ) {
            throw new BadRequestError(
                "استثناء نمی‌تواند قبل از شروع تخصیص منبع باشد."
            );
        }

        if (
            !assignmentEnd.isZero() &&
            !end.isZero() &&
            end.unix() > assignmentEnd.unix()
        ) {
            throw new BadRequestError(
                "استثناء نمی‌تواند بعد از پایان تخصیص منبع باشد."
            );
        }
    }

    e.next();

}, "resource_exceptions");


// ============================================================================
// ROLE PERMISSIONS
//
// Scope نقش و Permission باید یکسان باشد.
// ============================================================================

onRecordValidate((e) => {

    const role = e.app.findRecordById(
        "roles",
        e.record.getString("role_id")
    );

    const permission = e.app.findRecordById(
        "permissions",
        e.record.getString("permission_id")
    );

    if (
        role.getString("scope") !==
        permission.getString("scope")
    ) {
        throw new BadRequestError(
            "محدوده نقش و دسترسی باید یکسان باشد."
        );
    }

    e.next();

}, "role_permissions");


// ============================================================================
// USER SYSTEM ROLES
//
// - فقط Roleهایی با scope=system مجاز هستند.
// - در صورت وجود expires_at، باید بعد از assigned_at باشد.
// ============================================================================

onRecordValidate((e) => {

    const role = e.app.findRecordById(
        "roles",
        e.record.getString("role_id")
    );

    if (
        role.getString("scope") !== "system"
    ) {
        throw new BadRequestError(
            "فقط نقش‌های سیستمی را می‌توان به عنوان نقش سیستمی کاربر ثبت کرد."
        );
    }

    const assignedAt = e.record.getDateTime("assigned_at");
    const expiresAt = e.record.getDateTime("expires_at");

    if (
        !assignedAt.isZero() &&
        !expiresAt.isZero() &&
        assignedAt.unix() >= expiresAt.unix()
    ) {
        throw new BadRequestError(
            "expires_at باید بعد از assigned_at باشد."
        );
    }

    e.next();

}, "user_system_roles");


// ============================================================================
// BRANCH MEMBERSHIP
//
// تمام Roleهای موجود در Membership باید scope=branch داشته باشند.
// ============================================================================

onRecordValidate((e) => {

    const roleIds = e.record.getStringSlice("roles");

    if (roleIds.length > 0) {

        const roles = e.app.findRecordsByIds(
            "roles",
            roleIds
        );

        if (
            roles.length !==
            roleIds.length
        ) {
            throw new BadRequestError(
                "یک یا چند نقش انتخاب‌شده وجود ندارند."
            );
        }

        for (let i = 0; i < roles.length; i++) {
            if (
                roles[i].getString("scope") !==
                "branch"
            ) {
                throw new BadRequestError(
                    "در عضویت شعبه فقط می‌توان از نقش‌های سطح شعبه استفاده کرد."
                );
            }
        }
    }

    e.next();

}, "branch_membership");


// ============================================================================
// FAVORITES
//
// هر Favorite باید فقط به یکی از این موارد اشاره کند: Business یا Resource.
// ============================================================================

onRecordValidate((e) => {

    const businessId = e.record.getString("business_id");
    const resourceId = e.record.getString("resource_id");

    const hasBusiness = businessId !== "";
    const hasResource = resourceId !== "";

    if (hasBusiness === hasResource) {
        throw new BadRequestError(
            "هر مورد علاقه باید فقط به یک کسب‌وکار یا یک منبع اشاره کند."
        );
    }

    e.next();

}, "favorites");


// ============================================================================
// REVIEWS
//
// 1. فقط مشتری همان Appointment اجازه ثبت Review دارد.
// 2. Appointment باید completed باشد.
// 3. rating باید بین 1 تا 5 باشد.
// ============================================================================

onRecordValidate((e) => {

    const appointmentService = e.app.findRecordById(
        "appointment_services",
        e.record.getString("appointment_service_id")
    );

    const appointment = e.app.findRecordById(
        "appointment",
        appointmentService.getString("appointment_id")
    );

    const reviewUserId = e.record.getString("user_id");

    if (
        appointment.getString("client_user_id") !== reviewUserId
    ) {
        throw new BadRequestError(
            "فقط مشتری دریافت‌کننده این خدمت می‌تواند برای آن نظر ثبت کند."
        );
    }

    if (
        appointment.getString("status") !== "completed"
    ) {
        throw new BadRequestError(
            "فقط برای خدمات نوبت‌های تکمیل‌شده می‌توان نظر ثبت کرد."
        );
    }

    const rating = e.record.getInt("rating");

    if (rating < 1 || rating > 5) {
        throw new BadRequestError(
            "امتیاز باید بین 1 تا 5 باشد."
        );
    }

    e.next();

}, "reviews");


// ============================================================================
// APPOINTMENT
//
// این چک‌ها عمداً «نرم» هستند: چون رکورد appointment در ابتدا بدون هیچ
// appointment_service ای ساخته می‌شود (start/end/total_price/discount_amount/
// final_price بعداً توسط pb_hooks/30_appointment_services_aggregate.pb.js
// محاسبه و پر می‌شوند)، این validate نباید در لحظه‌ی ایجاد اولیه (که این
// مقادیر هنوز خالی‌اند) جلوی ساخت رکورد را بگیرد. فقط وقتی مقداری واقعاً ست
// شده، منطقی‌بودنش بررسی می‌شود.
// ============================================================================

onRecordValidate((e) => {

    const start = e.record.getDateTime("start");
    const end = e.record.getDateTime("end");

    if (!start.isZero() && !end.isZero() && start.unix() >= end.unix()) {
        throw new BadRequestError(
            "زمان شروع نوبت باید قبل از زمان پایان آن باشد."
        );
    }

    const totalPrice = e.record.getFloat("total_price");
    const discountAmount = e.record.getFloat("discount_amount");
    const finalPrice = e.record.getFloat("final_price");

    if (totalPrice < 0 || discountAmount < 0 || finalPrice < 0) {
        throw new BadRequestError(
            "مبلغ‌های نوبت نمی‌توانند منفی باشند."
        );
    }

    if (discountAmount > totalPrice) {
        throw new BadRequestError(
            "مبلغ تخفیف نمی‌تواند از مبلغ کل بیشتر باشد."
        );
    }

    e.next();

}, "appointment");


// ============================================================================
// APPOINTMENT - CREATE REQUEST
//
// در لحظه‌ی ساخت اولیه‌ی appointment (پیش از افزوده‌شدن هیچ appointment_service
// ای)، فقط branch_id/client_user_id/status لازم است اما start/end/قیمت‌ها
// هنوز معنایی ندارند. این هوک مقادیر پیش‌فرض امن برای فیلدهای required
// (متنی/انتخابی) قرار می‌دهد تا کلاینت مجبور به فرستادن مقادیر ساختگی
// نباشد و اعتبارسنجی سطح schema هم رد شود.
// ============================================================================

onRecordCreateRequest((e) => {

    if (e.record.getString("total_price") === "") {
        e.record.set("total_price", 0);
    }

    if (e.record.getString("discount_amount") === "") {
        e.record.set("discount_amount", 0);
    }

    if (e.record.getString("final_price") === "") {
        e.record.set("final_price", 0);
    }

    // وضعیت و مبالغ تجمیعی داده‌های server-owned هستند. قانون API جلوی
    // ارسال status دلخواه در create را نمی‌گیرد و بدون این overwrite یک
    // مشتری می‌توانست نوبت را مستقیماً completed/no_show بسازد.
    if (!e.hasSuperuserAuth()) {
        e.record.set("total_price", 0);
        e.record.set("discount_amount", 0);
        e.record.set("final_price", 0);
        e.record.set("status", "pending");
    } else if (e.record.getString("status") === "") {
        e.record.set("status", "pending");
    }

    e.next();

}, "appointment");


// مقادیر اولیه‌ی وضعیت هر خط رزرو نیز server-owned است. علاوه بر جلوگیری
// از create مستقیم با completed، افزودن خدمت به یک نوبت نهایی‌شده را می‌بندد.
onRecordCreateRequest((e) => {

    const appointment = e.app.findRecordById(
        "appointment",
        e.record.getString("appointment_id")
    );

    const appointmentStatus = appointment.getString("status");
    if (
        !e.hasSuperuserAuth() &&
        appointmentStatus !== "pending" &&
        appointmentStatus !== "confirmed"
    ) {
        throw new BadRequestError(
            "افزودن خدمت به نوبت نهایی‌شده مجاز نیست."
        );
    }

    if (!e.hasSuperuserAuth()) {
        e.record.set("status", "pending");
    } else if (e.record.getString("status") === "") {
        e.record.set("status", "pending");
    }

    e.next();

}, "appointment_services");


// ============================================================================
// APPOINTMENT SERVICES
//
// توجه: بررسیِ «داخل بازه‌ی appointment بودن start_at» عمداً اینجا انجام
// نمی‌شود، چون در طراحی فعلی این رابطه برعکس است: start/end والدِ appointment
// از روی start_at و duration فرزندهای appointment_services محاسبه می‌شود
// (نگاه کنید به pb_hooks/30_appointment_services_aggregate.pb.js).
// قیمت/مدت/تطابق شعبه توسط pb_hooks/10_appointment_services_pricing.pb.js
// از روی داده‌ی معتبر سرور محاسبه و کنترل می‌شوند؛ این‌جا فقط یک لایه‌ی
// دفاعیِ نهایی برای منطقی‌بودن مقادیر ذخیره‌شده است (مهم برای مسیر update،
// چون pricing hook فقط روی create اجرا می‌شود).
// ============================================================================

onRecordValidate((e) => {

    if (e.record.getInt("duration") <= 0) {
        throw new BadRequestError(
            "مدت زمان appointment_service باید بزرگ‌تر از صفر باشد."
        );
    }

    if (e.record.getFloat("price") < 0) {
        throw new BadRequestError(
            "قیمت appointment_service نمی‌تواند منفی باشد."
        );
    }

    if (e.record.getDateTime("start_at").isZero()) {
        throw new BadRequestError(
            "زمان شروع appointment_service الزامی است."
        );
    }

    e.next();

}, "appointment_services");


// ============================================================================
// DISCOUNTS
//
// - valid_from نباید بعد از valid_until باشد.
// - value / min_order_amount / max_uses نباید منفی باشند.
// - تخفیف درصدی نباید بیشتر از 100 باشد.
// - همه‌ی applicable_services باید متعلق به همان business باشند.
// ============================================================================

onRecordValidate((e) => {

    const businessId = e.record.getString("business_id");
    const validFrom = e.record.getDateTime("valid_from");
    const validUntil = e.record.getDateTime("valid_until");

    if (
        !validFrom.isZero() &&
        !validUntil.isZero() &&
        validFrom.unix() > validUntil.unix()
    ) {
        throw new BadRequestError(
            "تاریخ شروع تخفیف نمی‌تواند بعد از تاریخ پایان آن باشد."
        );
    }

    if (e.record.getFloat("value") < 0) {
        throw new BadRequestError("مقدار تخفیف نمی‌تواند منفی باشد.");
    }

    if (e.record.getFloat("min_order_amount") < 0) {
        throw new BadRequestError("حداقل مبلغ سفارش نمی‌تواند منفی باشد.");
    }

    if (e.record.getInt("max_uses") < 0) {
        throw new BadRequestError("حداکثر تعداد استفاده نمی‌تواند منفی باشد.");
    }

    if (e.record.get("type") === "percent" && e.record.getFloat("value") > 100) {
        throw new BadRequestError("تخفیف درصدی نمی‌تواند بیشتر از 100 باشد.");
    }

    const serviceIds = e.record.getStringSlice("applicable_services");

    for (let i = 0; i < serviceIds.length; i++) {
        const service = e.app.findRecordById("services", serviceIds[i]);

        if (service.getString("business_id") !== businessId) {
            throw new BadRequestError(
                "همه‌ی سرویس‌های قابل‌اعمال تخفیف باید متعلق به همان کسب‌وکار باشند."
            );
        }
    }

    e.next();

}, "discounts");


// ============================================================================
// REPUTATION EVENTS
//
// اگر appointment_id ست شده باشد، business_id باید همان کسب‌وکار appointment باشد.
// ============================================================================

onRecordValidate((e) => {

    const appointmentId = e.record.getString("appointment_id");
    const businessId = e.record.getString("business_id");

    if (appointmentId === "") {
        e.next();
        return;
    }

    if (businessId === "") {
        throw new BadRequestError(
            "برای رویدادهای اعتباری مرتبط با نوبت، business_id الزامی است."
        );
    }

    const appointment = e.app.findRecordById("appointment", appointmentId);
    const branch = e.app.findRecordById("branches", appointment.getString("branch_id"));

    if (branch.getString("business_id") !== businessId) {
        throw new BadRequestError(
            "business_id رویداد اعتباری باید با کسب‌وکار نوبت یکسان باشد."
        );
    }

    e.next();

}, "reputation_events");
