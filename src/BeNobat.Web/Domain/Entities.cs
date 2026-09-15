using Microsoft.AspNetCore.Identity;

namespace BeNobat.Web.Domain;

public abstract class Entity
{
    public Guid Id { get; init; } = Guid.CreateVersion7();
    public DateTimeOffset CreatedAt { get; init; } = DateTimeOffset.UtcNow;
    public DateTimeOffset UpdatedAt { get; set; } = DateTimeOffset.UtcNow;
    public DateTimeOffset? DeletedAt { get; set; }
}

public sealed class AppUser : IdentityUser<Guid>
{
    public string DisplayName { get; set; } = string.Empty;

    // [fix] عکس پروفایل داخل دیتابیس نگهداری می‌شود. نوشتن روی فایل‌سیستم در
    // کانتینر (که با کاربر غیر root اجرا می‌شود) مجوز ندارد و با هر بازساخت
    // image هم پاک می‌شد.
    public string? AvatarContentType { get; set; }
    public byte[]? AvatarData { get; set; }

    public ICollection<BranchMembership> BranchMemberships { get; } = [];
    public ICollection<Review> Reviews { get; } = [];
}

public sealed class Business : Entity
{
    public required string Name { get; set; }
    public string Slug { get; set; } = string.Empty;
    public string Category { get; set; } = "عمومی";
    public string City { get; set; } = "تهران";
    public string Description { get; set; } = string.Empty;
    public ICollection<Branch> Branches { get; } = [];
    public ICollection<Service> Services { get; } = [];
    public ICollection<AvailabilityRule> AvailabilityRules { get; } = [];
    public ICollection<Review> Reviews { get; } = [];
}

public sealed class Branch : Entity
{
    public Guid BusinessId { get; set; }
    public required string Name { get; set; }
    public string Address { get; set; } = string.Empty;
    public string TimeZoneId { get; set; } = "Asia/Tehran";
    public int OpenHour { get; set; } = 9;
    public int CloseHour { get; set; } = 18;
    public Business Business { get; set; } = null!;
    public ICollection<Resource> Resources { get; } = [];

    // [fix] این سه نویگیشن در AppDbContext با WithMany(...) استفاده شده بودند
    // ولی روی موجودیت تعریف نشده بودند؛ پروژه کامپایل نمی‌شد.
    public ICollection<BranchMembership> Memberships { get; } = [];
    public ICollection<AvailabilityRule> AvailabilityRules { get; } = [];
    public ICollection<Review> Reviews { get; } = [];
}

public sealed class Service : Entity
{
    public Guid BusinessId { get; set; }
    public required string Name { get; set; }
    public string Description { get; set; } = string.Empty;
    public int DurationMinutes { get; set; } = 30;
    public decimal Price { get; set; }
    public string Currency { get; set; } = "IRR";
    public Business Business { get; set; } = null!;
}

public sealed class Resource : Entity
{
    public Guid BranchId { get; set; }
    public required string Name { get; set; }
    public string Kind { get; set; } = "staff";

    // [fix] منبع از نوع «عضو تیم» حالا می‌تواند به یک حساب کاربری واقعی وصل شود
    // تا در صفحه‌ی شعب از لیست کشویی انتخاب شود، نه به‌صورت متن آزاد.
    public Guid? UserId { get; set; }
    public AppUser? User { get; set; }

    public Branch Branch { get; set; } = null!;
}

public sealed class Appointment : Entity
{
    public Guid BranchId { get; set; }
    public Guid ServiceId { get; set; }
    public Guid? ResourceId { get; set; }
    public Guid CustomerId { get; set; }
    public DateTimeOffset StartsAt { get; set; }
    public DateTimeOffset EndsAt { get; set; }
    public AppointmentStatus Status { get; set; } = AppointmentStatus.Pending;
    public decimal FinalPrice { get; set; }
    public string Currency { get; set; } = "IRR";

    public Branch Branch { get; set; } = null!;
    public Service Service { get; set; } = null!;
    public Resource? Resource { get; set; }
    public AppUser Customer { get; set; } = null!;
}

public enum AppointmentStatus { Pending, Confirmed, Completed, Cancelled, NoShow }

public sealed class BranchMembership : Entity
{
    public Guid BranchId { get; set; }
    public Guid UserId { get; set; }
    public string Role { get; set; } = "Staff";
    public Branch Branch { get; set; } = null!;
    public AppUser User { get; set; } = null!;
}

/// <summary>A recurring opening interval. A null BranchId applies to every branch in the business.</summary>
public sealed class AvailabilityRule : Entity
{
    public Guid BusinessId { get; set; }
    public Guid? BranchId { get; set; }
    public DayOfWeek DayOfWeek { get; set; }
    public TimeOnly StartsAt { get; set; } = new(9, 0);
    public TimeOnly EndsAt { get; set; } = new(18, 0);
    public bool IsAvailable { get; set; } = true;
    public Business Business { get; set; } = null!;
    public Branch? Branch { get; set; }
}

/// <summary>نظر مشتری درباره‌ی کسب‌وکار/خدمت.</summary>
public sealed class Review : Entity
{
    public Guid BusinessId { get; set; }
    public Guid? BranchId { get; set; }
    public Guid CustomerId { get; set; }
    public Guid? AppointmentId { get; set; }
    public int Rating { get; set; }
    public string Comment { get; set; } = string.Empty;
    public string? ManagerReply { get; set; }
    public ReviewStatus Status { get; set; } = ReviewStatus.Pending;
    public Business Business { get; set; } = null!;
    public Branch? Branch { get; set; }
    public AppUser Customer { get; set; } = null!;
    public Appointment? Appointment { get; set; }
}

/// <summary>
/// [feature] امتیاز و یادداشت مجموعه درباره‌ی «سرویس‌گیرنده». جدا از Review نگه
/// داشته شده تا جدول نظرهای عمومی دست‌نخورده بماند؛ این رکوردها هرگز برای
/// عموم نمایش داده نمی‌شوند و فقط در پنل مدیریت دیده می‌شوند.
/// </summary>
public sealed class CustomerReview : Entity
{
    public Guid AppointmentId { get; set; }
    public Guid BusinessId { get; set; }
    public Guid CustomerId { get; set; }
    public Guid AuthorId { get; set; }
    public int Rating { get; set; }
    public string Comment { get; set; } = string.Empty;
    public Appointment Appointment { get; set; } = null!;
    public Business Business { get; set; } = null!;
    public AppUser Customer { get; set; } = null!;
    public AppUser Author { get; set; } = null!;
}

public enum ReviewStatus { Pending, Published, Rejected }
