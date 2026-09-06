# Be Nobat

بازنویسی سامانه‌ی Be Nobat با ASP.NET Core، Blazor و PostgreSQL. این شاخه شروع
جایگزینی تدریجی PocketBase است؛ فایل‌های PocketBase فعلاً برای مرجع مهاجرت داده
حفظ شده‌اند و منبع اجرای برنامه‌ی جدید نیستند.

## اجرای محلی

پیش‌نیازها: Docker 24+ و Docker Compose v2.

```bash
docker compose up --build
```

سپس پنل در `http://localhost:8080` و health check در
`http://localhost:8080/health` در دسترس است.

## ساختار

- `src/BeNobat.Web`: میزبان ASP.NET Core، Blazor، Identity و REST API
- `src/BeNobat.Web/Domain`: مدل دامنه‌ی مستقل از رابط کاربری
- `src/BeNobat.Web/Infrastructure`: EF Core و PostgreSQL
- `tests/BeNobat.Web.Tests`: تست‌های معماری و دامنه
- `docs/05_DotNetMigrationPlan.md`: مرز فاز اول و نقشه‌ی مهاجرت

## وضعیت فاز اول

فاز اول یک foundation قابل اجرا شامل پنل مدیریتی RTL، داشبورد، PostgreSQL،
Identity API، OpenAPI، health check و مدل اولیه‌ی کسب‌وکار/شعبه/سرویس/منبع/نوبت
است. قابلیت‌های عملیاتی در فازهای بعدی به‌صورت vertical slice اضافه می‌شوند.
