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
