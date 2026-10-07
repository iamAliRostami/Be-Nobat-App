using BeNobat.Web.Components;
using BeNobat.Web.Api;
using BeNobat.Web.Application;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using BeNobat.Web.Security;
using Microsoft.AspNetCore.Antiforgery;
using Microsoft.AspNetCore.DataProtection;
using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.RateLimiting;
using System.Threading.RateLimiting;
using System.Text.Json.Serialization;
using Npgsql;

var resetDemo = args.Contains("--reset-demo", StringComparer.Ordinal);
var seedDemo = resetDemo || args.Contains("--seed-demo", StringComparer.Ordinal);
var hostArgs = args.Where(arg => arg is not "--seed-demo" and not "--reset-demo").ToArray();
var builder = WebApplication.CreateBuilder(hostArgs);
var seedDemoOnStartup = builder.Configuration.GetValue<bool>("Seed:DemoOnStartup");
if ((seedDemo || seedDemoOnStartup) && !builder.Environment.IsDevelopment())
{
    throw new InvalidOperationException("داده‌های نمایشی فقط در محیط Development قابل ایجاد هستند.");
}
var connectionString = builder.Configuration.GetConnectionString("Default")
    ?? throw new InvalidOperationException("ConnectionStrings:Default is required.");

// Razor components in the same circuit may initialize concurrently (for example,
// a page and the avatar in its layout). A scoped DbContext would then be shared by
// both components even though DbContext does not support parallel operations.
builder.Services.AddDbContextFactory<AppDbContext>(options => options.UseNpgsql(connectionString));

// بدون ذخیره‌ی کلیدها، با هر ری‌استارت/دیپلوی کانتینر همه‌ی کوکی‌های ورود و توکن‌های
// antiforgery باطل می‌شدند و کاربران بی‌دلیل از حساب بیرون می‌افتادند.
var dataProtectionKeysPath = builder.Configuration["DataProtection:KeysPath"];
if (!string.IsNullOrWhiteSpace(dataProtectionKeysPath))
{
    builder.Services.AddDataProtection()
        .SetApplicationName("BeNobat")
        .PersistKeysToFileSystem(new DirectoryInfo(dataProtectionKeysPath));
}

// پاک‌سازی دوره‌ای نوبت‌های «در انتظار»ی که زمانشان گذشته است.
builder.Services.AddHostedService<AppointmentMaintenanceService>();

// [fix] AddIdentityApiEndpoints<T>() configures bearer-token authentication and is meant
// for SPA/mobile clients calling the JSON /api/auth/* endpoints directly. It does not
// integrate with SignInManager-based sign-in from server-rendered Razor/Blazor forms,
// which is what this app's Login/Register pages need. AddIdentity<T,TRole>() + the
// cookie scheme below is the pattern used by the official "Blazor Web App with
// Individual Accounts" template and is what actually works here.
builder.Services.AddCascadingAuthenticationState();
builder.Services.AddScoped<AdminAccessScope>();
builder.Services.AddHttpContextAccessor();
builder.Services.AddScoped<UiLocale>();
builder.Services.AddScoped<ApiScope>();
builder.Services.AddScoped<MobileTokenService>();
builder.Services.AddScoped<AppointmentBookingService>();
builder.Services.ConfigureHttpJsonOptions(options => options.SerializerOptions.Converters.Add(new JsonStringEnumConverter()));
builder.Services.Configure<RouteHandlerOptions>(options => options.ThrowOnBadRequest = true);
builder.Services.AddRateLimiter(options =>
{
    options.AddPolicy("mobile-auth", http => RateLimitPartition.GetFixedWindowLimiter(
        http.Connection.RemoteIpAddress?.ToString() ?? "unknown", _ => new FixedWindowRateLimiterOptions
        {
            PermitLimit = 8, Window = TimeSpan.FromMinutes(1), QueueLimit = 0, AutoReplenishment = true,
        }));
    options.OnRejected = async (context, cancellationToken) =>
    {
        context.HttpContext.Response.StatusCode = StatusCodes.Status429TooManyRequests;
        context.HttpContext.Response.Headers.RetryAfter = "60";
        await context.HttpContext.Response.WriteAsJsonAsync(new ApiError("rate_limited", "Too many attempts. Try again in a minute."), cancellationToken);
    };
});
builder.Services.AddAuthorizationBuilder()
    .AddPolicy(Policies.ManagePlatform, policy => policy.RequireRole(AppRoles.PlatformAdmin))
    // مدیریت کل کسب‌وکار (خدمات، شعبه‌ها، تیم): فقط نقش‌های مدیریتی بالادستی.
    .AddPolicy(Policies.ManageBusiness, policy => policy.RequireRole(AppRoles.BusinessManagers))
    // مدیریت نوبت‌ها (تقویم، تغییر وضعیت): مدیریتی‌ها + پرسنل.
    .AddPolicy(Policies.ManageAppointments, policy => policy.RequireRole(AppRoles.AppointmentManagers))
    // مشاهده نوبت‌های خود: کافیست کاربر لاگین کرده باشد.
    .AddPolicy(Policies.ViewOwnAppointments, policy => policy.RequireAuthenticatedUser());
builder.Services
    .AddIdentity<AppUser, IdentityRole<Guid>>(options =>
    {
        options.SignIn.RequireConfirmedAccount = false;
        options.Password.RequireNonAlphanumeric = false;
        options.Password.RequireUppercase = false;
        options.Password.RequiredLength = 6;
        options.User.RequireUniqueEmail = true;
    })
    .AddEntityFrameworkStores<AppDbContext>()
    .AddSignInManager()
    .AddClaimsPrincipalFactory<AppUserClaimsPrincipalFactory>()
    // [fix] پیام‌های خطای Identity به فارسی؛ قبلاً متن انگلیسی خام
    // (مثل "Username 'x' is already taken.") در فرم ثبت‌نام نمایش داده می‌شد.
    .AddErrorDescriber<PersianIdentityErrorDescriber>()
    .AddDefaultTokenProviders();
builder.Services.AddAuthentication().AddScheme<AuthenticationSchemeOptions, MobileBearerHandler>(MobileAuthentication.Scheme, _ => { });

builder.Services.ConfigureApplicationCookie(options =>
{
    options.LoginPath = "/account/login";
    options.LogoutPath = "/account/logout";
    options.AccessDeniedPath = "/account/access-denied";
    options.Cookie.Name = "BeNobat.Auth";
    options.ExpireTimeSpan = TimeSpan.FromDays(14);
    options.SlidingExpiration = true;
});

builder.Services.AddRazorComponents().AddInteractiveServerComponents();
builder.Services.AddOpenApi();
builder.Services.AddHealthChecks().AddDbContextCheck<AppDbContext>();

var app = builder.Build();

// Foundation bootstrap; this is replaced by reviewed EF migrations before cut-over.
await using (var scope = app.Services.CreateAsyncScope())
{
    var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
    await db.Database.EnsureCreatedAsync();
    await CompatibilitySchemaUpgrade.ApplyAsync(db);
    await DbSeeder.SeedAsync(scope.ServiceProvider, app.Configuration);
    if (seedDemo || seedDemoOnStartup)
    {
        await DemoSeeder.SeedAsync(scope.ServiceProvider, reset: resetDemo);
    }
}

if (seedDemo)
{
    app.Logger.LogInformation("داده‌های نمایشی آماده شدند؛ موارد موجود دوباره ساخته نشدند.");
    await app.DisposeAsync();
    return;
}

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

app.UseHttpsRedirection();
app.UseStaticFiles();
app.Use(async (http, next) =>
{
    if (!http.Request.Path.StartsWithSegments("/api/v1")) { await next(http); return; }
    try { await next(http); }
    catch (BadHttpRequestException ex) when (!http.Response.HasStarted)
    {
        http.Response.Clear(); http.Response.StatusCode = ex.StatusCode;
        await http.Response.WriteAsJsonAsync(new ApiError("invalid_input", "The request body or parameters are invalid."));
    }
    catch (DbUpdateConcurrencyException) when (!http.Response.HasStarted)
    {
        http.Response.Clear(); http.Response.StatusCode = 409;
        await http.Response.WriteAsJsonAsync(new ApiError("conflict", "The record changed. Refresh and try again."));
    }
    catch (DbUpdateException ex) when (ex.InnerException is PostgresException { SqlState: PostgresErrorCodes.UniqueViolation } && !http.Response.HasStarted)
    {
        http.Response.Clear(); http.Response.StatusCode = 409;
        await http.Response.WriteAsJsonAsync(new ApiError("conflict", "This record already exists. Refresh and try again."));
    }
    catch (Exception ex) when (!http.Response.HasStarted)
    {
        app.Logger.LogError(ex, "API request failed for {Path}", http.Request.Path);
        http.Response.Clear(); http.Response.StatusCode = 500;
        await http.Response.WriteAsJsonAsync(new ApiError("server_error", "The request could not be completed."));
    }
});

// [fix] UseAuthentication() was missing entirely, so no request ever had its auth
// cookie validated into a signed-in ClaimsPrincipal -- every request looked
// anonymous regardless of login state, which is why the admin panel could not
// tell staff from the public and login had no visible effect.
app.UseAuthentication();
app.UseRateLimiter();
app.UseAuthorization();

// [fix] UseAntiforgery() must run after UseAuthentication/UseAuthorization
// (ASP.NET Core 8+ throws at startup if antiforgery is registered before
// authorization when both are present).
app.UseAntiforgery();

app.MapPost("/account/logout", async (SignInManager<AppUser> signInManager, HttpContext http, IAntiforgery antiforgery) =>
{
    try { await antiforgery.ValidateRequestAsync(http); }
    catch (AntiforgeryValidationException) { return Results.BadRequest("Invalid antiforgery token."); }
    await signInManager.SignOutAsync();
    var request = http.Request;
    var returnUrl = request.Form.TryGetValue("returnUrl", out var value) ? value.ToString() : "/";
    // LocalRedirect روی آدرس غیرمحلی exception می‌اندازد؛ ورودی را پیش از آن پاک‌سازی می‌کنیم.
    return Results.LocalRedirect(SafeRedirect.Local(returnUrl));
}).RequireAuthorization();

// تغییر رمز عبور توسط خود کاربر. مثل تغییر ایمیل یک endpoint معمولی است، چون تغییر رمز
// SecurityStamp را عوض می‌کند و باید همان‌جا با RefreshSignInAsync کوکی تازه شود.
app.MapPost("/account/change-password", async (
    HttpContext http,
    IAntiforgery antiforgery,
    UserManager<AppUser> userManager,
    SignInManager<AppUser> signInManager,
    AppDbContext db) =>
{
    try
    {
        await antiforgery.ValidateRequestAsync(http);
    }
    catch (AntiforgeryValidationException)
    {
        return Results.LocalRedirect("/account/profile?passwordError=invalid");
    }

    var form = await http.Request.ReadFormAsync(http.RequestAborted);
    var currentPassword = form["currentPassword"].ToString();
    var newPassword = form["newPassword"].ToString();
    var confirmPassword = form["confirmPassword"].ToString();

    if (string.IsNullOrEmpty(currentPassword) || string.IsNullOrEmpty(newPassword))
    {
        return Results.LocalRedirect("/account/profile?passwordError=invalid");
    }

    if (newPassword != confirmPassword)
    {
        return Results.LocalRedirect("/account/profile?passwordError=mismatch");
    }

    var result = await AccountCredentialChanges.ChangePasswordAsync(db, userManager, http.User,
        currentPassword, newPassword, http.RequestAborted);
    if (result.Failure == CredentialChangeFailure.SessionExpired)
    {
        await signInManager.SignOutAsync();
        return Results.LocalRedirect("/account/login");
    }
    if (!result.Success)
    {
        var error = result.Failure switch
        {
            CredentialChangeFailure.PasswordMismatch => "wrong",
            CredentialChangeFailure.WeakPassword => "weak",
            _ => "invalid",
        };
        return Results.LocalRedirect($"/account/profile?passwordError={error}");
    }

    await signInManager.RefreshSignInAsync(result.User!);
    return Results.LocalRedirect("/account/profile?passwordChanged=1");
}).RequireAuthorization();

// [feature] تغییر ایمیل حساب. عمداً یک endpoint جداست و نه کد داخل کامپوننت
// تعاملی: تغییر ایمیل/نام کاربری، SecurityStamp را عوض می‌کند و بدون
// RefreshSignInAsync کوکی کاربر در اولین اعتبارسنجی باطل می‌شد و کاربر بی‌دلیل
// از حساب بیرون می‌افتاد. RefreshSignInAsync فقط جایی کار می‌کند که HttpContext
// در دسترس باشد.
app.MapPost("/account/change-email", async (
    HttpContext http,
    Microsoft.AspNetCore.Antiforgery.IAntiforgery antiforgery,
    UserManager<AppUser> userManager,
    SignInManager<AppUser> signInManager,
    AppDbContext db) =>
{
    try
    {
        await antiforgery.ValidateRequestAsync(http);
    }
    catch (Microsoft.AspNetCore.Antiforgery.AntiforgeryValidationException)
    {
        return Results.LocalRedirect("/account/profile?emailError=invalid");
    }

    var form = await http.Request.ReadFormAsync(http.RequestAborted);
    var result = await AccountCredentialChanges.ChangeEmailAsync(db, userManager, http.User,
        form["email"].ToString(), http.RequestAborted);
    if (result.Failure == CredentialChangeFailure.SessionExpired)
    {
        await signInManager.SignOutAsync();
        return Results.LocalRedirect("/account/login");
    }
    if (!result.Success)
        return Results.LocalRedirect(result.Failure == CredentialChangeFailure.DuplicateEmail
            ? "/account/profile?emailError=duplicate"
            : "/account/profile?emailError=invalid");
    if (!result.Changed) return Results.LocalRedirect("/account/profile");

    await signInManager.RefreshSignInAsync(result.User!);
    return Results.LocalRedirect("/account/profile?emailChanged=1");
}).RequireAuthorization();

// [feature] سرو کردن عکس پروفایل کاربر. تصویر داخل دیتابیس نگهداری می‌شود،
// چون مسیر wwwroot در image داکر فقط-خواندنی است و با هر build پاک می‌شود.
app.MapGet("/media/avatar/{id:guid}", async (Guid id, HttpContext http, AppDbContext db, CancellationToken cancellationToken) =>
{
    var avatar = await db.Users
        .AsNoTracking()
        .Where(u => u.Id == id && u.AvatarData != null)
        .Select(u => new { u.AvatarData, u.AvatarContentType })
        .FirstOrDefaultAsync(cancellationToken);

    if (avatar is null) return Results.NotFound();

    // آدرس تصویر شامل نسخه (ConcurrencyStamp) است، پس کش طولانی امن است و بار دیتابیس را کم می‌کند.
    http.Response.Headers.CacheControl = "public, max-age=604800";
    http.Response.Headers["X-Content-Type-Options"] = "nosniff";
    return Results.File(avatar.AvatarData!, avatar.AvatarContentType ?? "image/png");
}).AllowAnonymous();

app.MapGet("/api/dashboard", async (AppDbContext db, CancellationToken cancellationToken) =>
    new DashboardSummary(
        await db.Businesses.CountAsync(cancellationToken),
        await db.Branches.CountAsync(cancellationToken),
        await db.Services.CountAsync(cancellationToken),
        await db.Appointments.CountAsync(cancellationToken)))
    // این شمارنده‌ها سراسری‌اند؛ برای پرسنل و مدیران یک کسب‌وکار نباید قابل مشاهده باشند.
    .RequireAuthorization(Policies.ManagePlatform);
app.MapHealthChecks("/health");
app.MapGroup("/api/v1").MapMobileAuthentication().MapPublicEndpoints().MapCustomerEndpoints().MapManagementEndpoints();
app.MapFallback("/api/v1/{**path}", () => ApiResults.Error("not_found", "The requested API route was not found.", 404));
app.MapRazorComponents<App>().AddInteractiveServerRenderMode();

app.Run();

public sealed record DashboardSummary(int Businesses, int Branches, int Services, int Appointments);

public partial class Program;
