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
    public ICollection<Branch> Branches { get; } = [];
}

public sealed class Branch : Entity
{
    public Guid BusinessId { get; set; }
    public required string Name { get; set; }
    public string TimeZoneId { get; set; } = "Asia/Tehran";
    public Business Business { get; set; } = null!;
}

public sealed class Service : Entity
{
    public Guid BusinessId { get; set; }
    public required string Name { get; set; }
    public int DurationMinutes { get; set; }
    public decimal Price { get; set; }
    public string Currency { get; set; } = "IRR";
}

public sealed class Resource : Entity
{
    public Guid BranchId { get; set; }
    public required string Name { get; set; }
    public string Kind { get; set; } = "staff";
}

public sealed class Appointment : Entity
{
    public Guid BranchId { get; set; }
    public Guid CustomerId { get; set; }
    public DateTimeOffset StartsAt { get; set; }
    public DateTimeOffset EndsAt { get; set; }
    public AppointmentStatus Status { get; set; } = AppointmentStatus.Pending;
    public decimal FinalPrice { get; set; }
    public string Currency { get; set; } = "IRR";
}

public enum AppointmentStatus { Pending, Confirmed, Completed, Cancelled, NoShow }
