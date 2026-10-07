using BeNobat.Web.Domain;
using BeNobat.Web.Security;
using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;

namespace BeNobat.Web.Infrastructure;

public static class DbSeeder
{
    // [fix] Kept for backward compatibility with anything still referencing the old
    // simple role names; the real seeded/authoritative role set is BeNobat.Web.Security.AppRoles.
    private const string DefaultAdminPassword = "Admin123!";
    public const string AdminRole = AppRoles.PlatformAdmin;
    public const string CustomerRole = AppRoles.Customer;

    public static async Task SeedAsync(IServiceProvider services, IConfiguration configuration, CancellationToken cancellationToken = default)
    {
        var userManager = services.GetRequiredService<UserManager<AppUser>>();
        var roleManager = services.GetRequiredService<RoleManager<IdentityRole<Guid>>>();

        foreach (var role in AppRoles.All)
        {
            if (!await roleManager.RoleExistsAsync(role))
            {
                await roleManager.CreateAsync(new IdentityRole<Guid>(role));
            }
        }

        var adminEmail = configuration["Seed:AdminEmail"] ?? "admin@benobat.local";
        var adminPassword = configuration["Seed:AdminPassword"] ?? DefaultAdminPassword;

        // رمز پیش‌فرض ادمین در مخزن کد عمومی است؛ در محیط غیر توسعه نباید با آن حساب مدیر ساخته شود.
        var isDevelopment = services.GetRequiredService<IHostEnvironment>().IsDevelopment();
        var allowCreateAdmin = isDevelopment || adminPassword != DefaultAdminPassword;

        var admin = await userManager.FindByEmailAsync(adminEmail);
        if (admin is null && !allowCreateAdmin)
        {
            services.GetRequiredService<ILoggerFactory>().CreateLogger("DbSeeder").LogWarning(
                "حساب مدیر سامانه ساخته نشد: در محیط غیر توسعه باید Seed__AdminPassword را روی مقداری غیر از پیش‌فرض تنظیم کنید.");
        }
        else if (admin is null)
        {
            admin = new AppUser
            {
                UserName = adminEmail,
                Email = adminEmail,
                EmailConfirmed = true,
                DisplayName = "مدیر سیستم",
            };

            var createResult = await userManager.CreateAsync(admin, adminPassword);
            if (createResult.Succeeded)
            {
                await userManager.AddToRoleAsync(admin, AppRoles.PlatformAdmin);
            }
        }
        // An existing email is not proof that an account was provisioned by the
        // seeder. Preserve explicit role revocations and never promote a customer
        // who registered this address before the first administrator was created.

        var db = services.GetRequiredService<AppDbContext>();
        // Category names are editable identities in the current schema. Seed an
        // entire kind only when it has never been configured, including archived
        // rows, so a restart cannot resurrect renamed or disabled defaults.
        var configuredCategoryKinds = (await db.CategoryDefinitions.IgnoreQueryFilters()
            .Select(x => x.Kind).Distinct().ToListAsync(cancellationToken)).ToHashSet();
        var categories = Taxonomy.BusinessCategories.Select((name, index) => new CategoryDefinition { Name = name, Kind = CategoryKind.Business, SortOrder = index })
            .Concat(Taxonomy.ServiceCategories.Select((name, index) => new CategoryDefinition { Name = name, Kind = CategoryKind.Service, SortOrder = index }));
        db.CategoryDefinitions.AddRange(categories.Where(category => !configuredCategoryKinds.Contains(category.Kind)));
        await db.SaveChangesAsync(cancellationToken);
        var catalog = new[]
        {
            Catalog("اصلاح و ابرو", "beauty-eyebrow", "پوست و زیبایی", 30), Catalog("کوتاهی مو", "haircut", "مو و آرایش", 45),
            Catalog("رنگ و لایت مو", "hair-color", "مو و آرایش", 120), Catalog("براشینگ مو", "hair-styling", "مو و آرایش", 45),
            Catalog("پاکسازی پوست", "skin-care", "پوست و زیبایی", 60), Catalog("میکاپ", "makeup", "پوست و زیبایی", 90),
            Catalog("مانیکور", "manicure", "ناخن", 45), Catalog("پدیکور", "pedicure", "ناخن", 60),
            Catalog("کوتاهی موی آقایان", "mens-haircut", "پیرایش آقایان", 40), Catalog("اصلاح ریش", "beard-trim", "پیرایش آقایان", 30),
            Catalog("ویزیت عمومی", "general-visit", "پزشکی عمومی", 20), Catalog("معاینه دندان", "dental-exam", "دندانپزشکی", 30),
            Catalog("جرم‌گیری دندان", "dental-scaling", "دندانپزشکی", 60), Catalog("ترمیم دندان", "dental-filling", "دندانپزشکی", 60),
            Catalog("ارزیابی فیزیوتراپی", "physio-assessment", "فیزیوتراپی و توان‌بخشی", 45), Catalog("تمرین درمانی", "therapeutic-exercise", "فیزیوتراپی و توان‌بخشی", 60),
            Catalog("ماساژ سوئدی", "swedish-massage", "ماساژ", 60), Catalog("ماساژ ورزشی", "sports-massage", "ماساژ", 60),
            Catalog("مشاوره فردی", "individual-counseling", "مشاوره و روان‌شناسی", 50), Catalog("مشاوره خانواده", "family-counseling", "مشاوره و روان‌شناسی", 75),
            Catalog("ارزیابی تناسب اندام", "fitness-assessment", "ورزش و تناسب اندام", 45), Catalog("تمرین خصوصی", "personal-training", "ورزش و تناسب اندام", 60),
            Catalog("معاینه حیوانات خانگی", "pet-exam", "دامپزشکی", 30), Catalog("واکسیناسیون حیوانات", "pet-vaccination", "دامپزشکی", 30),
            Catalog("کلاس خصوصی زبان", "private-language-class", "آموزش", 60), Catalog("مشاوره تحصیلی", "education-consulting", "آموزش", 45),
            Catalog("سرویس دوره‌ای خودرو", "car-periodic-service", "خدمات خودرو", 90), Catalog("کارواش", "car-wash", "خدمات خودرو", 45),
            Catalog("نظافت منزل", "home-cleaning", "خدمات منزل", 180), Catalog("تعمیر لوازم خانگی", "appliance-repair", "خدمات منزل", 90)
        };
        // Seed defaults only once. Platform managers may review, customize or
        // archive built-in items, and a restart must preserve those decisions.
        var existingSlugs = (await db.ServiceCatalogItems.IgnoreQueryFilters()
            .Select(x => x.Slug).ToListAsync(cancellationToken)).ToHashSet(StringComparer.Ordinal);
        db.ServiceCatalogItems.AddRange(catalog.Where(item => !existingSlugs.Contains(item.Slug)));
        await db.SaveChangesAsync(cancellationToken);

    }

    private static ServiceCatalogItem Catalog(string name, string slug, string category, int duration) =>
        new() { Name = name, Slug = slug, Category = category, SuggestedDurationMinutes = duration };
}
