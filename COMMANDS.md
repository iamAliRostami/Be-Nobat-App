# راهنمای جامع دستورات اجرایی سامانه به‌نوبت (BeNobat Cheat Sheet)

این راهنما شامل تمامی دستورات کاربردی برای اجرا، مدیریت پایگاه داده، سیدینگ داده‌های دمو، پاکسازی اطلاعات تستی و کامپایل نسخه‌های اندروید است.

---

## ۱. اجرای سرویس وب (Web Application)

### اجرا با Docker

* **اجرا همراه با بیلد مجدد (بعد از هر تغییر در کدهای C# یا Blazor):**

```
docker compose up --build -d web
```

* **اجرای سریع بدون بیلد (استفاده از ایمیج موجود در کش):**

```
docker compose up -d --no-build web
```

* **مشاهده لاگ‌های زنده سرور وب:**

```
docker logs -f be-nobat-app-web-1
```

### اجرا مستقیم با دات‌نت (بدون Docker)

* **بیلد و اجرای مستقیم با پورت 8080:**

```
dotnet run --project src/BeNobat.Web/BeNobat.Web.csproj --urls http://localhost:8080
```

---

## ۲. سید شدن دیتا (Seed - افزودن داده‌های دمو بدون حذف داده‌های قبلی)

این دستورات داده‌های نمایشی (کسب‌وکارهای دمو، شعب، خدمات و نوبت‌ها) را به دیتابیس اضافه می‌کنند، بدون آنکه رکوردهای موجود را پاک کنند.

* **با داکر (همراه با بیلد):**

```
docker compose --profile demo run --rm seed-demo
```

* **با داکر (سریع و بدون بیلد):**

```
docker compose --profile demo run --rm --no-build seed-demo
```

* **بدون داکر (با دات‌نت):**

```
dotnet run --project src/BeNobat.Web/BeNobat.Web.csproj -- --seed-demo
```

---

## ۳. ریست شدن دیتا (Reset Demo - پاکسازی دموهای قبلی و ساخت مجدد از ابتدا)

این دستورات کسب‌وکارهای تستی قبلی را پاک کرده و مجدداً یک مجموعه تازه از اطلاعات دمو را در دیتابیس می‌سازند.

* **با داکر (همراه با بیلد):**

```
docker compose --profile demo run --rm reset-demo
```

* **با داکر (سریع و بدون بیلد):**

```
docker compose --profile demo run --rm --no-build reset-demo
```

* **بدون داکر (با دات‌نت):**

```
dotnet run --project src/BeNobat.Web/BeNobat.Web.csproj -- --reset-demo
```

---

## ۴. پاکسازی داده‌ها برای انتشار عمومی (Production Cleanup)

این دستورات تمامی اطلاعات تستی (کسب‌وکارها، شعب، پرسنل، نوبت‌ها، پیام‌ها، و ۱۲۲ کاربر دمو) را حذف کرده و **تنها حساب مدیر کل (`PlatformAdmin`) و دسته‌بندی‌های پایه و کاتالوگ خدمات عمومی** را نگه می‌دارند.

* **تهیه بکاپ سریع قبل از پاکسازی (پیشنهادی):**

```
docker exec be-nobat-app-postgres-1 pg_dump -U benobat -d benobat > tools/backup.sql
```

* **اجرای اسکریپت پاکسازی داده‌های اضافی:**

```
Get-Content tools/cleanup-demo-data.sql | docker exec -i be-nobat-app-postgres-1 psql -U benobat -d benobat
```

* **بازیابی بکاپ در صورت نیاز به بازگشت:**

```
Get-Content tools/backup.sql | docker exec -i be-nobat-app-postgres-1 psql -U benobat -d benobat
```

---

## ۵. ریست کامل و حذف تمام کانتینرها و دیتابیس از ریشه (Clean Slate)

* **توقف کانتینرها بدون حذف دیتابیس:**

```
docker compose down
```

* **توقف و حذف کامل دیتابیس و Volumeهای داکر (دیتابیس کاملاً فرمت و حذف می‌شود):**

```
docker compose down -v
```

* **راه‌اندازی مجدد با دیتابیس کاملاً نو (فقط شامل ادمین و اطلاعات پایه):**

```
docker compose up -d web
```

---

## ۶. دستورات مربوط به اندروید (Android APK)

اجرا از پوشه پروژه اندروید:

* **بیلد نسخه Debug برای توسعه محلی (متصل به لوکال `127.0.0.1:8080` / `10.0.2.2:8080`):**

```
.\gradlew assembleProdDebug
```

* **بیلد نسخه Release برای انتشار عمومی (متصل به آدرس واقعی `https://benobat.ir`):**

```
.\gradlew assembleProdRelease
```

* **اجرای تست‌های یونیتی اندروید:**

```
.\gradlew testProdDebugUnitTest
```

* **نصب APK نسخه Debug روی شبیه‌ساز یا گوشی متصل:**

```
adb install -r app/build/outputs/apk/prod/debug/app-prod-debug.apk
```

* **انتقال پورت برای اتصال گوشی واقعی از طریق کابل USB به سرور لوکال کامپیوتر:**

```
adb reverse tcp:8080 tcp:8080
```

---

## ۷. مشخصات حساب‌های پیش‌فرض

| نوع حساب | ایمیل / نام کاربری | رمز عبور | توضیحات |
| :--- | :--- | :--- | :--- |
| **مدیر کل سیستم** | `admin@benobat.local` | `Admin123!` | دسترسی کامل به پنل مدیریت کل پلتفرم |
| **مشتری دمو (فقط در حالت دمو)** | `customer01@demo.benobat.example` | `Demo123456` | کاربر عادی برای تست رزرو نوبت |
