# Be Nobat

بازنویسی سامانه‌ی Be Nobat با ASP.NET Core، Blazor و PostgreSQL. این شاخه شروع
جایگزینی تدریجی PocketBase است؛ فایل‌های PocketBase فعلاً برای مرجع مهاجرت داده
حفظ شده‌اند و منبع اجرای برنامه‌ی جدید نیستند.

## راه‌اندازی پروژه

برای اجرای پروژه دو راه وجود دارد. روش Docker ساده‌تر است و پیشنهاد می‌شود؛ چون
نسخه‌ی درست PostgreSQL و .NET را بدون نصب جداگانه در اختیار برنامه قرار می‌دهد.

### روش اول: اجرا با Docker (پیشنهادی)

#### ۱. پیش‌نیازها

- [Git](https://git-scm.com/)
- Docker Engine نسخه‌ی 24 یا جدیدتر، یا Docker Desktop
- Docker Compose v2 (دستور `docker compose`، بدون خط تیره)
- حداقل ۲ گیگابایت RAM آزاد

نصب صحیح ابزارها را بررسی کنید:

```bash
git --version
docker --version
docker compose version
```

#### ۲. دریافت و ورود به پروژه

```bash
git clone <REPOSITORY_URL>
cd Be-Nobat-App
```

اگر پروژه را از قبل دریافت کرده‌اید، کافی است در ترمینال وارد پوشه‌ی ریشه شوید؛
همان پوشه‌ای که فایل‌های `compose.yaml` و `BeNobat.slnx` در آن قرار دارند.

#### ۳. ساخت و اجرای سرویس‌ها

```bash
docker compose up --build
```

این دستور دو سرویس را راه‌اندازی می‌کند:

1. `postgres`: پایگاه داده‌ی PostgreSQL 17 با دیتابیس `benobat`
2. `web`: برنامه‌ی ASP.NET Core/Blazor روی پورت `8080`

در اولین اجرا imageها دانلود، پروژه restore و publish می‌شود؛ بنابراین ممکن است
چند دقیقه زمان ببرد. آماده شدن برنامه را می‌توانید با مشاهده‌ی لاگ‌ها یا اجرای
دستور زیر در ترمینال دیگری بررسی کنید:

```bash
curl http://localhost:8080/health
```

پاسخ `Healthy` یعنی وب‌اپ و اتصال دیتابیس آماده هستند.

#### ۴. آدرس صفحات

| بخش | آدرس |
| --- | --- |
| صفحه‌ی اصلی مراجعه‌کنندگان | <http://localhost:8080/> |
| نوبت‌های مراجعه‌کننده | <http://localhost:8080/appointments> |
| پنل مدیریت کسب‌وکار | <http://localhost:8080/admin> |
| تقویم مدیر | <http://localhost:8080/admin/calendar> |
| مدیریت خدمات | <http://localhost:8080/businesses> |
| مدیریت شعب و منابع | <http://localhost:8080/admin/branches> |
| مدیریت کل کسب‌وکارها | <http://localhost:8080/platform> |
| مدیریت کاربران و دسترسی‌ها | <http://localhost:8080/platform/access> |
| بررسی سلامت سرویس | <http://localhost:8080/health> |
| سند OpenAPI در محیط Development | <http://localhost:8080/openapi/v1.json> |

#### ۵. اجرا در پس‌زمینه

برای آزاد ماندن ترمینال، سرویس‌ها را detached اجرا کنید:

```bash
docker compose up --build -d
docker compose ps
docker compose logs -f web
```

برای خروج از نمایش لاگ‌ها `Ctrl+C` را بزنید؛ سرویس‌های پس‌زمینه متوقف نمی‌شوند.

#### ۶. توقف، اجرای دوباره و حذف داده‌ها

```bash
# توقف و حذف containerها (اطلاعات PostgreSQL باقی می‌ماند)
docker compose down

# اجرای دوباره بدون build اجباری
docker compose up -d

# توقف و حذف containerها به‌همراه تمام داده‌های محلی PostgreSQL
docker compose down -v
```

> **هشدار:** دستور `docker compose down -v` دیتابیس توسعه را به‌طور کامل پاک
> می‌کند. فقط زمانی از آن استفاده کنید که به reset کامل داده‌ها نیاز دارید.

### روش دوم: اجرا بدون Docker

در این روش باید ابزارهای زیر روی سیستم نصب باشند:

- .NET SDK 10
- PostgreSQL 17 (نسخه‌های سازگار جدید نیز معمولاً قابل استفاده‌اند)

#### ۱. ساخت دیتابیس توسعه

با کاربر مدیر PostgreSQL وارد `psql` شوید و کاربر و دیتابیس محلی را بسازید:

```sql
CREATE USER benobat WITH PASSWORD 'benobat-dev-only';
CREATE DATABASE benobat OWNER benobat;
```

connection string پیش‌فرض موجود در `appsettings.json` برای همین تنظیمات است:

```text
Host=localhost;Port=5432;Database=benobat;Username=benobat;Password=benobat-dev-only
```

برای استفاده از اطلاعات متفاوت، بدون تغییر فایل تنظیمات، متغیر محیطی زیر را
تنظیم کنید:

```bash
export ConnectionStrings__Default='Host=localhost;Port=5432;Database=benobat;Username=YOUR_USER;Password=YOUR_PASSWORD'
```

در PowerShell:

```powershell
$env:ConnectionStrings__Default='Host=localhost;Port=5432;Database=benobat;Username=YOUR_USER;Password=YOUR_PASSWORD'
```

#### ۲. restore، build و اجرا

تمام فرمان‌ها را از پوشه‌ی ریشه‌ی repository اجرا کنید:

```bash
dotnet restore BeNobat.slnx
dotnet build BeNobat.slnx
dotnet run --project src/BeNobat.Web/BeNobat.Web.csproj --urls http://localhost:8080
```

برنامه هنگام شروع، schema موردنیاز را در دیتابیس ایجاد می‌کند. برای توسعه همراه
با اعمال خودکار تغییرات Razor می‌توانید به‌جای دستور آخر از watch استفاده کنید:

```bash
dotnet watch --project src/BeNobat.Web/BeNobat.Web.csproj run --urls http://localhost:8080
```

### اجرای تست‌ها

با نصب بودن .NET SDK، همه‌ی تست‌ها را از ریشه‌ی پروژه اجرا کنید:

```bash
dotnet test BeNobat.slnx
```

برای اجرای فقط پروژه‌ی تست:

```bash
dotnet test tests/BeNobat.Web.Tests/BeNobat.Web.Tests.csproj
```

### تنظیمات و نکات امنیتی

- مقادیر نام کاربری و رمز عبور موجود در `compose.yaml` و `appsettings.json` فقط
  برای محیط توسعه‌ی محلی هستند؛ در staging یا production از آن‌ها استفاده نکنید.
- در Docker، connection string از متغیر `ConnectionStrings__Default` خوانده
  می‌شود و hostname دیتابیس باید نام سرویس یعنی `postgres` باشد، نه `localhost`.
- برای محیط‌های واقعی، secretها را با environment variable یا secret manager
  تزریق کنید و فایل حاوی رمز واقعی را commit نکنید.
- فایل‌های `pb_data` و `pb_migrations` مرجع مهاجرت PocketBase هستند. برنامه‌ی
  جدید مستقیماً از دیتابیس PocketBase اجرا نمی‌شود.

### رفع اشکال‌های رایج

#### پورت 8080 در حال استفاده است

در `compose.yaml` نگاشت پورت وب را از `8080:8080` به، برای مثال، `8081:8080`
تغییر دهید؛ سپس برنامه را در `http://localhost:8081` باز کنید. برای پیدا کردن
پردازشی که پورت را اشغال کرده است نیز می‌توانید اجرا کنید:

```bash
lsof -i :8080
```

#### سرویس web منتظر PostgreSQL مانده است

وضعیت و لاگ سرویس‌ها را بررسی کنید:

```bash
docker compose ps
docker compose logs postgres
docker compose logs web
```

سرویس `web` فقط بعد از Healthy شدن health check پایگاه داده شروع می‌شود.

#### تغییرات کد در Docker دیده نمی‌شود

Dockerfile کد را هنگام build داخل image کپی می‌کند. پس از تغییر کد image را
دوباره بسازید:

```bash
docker compose up --build -d
```

#### دیتابیس محلی خراب یا ناسازگار شده است

اگر حفظ داده‌های توسعه اهمیت ندارد، volume را reset کنید:

```bash
docker compose down -v
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
- داشبورد روزانه، تقویم تیم و مدیریت خدمات، شعب، منابع، مشتریان و اعضای تیم برای مدیر مجموعه
- پنل مدیر کل برای مدیریت همهٔ کسب‌وکارها، وضعیت انتشار و نقش‌های دسترسی کاربران

زیرساخت PostgreSQL، Identity API، OpenAPI و health check نیز فعال است. داده‌های
نمونه‌ی رابط کاربری در ادامه‌ی مهاجرت، به vertical sliceهای عملیاتی و دیتابیس
متصل خواهند شد.
