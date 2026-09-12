# Be Nobat

بازنویسی سامانه‌ی Be Nobat با ASP.NET Core، Blazor و PostgreSQL. این شاخه شروع
جایگزینی تدریجی PocketBase است؛ فایل‌های PocketBase فعلاً برای مرجع مهاجرت داده
حفظ شده‌اند و منبع اجرای برنامه‌ی جدید نیستند.

## اجرای محلی

پیش‌نیازها: Docker 24+ و Docker Compose v2.

```bash
docker compose up --build
```

سپس وب‌اپ مراجعه‌کنندگان در `http://localhost:8080`، پنل کسب‌وکار در
`http://localhost:8080/admin` و health check در
`http://localhost:8080/health` در دسترس است.

## ساختار

- `src/BeNobat.Web`: میزبان ASP.NET Core، Blazor، Identity و REST API
- `src/BeNobat.Web/Domain`: مدل دامنه‌ی مستقل از رابط کاربری
- `src/BeNobat.Web/Infrastructure`: EF Core و PostgreSQL
- `tests/BeNobat.Web.Tests`: تست‌های معماری و دامنه
- `docs/05_DotNetMigrationPlan.md`: مرز فاز اول و نقشه‌ی مهاجرت

## تجربه‌های کاربری فعلی

- صفحه‌ی کشف خدمات و مراکز پیشنهادی برای مراجعه‌کننده
- جریان سه‌مرحله‌ای انتخاب خدمت، زمان و تأیید نوبت
- صفحه‌ی نوبت‌های پیش‌رو و تاریخچه‌ی مراجعه‌کننده
- داشبورد روزانه، تقویم تیم و مدیریت خدمات برای مدیر مجموعه

زیرساخت PostgreSQL، Identity API، OpenAPI و health check نیز فعال است. داده‌های
نمونه‌ی رابط کاربری در ادامه‌ی مهاجرت، به vertical sliceهای عملیاتی و دیتابیس
متصل خواهند شد.
