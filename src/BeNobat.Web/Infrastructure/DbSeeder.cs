using BeNobat.Web.Domain;
using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;

namespace BeNobat.Web.Infrastructure;

public static class DbSeeder
{
    public const string AdminRole = "Admin";
    public const string CustomerRole = "Customer";

    public static async Task SeedAsync(IServiceProvider services, IConfiguration configuration, CancellationToken cancellationToken = default)
    {
        var db = services.GetRequiredService<AppDbContext>();
        var userManager = services.GetRequiredService<UserManager<AppUser>>();
        var roleManager = services.GetRequiredService<RoleManager<IdentityRole<Guid>>>();

        foreach (var role in new[] { AdminRole, CustomerRole })
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
                await userManager.AddToRoleAsync(admin, AdminRole);
            }
        }
        else if (!await userManager.IsInRoleAsync(admin, AdminRole))
        {
            await userManager.AddToRoleAsync(admin, AdminRole);
        }

        if (!await db.Businesses.IgnoreQueryFilters().AnyAsync(cancellationToken))
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

            var secondBusiness = new Business
            {
                Name = "کلینیک دندانپزشکی سپید",
                Slug = "sepid-dental-clinic",
                Category = "دندانپزشکی",
                City = "تهران",
                Description = "کلینیک دندانپزشکی تخصصی در سعادت‌آباد.",
            };

            var secondBranch = new Branch
            {
                Business = secondBusiness,
                BusinessId = secondBusiness.Id,
                Name = "شعبه سعادت‌آباد",
                Address = "سعادت‌آباد، بلوار سرو",
                OpenHour = 9,
                CloseHour = 18,
            };

            var secondServices = new List<Service>
            {
                new()
                {
                    Business = secondBusiness,
                    BusinessId = secondBusiness.Id,
                    Name = "معاینه و مشاوره",
                    Description = "معاینه اولیه و برنامه درمان",
                    DurationMinutes = 30,
                    Price = 350_000,
                },
                new()
                {
                    Business = secondBusiness,
                    BusinessId = secondBusiness.Id,
                    Name = "جرم‌گیری دندان",
                    Description = "جرم‌گیری و پولیش کامل",
                    DurationMinutes = 45,
                    Price = 600_000,
                },
            };

            db.Businesses.Add(secondBusiness);
            db.Branches.Add(secondBranch);
            db.Services.AddRange(secondServices);

            await db.SaveChangesAsync(cancellationToken);
        }
    }
}
