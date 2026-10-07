/* Exact resource matching keeps user-entered names, reviews and form values intact.
 * Blazor may update a text node or attribute in place; every state tracks both the
 * latest server source and our last write, rather than caching the first render. */
(function (root, factory) {
    if (typeof module === 'object' && module.exports) module.exports = factory;
    else root.beNobatI18n = factory(root.beNobatCatalog);
})(typeof window === 'undefined' ? globalThis : window, function createLocalizer(catalog) {
    'use strict';
    const digits = { fa: '۰۱۲۳۴۵۶۷۸۹', ar: '٠١٢٣٤٥٦٧٨٩' };
    const textStates = new WeakMap();
    const attributeStates = new WeakMap();
    const messages = catalog.messages;
    let language = 'fa';
    const normalize = value => value.replace(/\s+/gu, ' ').trim();
    const latinDigits = value => value.replace(/[۰-۹]/gu, digit => String(digit.charCodeAt(0) - 0x06f0))
        .replace(/[٠-٩]/gu, digit => String(digit.charCodeAt(0) - 0x0660));
    const numberPattern = /^[+−-]?[0-9۰-۹٠-٩]+(?:[.,٬٫:/-][0-9۰-۹٠-٩]+)*$/u;
    function localizeDigits(value) {
        // Form values and tokens containing Latin letters (email, URL, code, zone)
        // never pass through numeric localization.
        return value.split(/(\s+)/u).map(token => {
            if (/[A-Za-z@]/u.test(token)) return token;
            const latin = latinDigits(token).replace(/(?<=\d)٬(?=\d)/gu, ',').replace(/(?<=\d)٫(?=\d)/gu, '.');
            if (!digits[language]) return latin;
            return latin.replace(/\d/gu, digit => digits[language][Number(digit)])
                .replace(/(?<=[۰-۹٠-٩]),(?=[۰-۹٠-٩])/gu, '٬')
                .replace(/(?<=[۰-۹٠-٩])\.(?=[۰-۹٠-٩])/gu, '٫');
        }).join('');
    }
    const escape = value => value.replace(/[.*+?^${}()|[\]\\]/gu, '\\$&');
    const templates = catalog.templates.map(template => {
        let last = 0;
        let pattern = '^';
        const indexes = [];
        for (const match of template.fa.matchAll(/\{(\d+)\}/gu)) {
            pattern += escape(template.fa.slice(last, match.index)).replace(/ /gu, '\\s+');
            pattern += template.args[Number(match[1])] === 'number' ? '([+−\\-]?[0-9۰-۹٠-٩][0-9۰-۹٠-٩.,٬٫:/\\-]*)' : '(.+?)';
            indexes.push(Number(match[1]));
            last = match.index + match[0].length;
        }
        pattern += escape(template.fa.slice(last)).replace(/ /gu, '\\s+') + '$';
        return { ...template, pattern: new RegExp(pattern, 'u'), indexes };
    }).sort((left, right) => right.fa.replace(/\{\d+\}/gu, '').length - left.fa.replace(/\{\d+\}/gu, '').length);

    const validationCandidates = Object.keys(messages).filter(item => /[.!؟]$/u.test(item)).sort((a, b) => b.length - a.length);
    function translateCore(key, allowTemplates = true) {
        const message = messages[normalize(key)];
        if (message) return localizeDigits(message[language]);
        if (language === 'fa') return localizeDigits(key);
        if (allowTemplates) for (const template of templates) {
            const match = key.match(template.pattern);
            if (!match) continue;
            const args = [];
            template.indexes.forEach((index, order) => { args[index] = match[order + 1]; });
            let target = template[language];
            if (language === 'en' && template.enOne && Number(latinDigits(args[0])) === 1) target = template.enOne;
            // Translate literal parts independently; preserve data arguments exactly,
            // including passwords, names containing digits and tracking codes.
            return target.split(/(\{\d+\})/u).map(part => {
                const placeholder = part.match(/^\{(\d+)\}$/u);
                if (!placeholder) return localizeDigits(part);
                const index = Number(placeholder[1]);
                const kind = template.args[index];
                if (kind === 'number') return localizeDigits(args[index]);
                if (kind === 'ui') {
                    const value = normalize(args[index]);
                    const items = value.split(/[،,]\s*/u);
                    if (items.length > 1 && items.every(item => messages[item])) {
                        return items.map(item => localizeDigits(messages[item][language])).join(language === 'ar' ? '، ' : ', ');
                    }
                    return messages[value] ? translateCore(value, false) : args[index];
                }
                return args[index];
            }).join('');
        }
        // A joined validation summary consists exclusively of complete known
        // messages separated by whitespace or punctuation. Never substitute a
        // recognized word inside unknown user text.
        const separators = /^\s*(?:[،؛;,]\s*)?/u;
        let remaining = key;
        const parts = [];
        while (remaining.length) {
            const prefix = validationCandidates.find(item => remaining.startsWith(item));
            if (!prefix) break;
            parts.push(localizeDigits(messages[prefix][language]));
            remaining = remaining.slice(prefix.length);
            const separator = remaining.match(separators)[0];
            parts.push(separator.replace('،', ',').replace('؛', ';'));
            remaining = remaining.slice(separator.length);
        }
        if (!remaining.length && parts.length) return parts.join('');
        return localizeDigits(key);
    }
    function translateText(source) {
        if (!source || !source.trim()) return source;
        const key = source.trim();
        let translated = translateCore(key);
        if (translated === localizeDigits(key) && !messages[normalize(key)]) {
            // Symbols are presentation, not part of the resource. Keep them in
            // place while matching the complete remaining phrase.
            const match = key.match(/^([+→←▦◈♡⌖◷💳✉☎★●—–·\s]*)(.*?)([→←—\s]*)$/u);
            if (match?.[2]) translated = match[1] + translateCore(match[2]) + match[3];
        }
        const prefix = source.match(/^\s*/u)[0];
        const suffix = source.match(/\s*$/u)[0];
        return prefix + translated + suffix;
    }
    function nextState(previous, current) {
        return !previous || current !== previous.written ? { source: current, written: current } : previous;
    }
    function translateNode(node) {
        if (!node.parentElement || node.parentElement.closest('script,style,textarea,code,pre,[data-i18n-skip],[translate="no"],[contenteditable="true"]')) return;
        const state = nextState(textStates.get(node), node.nodeValue);
        const translated = translateText(state.source);
        if (node.nodeValue !== translated) node.nodeValue = translated;
        state.written = translated;
        textStates.set(node, state);
    }
    function translateAttributes(element) {
        if (!element?.getAttribute || element.closest('script,style,[translate="no"]')) return;
        let states = attributeStates.get(element);
        if (!states) { states = new Map(); attributeStates.set(element, states); }
        for (const attribute of ['placeholder', 'title', 'aria-label', 'alt']) {
            if (!element.hasAttribute(attribute)) { states.delete(attribute); continue; }
            const state = nextState(states.get(attribute), element.getAttribute(attribute));
            const translated = translateText(state.source);
            if (element.getAttribute(attribute) !== translated) element.setAttribute(attribute, translated);
            state.written = translated;
            states.set(attribute, state);
        }
    }
    function translate(root = document.body) {
        if (!root) return;
        if (root.nodeType === 3) { translateNode(root); return; }
        if (root.nodeType !== 1 && root.nodeType !== 9 && root.nodeType !== 11) return;
        const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
        while (walker.nextNode()) translateNode(walker.currentNode);
        translateAttributes(root);
        root.querySelectorAll?.('[placeholder],[title],[aria-label],[alt]').forEach(translateAttributes);
    }
    function setLanguage(value) { language = ['fa', 'en', 'ar'].includes(value) ? value : 'fa'; }
    return { setLanguage, getLanguage: () => language, translateText, translate, translateNode, translateAttributes, localizeDigits };
});
