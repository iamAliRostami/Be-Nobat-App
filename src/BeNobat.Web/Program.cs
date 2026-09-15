using BeNobat.Web.Components;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using BeNobat.Web.Security;
using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;

var seedDemo = args.Contains("--seed-demo", StringComparer.Ordinal);
var hostArgs = args.Where(arg => arg != "--seed-demo").ToArray();
var builder = WebApplication.CreateBuilder(hostArgs);
if (seedDemo && !builder.Environment.IsDevelopment())
{
    throw new InvalidOperationException("داده‌های نمایشی فقط در محیط Development قابل ایجاد هستند.");
}
var connectionString = builder.Configuration.GetConnectionString("Default")
    ?? throw new InvalidOperationException("ConnectionStrings:Default is required.");

builder.Services.AddDbContext<AppDbContext>(options => options.UseNpgsql(connectionString));

// [fix] AddIdentityApiEndpoints<T>() configures bearer-token authentication and is meant
// for SPA/mobile clients calling the JSON /api/auth/* endpoints directly. It does not
// integrate with SignInManager-based sign-in from server-rendered Razor/Blazor forms,
// which is what this app's Login/Register pages need. AddIdentity<T,TRole>() + the
// cookie scheme below is the pattern used by the official "Blazor Web App with
// Individual Accounts" template and is what actually works here.
builder.Services.AddCascadingAuthenticationState();
builder.Services.AddAuthorizationBuilder()
    .AddPolicy(Policies.ManagePlatform, policy => policy.RequireRole(AppRoles.PlatformAdmin))
    // مدیریت کل کسب‌وکار (خدمات، شعبه‌ها، تیم): فقط نقش‌های مدیریتی بالادستی.
    .AddPolicy(Policies.ManageBusiness, policy => policy.RequireRole(
        AppRoles.PlatformAdmin, AppRoles.Owner, AppRoles.Manager))
    // مدیریت نوبت‌ها (تقویم، تغییر وضعیت): مدیریتی‌ها + پرسنل.
    .AddPolicy(Policies.ManageAppointments, policy => policy.RequireRole(
        AppRoles.PlatformAdmin, AppRoles.Owner, AppRoles.Manager, AppRoles.Staff))
    // مشاهده نوبت‌های خود: کافیست کاربر لاگین کرده باشد.
    .AddPolicy(Policies.ViewOwnAppointments, policy => policy.RequireAuthenticatedUser());
builder.Services
    .AddIdentity<AppUser, IdentityRole<Guid>>(options =>
    {
        options.SignIn.RequireConfirmedAccount = false;
        options.Password.RequireNonAlphanumeric = false;
        options.Password.RequireUppercase = false;
        options.Password.RequiredLength = 6;
    })
    .AddEntityFrameworkStores<AppDbContext>()
    .AddSignInManager()
    .AddClaimsPrincipalFactory<AppUserClaimsPrincipalFactory>()
    // [fix] پیام‌های خطای Identity به فارسی؛ قبلاً متن انگلیسی خام
    // (مثل "Username 'x' is already taken.") در فرم ثبت‌نام نمایش داده می‌شد.
    .AddErrorDescriber<PersianIdentityErrorDescriber>()
    .AddDefaultTokenProviders();

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
    if (seedDemo)
    {
        await DemoSeeder.SeedAsync(scope.ServiceProvider);
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

// [fix] UseAuthentication() was missing entirely, so no request ever had its auth
// cookie validated into a signed-in ClaimsPrincipal -- every request looked
// anonymous regardless of login state, which is why the admin panel could not
// tell staff from the public and login had no visible effect.
app.UseAuthentication();
app.UseAuthorization();

// [fix] UseAntiforgery() must run after UseAuthentication/UseAuthorization
// (ASP.NET Core 8+ throws at startup if antiforgery is registered before
// authorization when both are present).
app.UseAntiforgery();

app.MapPost("/account/logout", async (SignInManager<AppUser> signInManager, HttpRequest request) =>
{
    await signInManager.SignOutAsync();
    var returnUrl = request.Form.TryGetValue("returnUrl", out var value) ? value.ToString() : "/";
    return Results.LocalRedirect(string.IsNullOrWhiteSpace(returnUrl) ? "/" : returnUrl);
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
    SignInManager<AppUser> signInManager) =>
{
    try
    {
        await antiforgery.ValidateRequestAsync(http);
    }
    catch (Microsoft.AspNetCore.Antiforgery.AntiforgeryValidationException)
    {
        return Results.LocalRedirect("/account/profile?emailError=invalid");
    }

    var user = await userManager.GetUserAsync(http.User);
    if (user is null)
    {
        return Results.LocalRedirect("/account/login");
    }

    var email = http.Request.Form["email"].ToString().Trim();

    if (string.IsNullOrWhiteSpace(email) || !email.Contains('@') || email.Length > 256)
    {
        return Results.LocalRedirect("/account/profile?emailError=invalid");
    }

    if (string.Equals(email, user.Email, StringComparison.OrdinalIgnoreCase))
    {
        return Results.LocalRedirect("/account/profile");
    }

    var existing = await userManager.FindByEmailAsync(email);
    if (existing is not null && existing.Id != user.Id)
    {
        return Results.LocalRedirect("/account/profile?emailError=duplicate");
    }

    if (!(await userManager.SetEmailAsync(user, email)).Succeeded ||
        !(await userManager.SetUserNameAsync(user, email)).Succeeded)
    {
        return Results.LocalRedirect("/account/profile?emailError=invalid");
    }

    user.EmailConfirmed = true;
    await userManager.UpdateAsync(user);
    await signInManager.RefreshSignInAsync(user);

    return Results.LocalRedirect("/account/profile?emailChanged=1");
}).RequireAuthorization();

// [feature] سرو کردن عکس پروفایل کاربر. تصویر داخل دیتابیس نگهداری می‌شود،
// چون مسیر wwwroot در image داکر فقط-خواندنی است و با هر build پاک می‌شود.
app.MapGet("/media/avatar/{id:guid}", async (Guid id, AppDbContext db, CancellationToken cancellationToken) =>
{
    var avatar = await db.Users
        .Where(u => u.Id == id && u.AvatarData != null)
        .Select(u => new { u.AvatarData, u.AvatarContentType })
        .FirstOrDefaultAsync(cancellationToken);

    return avatar is null
        ? Results.NotFound()
        : Results.File(avatar.AvatarData!, avatar.AvatarContentType ?? "image/png");
}).AllowAnonymous();

app.MapGet("/api/dashboard", async (AppDbContext db, CancellationToken cancellationToken) =>
    new DashboardSummary(
        await db.Businesses.CountAsync(cancellationToken),
        await db.Branches.CountAsync(cancellationToken),
        await db.Services.CountAsync(cancellationToken),
        await db.Appointments.CountAsync(cancellationToken)))
    .RequireAuthorization(Policies.ManageAppointments);
app.MapHealthChecks("/health");
app.MapRazorComponents<App>().AddInteractiveServerRenderMode();

app.Run();

public sealed record DashboardSummary(int Businesses, int Branches, int Services, int Appointments);

public partial class Program;
