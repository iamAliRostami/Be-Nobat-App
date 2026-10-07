# Web localization

The web interface supports Persian (`fa`), English (`en`) and Arabic (`ar`).
`UiLocale` captures the validated `benobat-language` cookie for each request or
Blazor circuit, so server-rendered dates use the selected calendar. The browser
stores the same preference and writes its cookie before reloading after a language
change. Older localStorage preferences are migrated on the first request.

The reviewed source resources are:

- `src/BeNobat.Web/Resources/WebTranslations.tsv`: Persian source, English and
  Arabic columns, including labels, help, placeholders, accessibility text,
  validation, identity errors and empty/loading states.
- `src/BeNobat.Web/Resources/WebTemplates.json`: complete sentences with numbered
  placeholders. Argument types distinguish numbers, UI labels and user data.

Generate the three JSON resources and browser catalog with:

```sh
python tools/update-web-localization.py
```

The runtime matches complete resources and complete typed templates. It does not
replace arbitrary recognized words inside unknown text. Business names, service
names, people, addresses, descriptions, customer notes and reviews have
`data-i18n-skip` markers. Form values, URLs, email addresses, tracking codes and
password arguments are preserved. Keep those markers when refactoring a view;
wrap user data separately when a sentence also contains a localizable UI label.
Known category labels may be translated for display without changing their
submitted values or search URLs.

Text nodes and `placeholder`, `title`, `aria-label` and `alt` attributes track their
latest Blazor source and the localizer's last write. Dynamic updates are translated
again without restoring an old value. The resource generator checks that each
language contains exactly the same placeholders as the Persian template.

Run the dependency-free resource and regression checks:

```sh
python tools/update-web-localization.py --check
node --test tests/localization/web-localization.test.cjs
```

The static coverage test detects newly added Razor labels, attributes and Identity
validation messages without English/Arabic resources. Add a reviewed translation
rather than weakening its missing-resource assertions. Add templates for new
interpolated sentences and protect data arguments.

A real Chromium regression checks newly rendered elements, updated text and aria
attributes, all three language switches, counts and preservation of user data:

```sh
node tools/check-web-localization-browser.cjs
```

This command needs Playwright and its Chromium browser. An existing cloud browser
installation can be selected with `PLAYWRIGHT_PACKAGE_PATH` and
`CHROMIUM_EXECUTABLE`; the test serves its own fixture and does not require a live
backend or credentials.
