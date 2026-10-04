using BeNobat.Web.Domain;
using Microsoft.EntityFrameworkCore;

namespace BeNobat.Web.Infrastructure;

public static class DemoSeeder
{
    private static readonly string[] DemoSlugs =
    [
        "avan-beauty-studio", "sepid-dental-clinic", "rahaei-massage-wellness", "omid-family-consulting",
        "demo-niloufar", "demo-labkhand", "demo-tavan", "demo-aramesh", "demo-aryana", "demo-roshana",
        "demo-homa", "demo-pet", "demo-fitness", "demo-language", "demo-auto", "demo-home"
    ];

    public static async Task SeedAsync(IServiceProvider serviceProvider, CancellationToken cancellationToken = default, bool reset = false)
    {
        var db = serviceProvider.GetRequiredService<AppDbContext>();
        await using var transaction = await db.Database.BeginTransactionAsync(cancellationToken);
        await db.Database.ExecuteSqlRawAsync("SELECT pg_advisory_xact_lock(7261401)", cancellationToken);
        if (reset) await ResetAsync(db, cancellationToken);
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
                Category = "مشاوره و روان‌شناسی",
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
        await EnsureBookableDemoGraphAsync(db, cancellationToken);
        await transaction.CommitAsync(cancellationToken);
    }

    /// <summary>
    /// Keeps every demo tenant operational, including databases seeded by older versions
    /// that only had businesses, branches and services without the booking relationships.
    /// </summary>
    private static async Task EnsureBookableDemoGraphAsync(AppDbContext db, CancellationToken ct)
    {
        var demoSlugs = DemoSlugs.ToHashSet();
        var businesses = await db.Businesses.Include(x => x.Services).Include(x => x.Branches).ThenInclude(x => x.Resources)
            .Where(x => demoSlugs.Contains(x.Slug)).ToListAsync(ct);
        var businessIds = businesses.Select(x => x.Id).ToList();
        var branchIds = businesses.SelectMany(x => x.Branches).Select(x => x.Id).ToList();
        var resourceIds = businesses.SelectMany(x => x.Branches).SelectMany(x => x.Resources).Select(x => x.Id).ToList();
        var branchServices = await db.BranchServices.Where(x => branchIds.Contains(x.BranchId)).ToListAsync(ct);
        var serviceResources = await db.ServiceResources.Where(x => resourceIds.Contains(x.ResourceId)).ToListAsync(ct);
        var availability = await db.AvailabilityRules.Where(x => businessIds.Contains(x.BusinessId)).ToListAsync(ct);

        foreach (var business in businesses)
        foreach (var branch in business.Branches)
        {
            foreach (var service in business.Services)
            {
                if (!branchServices.Any(x => x.BranchId == branch.Id && x.ServiceId == service.Id))
                    db.BranchServices.Add(new BranchService { BranchId = branch.Id, ServiceId = service.Id, Price = service.Price });
                foreach (var resource in branch.Resources.Where(x => x.Kind == "staff"))
                    if (!serviceResources.Any(x => x.ResourceId == resource.Id && x.ServiceId == service.Id))
                        db.ServiceResources.Add(new ServiceResource { ResourceId = resource.Id, ServiceId = service.Id });
            }

            foreach (var resource in branch.Resources.Where(x => x.Kind == "staff"))
            foreach (var day in Enum.GetValues<DayOfWeek>().Where(x => x != DayOfWeek.Friday))
            {
                if (availability.Any(x => x.ResourceId == resource.Id && x.EffectiveDate == null && x.DayOfWeek == day)) continue;
                db.AvailabilityRules.Add(new AvailabilityRule
                {
                    BusinessId = business.Id, BranchId = branch.Id, ResourceId = resource.Id, DayOfWeek = day,
                    StartsAt = new TimeOnly(branch.OpenHour, 0), EndsAt = new TimeOnly(branch.CloseHour, 0), IsAvailable = true
                });
            }
        }
        await db.SaveChangesAsync(ct);
    }

    private static async Task ResetAsync(AppDbContext db, CancellationToken ct)
    {
        var businessIds = await db.Businesses.IgnoreQueryFilters().Where(x => DemoSlugs.Contains(x.Slug)).Select(x => x.Id).ToListAsync(ct);
        var branchIds = await db.Branches.IgnoreQueryFilters().Where(x => businessIds.Contains(x.BusinessId)).Select(x => x.Id).ToListAsync(ct);
        var appointmentIds = await db.Appointments.IgnoreQueryFilters().Where(x => branchIds.Contains(x.BranchId)).Select(x => x.Id).ToListAsync(ct);
        var resourceIds = await db.Resources.IgnoreQueryFilters().Where(x => branchIds.Contains(x.BranchId)).Select(x => x.Id).ToListAsync(ct);
        var serviceIds = await db.Services.IgnoreQueryFilters().Where(x => businessIds.Contains(x.BusinessId)).Select(x => x.Id).ToListAsync(ct);

        await db.CustomerReviews.IgnoreQueryFilters().Where(x => businessIds.Contains(x.BusinessId)).ExecuteDeleteAsync(ct);
        await db.Reviews.IgnoreQueryFilters().Where(x => businessIds.Contains(x.BusinessId)).ExecuteDeleteAsync(ct);
        await db.AppointmentServices.IgnoreQueryFilters().Where(x => appointmentIds.Contains(x.AppointmentId)).ExecuteDeleteAsync(ct);
        await db.Appointments.IgnoreQueryFilters().Where(x => appointmentIds.Contains(x.Id)).ExecuteDeleteAsync(ct);
        await db.AvailabilityRules.IgnoreQueryFilters().Where(x => businessIds.Contains(x.BusinessId)).ExecuteDeleteAsync(ct);
        await db.ServiceResources.IgnoreQueryFilters().Where(x => resourceIds.Contains(x.ResourceId)).ExecuteDeleteAsync(ct);
        await db.BranchServices.IgnoreQueryFilters().Where(x => branchIds.Contains(x.BranchId)).ExecuteDeleteAsync(ct);
        await db.BranchMemberships.IgnoreQueryFilters().Where(x => branchIds.Contains(x.BranchId)).ExecuteDeleteAsync(ct);
        await db.Resources.IgnoreQueryFilters().Where(x => resourceIds.Contains(x.Id)).ExecuteDeleteAsync(ct);
        await db.Services.IgnoreQueryFilters().Where(x => serviceIds.Contains(x.Id)).ExecuteDeleteAsync(ct);
        await db.Branches.IgnoreQueryFilters().Where(x => branchIds.Contains(x.Id)).ExecuteDeleteAsync(ct);
        await db.Businesses.IgnoreQueryFilters().Where(x => businessIds.Contains(x.Id)).ExecuteDeleteAsync(ct);

        var demoUsers = await db.Users.Where(x => x.Email != null && x.Email.EndsWith("@demo.benobat.example")).ToListAsync(ct);
        db.Users.RemoveRange(demoUsers);
        await db.SaveChangesAsync(ct);
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
