window.beNobat = {
    // [fix] اسکرول به نتایج جست‌وجو کار نمی‌کرد. دو دلیل داشت:
    //   ۱) بخش نتایج داخل .public-main است که overflow:hidden دارد؛ مرورگر آن را
    //      یک scroll container می‌بیند و scrollIntoView به‌جای پنجره، همان
    //      کانتینر (که اصلاً اسکرول ندارد) را هدف می‌گرفت.
    //   ۲) فراخوانی در وسط هندلر کلیک انجام می‌شد، یعنی قبل از اینکه Blazor
    //      نتایج تازه را رندر کند.
    // اینجا موقعیت مطلق را خودمان حساب می‌کنیم و window.scrollTo می‌زنیم؛
    // ارتفاع هدر هم کم می‌شود تا عنوان بخش زیر هدر پنهان نشود.
    scrollToResults: (selector) => {
        const target = document.querySelector(selector || '#businesses');
        if (!target) return;

        requestAnimationFrame(() => {
            const header = document.querySelector('.public-header');
            const headerHeight = header ? header.getBoundingClientRect().height : 0;
            const top = target.getBoundingClientRect().top + window.scrollY - headerHeight - 12;
            window.scrollTo({ top: Math.max(top, 0), behavior: 'smooth' });
        });
    }
};

(() => {
    const translations = {
        ar: {
            'به‌نوبت':'بي نوبات','پنل مدیریت':'لوحة الإدارة','داشبورد':'لوحة التحكم','تقویم نوبت‌ها':'تقويم المواعيد','مدیریت کسب‌وکار':'إدارة النشاط','خدمات':'الخدمات','شعب و منابع':'الفروع والموارد','زمان‌های خالی':'الأوقات المتاحة','امتیازها و نظرات':'التقييمات والمراجعات','مشتریان':'العملاء','اعضای تیم':'أعضاء الفريق','مدیریت سامانه':'إدارة النظام','کسب‌وکارها':'الأنشطة التجارية','کاربران و دسترسی‌ها':'المستخدمون والصلاحيات','حساب':'الحساب','مشاهده صفحه عمومی':'عرض الصفحة العامة','نوبت‌های من':'مواعيدي','حساب من':'حسابي','ورود':'تسجيل الدخول','ثبت‌نام':'إنشاء حساب','خروج':'تسجيل الخروج','اطلاعات حساب':'بيانات الحساب','پیش رو':'القادمة','گذشته':'السابقة','لغو شده':'الملغاة','رزرو نوبت جدید':'+ حجز موعد جديد','ثبت نظر':'إضافة تقييم','نظر ثبت شد':'تم إرسال التقييم','امتیاز':'التقييم','نظر شما':'تعليقك','ارسال برای بررسی':'إرسال للمراجعة','نوبت‌های امروز':'مواعيد اليوم','تکمیل‌شده امروز':'المكتملة اليوم','در انتظار تأیید':'بانتظار التأكيد','کل نوبت‌ها':'كل المواعيد','مشاهده تقویم':'عرض التقويم','تأیید':'تأكيد','تکمیل شد':'مكتمل','لغو':'إلغاء','دسترسی سریع':'وصول سريع','تقویم کاری':'تقويم العمل','مدیریت خدمات':'إدارة الخدمات','سطح دسترسی':'مستوى الصلاحية','ذخیره نقش':'حفظ الدور','فعال‌سازی':'تفعيل','غیرفعال‌سازی':'تعطيل','فعال':'نشط','غیرفعال':'غير نشط'
        },
        en: {
            'به‌نوبت':'Be Nobat','پنل مدیریت':'Admin panel','داشبورد':'Dashboard','تقویم نوبت‌ها':'Appointments calendar','مدیریت کسب‌وکار':'Business management','خدمات':'Services','شعب و منابع':'Branches & resources','زمان‌های خالی':'Availability','امتیازها و نظرات':'Ratings & reviews','مشتریان':'Customers','اعضای تیم':'Team members','مدیریت سامانه':'Platform management','کسب‌وکارها':'Businesses','کاربران و دسترسی‌ها':'Users & access','حساب':'Account','مشاهده صفحه عمومی':'View public page','نوبت‌های من':'My appointments','حساب من':'My account','ورود':'Sign in','ثبت‌نام':'Register','خروج':'Sign out','اطلاعات حساب':'Account details','پیش رو':'Upcoming','گذشته':'Past','لغو شده':'Cancelled','رزرو نوبت جدید':'+ Book a new appointment','ثبت نظر':'Add review','نظر ثبت شد':'Review submitted','امتیاز':'Rating','نظر شما':'Your comment','ارسال برای بررسی':'Submit review','نوبت‌های امروز':"Today's appointments",'تکمیل‌شده امروز':'Completed today','در انتظار تأیید':'Awaiting confirmation','کل نوبت‌ها':'All appointments','مشاهده تقویم':'View calendar','تأیید':'Confirm','تکمیل شد':'Complete','لغو':'Cancel','دسترسی سریع':'Quick access','تقویم کاری':'Work calendar','مدیریت خدمات':'Manage services','سطح دسترسی':'Access level','ذخیره نقش':'Save role','فعال‌سازی':'Enable','غیرفعال‌سازی':'Disable','فعال':'Active','غیرفعال':'Disabled'
        }
    };
    const originals = new WeakMap();
    let language = 'fa';
    let translating = false;

    function translate(root = document.body) {
        if (!root || translating) return;
        translating = true;
        const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
        const nodes = [];
        while (walker.nextNode()) nodes.push(walker.currentNode);
        for (const node of nodes) {
            if (node.parentElement?.closest('script,style')) continue;
            if (!originals.has(node)) originals.set(node, node.nodeValue);
            const original = originals.get(node);
            const key = original.trim();
            let translated = key;
            if (language !== 'fa') {
                const dictionary = translations[language] || {};
                translated = dictionary[key] || Object.keys(dictionary).sort((a,b) => b.length-a.length)
                    .reduce((text, source) => text.replaceAll(source, dictionary[source]), key);
            }
            node.nodeValue = original.replace(key, translated);
        }
        const elements = [root, ...(root.querySelectorAll?.('[placeholder],[title],[aria-label]') || [])];
        for (const element of elements) for (const attribute of ['placeholder','title','aria-label']) {
            if (!element?.hasAttribute?.(attribute)) continue;
            const storage = `i18n${attribute.replace('-', '')}`;
            element.dataset[storage] ||= element.getAttribute(attribute);
            const original = element.dataset[storage];
            const dictionary = translations[language] || {};
            const translated = language === 'fa' ? original : Object.keys(dictionary).sort((a,b) => b.length-a.length)
                .reduce((text, source) => text.replaceAll(source, dictionary[source]), original);
            element.setAttribute(attribute, translated);
        }
        translating = false;
    }

    function setLanguage(value) {
        language = ['fa','ar','en'].includes(value) ? value : 'fa';
        localStorage.setItem('benobat-language', language);
        document.documentElement.lang = language;
        document.documentElement.dir = language === 'en' ? 'ltr' : 'rtl';
        translate();
    }
    function applyTheme(value) {
        document.documentElement.dataset.theme = value;
        localStorage.setItem('benobat-theme', value);
    }
    window.beNobat.preferences = {
        initialize: () => {
            const selected = localStorage.getItem('benobat-language') || 'fa';
            applyTheme(localStorage.getItem('benobat-theme') || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'));
            setLanguage(selected);
            if (!window.beNobatTranslationObserver) {
                window.beNobatTranslationObserver = new MutationObserver(records => {
                    if (translating) return;
                    for (const record of records) for (const node of record.addedNodes) if (node.nodeType === Node.ELEMENT_NODE) translate(node);
                });
                window.beNobatTranslationObserver.observe(document.body, {childList:true, subtree:true});
            }
            return selected;
        },
        setLanguage,
        toggleTheme: () => applyTheme(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark')
    };
})();
