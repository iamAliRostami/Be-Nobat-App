# -*- coding: utf-8 -*-
import os

new_settings_entries = [
    ("نشانی و موقعیت مکانی دفتر مرکزی", "Head Office Address & Map Location", "عنوان المقر الرئيسي والموقع على الخريطة"),
    ("این نشانی در صفحه ارتباط با ما و فوتر اپلیکیشن نمایش داده می‌شود و کاربران می‌توانند مستقیماً به آن مسیریابی کنند.", "This address is displayed on Contact Us and app footer, allowing users to navigate directly.", "يظهر هذا العنوان في صفحة اتصل بنا وتذييل التطبيق، ويمكن للمستخدمين التوجيه الملاحي إليه مباشرة."),
    ("نشانی کامل دفتر مرکزی", "Head office full address", "العنوان الكامل للمقر الرئيسي"),
    ("پیش‌نمایش مسیریابی کاربران:", "User navigation preview:", "معاينة الملاحة للمستخدمين:"),
    ("اطلاعات بانکی و شماره حساب‌ها", "Bank Details & Account Numbers", "المعلومات البنكية وأرقام الحسابات"),
    ("شماره کارت و شبای رسمی سامانه جهت دریافت مبالغ ثبت کسب‌وکار و افزودن شعب اضافی.", "Official platform card and IBAN for business registration and additional branch fees.", "رقم البطاقة والآيبان الرسمي للنظام لاستلام رسوم تسجيل الأعمال وإضافة الفروع."),
    ("نام بانک", "Bank name", "اسم البنك"),
    ("نام صاحب حساب", "Account holder name", "اسم صاحب الحساب"),
    ("شماره کارت (۱۶ رقمی)", "Card number (16 digits)", "رقم البطاقة (١٦ رقمًا)"),
    ("شماره شبا (IBAN)", "IBAN", "رقم الآيبان (IBAN)"),
    ("شماره حساب (اختیاری)", "Account number (optional)", "رقم الحساب (اختياري)"),
    ("دستورالعمل واریز برای کاربر", "Deposit instructions for user", "تعليمات الإيداع للمستخدم"),
    ("راه‌های ارتباطی و ساعات کاری", "Contact Channels & Working Hours", "قنوات التواصل وساعات العمل"),
    ("شماره‌های پاسخگویی به مشتریان و ساعات اداری که در اپلیکیشن و وب‌سایت نمایش داده می‌شوند.", "Customer support numbers and office hours displayed in app and website.", "أرقام خدمة العملاء وساعات العمل المعروضة في التطبيق والموقع."),
    ("تلفن ثابت ۱", "Landline phone 1", "الهاتف الثابت ١"),
    ("تلفن پشتیبانی ۲", "Support phone 2", "هاتف الدعم ٢"),
    ("تلفن همراه و اضطراری", "Mobile & emergency phone", "رقم الجوال والطوارئ"),
    ("ایمیل پشتیبانی", "Support email", "البريد الإلكتروني للدعم"),
    ("ایمیل عمومی و سازمانی", "General & corporate email", "البريد الإلكتروني العام للمؤسسة"),
    ("ساعات کاری و پاسخگویی", "Working & response hours", "ساعات العمل والرد"),
    ("شنبه تا چهارشنبه", "Saturday to Wednesday", "من السبت إلى الأربعاء"),
    ("پنج‌شنبه‌ها", "Thursdays", "أيام الخميس"),
    ("روزهای تعطیل", "Holidays", "أيام العطلات"),
    ("تعرفه‌ها و شبکه‌های اجتماعی", "Tariffs & Social Networks", "التعريفات وشبكات التواصل"),
    ("تنظیم مبالغ ارتقای کسب‌وکار و آیدی کانال‌های پشتیبانی در شبکه‌های اجتماعی.", "Configure business upgrade fees and official social support channels.", "ضبط رسوم ترقية الأعمال وقنوات الدعم على وسائل التواصل."),
    ("تعرفه ثبت کسب‌وکار (تومان)", "Business registration fee (Toman)", "رسوم تسجيل النشاط التجاري (تومان)"),
    ("تعرفه هر شعبه اضافی (تومان)", "Additional branch fee (Toman)", "رسوم كل فرع إضافي (تومان)"),
    ("شبکه‌های اجتماعی و پیام‌رسان‌ها", "Social Networks & Messengers", "شبكات التواصل الاجتماعي وتطبيقات المراسلة"),
    ("آیدی تلگرام", "Telegram handle", "معرّف تليغرام"),
    ("صفحه اینستاگرام", "Instagram page", "صفحة إنستغرام"),
    ("کانال ایتا", "Eitaa channel", "قناة إيتا"),
    ("ذخیره تغییرات سامانه", "Save Platform Changes", "حفظ تغييرات النظام"),
    ("تنظیمات با موفقیت ذخیره شد و در کل سیستم (وب و اندروید) اعمال گردید.", "Settings were saved successfully and applied across Web and Android.", "تم حفظ الإعدادات بنجاح وتطبيقها على مستوى النظام (الويب والأندرويد).")
]

tsv_path = os.path.join(r"d:\Rostami\Projects\Be-Nobat-App\src\BeNobat.Web\Resources\WebTranslations.tsv")
with open(tsv_path, "r", encoding="utf-8") as f:
    existing = f.read()

lines = existing.splitlines()
seen = set()
for l in lines:
    parts = l.split("\t")
    if parts:
        seen.add(parts[0].strip())

added = 0
with open(tsv_path, "a", encoding="utf-8") as f:
    for fa, en, ar in new_settings_entries:
        if fa.strip() not in seen:
            f.write(f"\n{fa}\t{en}\t{ar}")
            seen.add(fa.strip())
            added += 1

print(f"Added {added} new entries to WebTranslations.tsv")
