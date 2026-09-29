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
    // ===== اعداد مطابق زبان برنامه =====
    // سرور اعداد را لاتین (1405، 3,500,000، 06:30 ...) رندر می‌کند. این‌جا همه‌ی متن‌های
    // نمایشی را بر اساس زبان فعلی تبدیل می‌کنیم: فارسی ← ۰۱۲۳، عربی ← ٠١٢٣، انگلیسی ← 0123.
    // توکن‌هایی که حروف لاتین دارند (ایمیل، slug، Asia/Tehran) دست‌نخورده می‌مانند تا
    // داده‌ی فنی خراب نشود. مقدار inputها هم هرگز تغییر نمی‌کند.
    const DIGITS = { fa: '۰۱۲۳۴۵۶۷۸۹', ar: '٠١٢٣٤٥٦٧٨٩' };
    function localizeDigits(text) {
        if (!/[0-9۰-۹٠-٩]/.test(text)) return text;
        const target = DIGITS[language];
        return text.split(/(\s+)/).map(token => {
            if (!/[0-9۰-۹٠-٩]/.test(token) || /[A-Za-z@]/.test(token)) return token;
            let latin = token.replace(/[۰-۹]/g, d => String(d.charCodeAt(0) - 0x06F0)).replace(/[٠-٩]/g, d => String(d.charCodeAt(0) - 0x0660));
            if (!target) return latin;
            latin = latin.replace(/\d/g, d => target[+d]);
            return latin.replace(/(?<=[۰-۹٠-٩]),(?=[۰-۹٠-٩])/g, '٬').replace(/(?<=[۰-۹٠-٩])\.(?=[۰-۹٠-٩])/g, '٫');
        }).join('');
    }

    // برای هر text node، «متن اصلی» و «آخرین مقداری که خودمان نوشتیم» را نگه می‌داریم.
    // اگر Blazor خودش مقدار node را عوض کرده باشد (مقدار فعلی ≠ آخرین نوشته‌ی ما)، مقدار
    // تازه‌ی Blazor متن اصلی جدید حساب می‌شود؛ قبلاً متن قدیمی برمی‌گشت.
    const states = new WeakMap();
    let language = 'fa';
    let translating = false;

    function translateText(source) {
        const key = source.trim();
        if (!key) return source;
        let translated = key;
        if (language !== 'fa') {
            const dictionary = translations[language] || {};
            translated = dictionary[key] || Object.keys(dictionary).sort((a,b) => b.length-a.length)
                .reduce((text, item) => text.replaceAll(item, dictionary[item]), key);
        }
        return source.replace(key, localizeDigits(translated));
    }

    function translateNode(node) {
        if (node.parentElement?.closest('script,style,textarea')) return;
        let state = states.get(node);
        if (!state || node.nodeValue !== state.written) {
            state = { original: node.nodeValue, written: node.nodeValue };
            states.set(node, state);
        }
        const next = translateText(state.original);
        if (node.nodeValue !== next) node.nodeValue = next;
        state.written = next;
    }

    function translate(root = document.body) {
        if (!root || translating) return;
        translating = true;
        try {
            if (root.nodeType === Node.TEXT_NODE) translateNode(root);
            else {
                const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
                const nodes = [];
                while (walker.nextNode()) nodes.push(walker.currentNode);
                nodes.forEach(translateNode);
            }
            const scope = root.nodeType === Node.TEXT_NODE ? root.parentElement : root;
            const elements = [scope, ...(scope?.querySelectorAll?.('[placeholder],[title],[aria-label]') || [])];
            for (const element of elements) for (const attribute of ['placeholder','title','aria-label']) {
                if (!element?.hasAttribute?.(attribute)) continue;
                const storage = `i18n${attribute.replace('-', '')}`;
                element.dataset[storage] ||= element.getAttribute(attribute);
                const original = element.dataset[storage];
                const dictionary = translations[language] || {};
                const translated = language === 'fa' ? original : Object.keys(dictionary).sort((a,b) => b.length-a.length)
                    .reduce((text, source) => text.replaceAll(source, dictionary[source]), original);
                if (element.getAttribute(attribute) !== translated) element.setAttribute(attribute, translated);
            }
        } finally {
            translating = false;
        }
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
    function ensureTranslationObserver() {
        if (window.beNobatTranslationObserver) return;
        // childList: نودهای تازه؛ characterData: وقتی Blazor متن یک نود موجود را به‌روز می‌کند
        // (مثلاً وضعیت نوبت یا شمارنده) و اعداد باید دوباره تبدیل شوند.
        window.beNobatTranslationObserver = new MutationObserver(records => {
            for (const record of records) {
                if (record.type === 'characterData') translate(record.target);
                else for (const node of record.addedNodes) if (node.nodeType === Node.ELEMENT_NODE || node.nodeType === Node.TEXT_NODE) translate(node);
            }
        });
        window.beNobatTranslationObserver.observe(document.body, {childList:true, subtree:true, characterData:true});
    }
    // [fix] حالت روشن/تیره بعد از جابه‌جایی بین صفحه‌ها به حالت پیش‌فرض برمی‌گشت. علتش
    // این بود که تم فقط یک‌بار، داخل OnAfterRenderAsync(firstRender) کامپوننت
    // UiPreferences اعمال می‌شد؛ چون آن کامپوننت داخل layout مشترک است، بین ناوبری‌های
    // «enhanced navigation» بلیزور دوباره ساخته نمی‌شود، ولی خودِ ناوبری بلیزور صفت
    // data-theme را که فقط با جاوااسکریپت روی <html> نشسته بود پاک می‌کرد. راه‌حل: تم و
    // زبان را مستقل از چرخه‌ی عمر کامپوننت، همین‌جا و بعد از هر ناوبری enhanced دوباره
    // اعمال می‌کنیم.
    function applyPreferencesFromStorage() {
        applyTheme(localStorage.getItem('benobat-theme') || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'));
        setLanguage(localStorage.getItem('benobat-language') || 'fa');
        ensureTranslationObserver();
    }
    applyPreferencesFromStorage();
    window.Blazor?.addEventListener?.('enhancedload', applyPreferencesFromStorage);

    window.beNobat.preferences = {
        initialize: () => {
            applyPreferencesFromStorage();
            return language;
        },
        setLanguage,
        getLanguage: () => language,
        reload: () => window.location.reload(),
        toggleTheme: () => applyTheme(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark')
    };

    // Native <details> does not close when the user clicks elsewhere. Keep both
    // header menus consistent: clicking outside either menu closes it.
    document.addEventListener('click', event => {
        for (const menu of document.querySelectorAll('.public-user-menu[open], .language-menu[open]')) {
            if (!menu.contains(event.target)) menu.removeAttribute('open');
        }
    });
})();
