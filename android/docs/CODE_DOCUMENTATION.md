# Code Documentation / مستندات کد

[English](#english) · [فارسی](#فارسی) · [Change log / تاریخچه تغییرات](#documentation-change-log--تاریخچه-تغییرات-مستندات)

> **Maintenance rule / قانون نگهداری:** Every code change must update this document and the
> relevant root README in the same commit. / هر تغییر کد باید در همان کامیت، این سند و README اصلی
> مرتبط را نیز به‌روزرسانی کند.

This document describes the responsibility of every current production class and its explicit
functions under `app/src/main/java`. Compiler-generated data-class functions are not listed.

The login presentation is a responsive, scroll-safe Material card with localized RTL/LTR copy,
keyboard submission, accessible theme/language controls, and the custom Be Nobat BN monogram.

این سند مسئولیت تمام کلاس‌های فعلی کد اصلی و توابع صریح آن‌ها در مسیر `app/src/main/java` را شرح
می‌دهد. توابعی که Kotlin به‌صورت خودکار برای data classها تولید می‌کند فهرست نشده‌اند.

نمای ورود یک کارت متریال واکنش‌گرا و قابل اسکرول با متن بومی‌سازی‌شده راست‌به‌چپ/چپ‌به‌راست،
ارسال از صفحه‌کلید، کنترل‌های دسترس‌پذیر پوسته و زبان، و مونوگرام اختصاصی BN «به نوبت» است.

---

## English

### Architecture and request flow

```text
LoginActivity
    -> AuthViewModel.login()
        -> LoginUseCase.invoke()
            -> AuthRepository.login()
                -> AuthRepositoryImpl
                    -> PocketBaseClient.authWithPassword()
                    -> TokenManager.save()
```

- **Presentation:** Activities render Android resources and observe ViewModels.
- **Domain:** Use cases, repository contracts, identifiers, errors, and business models.
- **Data:** Repository implementations, PocketBase transport, encrypted local preferences.
- **DI:** Koin owns shared storage, HTTP, repository, use-case, and ViewModel instances.

### Application and shared UI

#### `App`

Android `Application` entry point. It exposes logging tags and `DEFAULT_LANGUAGE_TAG`, applies
Persian only when no application locale has been selected, starts Koin with all modules, loads the
saved theme from `ThemeManager`, and applies it before normal UI usage.

| Function | Responsibility |
|---|---|
| `onCreate()` | Selects Persian on first launch without overwriting a saved locale, starts Koin, reads the persisted theme, and applies night mode. |

#### `BaseActivity`

Base class for screens that share edge-to-edge handling, a toolbar, theme switching, menu actions,
and a content container.

| Function | Responsibility |
|---|---|
| `onCreate()` | Inflates the base layout and child layout, applies system-bar insets, initializes the toolbar, then calls screen hooks. |
| `setupBaseToolbar()` | Connects toolbar navigation to the back dispatcher. |
| `onCreateOptionsMenu()` | Inflates and controls visibility of shared toolbar actions. |
| `onOptionsItemSelected()` | Dispatches selected toolbar actions to overridable callbacks. |
| `setupThemeToggle()` | Detects the current night mode and delegates the change to `MainViewModel`. |
| `hideToolbar()` | Hides the shared toolbar. |
| `setupViews()` | Abstract hook for view initialization in each screen. |
| `observeViewModel()` | Abstract hook for lifecycle-aware state observation. |
| `onSearchClicked()` / `onFilterClicked()` / `onSortClicked()` / `onRefreshClicked()` | Optional hooks for shared menu actions. |
| `setHorizontalButtonsVisibility()` | Shows or hides the shared action items and refreshes the menu. |
| `setMenuVisibility()` | Enables or disables the shared options menu. |
| `showPopupMenu()` | Displays a menu resource anchored to a view and returns the selected item ID. |
| `setToolbarTitle(String)` | Sets a localized toolbar title and enables back navigation. |
| `setToolbarTitle(Boolean)` | Hides the toolbar or initializes its navigation behavior. |

### Dependency injection (`AppModule.kt`)

| Module | Responsibility |
|---|---|
| `localStorageModule` | Provides one Preferences DataStore, `TokenManager`, and `ThemeManager`. |
| `repositoryModule` | Binds `AuthRepository` to `AuthRepositoryImpl` and creates `LoginUseCase`. |
| `viewModelModule` | Creates `AuthViewModel` through Koin's ViewModel DSL. |
| `networkModule` | Provides shared JSON and Ktor clients, timeouts, safe debug logging, and `PocketBaseClient`. |
| Ktor `Logger.log()` | Sends request/response metadata to Logcat with `App.API_TAG`; secrets and bodies are not logged. |

### Local data and security

#### `ICryptoManager` and `CryptoManager`

`ICryptoManager` defines encryption/decryption operations. `CryptoManager` implements them with a
key stored in Android Keystore and Base64 transport encoding.

| Function | Responsibility |
|---|---|
| `getKey()` | Reads the secret key from Android Keystore or creates it when missing. |
| `createKey()` | Generates and stores the AES key under the application alias. |
| `encrypt()` | Encrypts plain text, prefixes the IV, and Base64-encodes the result. |
| `decrypt()` | Decodes the stored payload, extracts IV/ciphertext, and returns decrypted text. |

#### `TokenManager`

Stores the authentication token as encrypted Preferences DataStore data.

| Function/property | Responsibility |
|---|---|
| `userToken` | Emits the decrypted token or `null` when no usable token exists. |
| `save()` | Encrypts and persists a token. |
| `deleteToken()` | Removes the saved token (logout storage step). |

#### `ThemeManager`

| Function/property | Responsibility |
|---|---|
| `themeMode` | Emits the saved AppCompat night mode, defaulting to the system mode. |
| `save()` | Persists the selected night mode. |
| `clearTheme()` | Removes the override and restores default behavior. |

### Remote API

#### `PocketBaseConfig`

Builds a normalized base URL, login path, and final login URL from BuildConfig values.

#### `PocketBaseException`, `PbListResponse<T>`, `PbAuthResponse`

- `PocketBaseException` carries a failed HTTP status and response message inside the data layer.
- `PbListResponse<T>` models PocketBase pagination metadata and items.
- `PbAuthResponse` models the authentication token and returned user record.

#### `PocketBaseClient`

Single PocketBase HTTP gateway. Every operation returns `Result` so transport/HTTP failures travel
to repositories without crashing callers.

| Function | Responsibility |
|---|---|
| `currentToken()` | Obtains the latest token from the injected suspend provider. |
| `HttpRequestBuilder.auth()` | Adds a Bearer header only when a token exists. |
| `HttpResponse.ensureSuccess()` | Accepts 2xx responses; otherwise throws `PocketBaseException`. |
| `list()` | Requests a paginated collection with optional filter, sort, and expand parameters. |
| `get()` | Fetches one collection record by ID. |
| `create()` | POSTs a JSON record and decodes the created model. |
| `update()` | PATCHes a record and decodes the updated model. |
| `delete()` | Deletes a record and returns successful `Unit`. |
| `authWithPassword()` | Sends email/mobile identity and password to the configured login endpoint. |

### Authentication domain and repository

#### `LoginIdentifier`

Sealed identifier with `Email` and `Mobile` variants.

| Function | Responsibility |
|---|---|
| `parse()` | Trims input, validates email or Iranian mobile format, lowercases email, and returns a typed identifier. |
| `normalizeIranianMobile()` | Converts supported `0`, `98`, `0098`, and `+98` prefixes to canonical `+98`. |

#### `AuthException`

Language-independent domain errors: invalid identifier, empty password, invalid credentials, rate
limit, unavailable service/network, timeout, and unexpected failure. The UI maps each type to a
localized string.

#### `AuthRepository` and `AuthRepositoryImpl`

| Function | Responsibility |
|---|---|
| `AuthRepository.login()` | Domain contract for login without exposing HTTP details. |
| `AuthRepositoryImpl.login()` | Calls PocketBase, stores the successful token, returns the user, and maps failures. |
| `Throwable.toAuthException()` | Converts timeout, I/O, and HTTP statuses into safe domain error types. |

#### `LoginUseCase`

| Function | Responsibility |
|---|---|
| `invoke()` | Parses/normalizes identity, rejects blank passwords, then calls `AuthRepository.login()`. |

### Presentation

#### `LoginUiState` and `AuthViewModel`

`LoginUiState` represents `Idle`, `Loading`, `Success`, and `Error` rendering states.

| Function | Responsibility |
|---|---|
| `AuthViewModel.login()` | Prevents duplicate loading calls, executes `LoginUseCase`, and publishes success/error state. |

#### `LoginActivity`

| Function | Responsibility |
|---|---|
| `setupViews()` | Registers theme, guest, login, and language click listeners. |
| `observeViewModel()` | Collects login state only while the Activity is started. |
| `onClick()` | Dispatches theme, language, guest, and login actions. |
| `renderLoginState()` | Updates loading UI, renders localized errors, and navigates after success. |
| `clearInputErrors()` | Clears stale identity/password errors before another submission. |
| `showLanguageMenu()` | Shows EN/FA/AR options and applies the selected AppCompat locale. |
| `Throwable.messageResource()` | Maps domain failures to localized Android string resources. |

#### Other Activities and ViewModels

| Class/function | Responsibility |
|---|---|
| `StartActivity.onCreate()` | Keeps the splash visible while reading the token, then routes to login or home. |
| `HomeActivity.setupViews()` | Configures the localized home toolbar and menu visibility. |
| `HomeActivity.observeViewModel()` | Reserved state-observation hook for the home screen. |
| `ExampleActivity.setupViews()` | Configures the example toolbar and shared menu actions. |
| `ExampleActivity.observeViewModel()` | Reserved state-observation hook for the example screen. |
| `MainViewModel.toggleTheme()` | Persists and applies the opposite light/dark mode. |

### Serializable models

All models below map PocketBase records through Kotlin Serialization. Models extending
`BaseDomain` also inherit `id`, `created`, and `updated`.

| Class | Record represented |
|---|---|
| `BaseDomain` | Shared PocketBase record metadata. |
| `PocketBaseResponse<T>` | Generic paginated response (legacy domain shape). |
| `QueueRecord` | Minimal queue item containing ID and title. |
| `User` / `UserProfile` | Authentication user and extended profile. |
| `Businesse` / `Branche` | Business and its physical branch. |
| `BranchMembership` | User membership, roles, and status in a branch. |
| `Appointment` | Booking times, client, pricing, status, and notes. |
| `AppointmentService` | A service line attached to an appointment. |
| `Service` / `ServiceCategory` | Service catalog item and hierarchical category. |
| `BranchService` | Branch-specific service price/duration/approval configuration. |
| `ServiceAssignment` | Connection between a branch service and assigned resource. |
| `Resource` / `ResourceType` | Bookable staff/equipment and its type. |
| `ResourceAssignment` | Assignment of a resource to a branch and date range. |
| `ResourceAvailability` | Weekly open/close schedule for an assignment. |
| `ResourceException` | Temporary availability override or closure. |
| `Discount` | Discount rule, validity, usage limits, and applicable services. |
| `Favorite` | User favorite relation to a business/resource. |
| `Review` | Rating/comment connected to an appointment service. |
| `Notification` | User notification content, reference, state, and send time. |
| `ReputationEvent` | Reputation score event and its related actors/entities. |
| `Role` / `Permission` / `RolePermission` | Authorization role, capability, and their relation. |
| `UserSystemRole` | Role assignment to a user, including issuer and expiry. |

---

## فارسی

### معماری و مسیر درخواست

```text
LoginActivity
    -> AuthViewModel.login()
        -> LoginUseCase.invoke()
            -> AuthRepository.login()
                -> AuthRepositoryImpl
                    -> PocketBaseClient.authWithPassword()
                    -> TokenManager.save()
```

- **نمایش:** Activityها منابع اندروید را نمایش می‌دهند و ViewModelها را مشاهده می‌کنند.
- **دامنه:** شامل Use Case، قرارداد Repository، شناسه ورود، خطاها و مدل‌های کسب‌وکار است.
- **داده:** پیاده‌سازی Repository، ارتباط PocketBase و Preferences رمزنگاری‌شده را دربر می‌گیرد.
- **تزریق وابستگی:** Koin چرخه عمر Storage، HTTP، Repository، Use Case و ViewModel را مدیریت می‌کند.

### برنامه و رابط مشترک

#### `App`

نقطه شروع Application اندروید است؛ Tagهای لاگ و `DEFAULT_LANGUAGE_TAG` را ارائه می‌کند، فقط در نبود
زبان انتخاب‌شده فارسی را اعمال می‌کند، Koin را راه‌اندازی می‌کند و تم ذخیره‌شده را بارگذاری می‌کند.

| تابع | عملکرد |
|---|---|
| `onCreate()` | بدون بازنویسی زبان ذخیره‌شده، در اولین اجرا فارسی را انتخاب و سپس Koin و تم را راه‌اندازی می‌کند. |

#### `BaseActivity`

کلاس پایه صفحه‌ها برای مدیریت edge-to-edge، Toolbar، تم، منوی مشترک و container محتوا است.

| تابع | عملکرد |
|---|---|
| `onCreate()` | layout پایه و صفحه فرزند را می‌سازد، insetها و Toolbar را تنظیم و hookهای صفحه را اجرا می‌کند. |
| `setupBaseToolbar()` | دکمه بازگشت Toolbar را به back dispatcher متصل می‌کند. |
| `onCreateOptionsMenu()` | منوی مشترک را ساخته و وضعیت نمایش actionها را اعمال می‌کند. |
| `onOptionsItemSelected()` | action انتخاب‌شده را به callback مربوط هدایت می‌کند. |
| `setupThemeToggle()` | حالت شب فعلی را تشخیص داده و تغییر تم را به `MainViewModel` می‌سپارد. |
| `hideToolbar()` | Toolbar مشترک را مخفی می‌کند. |
| `setupViews()` | hook اجباری راه‌اندازی Viewهای هر صفحه است. |
| `observeViewModel()` | hook اجباری مشاهده lifecycle-aware وضعیت صفحه است. |
| `onSearchClicked()` / `onFilterClicked()` / `onSortClicked()` / `onRefreshClicked()` | hookهای اختیاری actionهای منو هستند. |
| `setHorizontalButtonsVisibility()` | نمایش actionهای افقی را تغییر داده و منو را refresh می‌کند. |
| `setMenuVisibility()` | منوی option مشترک را فعال یا غیرفعال می‌کند. |
| `showPopupMenu()` | منوی مشخص‌شده را کنار یک View نمایش داده و ID گزینه را تحویل می‌دهد. |
| `setToolbarTitle(String)` | عنوان ترجمه‌شده Toolbar را تنظیم و بازگشت را فعال می‌کند. |
| `setToolbarTitle(Boolean)` | Toolbar را مخفی یا navigation آن را راه‌اندازی می‌کند. |

### تزریق وابستگی (`AppModule.kt`)

| ماژول | عملکرد |
|---|---|
| `localStorageModule` | DataStore مشترک، `TokenManager` و `ThemeManager` را می‌سازد. |
| `repositoryModule` | قرارداد `AuthRepository` را به پیاده‌سازی متصل و `LoginUseCase` را ایجاد می‌کند. |
| `viewModelModule` | `AuthViewModel` را با DSL مخصوص Koin می‌سازد. |
| `networkModule` | JSON، کلاینت Ktor، timeout، لاگ امن Debug و `PocketBaseClient` را فراهم می‌کند. |
| `Logger.log()` | اطلاعات request/response را بدون body و اطلاعات حساس با `App.API_TAG` به Logcat می‌فرستد. |

### داده محلی و امنیت

#### `ICryptoManager` و `CryptoManager`

قرارداد و پیاده‌سازی رمزنگاری هستند و کلید را در Android Keystore نگه می‌دارند.

| تابع | عملکرد |
|---|---|
| `getKey()` | کلید را از Keystore می‌خواند یا در صورت نبودن ایجاد می‌کند. |
| `createKey()` | کلید AES را با alias برنامه تولید و ذخیره می‌کند. |
| `encrypt()` | متن را رمزنگاری، IV را به آن اضافه و نتیجه را Base64 می‌کند. |
| `decrypt()` | payload را decode، IV و ciphertext را جدا و متن اصلی را برمی‌گرداند. |

#### `TokenManager`

| تابع/ویژگی | عملکرد |
|---|---|
| `userToken` | توکن رمزگشایی‌شده یا در نبود توکن معتبر `null` منتشر می‌کند. |
| `save()` | توکن را رمزنگاری و در DataStore ذخیره می‌کند. |
| `deleteToken()` | توکن ذخیره‌شده را حذف می‌کند. |

#### `ThemeManager`

| تابع/ویژگی | عملکرد |
|---|---|
| `themeMode` | تم ذخیره‌شده یا حالت پیش‌فرض سیستم را منتشر می‌کند. |
| `save()` | حالت شب انتخاب‌شده را ذخیره می‌کند. |
| `clearTheme()` | تنظیم ذخیره‌شده را حذف می‌کند. |

### API و PocketBase

- `PocketBaseConfig` آدرس پایه، مسیر ورود و URL نهایی را از BuildConfig می‌سازد.
- `PocketBaseException` وضعیت HTTP ناموفق را در لایه داده حمل می‌کند.
- `PbListResponse<T>` پاسخ صفحه‌بندی‌شده و `PbAuthResponse` توکن و کاربر ورود را مدل می‌کنند.
- `PocketBaseClient` درگاه واحد تمام درخواست‌های PocketBase است.

| تابع `PocketBaseClient` | عملکرد |
|---|---|
| `currentToken()` | آخرین توکن را از provider تزریق‌شده می‌خواند. |
| `auth()` | در صورت وجود توکن، Bearer header اضافه می‌کند. |
| `ensureSuccess()` | پاسخ 2xx را می‌پذیرد و برای سایر وضعیت‌ها exception ایجاد می‌کند. |
| `list()` | لیست صفحه‌بندی‌شده را با filter، sort و expand اختیاری می‌گیرد. |
| `get()` | یک رکورد را با ID دریافت می‌کند. |
| `create()` | رکورد JSON را با POST ایجاد می‌کند. |
| `update()` | رکورد را با PATCH ویرایش می‌کند. |
| `delete()` | رکورد را حذف می‌کند. |
| `authWithPassword()` | ایمیل/موبایل و گذرواژه را به endpoint ورود ارسال می‌کند. |

### ورود و دامنه

| کلاس/تابع | عملکرد |
|---|---|
| `LoginIdentifier.Email` / `Mobile` | نوع شناسه معتبر ورود را مشخص می‌کنند. |
| `LoginIdentifier.parse()` | ایمیل یا موبایل را trim، اعتبارسنجی و normalize می‌کند. |
| `normalizeIranianMobile()` | پیش‌شماره‌های پشتیبانی‌شده را به قالب `+98` تبدیل می‌کند. |
| `AuthException` | خطاهای مستقل از زبان برای اعتبارسنجی، HTTP و شبکه را تعریف می‌کند. |
| `AuthRepository.login()` | قرارداد ورود بدون وابستگی به جزئیات HTTP است. |
| `AuthRepositoryImpl.login()` | API را صدا می‌زند، توکن موفق را ذخیره و خطاها را تبدیل می‌کند. |
| `Throwable.toAuthException()` | timeout، I/O و statusهای HTTP را به خطای امن دامنه تبدیل می‌کند. |
| `LoginUseCase.invoke()` | شناسه و گذرواژه را اعتبارسنجی و Repository را فراخوانی می‌کند. |

### لایه نمایش

| کلاس/تابع | عملکرد |
|---|---|
| `LoginUiState` | وضعیت‌های Idle، Loading، Success و Error صفحه ورود است. |
| `AuthViewModel.login()` | از درخواست تکراری جلوگیری، Use Case را اجرا و وضعیت UI را منتشر می‌کند. |
| `LoginActivity.setupViews()` | listenerهای تم، مهمان، ورود و زبان را ثبت می‌کند. |
| `observeViewModel()` | وضعیت ورود را فقط در حالت STARTED جمع‌آوری می‌کند. |
| `onClick()` | رویدادهای دکمه‌های صفحه را هدایت می‌کند. |
| `renderLoginState()` | loading، خطای ترجمه‌شده و navigation موفق را نمایش می‌دهد. |
| `clearInputErrors()` | خطاهای قدیمی فیلدها را پاک می‌کند. |
| `showLanguageMenu()` | منوی فارسی/انگلیسی/عربی را نمایش و locale را اعمال می‌کند. |
| `messageResource()` | نوع خطای دامنه را به string resource زبان فعال تبدیل می‌کند. |
| `StartActivity.onCreate()` | توکن را هنگام Splash می‌خواند و کاربر را به ورود یا خانه می‌فرستد. |
| `HomeActivity.setupViews()` | Toolbar ترجمه‌شده صفحه خانه را تنظیم می‌کند. |
| `HomeActivity.observeViewModel()` | hook رزروشده برای وضعیت آینده صفحه خانه است. |
| `ExampleActivity.setupViews()` | Toolbar و منوی صفحه نمونه را تنظیم می‌کند. |
| `ExampleActivity.observeViewModel()` | hook رزروشده برای وضعیت آینده صفحه نمونه است. |
| `MainViewModel.toggleTheme()` | تم روشن/تیره مخالف را ذخیره و اعمال می‌کند. |

### مدل‌های قابل Serialization

تمام مدل‌های زیر رکوردهای PocketBase هستند. فرزندان `BaseDomain` فیلدهای `id`، `created` و
`updated` را نیز دارند.

| کلاس | کاربرد |
|---|---|
| `BaseDomain` | اطلاعات مشترک رکورد PocketBase. |
| `PocketBaseResponse<T>` | شکل عمومی پاسخ صفحه‌بندی‌شده دامنه. |
| `QueueRecord` | آیتم ساده صف با ID و عنوان. |
| `User` / `UserProfile` | کاربر ورود و اطلاعات تکمیلی پروفایل. |
| `Businesse` / `Branche` | کسب‌وکار و شعبه فیزیکی آن. |
| `BranchMembership` | عضویت، نقش‌ها و وضعیت کاربر در شعبه. |
| `Appointment` | زمان، مشتری، مبلغ، وضعیت و توضیحات نوبت. |
| `AppointmentService` | خدمت متصل‌شده به یک نوبت. |
| `Service` / `ServiceCategory` | خدمت و دسته‌بندی سلسله‌مراتبی آن. |
| `BranchService` | قیمت، زمان و تأیید خدمت در یک شعبه. |
| `ServiceAssignment` | اتصال خدمت شعبه به منبع ارائه‌دهنده. |
| `Resource` / `ResourceType` | نیروی انسانی/تجهیزات قابل رزرو و نوع آن. |
| `ResourceAssignment` | تخصیص منبع به شعبه در یک بازه زمانی. |
| `ResourceAvailability` | برنامه هفتگی دسترسی منبع. |
| `ResourceException` | تعطیلی یا تغییر موقت برنامه منبع. |
| `Discount` | قانون تخفیف، اعتبار و محدودیت استفاده. |
| `Favorite` | علاقه‌مندی کاربر به کسب‌وکار یا منبع. |
| `Review` | امتیاز و نظر مربوط به خدمت نوبت. |
| `Notification` | پیام، مرجع، وضعیت و زمان اعلان کاربر. |
| `ReputationEvent` | رویداد امتیاز اعتبار و موجودیت‌های مرتبط. |
| `Role` / `Permission` / `RolePermission` | نقش، مجوز و ارتباط آن‌ها. |
| `UserSystemRole` | تخصیص نقش به کاربر با ثبت تخصیص‌دهنده و انقضا. |

---

## Documentation change log / تاریخچه تغییرات مستندات

| Commit/change | English | فارسی |
|---|---|---|
| Initial documentation | Added bilingual architecture, class, function, model, API, UI, localization, security, and maintenance documentation. | مستند دوزبانه معماری، کلاس‌ها، توابع، مدل‌ها، API، رابط، چندزبانه‌سازی، امنیت و قانون نگهداری اضافه شد. |
| Persian default locale | Documented first-launch Persian selection and preservation of the user's saved locale. | انتخاب فارسی در اولین اجرا و حفظ زبان ذخیره‌شده کاربر مستند شد. |
