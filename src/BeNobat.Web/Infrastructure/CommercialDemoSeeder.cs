using System.Security.Cryptography;
using System.Text;
using BeNobat.Web.Domain;
using BeNobat.Web.Security;
using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;

namespace BeNobat.Web.Infrastructure;

// Called only by DemoSeeder, inside its transaction and advisory lock.
public static class CommercialDemoSeeder
{
    private static Guid Id(string key) =>
        new(SHA256.HashData(Encoding.UTF8.GetBytes("benobat-commercial-demo-v1:" + key)).AsSpan(0, 16));

    public static async Task SeedAsync(IServiceProvider provider, CancellationToken ct = default)
    {
        var db = provider.GetRequiredService<AppDbContext>();
        var users = provider.GetRequiredService<UserManager<AppUser>>();
        var historyStart = DateTimeOffset.UtcNow.AddDays(-90);
        var configuration = provider.GetRequiredService<IConfiguration>();
        var password = configuration["Seed:DemoPassword"];
        if (string.IsNullOrWhiteSpace(password))
            throw new InvalidOperationException("برای ساخت مشتری‌های دمو، Seed__DemoPassword را مشخص کنید.");

        string[] firstNames = ["علی", "سارا", "رضا", "مریم", "امیر", "نگار", "محمد", "مهسا",
            "حسین", "نازنین", "آرمان", "الهام"];
        string[] lastNames = ["احمدی", "رضایی", "محمدی", "کریمی"];
        var customers = new List<AppUser>();
        for (var i = 0; i < 48; i++)
        {
            var userId = Id($"customer-{i}");
            var email = $"customer{i + 1:00}@demo.benobat.example";
            var user = await users.FindByIdAsync(userId.ToString());
            if (user is null)
            {
                if (await users.FindByEmailAsync(email) is not null)
                    throw new InvalidOperationException($"ایمیل {email} قبلاً به حساب دیگری اختصاص دارد.");
                user = new AppUser
                {
                    Id = userId, UserName = email, Email = email, EmailConfirmed = true,
                    DisplayName = $"{firstNames[i % 12]} {lastNames[i / 12]}"
                };
                Check(await users.CreateAsync(user, password));
                Check(await users.AddToRoleAsync(user, AppRoles.Customer));
            }
            customers.Add(user);
        }

        // Price values follow the existing model's IRR currency.
        var catalogs = new[]
        {
            new Catalog("demo-niloufar", "سالن زیبایی نیلوفر", "زیبایی و آرایش", "تهران",
                ["سعادت‌آباد", "نیاوران"], ["بلوار سرو، خیابان پنجم", "خیابان باهنر، کوچه یاس"],
                ["کوتاهی و براشینگ", "رنگ ریشه", "مانیکور", "پاک‌سازی پوست"],
                [60, 90, 45, 60], [4800000m, 8500000m, 3200000m, 6500000m]),
            new Catalog("demo-labkhand", "کلینیک دندانپزشکی لبخند", "دندانپزشکی", "شیراز",
                ["معالی‌آباد", "قصرالدشت"], ["بلوار معالی‌آباد، ساختمان پزشکان", "خیابان قصرالدشت، کوچه ۱۲"],
                ["معاینه و مشاوره", "جرم‌گیری", "ترمیم دندان", "ویزیت پیگیری"],
                [30, 60, 60, 30], [1500000m, 9000000m, 14000000m, 1000000m]),
            new Catalog("demo-tavan", "مرکز فیزیوتراپی توان", "فیزیوتراپی", "اصفهان",
                ["مرداویج", "جلفا"], ["خیابان مرداویج، ساختمان سلامت", "خیابان حکیم نظامی، کوچه بهار"],
                ["ارزیابی اولیه", "فیزیوتراپی کمر", "تمرین درمانی", "توان‌بخشی زانو"],
                [45, 60, 45, 60], [3500000m, 4200000m, 3000000m, 4500000m]),
            new Catalog("demo-aramesh", "مرکز مشاوره آرامش", "مشاوره", "مشهد",
                ["سجاد", "وکیل‌آباد"], ["بلوار سجاد، خیابان بهار", "بلوار وکیل‌آباد، ساختمان آفتاب"],
                ["مشاوره فردی", "مشاوره خانواده", "مشاوره تحصیلی", "جلسه پیگیری"],
                [60, 90, 60, 45], [6000000m, 9000000m, 4500000m, 4000000m]),
            new Catalog("demo-aryana", "پیرایش آریانا", "پیرایش آقایان", "کرج",
                ["عظیمیه", "گوهردشت"], ["بلوار شریعتی، میدان مهر", "خیابان اصلی، کوچه هشتم"],
                ["کوتاهی مو", "اصلاح ریش", "پاک‌سازی صورت", "پکیج داماد"],
                [45, 30, 45, 90], [2200000m, 1200000m, 2800000m, 11000000m]),
            new Catalog("demo-roshana", "مرکز تندرستی روشنا", "ماساژ و تندرستی", "رشت",
                ["گلسار", "منظریه"], ["بلوار گلسار، خیابان ۹۲", "خیابان منظریه، کوچه باران"],
                ["ماساژ سوئدی", "ماساژ ورزشی", "ماساژ پا", "جلسه ریلکسیشن"],
                [60, 60, 30, 90], [7000000m, 8000000m, 3500000m, 10000000m])
        };
        var demoBranches = new List<(Branch Branch, List<Service> Services, List<Resource> Resources)>();
        for (var c = 0; c < catalogs.Length; c++)
        {
            var catalog = catalogs[c];
            var businessId = Id(catalog.Slug);
            var business = await db.Businesses.IgnoreQueryFilters().SingleOrDefaultAsync(x => x.Id == businessId, ct);
            if (business is null)
            {
                business = new Business
                {
                    Id = businessId, Name = catalog.Name, Slug = catalog.Slug,
                    Category = catalog.Category, City = catalog.City,
                    CreatedAt = historyStart, UpdatedAt = historyStart,
                    Description = $"مرکز تخصصی {catalog.Category} با امکان رزرو آنلاین و انتخاب ارائه‌دهنده."
                };
                db.Businesses.Add(business);
            }
            if (business.DeletedAt is not null) continue;
            var serviceList = new List<Service>();
            for (var s = 0; s < 4; s++)
            {
                var serviceId = Id($"{catalog.Slug}-service-{s}");
                var service = await db.Services.IgnoreQueryFilters().SingleOrDefaultAsync(x => x.Id == serviceId, ct);
                if (service is null)
                {
                    service = new Service
                    {
                        Id = serviceId, BusinessId = business.Id, Business = business,
                        Name = catalog.Services[s], Description = "ارائه خدمت با رزرو قبلی؛ زمان شامل آماده‌سازی است.",
                        DurationMinutes = catalog.Durations[s], Price = catalog.Prices[s], Currency = "IRR",
                        CreatedAt = historyStart, UpdatedAt = historyStart
                    };
                    db.Services.Add(service);
                }
                if (service.DeletedAt is null) serviceList.Add(service);
            }
            for (var b = 0; b < 2; b++)
            {
                var branchId = Id($"{catalog.Slug}-branch-{b}");
                var branch = await db.Branches.IgnoreQueryFilters().SingleOrDefaultAsync(x => x.Id == branchId, ct);
                if (branch is null)
                {
                    branch = new Branch
                    {
                        Id = branchId, BusinessId = business.Id, Business = business,
                        Name = $"شعبه {catalog.Branches[b]}", Address = catalog.Addresses[b],
                        TimeZoneId = "Asia/Tehran", OpenHour = 9, CloseHour = 19,
                        CreatedAt = historyStart, UpdatedAt = historyStart
                    };
                    db.Branches.Add(branch);
                }
                if (branch.DeletedAt is not null) continue;
                var resources = new List<Resource>();
                for (var r = 0; r < 3; r++)
                {
                    var resourceId = Id($"{catalog.Slug}-branch-{b}-resource-{r}");
                    var resource = await db.Resources.IgnoreQueryFilters().SingleOrDefaultAsync(x => x.Id == resourceId, ct);
                    if (resource is null)
                    {
                        var nameIndex = c * 6 + b * 3 + r;
                        resource = new Resource
                        {
                            Id = resourceId, BranchId = branch.Id, Branch = branch, Kind = "staff",
                            Name = $"{firstNames[nameIndex % 12]} {lastNames[(nameIndex / 12) % 4]}",
                            CreatedAt = historyStart, UpdatedAt = historyStart
                        };
                        db.Resources.Add(resource);
                    }
                    if (resource.DeletedAt is null) resources.Add(resource);
                }
                if (serviceList.Count > 0 && resources.Count > 0)
                    demoBranches.Add((branch, serviceList, resources));
            }
        }
        await db.SaveChangesAsync(ct);
        var now = DateTimeOffset.UtcNow;
        var tehran = TimeZoneInfo.FindSystemTimeZoneById("Asia/Tehran");
        var today = TimeZoneInfo.ConvertTime(now, tehran).Date;
        var existing = await db.Appointments.IgnoreQueryFilters().ToListAsync(ct);
        var ids = existing.Select(a => a.Id).ToHashSet();
        for (var b = 0; b < demoBranches.Count; b++)
        {
            var (branch, services, resources) = demoBranches[b];
            for (var day = -30; day <= 14; day++)
            for (var slot = 0; slot < 3; slot++)
            {
                var appointmentId = Id($"appointment-{branch.Id}-{day}-{slot}");
                if (ids.Contains(appointmentId)) continue;
                var service = services[(day + 30 + slot) % services.Count];
                var resource = resources[(day + 30 + slot) % resources.Count];
                var customer = customers[((day + 30) * 7 + b * 3 + slot) % customers.Count];
                var local = DateTime.SpecifyKind(today.AddDays(day).AddHours(10 + slot * 3), DateTimeKind.Unspecified);
                var start = new DateTimeOffset(TimeZoneInfo.ConvertTimeToUtc(local, tehran));
                var end = start.AddMinutes(service.DurationMinutes);
                if (service.DurationMinutes <= 0 || local.Hour < branch.OpenHour
                    || local.AddMinutes(service.DurationMinutes).Date != local.Date
                    || local.AddMinutes(service.DurationMinutes).TimeOfDay > TimeSpan.FromHours(branch.CloseHour))
                    continue;
                // Preserve real bookings and avoid resource, branch and customer overlaps.
                if (existing.Any(a => a.DeletedAt is null && a.Status != AppointmentStatus.Cancelled
                    && (a.BranchId == branch.Id || a.ResourceId == resource.Id || a.CustomerId == customer.Id)
                    && a.StartsAt < end && start < a.EndsAt))
                    continue;
                var variation = (b + day + 30 + slot) % 12;
                var status = end <= now
                    ? (variation == 0 ? AppointmentStatus.Cancelled
                        : variation == 1 ? AppointmentStatus.NoShow : AppointmentStatus.Completed)
                    : (variation == 0 ? AppointmentStatus.Cancelled
                        : variation < 4 ? AppointmentStatus.Pending : AppointmentStatus.Confirmed);
                var appointment = new Appointment
                {
                    Id = appointmentId, BranchId = branch.Id, ServiceId = service.Id,
                    ResourceId = resource.Id, CustomerId = customer.Id,
                    StartsAt = start, EndsAt = end, Status = status,
                    FinalPrice = service.Price, Currency = service.Currency,
                    CreatedAt = start.AddDays(-7), UpdatedAt = end <= now ? end : now
                };
                // Future appointments are booked now, never in the future.
                if (appointment.CreatedAt > now)
                    appointment = new Appointment
                    {
                        Id = appointment.Id, BranchId = branch.Id, ServiceId = service.Id,
                        ResourceId = resource.Id, CustomerId = customer.Id,
                        StartsAt = start, EndsAt = end, Status = status,
                        FinalPrice = service.Price, Currency = service.Currency,
                        CreatedAt = now, UpdatedAt = now
                    };
                db.Appointments.Add(appointment);
                existing.Add(appointment);
                ids.Add(appointmentId);
            }
        }
        await db.SaveChangesAsync(ct);
    }

    private static void Check(IdentityResult result)
    {
        if (!result.Succeeded)
            throw new InvalidOperationException("ساخت حساب دمو ناموفق بود: "
                + string.Join("؛ ", result.Errors.Select(e => e.Description)));
    }

    private sealed record Catalog(string Slug, string Name, string Category, string City,
        string[] Branches, string[] Addresses, string[] Services, int[] Durations, decimal[] Prices);
}
