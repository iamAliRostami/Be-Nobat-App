# Native Be Nobat Android app

This is a Kotlin and Material Android application, using the uploaded brand palette, Vazir font, icons, Android resources, and native views. It calls the Be Nobat web server's `/api/v1` JSON API. It contains no WebView and makes no direct PocketBase requests.

## Build and run

Use a complete JDK 17, Android SDK platform 35, and the checked-in Gradle 8.9 wrapper. Android Studio can open this `android/` directory directly. Set `ANDROID_HOME` or an ignored `local.properties` containing `sdk.dir=/absolute/path/to/android-sdk`.

```bash
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Install it on Android 8.0 or later:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Start the web server and PostgreSQL using the repository's main setup instructions. The debug app defaults to `http://10.0.2.2:8080`, Android Emulator's route to the host. For a USB device, use `adb reverse tcp:8080 tcp:8080` and set the app's server URL to `http://127.0.0.1:8080` under Settings. HTTPS server URLs work on both emulator and physical devices. Debug HTTP is limited to `10.0.2.2`, `localhost`, and `127.0.0.1`; arbitrary cleartext hosts are rejected.

In this cloud workspace the verified tools are under `/workspace/tools`. Use:

```bash
export JAVA_HOME=/workspace/tools/jdk17
export ANDROID_HOME=/workspace/tools/android-sdk
export ANDROID_USER_HOME=/workspace/tools/android-user
export GRADLE_USER_HOME=/workspace/tools/gradle-home
export JAVA_TOOL_OPTIONS='-Duser.home=/workspace/tools/java-home -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts'
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
cd /workspace/Be-Nobat-App/android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

## Native workflows

- Discover businesses with name/service search, city/category filters, name/rating/city sorting, paging, business details, branches, service prices, published reviews, and server-persisted favorites.
- Register and sign in by email; manage name, Iranian mobile number, profile photo, email, and password. Security changes replace the issued session token. Sign out revokes the server session and clears local credentials.
- Book a branch with multiple services, a named provider or any provider, and a day. Availability comes from live slots in the branch's time zone. Booking includes a contact number, note, terms acceptance, displayed price/duration check, tracking code, and approval status. A conflicting or changed slot is refreshed from the server.
- View and filter personal appointments, cancel eligible bookings, and review completed appointments. The server returns `canCancel` and `canReview`.
- Staff see their authorized appointment calendar and status actions. Managers and owners also see business, branch, service, resource, membership, availability, assignments, review moderation/replies, and private customer rating screens. Inherited business-wide availability rows are read-only for branch managers and owners; platform administrators can edit them. Memberships find accounts by exact email and select named accounts. The server filters all records by current membership and validates every write.
- Platform administrators can create and manage businesses, categories, service catalog items, users, roles, active states, and temporary passwords. Generated passwords appear once in a protected dialog and are not saved in app preferences.
- Persian, English, and Arabic resources cover interface labels, forms, validation messages, empty/loading states, and menu actions. Persian and Arabic support RTL; light, dark, and system appearance are persisted.

## Release configuration and security

Set `BENOBAT_API_BASE_URL=https://your-server.example` when building release. An unset release URL leaves the app waiting for an HTTPS URL in Settings. Release builds reject HTTP and are not debuggable. There are no embedded signing passwords or key paths. Signing is optional and supplied through `BENOBAT_KEYSTORE_PATH`, `BENOBAT_KEYSTORE_PASSWORD`, `BENOBAT_KEY_ALIAS`, and `BENOBAT_KEY_PASSWORD` outside the repository. Do not commit a keystore or secret configuration.

```bash
BENOBAT_API_BASE_URL=https://your-server.example ./gradlew :app:assembleRelease
```

Tokens and the user session are AES-GCM encrypted with a non-exportable Android Keystore key before being written to private preferences. App backup is disabled. Password fields are not saved as instance state; passwords are never put in ViewModel drafts or preferences. TLS uses Android's system trust anchors. Redirects are not followed by the API transport, preventing Bearer tokens from being forwarded to a different host. Changing servers clears the session.

## API and validation

The shared contract is documented in [`../docs/mobile-api.md`](../docs/mobile-api.md). Requests use camelCase JSON, Bearer sessions, UUID identifiers, ISO dates, and ISO instants. Business data and bookings persist on the server, and preferences/session persist on the device. Form drafts survive activity recreation through the lifecycle ViewModel; unfinished booking drafts are not persisted after process termination.

Unit tests verify decimal totals, Iranian phone normalization, server password rules, dates, release/debug URL restrictions, HTTP query encoding, Bearer transport, multi-service request serialization, conflict codes, and redirect handling. Instrumentation checks native screen creation, localized resources, and Keystore round trips. A device or emulator and a running API are required for full end-to-end UI testing; the JVM suite alone does not demonstrate touchscreen behavior.

The final verification built both APK variants and the instrumentation APK, passed all 11 JVM tests in both Debug and Release, and completed `lintDebug` and `lintRelease` with zero errors. The 63 lint warnings are mostly unused graphics/resources retained from the uploaded source (54 warnings), plus compatibility and style suggestions. Reproduce the full build checks with:

```bash
./gradlew :app:assembleDebug :app:assembleRelease \
  :app:testDebugUnitTest :app:testReleaseUnitTest \
  :app:lintDebug :app:lintRelease :app:assembleDebugAndroidTest
```

The cloud emulator successfully installed the APKs. Keystore and three-language resource instrumentation checks passed in an earlier run. The native screen and live API UI flows remain unverified on a device: this software-only emulator subsequently reported startup ANRs and `Process crashed` before completing the screen tests, with saturated emulator CPU and prior Android system-process crashes. The successful build and JVM results do not establish that these device flows passed. Run them on a physical device or a hardware-accelerated emulator:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.leon.be_nobat.NativeSmokeTest
```

`LiveApiFlowTest` also accepts `serverUrl`, `customerEmail`, `customerPassword`, `adminEmail`, `adminPassword`, `businessId`, `branchId`, and `serviceId` as instrumentation arguments. Pass these as `-Pandroid.testInstrumentationRunnerArguments.<name>=<value>` against a disposable development API with bookable fixture data. Those tests create a synthetic account and book and cancel one synthetic appointment; never supply production credentials or commit test passwords.

The app requires the new API server from this checkout. It cannot use an old server exposing only cookie-authenticated web pages. Booking and role checks remain authoritative on the server; loading errors offer a translated message and retry. Business descriptions and user-authored content retain their stored language.
