# Engineering audit and native Android delivery

This audit covers the existing business/branch/service/resource/membership graph,
administrative and customer permissions, schedules and booking, cancellation and
history, public reviews and private customer evaluations, account editing,
localization, dark appearance and a native Android client using the same server.
It fixes concrete failures and adds behavioral regression tests; it does not claim
that every possible production condition has been tested.

## Booking and availability

`Application/AppointmentBookingService.cs` is shared by the web UI and mobile API.
It reloads current business, branch, service assignments, qualified providers,
customer/session state, price, currency and duration before writing. Selected
times must belong to the generated branch-local slots. Changed displayed terms
require refreshing rather than silently charging different terms.

PostgreSQL transaction locks serialize overlapping bookings for both provider and
customer, including bookings through different providers or branches. Customer
caps, appointment conflicts and the selected provider's availability are checked
inside the transaction. Domain tests cover adjacent/overlapping windows, blackout
rules, overnight/time-zone boundaries, daylight-saving gaps and repeated times,
advance limits and calendar range edges.

Cancellation uses a conditional database update and preserves history. Reviews
require ownership of a completed appointment, enforce uniqueness and remain
private until moderation. Managers' evaluations of customers never enter the
public review feed. Archived business/branch/service/provider labels remain
available in historical appointments while current membership still controls
administrative access.

Booking confirmation pre-fills the fresh account phone and preserves edits when
navigating steps. A note needed across login is stored only in the browser tab's
session storage, rather than in the URL. Loading generations prevent old slot
requests overwriting new selections.

## Administration and account integrity

See [`06_AdministrationAudit.md`](06_AdministrationAudit.md) for access boundaries.
Identity roles grant entry; current database membership grants authority within a
particular business and branch. Revoked memberships, disabled accounts and changed
security stamps cannot retain administrative write access. Managers cannot change
global account identity or suspend shared customers. Global grants preserve
membership-derived roles. Last-owner and last-administrator protections and role
synchronization run in transactions.

Startup seeds roles for a newly provisioned administrator without promoting an
existing account or undoing a reviewed role revocation. Existing catalog entries
retain reviewed names, categories, durations, publication and archival. Category
defaults initialize an empty kind once, so renamed, disabled and archived defaults
remain changed after a restart. PostgreSQL regressions exercise repeated startup.

Branches and services retain their original business. Cross-business assignments
and mismatched schedules are rejected by management operations and hidden from
the live query graph. Pausing a parent hides its descendants; restoring it does
not restore independently archived children. Restoring memberships reuses their
unique branch/account row.

The API also returns inherited business-wide hours to current authorized branch
members, including when a specific authorized branch is selected. Those rows are
read-only for non-platform actors; changing them remains a platform operation.

The generic EF soft-delete filter now uses a typed nullable timestamp constant.
With an untyped null, combining it with an explicit `DeletedAt == null` predicate
could generate `WHERE FALSE`, hiding live businesses from management. Regression
tests assert both generated SQL and PostgreSQL behavior.

Profile mutations reload Identity state, validate trimmed names and normalized
Iranian phone numbers, and check uploaded image signatures and size. Account
credential changes rotate security stamps and refresh the successful web session.
The mobile session uses protected opaque tokens, a persisted session hash and
fresh account/role validation on every request. Logout revokes the specific token.

## Appearance and localization

Dark appearance retains the existing green/mint palette. Text, secondary labels,
form borders, placeholders, focus rings and semantic action colors have stronger
contrast. Active sidebar items and mint buttons use dark foregrounds. Dark cards,
account navigation and form backgrounds use the same surface variables. Favorite
cover gradients and the compact independent profile columns remain intact.

Chromium checks covered 21 routes and 8 creation dialogs, desktop and 390-pixel
mobile layouts. The solid-background text check covered 17,014 rendered text
elements in the first pass and 17,716 in the final pass: no page errors, overflow
or contrast failures were observed. It uses
WCAG text thresholds of 4.5:1, or 3:1 for large text; gradients, partially opaque
elements, disabled controls and non-text contrast are outside that measurement.
The dashboard and profile screenshots were also inspected visually.

There are 833 reviewed web messages and 58 typed sentence templates for Persian,
English and Arabic. The selected language cookie supplies server calendar output;
client updates translate exact interface resources and preserve user-authored
names, notes, descriptions, passwords and input values. The native Android app has
matching localized resources and RTL support. See
[`localization.md`](localization.md) for generation and coverage checks.

## Reproduce validation

Use .NET SDK 10 and an initialized disposable PostgreSQL database. Integration
fixtures roll back their rows or remove only their own identifiers. Without
`BENOBAT_TEST_DB`, database cases are explicitly skipped; a passing unit-only run
does not establish database behavior.

```sh
dotnet restore BeNobat.slnx
BENOBAT_TEST_DB='<disposable PostgreSQL connection>' dotnet test BeNobat.slnx
python3 tools/update-web-localization.py --check
node --test tests/localization/web-localization.test.cjs
```

In the cloud workspace, use `--artifacts-path /workspace/setup/artifacts` on
restore/build/test/publish to avoid modifying the repository's tracked build
outputs. `scripts/cloud-setup.sh` installs verified tool versions and runs the
web/resource/native checks. Live database tests still need `BENOBAT_TEST_DB`.

Start the updated server and run the HTTP suite with a development platform
administrator:

```sh
API_SMOKE_URL=http://127.0.0.1:8080 \
API_SMOKE_EMAIL='<development administrator>' \
API_SMOKE_PASSWORD='<development password>' \
python3 scripts/test-mobile-api.py
```

The suite creates uniquely named synthetic records and disables its accounts and
business after checking them. `API_SMOKE_DB_CONTAINER=benobat-public-review`
optionally enables the bounded historical review fixture for the cloud's
disposable database. Do not set it against a production database.

The browser booking check needs Playwright/Chromium and a bookable development
fixture with free slots. It registers one synthetic customer, books and cancels
one appointment, and retains that cancelled appointment for inspection:

```sh
WEB_SMOKE_URL=http://127.0.0.1:8080 \
WEB_SMOKE_BOOKING_PATH='/book/<business UUID>?branch=<branch UUID>&service=<service UUID>&provider=<resource UUID>' \
node scripts/test-web-booking.cjs
node tools/check-web-localization-browser.cjs
```

`PLAYWRIGHT_PACKAGE_PATH` and `CHROMIUM_EXECUTABLE` may select an existing browser
installation. Resource and browser regressions exercise language switching,
dynamic text/attributes and preservation of user data.

For Android, use JDK 17 and SDK 35:

```sh
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease :app:lintVitalRelease
```

The eleven JVM tests cover booking inputs and real mock HTTP request/response behavior.
Device instrumentation additionally checks native screens, Keystore and live API
flows; its execution requires a working emulator or physical device. Building
an APK or passing JVM tests alone is not a touchscreen test.

Final execution passed all 301 .NET cases with the disposable PostgreSQL connection
enabled and zero skips. The HTTP suites exercise 84 customer/management operations,
28 account operations and 9 isolated-client rate-limit checks. The real web browser
flow passed registration, phone normalization/prefill, provider slots, mandatory
terms, booking/tracking/note persistence, cancellation/history, email/password
changes, cookie refresh and a fresh login. Release publish also completed.

Strict real-page scans in English and Arabic covered 88 page/dialog snapshots,
including public details, booking/terms and invalid authentication forms, with no
untranslated interface text or browser errors. User-authored names/descriptions
are preserved rather than translated as interface text.

Android debug and minified release builds passed all eleven JVM cases per variant.
Lint reported zero errors and 63 warnings, mostly unused resources retained from
the upload. Device Keystore and locale cases passed in the earlier software
emulator run. Full native-screen and live API instrumentation remains unverified:
the emulator's Android framework reported process-startup ANRs/system-process
failures under high CPU pressure, and the runner exited before the live cases.
No application Java exception was observed in that bounded attempt. These device
failures are not counted as passing UI tests; run the checked-in instrumentation
on a working emulator or device before production release.

## Delivery and practical limits

The native Android source, wrapper, three-language resources, tests and build
instructions are in [`../android/README.md`](../android/README.md). Customers can
discover, favorite, book, track, cancel and review. Authorized staff/managers and
platform administrators have native management screens backed by scoped API
operations. There is no WebView or direct PocketBase dependency.

The installable debug APK is for review. Release builds are minified, non-debuggable
and require an HTTPS server; signing keys and the production URL are supplied
outside source control. An unsigned release build is not a store publication.
The mobile app requires deployment of this updated API server, not an older web
server without `/api/v1`. Drafts survive activity recreation but unfinished booking
drafts are not retained after process termination.

Production schema startup still uses the existing `EnsureCreated` and compatibility
upgrade workflow. Versioned EF migration rollout, production load/capacity testing,
payment integration and store publication have not been introduced or claimed by
this audit.
