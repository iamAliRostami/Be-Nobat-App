using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using BeNobat.Web.Security;
using Microsoft.EntityFrameworkCore;

namespace BeNobat.Web.Api;

public sealed record ApiError(string Code, string Message, object? Fields = null);
public sealed record ApiPage<T>(IReadOnlyList<T> Items, int Page, int PageSize, int Total);
public static class ApiResults
{
    public static IResult Error(string code, string message, int status = 400, object? fields = null) =>
        Results.Json(new ApiError(code, message, fields), statusCode: status);
    public static (int Page, int PageSize) Pagination(int? page, int? pageSize) =>
        (Math.Clamp(page ?? 1, 1, 100000), Math.Clamp(pageSize ?? 20, 1, 100));
    public static Guid[]? ServiceIds(string? value)
    {
        if (string.IsNullOrWhiteSpace(value)) return null;
        var values = value.Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries);
        if (values.Length is 0 or > 50) return null;
        var ids = new List<Guid>();
        foreach (var item in values) { if (!Guid.TryParse(item, out var id) || id == Guid.Empty) return null; ids.Add(id); }
        return ids.Distinct().ToArray();
    }
}

/// <summary>HTTP scopes always use the current database memberships. A role in another branch grants no access here.</summary>
public sealed class ApiScope(AppDbContext db, IHttpContextAccessor accessor)
{
    public Guid UserId => UserContext.IdOf(accessor.HttpContext?.User ?? new()) ?? Guid.Empty;
    public bool IsPlatform => accessor.HttpContext?.User.IsInRole(AppRoles.PlatformAdmin) == true;
    public IQueryable<Branch> Branches(bool managed = false)
    {
        var id = UserId;
        var query = db.Branches.Where(b => b.DeletedAt == null && b.Business.DeletedAt == null);
        return IsPlatform ? query : query.Where(b => db.BranchMemberships.Any(m => m.DeletedAt == null && m.UserId == id && m.BranchId == b.Id &&
            ((m.Role == AppRoles.Owner || m.Role == AppRoles.Manager) || (!managed && m.Role == AppRoles.Staff))));
    }
    public IQueryable<Business> Businesses(bool managed = false)
    {
        var branches = Branches(managed);
        return IsPlatform ? db.Businesses.Where(b => b.DeletedAt == null) : db.Businesses.Where(b => b.DeletedAt == null && branches.Any(branch => branch.BusinessId == b.Id));
    }
    public IQueryable<Appointment> Appointments()
    {
        var id = UserId;
        // Catalog filters govern new bookings. History keeps archived branch,
        // service and provider metadata while the actor's membership stays current.
        var query = db.Appointments.IgnoreQueryFilters().Where(a => a.DeletedAt == null);
        return IsPlatform ? query : query.Where(a => db.BranchMemberships.IgnoreQueryFilters().Any(m =>
            m.DeletedAt == null && m.UserId == id && m.BranchId == a.BranchId && m.Branch.Business.DeletedAt == null &&
            (m.Role == AppRoles.Owner || m.Role == AppRoles.Manager ||
             (m.Role == AppRoles.Staff && a.Resource != null && a.Resource.BranchId == a.BranchId && a.Resource.UserId == id))));
    }
    public Task<bool> CanManageBranch(Guid id) => Branches(true).AnyAsync(b => b.Id == id);
    public Task<bool> CanManageBusiness(Guid id) => Businesses(true).AnyAsync(b => b.Id == id);
    public async Task<bool> CanManageService(Guid serviceId, CancellationToken ct = default)
    {
        var service = await db.Services.IgnoreQueryFilters().Where(s => s.Id == serviceId && s.Business.DeletedAt == null)
            .Select(s => new { s.BusinessId }).SingleOrDefaultAsync(ct);
        if (service is null) return false;
        if (await CanOwnBusiness(service.BusinessId)) return true;
        var managed = Branches(true);
        var links = db.BranchServices.IgnoreQueryFilters().Where(l => l.ServiceId == serviceId && l.DeletedAt == null &&
            l.Branch.DeletedAt == null && l.Branch.BusinessId == service.BusinessId);
        return await links.AnyAsync(ct) && !await links.AnyAsync(l => !managed.Any(b => b.Id == l.BranchId), ct);
    }
    public async Task<bool> CanOwnBusiness(Guid id)
    {
        if (!await db.Businesses.AnyAsync(b => b.Id == id && b.DeletedAt == null)) return false;
        var user = UserId;
        return IsPlatform || await db.BranchMemberships.AnyAsync(m => m.DeletedAt == null && m.UserId == user && m.Role == AppRoles.Owner && m.Branch.BusinessId == id && m.Branch.DeletedAt == null && m.Branch.Business.DeletedAt == null);
    }
    public async Task<bool> CanAssignRole(string role, Guid branchId)
    {
        if (role is not (AppRoles.Owner or AppRoles.Manager or AppRoles.Staff)) return false;
        var business = await db.Branches.Where(b => b.Id == branchId && b.Business.DeletedAt == null).Select(b => (Guid?)b.BusinessId).FirstOrDefaultAsync();
        return business is Guid id && (role == AppRoles.Owner ? await CanOwnBusiness(id) : await CanManageBranch(branchId));
    }
    public IQueryable<Review> Reviews()
    {
        var id = UserId;
        // Deleted reviews remain available to the explicit restore endpoint; list
        // callers independently select active rows unless includeInactive is set.
        var query = db.Reviews.IgnoreQueryFilters();
        return IsPlatform ? query : query.Where(r => r.BranchId != null && db.BranchMemberships.IgnoreQueryFilters().Any(m =>
            m.DeletedAt == null && m.UserId == id && m.BranchId == r.BranchId && m.Branch.BusinessId == r.BusinessId &&
            m.Branch.Business.DeletedAt == null && (m.Role == AppRoles.Owner || m.Role == AppRoles.Manager)));
    }
    public IQueryable<CustomerReview> CustomerReviews()
    {
        var id = UserId;
        var query = db.CustomerReviews.IgnoreQueryFilters().Where(r => r.DeletedAt == null);
        return IsPlatform ? query : query.Where(r => db.BranchMemberships.IgnoreQueryFilters().Any(m =>
            m.DeletedAt == null && m.UserId == id && m.BranchId == r.Appointment.BranchId && m.Branch.Business.DeletedAt == null &&
            (m.Role == AppRoles.Owner || m.Role == AppRoles.Manager)));
    }
}

public sealed record UserDto(Guid Id, string DisplayName, string? Email, string? PhoneNumber, string? AvatarUrl, IReadOnlyList<string> Roles);
public sealed record BusinessDto(Guid Id, string Name, string Slug, string Category, string City, string Description, bool RequiresApproval, int BranchCount, int ServiceCount, double? Rating, int ReviewCount);
public sealed record BranchDto(Guid Id, Guid BusinessId, string Name, string Address, string TimeZoneId, int OpenHour, int CloseHour);
public sealed record ServiceDto(Guid Id, Guid BusinessId, string Name, string Description, int DurationMinutes, decimal Price, string Currency);
public sealed record ProviderDto(Guid Id, Guid BranchId, string Name, string Kind, Guid? UserId);
public sealed record PublicReviewDto(Guid Id, int Rating, string Comment, string? ManagerReply, string CustomerName, DateTimeOffset CreatedAt);
public sealed record AppointmentDto(Guid Id, Guid BusinessId, string BusinessName, Guid BranchId, string BranchName, string TimeZoneId,
    IReadOnlyList<string> ServiceNames, Guid? ResourceId, string? ResourceName, DateTimeOffset StartsAt, DateTimeOffset EndsAt,
    string Status, decimal FinalPrice, string Currency, string CustomerNote, string TrackingCode, bool CanCancel, bool CanReview, Guid? ReviewId);
