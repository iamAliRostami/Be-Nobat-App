window.beNobat = {
    scrollToResults: () => {
        const results = document.getElementById('businesses');
        if (results) {
            results.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    }
};
