using BeNobat.Web.Domain;
using Microsoft.EntityFrameworkCore;

namespace BeNobat.Web.Infrastructure;

public static class DemoSeeder
{
    public static async Task SeedAsync(IServiceProvider serviceProvider, CancellationToken cancellationToken = default)
    {
        var db = serviceProvider.GetRequiredService<AppDbContext>();
        await using var transaction = await db.Database.BeginTransactionAsync(cancellationToken);
        await db.Database.ExecuteSqlRawAsync("SELECT pg_advisory_xact_lock(7261401)", cancellationToken);
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

        string[] slugs = ["avan-beauty-studio", "sepid-dental-clinic",
            "rahaei-massage-wellness", "omid-family-consulting"];
        var branches = await db.Branches
            .Where(b => slugs.Contains(b.Business.Slug))
            .ToListAsync(cancellationToken);
        foreach (var branch in branches)
        {
            if (!await db.Resources.IgnoreQueryFilters()
                    .AnyAsync(r => r.BranchId == branch.Id, cancellationToken))
            {
                db.Resources.Add(new Resource
                {
                    Branch = branch,
                    BranchId = branch.Id,
                    Name = "ارائه‌دهنده آزمایشی",
                    Kind = "staff",
                });
            }
        }
        await db.SaveChangesAsync(cancellationToken);
        await CommercialDemoSeeder.SeedAsync(serviceProvider, cancellationToken);
        await transaction.CommitAsync(cancellationToken);
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
