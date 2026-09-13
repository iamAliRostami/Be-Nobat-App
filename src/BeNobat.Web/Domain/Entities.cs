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
