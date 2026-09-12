(() => {
    const form = document.querySelector('#auth-form');
    if (!form) return;
    let mode = 'login';
    const error = document.querySelector('#auth-error');
    const submitText = form.querySelector('.auth-submit span');
    document.querySelectorAll('[data-auth-tab]').forEach(button => button.addEventListener('click', () => {
        mode = button.dataset.authTab;
        document.querySelectorAll('[data-auth-tab]').forEach(tab => tab.classList.toggle('active', tab === button));
        document.querySelectorAll('.register-field').forEach(field => field.hidden = mode === 'login');
        submitText.textContent = mode === 'login' ? 'ورود به حساب' : 'ساخت حساب کاربری';
        document.querySelector('#auth-title').textContent = mode === 'login' ? 'خوش آمدید' : 'ساخت حساب جدید';
        error.hidden = true;
    }));
    form.addEventListener('submit', async event => {
        event.preventDefault(); error.hidden = true;
        const values = Object.fromEntries(new FormData(form));
        if (mode === 'register' && values.password !== values.confirmPassword) {
            error.textContent = 'تکرار رمز عبور یکسان نیست.'; error.hidden = false; return;
        }
        const endpoint = mode === 'login' ? '/api/auth/login?useCookies=true' : '/api/auth/register';
        const response = await fetch(endpoint, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: values.email, password: values.password }) });
        if (!response.ok) {
            error.textContent = mode === 'login' ? 'ایمیل یا رمز عبور صحیح نیست.' : 'ساخت حساب انجام نشد؛ ایمیل یا رمز را بررسی کنید.'; error.hidden = false; return;
        }
        if (mode === 'register') { mode = 'login'; form.requestSubmit(); return; }
        window.location.href = new URLSearchParams(window.location.search).get('returnUrl') || '/appointments';
    });
})();
window.beNobatLogout = async () => {
    await fetch('/api/auth/logout', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' });
    window.location.href = '/';
};
