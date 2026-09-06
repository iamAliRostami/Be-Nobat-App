# بازبینی PocketBase برای Production

## مبنای این بازبینی

این نسخه مستقیماً از branch `main` ساخته شده است. برخلاف اصلاح قبلی، هیچ فایل
`pb_data` حذف، جایگزین یا sanitize نشده است؛ زیرا migrationهای legacy پروژه یک
زنجیره‌ی bootstrap مستقل نیستند و حذف `data.db` باعث اجرای migration
`1723290000_add_soft_delete_fields.js` پیش از ساخته‌شدن collectionهای موردنیاز
و خطای `sql: no rows in result set` می‌شود.

## اصلاحات این مرحله

- فیلدهای محاسباتی و status اولیه‌ی appointment از ورودی کاربر پذیرفته
  نمی‌شوند و در hook سرور مقداردهی می‌شوند.
- appointment service برای کاربر عادی همیشه با status=`pending` ساخته می‌شود و
  به appointment نهایی‌شده نمی‌توان service جدید افزود.
- مشتری فقط مجاز به لغو appointment متعلق به خودش است؛ state machine نیز گذار
  pending/confirmed به cancelled را کنترل می‌کند.
- reputation event شناسه عامل تغییر را ذخیره می‌کند. لغو توسط provider دیگر
  باعث جریمه مشتری نمی‌شود.
- قواعد یکتایی مهم علاوه بر hook در SQLite نیز enforce می‌شوند تا درخواست‌های
  هم‌زمان نتوانند review، membership یا role-permission تکراری بسازند.

## نکات لازم پیش از استقرار

1. migration جدید ابتدا روی clone یا backup محیط staging اجرا شود.
2. قبل از unique indexها، داده‌های تکراری موجود بررسی شوند؛ در صورت وجود،
   migration عمداً fail می‌شود تا داده به‌صورت ضمنی حذف نشود.
3. `pb_data` محیط واقعی نباید از repository deploy شود. برای production یک
   volume خصوصی، backup زمان‌بندی‌شده و آزمون restore لازم است. حذف snapshot
   فعلی از Git باید فقط هم‌زمان با ساخت baseline migration مستقل انجام شود.
4. migrationهای legacy در یک تغییر جداگانه squash شوند. معیار پذیرش آن تغییر:
   اجرای موفق روی پوشه‌ی کاملاً خالی و رسیدن به schema یکسان با production است.
5. موارد باقی‌مانده برای فاز بعد: timezone شعبه، endpoint اتمیک booking با
   idempotency key، write-side enforcement ساعات کاری، RBAC مبتنی بر permission،
   مصرف transactional تخفیف و notification outbox دارای retry.
