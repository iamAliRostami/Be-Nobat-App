using System.Security.Claims;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using Microsoft.AspNetCore.Components.Authorization;
using Microsoft.EntityFrameworkCore;

namespace BeNobat.Web.Security;

/// <summary>
/// Provides the single source of truth for tenant/branch boundaries in the admin UI.
/// A platform administrator can see the whole platform; every other admin role is
/// restricted to branches explicitly assigned through <see cref="BranchMembership"/>.
/// </summary>
public sealed class AdminAccessScope(
    AppDbContext db,
    AuthenticationStateProvider authenticationStateProvider)
{
    private ClaimsPrincipal? principal;
    private Guid? userId;

    public async Task<ClaimsPrincipal> PrincipalAsync() =>
        principal ??= (await authenticationStateProvider.GetAuthenticationStateAsync()).User;

    public async Task<Guid> UserIdAsync()
    {
        if (userId is not null) return userId.Value;
        var value = (await PrincipalAsync()).FindFirst(ClaimTypes.NameIdentifier)?.Value;
        userId = Guid.TryParse(value, out var parsed) ? parsed : Guid.Empty;
        return userId.Value;
    }

    public async Task<bool> IsPlatformAdminAsync() =>
        (await PrincipalAsync()).IsInRole(AppRoles.PlatformAdmin);

    public async Task<IQueryable<Appointment>> AppointmentsAsync()
    {
        var query = db.Appointments.AsQueryable();
        if (await IsPlatformAdminAsync()) return query;
        var id = await UserIdAsync();
        var principal = await PrincipalAsync();
        if (principal.IsInRole(AppRoles.Staff) &&
            !principal.IsInRole(AppRoles.Owner) && !principal.IsInRole(AppRoles.Manager))
        {
            return query.Where(a => a.Resource != null && a.Resource.UserId == id);
        }
        return query.Where(a => db.BranchMemberships.Any(m => m.UserId == id && m.BranchId == a.BranchId));
    }

    public async Task<IQueryable<Branch>> BranchesAsync()
    {
        var query = db.Branches.AsQueryable();
        if (await IsPlatformAdminAsync()) return query;
        var id = await UserIdAsync();
        return query.Where(b => db.BranchMemberships.Any(m => m.UserId == id && m.BranchId == b.Id));
    }

    public async Task<IQueryable<Review>> ReviewsAsync()
    {
        var query = db.Reviews.IgnoreQueryFilters();
        if (await IsPlatformAdminAsync()) return query;
        var id = await UserIdAsync();
        return query.Where(r => r.BranchId != null &&
            db.BranchMemberships.Any(m => m.UserId == id && m.BranchId == r.BranchId));
    }

    public async Task<IQueryable<CustomerReview>> CustomerReviewsAsync()
    {
        var query = db.CustomerReviews.AsQueryable();
        if (await IsPlatformAdminAsync()) return query;
        var id = await UserIdAsync();
        return query.Where(r => db.BranchMemberships.Any(m => m.UserId == id && m.BranchId == r.Appointment.BranchId));
    }

    public async Task<bool> CanAccessBranchAsync(Guid branchId)
    {
        if (await IsPlatformAdminAsync()) return true;
        var id = await UserIdAsync();
        return await db.BranchMemberships.AnyAsync(m => m.UserId == id && m.BranchId == branchId);
    }
}
