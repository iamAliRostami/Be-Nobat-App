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
    const localizer = window.beNobatI18n;
    let language = 'fa';
    const serverLanguage = ['fa','en','ar'].includes(document.documentElement.lang) ? document.documentElement.lang : 'fa';
    let preferencesInitialized = false;
    const translate = root => localizer.translate(root);

    function setLanguage(value) {
        language = ['fa','ar','en'].includes(value) ? value : 'fa';
        localStorage.setItem('benobat-language', language);
        document.cookie = `benobat-language=${language}; path=/; max-age=31536000; SameSite=Lax${location.protocol === 'https:' ? '; Secure' : ''}`;
        localizer.setLanguage(language);
        document.documentElement.lang = language;
        document.documentElement.dir = language === 'en' ? 'ltr' : 'rtl';
        translate(document.documentElement);
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
                if (record.type === 'characterData') localizer.translateNode(record.target);
                else if (record.type === 'attributes') localizer.translateAttributes(record.target);
                else for (const node of record.addedNodes) if (node.nodeType === Node.ELEMENT_NODE || node.nodeType === Node.TEXT_NODE) translate(node);
            }
        });
        window.beNobatTranslationObserver.observe(document.documentElement, {childList:true, subtree:true, characterData:true, attributes:true, attributeFilter:['placeholder','title','aria-label','alt']});
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
        const preferredLanguage = localStorage.getItem('benobat-language') || document.documentElement.lang || 'fa';
        setLanguage(preferredLanguage);
        // Migrate older localStorage-only preferences so server calendars and
        // validation pages render with the same locale on the next request.
        if (!preferencesInitialized && language !== serverLanguage) {
            preferencesInitialized = true;
            window.location.reload();
        }
        preferencesInitialized = true;
        ensureTranslationObserver();
        initializeSidebar();
    }

    const sidebarViewport = matchMedia('(max-width: 900px)');
    function setSidebarCollapsed(collapsed, persist = false) {
        document.documentElement.dataset.sidebarCollapsed = String(collapsed);
        const sidebar = document.getElementById('admin-sidebar');
        if (sidebar) sidebar.inert = collapsed && sidebarViewport.matches;
        for (const button of document.querySelectorAll('[data-sidebar-toggle]')) {
            button.setAttribute('aria-expanded', String(!collapsed));
        }
        if (collapsed && !sidebarViewport.matches) {
            for (const group of document.querySelectorAll('.sidebar-group')) group.open = true;
        }
        if (persist && !sidebarViewport.matches) localStorage.setItem('benobat-sidebar-collapsed', String(collapsed));
    }
    function initializeSidebar() {
        setSidebarCollapsed(sidebarViewport.matches || localStorage.getItem('benobat-sidebar-collapsed') === 'true');
    }
    sidebarViewport.addEventListener('change', initializeSidebar);
    document.addEventListener('click', event => {
        if (event.target.closest('[data-sidebar-toggle]')) {
            setSidebarCollapsed(document.documentElement.dataset.sidebarCollapsed !== 'true', true);
        } else if (event.target.closest('[data-sidebar-close]') ||
            (sidebarViewport.matches && event.target.closest('.sidebar nav a'))) {
            setSidebarCollapsed(true);
        }
    });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && sidebarViewport.matches) {
            setSidebarCollapsed(true);
            document.querySelector('[data-sidebar-toggle]')?.focus();
        }
    });
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
