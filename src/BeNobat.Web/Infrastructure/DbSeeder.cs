using BeNobat.Web.Domain;
using BeNobat.Web.Security;
using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;

namespace BeNobat.Web.Infrastructure;

public static class DbSeeder
{
    // [fix] Kept for backward compatibility with anything still referencing the old
    // simple role names; the real seeded/authoritative role set is BeNobat.Web.Security.AppRoles.
    public const string AdminRole = AppRoles.PlatformAdmin;
    public const string CustomerRole = AppRoles.Customer;

    public static async Task SeedAsync(IServiceProvider services, IConfiguration configuration, CancellationToken cancellationToken = default)
    {
        var db = services.GetRequiredService<AppDbContext>();
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
        var adminPassword = configuration["Seed:AdminPassword"] ?? "Admin123!";

        var admin = await userManager.FindByEmailAsync(adminEmail);
        if (admin is null)
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
        else if (!await userManager.IsInRoleAsync(admin, AppRoles.PlatformAdmin))
        {
            await userManager.AddToRoleAsync(admin, AppRoles.PlatformAdmin);
        }

        await SeedBusinessIfMissingAsync(db, "avan-beauty-studio", cancellationToken, () =>
        {
            var business = new Business
            {
                Name = "استودیو زیبایی آوان",
                Slug = "avan-beauty-studio",
                Category = "زیبایی و آرایش",
                City = "تهران",
                Description = "سالن زیبایی و آرایش با بیش از ده سال سابقه در ونک.",
            };

            var branch = new Branch
            {
                Business = business,
                BusinessId = business.Id,
                Name = "شعبه ونک",
                Address = "ونک، خیابان ملاصدرا",
                OpenHour = 9,
                CloseHour = 19,
            };

            var services = new List<Service>
            {
                new()
                {
                    Business = business,
                    BusinessId = business.Id,
                    Name = "کوتاهی و استایل مو",
                    Description = "شستشو، کوتاهی و براشینگ",
                    DurationMinutes = 60,
                    Price = 480_000,
                },
                new()
                {
                    Business = business,
                    BusinessId = business.Id,
                    Name = "رنگ و لایت",
                    Description = "مشاوره رنگ، رنگ و مراقبت",
                    DurationMinutes = 120,
                    Price = 950_000,
                },
                new()
                {
                    Business = business,
                    BusinessId = business.Id,
                    Name = "اصلاح و ابرو",
                    Description = "اصلاح صورت و طراحی ابرو",
                    DurationMinutes = 30,
                    Price = 280_000,
                },
            };

            var resource = new Resource
            {
                Branch = branch,
                BranchId = branch.Id,
                Name = "الناز محمدی",
                Kind = "staff",
            };

            db.Businesses.Add(business);
            db.Branches.Add(branch);
            db.Services.AddRange(services);
            db.Resources.Add(resource);
        });

        await SeedBusinessIfMissingAsync(db, "sepid-dental-clinic", cancellationToken, () =>
        {
            var business = new Business
            {
                Name = "کلینیک دندانپزشکی سپید",
                Slug = "sepid-dental-clinic",
                Category = "دندانپزشکی",
                City = "تهران",
                Description = "کلینیک دندانپزشکی تخصصی در سعادت‌آباد.",
            };

            var branch = new Branch
            {
                Business = business,
                BusinessId = business.Id,
                Name = "شعبه سعادت‌آباد",
                Address = "سعادت‌آباد، بلوار سرو",
                OpenHour = 9,
                CloseHour = 18,
            };

            var services = new List<Service>
            {
                new()
                {
                    Business = business,
                    BusinessId = business.Id,
                    Name = "معاینه و مشاوره",
                    Description = "معاینه اولیه و برنامه درمان",
                    DurationMinutes = 30,
                    Price = 350_000,
                },
                new()
                {
                    Business = business,
                    BusinessId = business.Id,
                    Name = "جرم‌گیری دندان",
                    Description = "جرم‌گیری و پولیش کامل",
                    DurationMinutes = 45,
                    Price = 600_000,
                },
            };

            db.Businesses.Add(business);
            db.Branches.Add(branch);
            db.Services.AddRange(services);
        });

        await SeedBusinessIfMissingAsync(db, "rahaei-massage-wellness", cancellationToken, () =>
        {
            var business = new Business
            {
                Name = "مرکز ماساژ و تندرستی رهایی",
                Slug = "rahaei-massage-wellness",
                Category = "ماساژ و تندرستی",
                City = "اصفهان",
                Description = "ماساژ درمانی و خدمات آرامش‌بخش در اصفهان.",
            };

            var branch = new Branch
            {
                Business = business,
                BusinessId = business.Id,
                Name = "شعبه چهارباغ",
                Address = "چهارباغ بالا",
                OpenHour = 10,
                CloseHour = 21,
            };

            var services = new List<Service>
            {
                new()
                {
                    Business = business,
                    BusinessId = business.Id,
                    Name = "ماساژ سوئدی",
                    Description = "ماساژ آرامش‌بخش کل بدن",
                    DurationMinutes = 60,
                    Price = 700_000,
                },
            };

            db.Businesses.Add(business);
            db.Branches.Add(branch);
            db.Services.AddRange(services);
        });

        await SeedBusinessIfMissingAsync(db, "omid-family-consulting", cancellationToken, () =>
        {
            var business = new Business
            {
                Name = "مرکز مشاوره خانواده امید",
                Slug = "omid-family-consulting",
                Category = "مشاوره",
                City = "تهران",
                Description = "مشاوره خانواده، فردی و تحصیلی.",
            };

            var branch = new Branch
            {
                Business = business,
                BusinessId = business.Id,
                Name = "شعبه پاسداران",
                Address = "پاسداران، نبش گلستان",
                OpenHour = 9,
                CloseHour = 17,
            };

            var services = new List<Service>
            {
                new()
                {
                    Business = business,
                    BusinessId = business.Id,
                    Name = "مشاوره فردی",
                    Description = "یک جلسه ۵۰ دقیقه‌ای",
                    DurationMinutes = 50,
                    Price = 500_000,
                },
            };

            db.Businesses.Add(business);
            db.Branches.Add(branch);
            db.Services.AddRange(services);
        });
    }

    private static async Task SeedBusinessIfMissingAsync(
        AppDbContext db,
        string slug,
        CancellationToken cancellationToken,
        Action addEntities)
    {
        var exists = await db.Businesses.IgnoreQueryFilters().AnyAsync(b => b.Slug == slug, cancellationToken);
        if (exists)
        {
            return;
        }

        addEntities();
        await db.SaveChangesAsync(cancellationToken);
    }
}
