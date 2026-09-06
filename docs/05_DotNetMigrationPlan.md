# نقشه‌ی مهاجرت به .NET

## تصمیم معماری

برنامه‌ی مقصد یک modular monolith با ASP.NET Core 10، Blazor Web App، EF Core و
PostgreSQL است. پنل مدیریت بخشی از همان deployment است و REST API قرارداد مشترک
کلاینت‌های موبایل و وب خواهد بود. PocketBase پس از تطبیق داده و cut-over حذف
می‌شود، نه پیش از آن.

## فازها

1. **Foundation (این تغییر):** میزبان وب، پنل RTL، Identity، PostgreSQL، health،
   OpenAPI و مدل هسته.
2. **Catalog:** کسب‌وکار، شعبه، عضویت، خدمات، منابع و ساعات کاری.
3. **Booking:** availability مبتنی بر timezone، رزرو اتمیک و idempotent، جلوگیری
   دیتابیسی از تداخل، لغو و جابه‌جایی.
4. **Commerce:** قیمت، تخفیف تراکنشی، history و سیاست currency.
5. **Engagement:** notification outbox/retry، waiting list، review، reputation و
   loyalty.
6. **Operations:** audit غیرقابل تغییر، گزارش، backup/restore، observability و
   import نهایی داده‌های PocketBase.

## معیار cut-over

- تمام migrationهای EF روی PostgreSQL خالی اجرا شوند.
- import آزمایشی شمار رکورد و روابط را با snapshot PocketBase تطبیق دهد.
- تست‌های concurrency رزرو و تخفیف، authorization و restore پاس شوند.
- PocketBase در یک بازه‌ی کنترل‌شده read-only شده و سپس ترافیک منتقل شود.
