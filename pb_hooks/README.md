# نصب

همه‌ی فایل‌های این پوشه (`*.pb.js`) رو مستقیم توی پوشه‌ی `pb_hooks` پروژه‌ی PocketBase‌تون کپی کنید (کنار `pb_data`)، سپس سرور رو ری‌استارت کنید. PocketBase همه‌ی فایل‌های `*.pb.js` رو خودکار لود می‌کنه.

پیشوند عددی اسم فایل‌ها (`010_`, `10_`, `20_`, ...) عمداً هست: چون چند hook می‌تونن روی یک event/کالکشن ثبت بشن و به‌صورت زنجیره‌ای (هر کدوم با `e.next()`) اجرا می‌شن، ترتیب لود شدن فایل‌ها مهمه (PocketBase فایل‌ها رو به ترتیب الفبایی/lexicographic اسم‌شون لود می‌کنه). مثلاً `10_appointment_services_pricing.pb.js` باید قبل از `20_appointment_services_conflict.pb.js` اجرا بشه چون فایل دوم به `duration` ای نیاز داره که فایل اول محاسبه و ست می‌کنه.

## فایل‌ها

| فایل | کاره چیه |
|---|---|
| `010_domain_validations.pb.js` | اعتبارسنجی‌های دامنه برای بیشتر کالکشن‌ها (تعلق به یک business/شعبه، تداخل برنامه‌ی هفتگی resource، محدوده‌ی استثناها، تشخیص حلقه در دسته‌بندی‌ها، اعتبارسنجی discounts/reviews/reputation_events و ...) + مقداردهی پیش‌فرض appointment در لحظه‌ی ساخت |
| `020_availability.pb.js` | Endpoint سفارشی `POST /api/be-nobat/availability/base` برای گرفتن اسلات‌های خالی یک resource در یک روز (با احتساب برنامه‌ی هفتگی، استثناها، و نوبت‌های از قبل رزروشده) |
| `10_appointment_services_pricing.pb.js` | قیمت/مدت واقعی هر appointment_service رو از روی service_assignment/branch_service محاسبه می‌کنه (نه از ورودی کلاینت) و match بودن resource با شعبه رو چک می‌کنه |
| `20_appointment_services_conflict.pb.js` | جلوگیری از رزرو دوباره (double-booking) یک resource (نه فقط یک service_assignment خاص) در یک بازه‌ی زمانی هم‌پوشان، با محافظت در برابر race condition |
| `30_appointment_services_aggregate.pb.js` | بعد از هر تغییر در appointment_services، appointment والد (start/end/total_price/final_price) رو بازمحاسبه می‌کنه |
| `40_appointment_status.pb.js` | state machine برای appointment.status (نمی‌ذاره وضعیت از هر جایی به هر جایی بپره) |
| `41_appointment_services_status.pb.js` | همون state machine برای appointment_services.status |
| `50_reputation_events.pb.js` | بعد از completed/no_show/cancelled شدن نوبت، رکورد reputation_events خودکار می‌سازه |

## تغییرات برنچ `fix/production-readiness`

این پروژه قبلاً یک فایل دیگه هم داشت: `validator.pb.js`. اون فایل روی همون کالکشن‌هایی که `010_domain_validations.pb.js` پوشش می‌داد (service_category، services، branch_services، resource_assignments، service_assignments، resource_availability، resource_exceptions، role_permissions، user_system_roles، branch_membership، favorites) **دوباره** `onRecordValidate` ثبت می‌کرد، و علاوه بر این، برای `appointment`/`appointment_services` هم قانون‌هایی داشت که با معماری فعلی (محاسبه‌ی bottom-up از روی appointment_services) در تضاد بودن. مشکلات مشخص‌شده:

1. **چک هم‌پوشانی `resource_availability` در `validator.pb.js` هیچ‌وقت واقعاً اجرا نمی‌شد** — چون از `record.getDateTime()` روی فیلدهای `open_time`/`close_time` استفاده می‌کرد، در حالی که این فیلدها طبق schema فعلی از نوع `text` (فرمت `HH:mm`) هستن؛ یعنی همیشه DateTime صفر می‌گرفت.
2. **چک `appointment` ایجاب می‌کرد `start`/`end` همیشه پر باشن** — اما این مقادیر ابتدا خالی‌ان و بعد از اولین `appointment_service` محاسبه می‌شن؛ یعنی ساخت هر appointment جدید همیشه با خطا رد می‌شد.
3. **چک `appointment_services` ایجاب می‌کرد `start_at` داخل بازه‌ی `[appointment.start, appointment.end)` باشه** — چون این بازه در لحظه‌ی افزودن اولین appointment_service هنوز خالی/صفره، این چک همیشه خطا می‌داد و **عملاً کل فرآیند رزرو رو می‌بست**.

`validator.pb.js` حذف شد؛ چک‌های واقعاً مفیدش (تشخیص حلقه در دسته‌بندی‌ها، اعتبارسنجی `discounts`، `reputation_events`، محدوده‌ی رتبه‌ی `reviews`، انقضای `user_system_roles`، محدوده‌ی تاریخ `resource_exceptions`) با رفع باگ به `010_domain_validations.pb.js` منتقل شدن.

سایر تغییرات:

- **`020_availability.pb.js`**: قبلاً `resource_exceptions` و نوبت‌های از قبل رزروشده رو در نظر نمی‌گرفت (طبق کامنت خودِ فایل)، یعنی Slotهایی که واقعاً اشغال بودن هم به‌عنوان «آزاد» برمی‌گشتن. حالا این دو مورد لحاظ می‌شن.
- **`20_appointment_services_conflict.pb.js`**: دامنه‌ی چک از `service_assignment_id` به `resource_assignment_id` تغییر کرد (یک resource می‌تونه چند service_assignment داشته باشه)، و کل «چک + ذخیره» داخل یک تراکنش با قفل صریح انجام می‌شه تا race condition بین دو درخواست هم‌زمان بسته بشه.
- **Migration جدید برای `appointment`**: فیلدهای `start`/`end`/`total_price`/`discount_amount`/`final_price` دیگه در schema اجباری (`required`) نیستن (چون محاسباتی‌ان)، و یک هوک create-request مقدار پیش‌فرض امن براشون می‌ذاره.
- **Migration جدید برای `appointment_services`**: یک `updateRule` واقعی اضافه شد. قبلاً این مقدار `null` بود یعنی فقط superuser می‌تونست آپدیت کنه — یعنی state machine فایل `41_` و چک reschedule فایل `20_` عملاً برای کاربر عادی/صاحب کسب‌وکار قابل دسترس نبودن.
- **Migration جدید**: فیلدهای پولی (`appointment_services.price`, `appointment.total_price/discount_amount/final_price`) از `text` با regex عدد صحیح به `number` واقعی تبدیل شدن (چون `branch_services.price` منبعشون اعشار مجاز می‌ذاره).
- **Migration جدید**: تایپوی `"inavtive"` در مقادیر `discounts.status` به `"inactive"` اصلاح شد.
- **Migration جدید**: `notifications.sent_at` دیگه اجباری نیست (برای این‌که بشه نوتیفیکیشن با status=`pending` قبل از ارسال واقعی ساخت).

## ⚠️ مواردی که هنوز باید خودتون تصمیم بگیرید/تست کنید

1. **ترتیب اجرای hook های هم‌رویداد**: رفتار مستندشده‌ی PocketBase اینه که چند handler روی یک event به ترتیب لود فایل زنجیره می‌شن، ولی حتماً با لاگ (`app.logger()`) تأیید کنید که `10_` واقعاً قبل از `20_` اجرا می‌شه.
2. **`price_override`/`duration_override` صفر**: چون این فیلدها عدد optional هستن (نه nullable واقعی)، مقدار `0` به‌معنی «تنظیم نشده» در نظر گرفته شده. اگه لازم شد یه سرویس واقعاً با override صفر (رایگان) ثبت بشه، باید یه فیلد بولی جدا (`has_price_override`) به schema اضافه بشه.
3. **اعمال واقعی کد تخفیف**: `010_domain_validations.pb.js` فقط صحت داده‌ی `discounts` رو چک می‌کنه (تاریخ، مقدار، تعلق سرویس‌ها). اعمال خودِ کد روی یک appointment (چک شرط min_order_amount، افزایش used_count، جلوگیری از استفاده‌ی بیش از max_uses) هنوز پیاده نشده — چون به یه تصمیم UX نیاز داره (کد تخفیف کجای فلوی رزرو وارد میشه؟ روی appointment یا هر appointment_service؟). اگه بخواید، جداگانه براش hook می‌نویسم.
4. **ارسال واقعی push notification**: `notifications` collection فقط رکورد رو نگه می‌داره؛ ارسال واقعی به FCM/APNs نیاز به credential های خودتون و یه hook دیگه (`onRecordAfterCreateSuccess` روی `notifications` + `$http.send` به FCM) داره که چون به کلید/سرویس بیرونی نیاز داره، اینجا پیاده‌سازی نشده.
5. **`businesses.createRule` و `resources.createRule`/`updateRule` روی `null` هستن** (یعنی فقط superuser می‌تونه Business/Resource جدید بسازه یا Resource رو ویرایش کنه). این می‌تونه عمداً باشه (onboarding دستی توسط ادمین)، ولی اگه می‌خواید صاحبان کسب‌وکار خودشون مستقیم از اپ این کارها رو انجام بدن، این Ruleها باید تغییر کنن — من دست‌نزدم چون این یه تصمیم محصول/امنیتیه، نه یه باگ مشخص.
6. تمام پیام‌های خطا فارسی نوشته شدن؛ اگه اپ چندزبانه می‌شه، بهتره این پیام‌ها رو به یه لایه‌ی i18n جدا منتقل کنید.
