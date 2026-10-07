// Run: node --test tests/localization/web-localization.test.cjs
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../..');
const web = path.join(root, 'src/BeNobat.Web');
const sandbox = { window: {} };
vm.runInNewContext(fs.readFileSync(path.join(web, 'wwwroot/i18n/catalog.js'), 'utf8'), sandbox);
const catalog = JSON.parse(JSON.stringify(sandbox.window.beNobatCatalog));
const createLocalizer = require(path.join(web, 'wwwroot/i18n/localizer.js'));

function files(folder, suffix) {
    return fs.readdirSync(folder, { withFileTypes: true }).flatMap(entry => entry.isDirectory()
        ? files(path.join(folder, entry.name), suffix)
        : entry.name.endsWith(suffix) ? [path.join(folder, entry.name)] : []);
}

test('all three generated resources have matching nonempty keys and valid template placeholders', () => {
    for (const language of ['fa', 'en', 'ar']) {
        const resource = JSON.parse(fs.readFileSync(path.join(web, `wwwroot/i18n/${language}.json`), 'utf8'));
        assert.deepEqual(Object.keys(resource).sort(), Object.keys(catalog.messages).sort());
        for (const [key, entry] of Object.entries(catalog.messages)) {
            assert.equal(resource[key], entry[language], `${language}: ${key}`);
            assert.ok(entry[language].trim());
            if (language === 'en' && key !== 'فارسی') assert.doesNotMatch(entry.en, /[\u0600-\u06ff]/u, key);
        }
    }
    for (const template of catalog.templates) {
        const expected = Array.from({ length: template.args.length }, (_, index) => String(index)).sort();
        for (const language of ['fa', 'en', 'ar']) {
            const actual = [...new Set([...template[language].matchAll(/\{(\d+)\}/g)].map(match => match[1]))].sort();
            assert.deepEqual(actual, expected, `${language}: ${template.fa}`);
        }
    }
});

test('static Razor labels, attributes and server validation messages have reviewed resources', () => {
    const localizer = createLocalizer(catalog);
    localizer.setLanguage('en');
    const missing = [];
    const knownLanguageLiterals = new Set(['فا', 'عر', 'موظف واحد أو أكثر لا ينتمي إلى هذا الفرع؛ أعد اختيار الموظفين.', 'العربیة', 'فارسی', 'أسبوعي · التالي', 'اختيار التاريخ', 'الانتقال إلى تاريخ', 'اليوم', 'الأسبوع السابق', 'الأسبوع التالي', 'الشهر السابق', 'الشهر التالي', 'أدخل اسم الخدمة.', 'اختر خدمة من قائمة الكتالوج.', 'اختر فرعًا صالحًا لهذا النشاط.', 'اختر موظفًا واحدًا على الأقل لتقديم الخدمة.', 'اختر نشاطًا تجاريًا أولًا.', 'تمت إضافة خدمة الكتالوج هذه مسبقًا لهذا النشاط؛ عدّل الخدمة الحالية.', 'لا يمكن أن يكون سعر الخدمة سالبًا.', 'ليس لديك صلاحية إدارة خدمات هذا الفرع.', 'يجب ألا تقل مدة الخدمة عن ٥ دقائق.']);
    for (const file of [...files(path.join(web, 'Components'), '.razor'), path.join(web, 'Infrastructure/PersianIdentityErrorDescriber.cs'), path.join(web, 'Domain/Taxonomy.cs')]) {
        const source = fs.readFileSync(file, 'utf8').replace(/@\*[\s\S]*?\*@/g, '').replace(/\/\/[^\n]*/g, '');
        const candidates = [];
        for (const match of source.matchAll(/"((?:\\.|[^"\\])*)"/g)) {
            const context = source.slice(Math.max(0, match.index - 100), match.index);
            if (/Logger\.Log[^;]*$/u.test(context)) continue;
            candidates.push(match[1]);
        }
        for (const match of source.split('@code')[0].matchAll(/>([^<>]+)</g)) {
            if (!/[@{}="]/u.test(match[1])) candidates.push(match[1]);
        }
        for (const candidate of candidates) {
            const value = candidate.replace(/\s+/g, ' ').trim();
            if (value.length <= 1 || !/[\u0600-\u06ff]/u.test(value) || /[@{}]/u.test(value) || /^[\d۰-۹٠-٩]+$/u.test(value) || knownLanguageLiterals.has(value)) continue;
            if (/[\u0600-\u06ff]/u.test(localizer.translateText(value))) missing.push(`${path.relative(root, file)}: ${value}`);
        }
    }
    assert.deepEqual([...new Set(missing)], [], 'Add reviewed resources for new UI text before merging.');
});

test('counts, errors, metadata and decorative symbols translate as complete phrases', () => {
    const localizer = createLocalizer(catalog);
    localizer.setLanguage('en');
    assert.equal(localizer.translateText('  + افزودن شعبه  '), '  + Add branch  ');
    assert.equal(localizer.translateText('▦ نوبت‌های من'), '▦ My appointments');
    assert.equal(localizer.translateText('۱ نوبت'), '1 appointment');
    assert.equal(localizer.translateText('۳۶ نوبت'), '36 appointments');
    assert.equal(localizer.translateText('۳۰ دقیقه · ۲۸۰٬۰۰۰ تومان'), '30 minutes · 280,000 Toman');
    assert.equal(localizer.translateText('۳۰ دقیقه · ۲۸۰٬۰۰۰ تومان · اختصاصی'), '30 minutes · 280,000 Toman · Custom');
    assert.equal(localizer.translateText('9:00 تا 19:00'), '9:00 to 19:00');
    assert.equal(localizer.translateText('نقش‌ها: مدیر شعبه، مشتری'), 'Roles: Branch manager, Customer');
    assert.equal(localizer.translateText('رمز عبور باید حداقل ۸ کاراکتر باشد.'), 'Password must contain at least 8 characters.');
    assert.equal(localizer.translateText('ایمیل الزامی است. رمز عبور الزامی است.'), 'Email is required. Password is required.');
    localizer.setLanguage('ar');
    assert.equal(localizer.translateText('۳۶ نوبت'), '٣٦ مواعيد');
    assert.equal(localizer.translateText('۳ از ۵ ستاره'), '٣ من ٥ نجوم');
});

test('unknown text and typed data arguments do not receive substring substitutions', () => {
    const localizer = createLocalizer(catalog);
    localizer.setLanguage('en');
    assert.equal(localizer.translateText('خدمات من برای مشتری ویژه'), 'خدمات من برای مشتری ویژه');
    assert.equal(localizer.translateText('نتایج برای «خدمات من»'), 'Results for “خدمات من”');
    assert.equal(localizer.translateText('نتایج برای «خدمات   من»'), 'Results for “خدمات   من”');
    assert.equal(localizer.translateText('نام   واردشده'), 'نام   واردشده');
    localizer.setLanguage('ar');
    assert.equal(localizer.translateText('رمز موقت Ali09: «P@ss09!» — فقط همین یک‌بار نمایش داده می‌شود؛ آن را امن به کاربر بدهید و بخواهید پس از ورود تغییرش دهد.'),
        'كلمة المرور المؤقتة لـAli09: «P@ss09!» — تظهر مرة واحدة فقط؛ أوصلها بأمان واطلب من المستخدم تغييرها بعد الدخول.');
    assert.equal(localizer.localizeDigits('customer1@example.com Asia/Tehran X93 14:05'), 'customer1@example.com Asia/Tehran X93 ١٤:٠٥');
});

test('text state follows Blazor updates and round-trips across languages', () => {
    const localizer = createLocalizer(catalog);
    const node = { parentElement: { closest: () => null }, nodeValue: 'در انتظار' };
    localizer.setLanguage('en');
    localizer.translateNode(node);
    assert.equal(node.nodeValue, 'Pending');
    node.nodeValue = 'تأیید شده'; // A new value supplied by Blazor on this node.
    localizer.translateNode(node);
    assert.equal(node.nodeValue, 'Confirmed');
    localizer.setLanguage('ar');
    localizer.translateNode(node);
    assert.equal(node.nodeValue, 'مؤكد');
    localizer.setLanguage('fa');
    localizer.translateNode(node);
    assert.equal(node.nodeValue, 'تأیید شده');
    const userNode = { parentElement: { closest: () => ({}) }, nodeValue: 'فعال' };
    localizer.setLanguage('en');
    localizer.translateNode(userNode);
    assert.equal(userNode.nodeValue, 'فعال');
});

test('dynamic attributes use their latest source without changing input values', () => {
    const localizer = createLocalizer(catalog);
    const values = new Map([['aria-label', 'افزودن به علاقه‌مندی‌ها'], ['placeholder', 'مثلاً ۰۹۱۲۱۲۳۴۵۶۷'], ['value', '۰۹۱۲۱۲۳۴۵۶۷']]);
    const element = {
        closest: () => null,
        hasAttribute: key => values.has(key),
        getAttribute: key => values.get(key),
        setAttribute: (key, value) => values.set(key, value)
    };
    localizer.setLanguage('en');
    localizer.translateAttributes(element);
    assert.equal(values.get('aria-label'), 'Add to favorites');
    assert.equal(values.get('value'), '۰۹۱۲۱۲۳۴۵۶۷');
    values.set('aria-label', 'حذف از علاقه‌مندی‌ها');
    localizer.translateAttributes(element);
    assert.equal(values.get('aria-label'), 'Remove from favorites');
    localizer.setLanguage('ar');
    localizer.translateAttributes(element);
    assert.equal(values.get('aria-label'), 'إزالة من المفضلة');
    localizer.setLanguage('fa');
    localizer.translateAttributes(element);
    assert.equal(values.get('aria-label'), 'حذف از علاقه‌مندی‌ها');
    assert.equal(values.get('placeholder'), 'مثلاً ۰۹۱۲۱۲۳۴۵۶۷');
});
