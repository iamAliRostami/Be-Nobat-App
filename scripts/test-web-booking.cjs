// Runs against a development database: creates one account and one cancelled booking.
// WEB_SMOKE_BOOKING_PATH must select a business/branch/service/provider with future slots.
const assert = require('node:assert/strict');
const { randomUUID } = require('node:crypto');
const { chromium } = require(process.env.PLAYWRIGHT_PACKAGE_PATH || 'playwright');
const base = process.env.WEB_SMOKE_URL || 'http://127.0.0.1:8080';
const bookingPath = process.env.WEB_SMOKE_BOOKING_PATH;
assert.ok(bookingPath?.startsWith('/book/'), 'Set WEB_SMOKE_BOOKING_PATH for a development fixture.');

(async () => {
    const browser = await chromium.launch({
        ...(process.env.CHROMIUM_EXECUTABLE ? { executablePath: process.env.CHROMIUM_EXECUTABLE } : {}),
        args: ['--no-sandbox'],
    });
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    page.on('console', message => {
        if (message.type() === 'error' && /circuit|Unhandled|Exception/.test(message.text())) errors.push(message.text());
    });
    const email = `browser-${randomUUID()}@audit.benobat.example`;
    const password = `Test-${randomUUID()}`;
    try {
        await page.goto(`${base}/account/register`);
        await page.locator('[name="Input.DisplayName"]').fill('Browser audit');
        await page.locator('[name="Input.Email"]').fill(email);
        await page.locator('[name="Input.Phone"]').fill('۰۹۱۲۱۲۳۴۵۶۷');
        await page.locator('[name="Input.Password"]').fill(password);
        await page.locator('[name="Input.ConfirmPassword"]').fill(password);
        await page.locator('button[type="submit"]').click();
        await page.waitForURL(base + '/');
        await page.goto(base + bookingPath);
        await page.locator('.service-option.selected').first().waitFor();
        await page.locator('.booking-summary > button').click();
        await page.locator('.provider-filter select').waitFor();
        assert.ok(await page.locator('.provider-filter select').inputValue());
        // Search the next six branch-local days; today's elapsed slots are excluded.
        let found = false;
        for (let day = 1; day <= 6; day++) {
            await page.locator('.date-strip button').nth(day).click();
            await page.waitForFunction(() =>
                !!document.querySelector('.time-grid button') ||
                [...document.querySelectorAll('.booking-panel .empty-state')].some(e => e.textContent.includes('برای این روز زمان خالی وجود ندارد'))
            );
            if (await page.locator('.time-grid button').count()) { found = true; break; }
        }
        assert.ok(found, 'Fixture needs a free provider slot in the next six days.');
        await page.locator('.time-grid button').first().click();
        await page.locator('.booking-summary > button').click();
        const phone = page.locator('.booking-confirmation input[type="tel"]');
        const note = page.locator('.booking-confirmation textarea');
        await phone.waitFor();
        assert.equal(await phone.inputValue(), '09121234567');
        await note.fill('Browser booking regression');
        const submit = page.locator('.booking-summary > button');
        assert.equal(await submit.isDisabled(), true, 'Terms must be accepted before booking.');
        await page.locator('.booking-terms-consent button').click();
        await page.locator('.booking-rules-modal .primary-button').click();
        assert.equal(await submit.isEnabled(), true);
        await page.setViewportSize({ width: 390, height: 844 });
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false);
        await submit.click();
        await page.locator('.success-card').waitFor();
        const trackingCode = await page.locator('.success-details > div').first().locator('b').innerText();
        assert.match(trackingCode, /^[A-Z0-9]+$/i);
        await page.locator('.success-card a[href="/appointments"]').click();
        const appointment = page.locator('.appointment-card').filter({ hasText: trackingCode });
        await appointment.waitFor();
        assert.ok((await appointment.innerText()).includes('Browser booking regression'));
        await appointment.getByRole('button', { name: 'لغو نوبت', exact: true }).click();
        await page.getByRole('button', { name: 'بله، لغو شود', exact: true }).click();
        await appointment.waitFor({ state: 'detached' });
        await page.locator('.tabs button').filter({ hasText: 'لغو شده' }).click();
        await appointment.waitFor();
        assert.equal(await appointment.getByRole('button', { name: 'لغو نوبت', exact: true }).count(), 0);
        await page.goto(`${base}/account/profile`);
        // Allow the new interactive circuit to hydrate its initially rendered forms.
        await page.waitForTimeout(750);
        const changedEmail = email.replace('browser-', 'browser-updated-');
        const emailForm = page.locator('form[action="/account/change-email"]');
        await emailForm.locator('input[name="email"]').fill(changedEmail);
        await emailForm.locator('button[type="submit"]').click();
        await page.waitForURL('**/account/profile?emailChanged=1');
        await page.waitForTimeout(750);
        assert.equal(await emailForm.locator('input[name="email"]').inputValue(), changedEmail);
        const changedPassword = `${password}-updated`;
        const passwordForm = page.locator('form[action="/account/change-password"]');
        await passwordForm.locator('input[name="currentPassword"]').fill(password);
        await passwordForm.locator('input[name="newPassword"]').fill(changedPassword);
        await passwordForm.locator('input[name="confirmPassword"]').fill(changedPassword);
        await passwordForm.locator('button[type="submit"]').click();
        await page.waitForURL('**/account/profile?passwordChanged=1');
        await page.waitForTimeout(750);
        await page.locator('.account-menu form[action="/account/logout"] button').click();
        await page.waitForURL(base + '/');
        await page.goto(`${base}/account/login`);
        await page.locator('[name="Input.Email"]').fill(changedEmail);
        await page.locator('[name="Input.Password"]').fill(changedPassword);
        await page.locator('button[type="submit"]').click();
        await page.waitForURL(base + '/');
        await page.goto(`${base}/account/profile`);
        assert.equal(await emailForm.locator('input[name="email"]').inputValue(), changedEmail);
        assert.deepEqual(errors, []);
        console.log('PASS: web registration, Persian phone normalization/prefill, provider slots, required terms, mobile layout, persisted booking/tracking/note, cancellation/history, email/password changes, cookie refresh and fresh login.');
        console.log(`Development fixture retained as a cancelled booking for ${changedEmail}.`);
    } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exit(1); });
