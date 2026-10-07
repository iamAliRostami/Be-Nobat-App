using System.Security.Claims;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using Microsoft.AspNetCore.Components.Authorization;
using Microsoft.EntityFrameworkCore;

namespace BeNobat.Web.Security;

/// <summary>
/// تنها مرجع مرزهای دسترسی (tenant/branch) در پنل مدیریت.
/// <list type="bullet">
/// <item>مدیر سامانه همه‌چیز را می‌بیند.</item>
/// <item>هر نقش دیگر فقط به شعبه‌هایی دسترسی دارد که با <see cref="BranchMembership"/> به آن‌ها متصل شده است
///       و <b>نقشِ همان عضویت</b> تعیین می‌کند چه کاری مجاز است (نقش سراسری Identity فقط برای ورود به صفحه‌هاست).
///       یعنی کسی که در شعبه‌ی الف «مدیر» و در شعبه‌ی ب «پرسنل» است، در شعبه‌ی ب مدیریت نمی‌کند.</item>
/// </list>
/// </summary>
public sealed class AdminAccessScope(
    AppDbContext db,
    AuthenticationStateProvider authenticationStateProvider,
    IDbContextFactory<AppDbContext>? dbFactory = null)
{
    public async Task<ClaimsPrincipal> PrincipalAsync() =>
        (await authenticationStateProvider.GetAuthenticationStateAsync()).User;

    public async Task<Guid> UserIdAsync() => UserContext.IdOf(await PrincipalAsync()) ?? Guid.Empty;

    // A Blazor circuit may outlive a role change, lockout or password reset. Claims
    // identify the session; current persisted account/roles authorize each operation.
    private async Task<IQueryable<AppUser>> SessionUsersAsync()
    {
        var principal = await PrincipalAsync();
        var id = UserContext.IdOf(principal) ?? Guid.Empty;
        var stamp = principal.FindFirst("AspNet.Identity.SecurityStamp")?.Value;
        var now = DateTimeOffset.UtcNow;
        return db.Users.AsNoTracking().Where(u => id != Guid.Empty && u.Id == id &&
            (!u.LockoutEnabled || u.LockoutEnd == null || u.LockoutEnd <= now) &&
            (stamp == null || u.SecurityStamp == stamp));
    }

    private async Task<IQueryable<AppUser>> PlatformUsersAsync() =>
        (await SessionUsersAsync()).Where(u => db.UserRoles.Any(ur => ur.UserId == u.Id &&
            db.Roles.Any(r => r.Id == ur.RoleId && r.Name == AppRoles.PlatformAdmin)));

    public async Task<bool> IsPlatformAdminAsync()
    {
        if (dbFactory is null) return await (await PlatformUsersAsync()).AnyAsync();
        await using var fresh = await dbFactory.CreateDbContextAsync();
        return await (await new AdminAccessScope(fresh, authenticationStateProvider).PlatformUsersAsync()).AnyAsync();
    }

    public async Task<bool> IsActiveUserAsync()
    {
        if (dbFactory is null) return await (await SessionUsersAsync()).AnyAsync();
        await using var fresh = await dbFactory.CreateDbContextAsync();
        return await (await new AdminAccessScope(fresh, authenticationStateProvider).SessionUsersAsync()).AnyAsync();
    }

    /// <summary>نوبت‌هایی که کاربر اجازه‌ی دیدنشان را دارد: مدیر/مالک شعبه همه‌ی نوبت‌های شعبه، پرسنل فقط نوبت‌های خودش.</summary>
    public async Task<IQueryable<Appointment>> AppointmentsAsync()
    {
        // Appointment history keeps archived labels/resources. Authorization and
        // the appointment soft-delete predicate remain explicit below.
        var query = db.Appointments.IgnoreQueryFilters();
        var platform = await PlatformUsersAsync();
        var session = await SessionUsersAsync();
        var id = await UserIdAsync();
        return query.Where(a => a.DeletedAt == null && (platform.Any() || (session.Any() && db.BranchMemberships.IgnoreQueryFilters().Any(m =>
            m.DeletedAt == null && m.Branch.Business.DeletedAt == null &&
            m.UserId == id && m.BranchId == a.BranchId &&
            (m.Role == AppRoles.Owner || m.Role == AppRoles.Manager ||
             (m.Role == AppRoles.Staff && a.Resource != null && a.Resource.BranchId == a.BranchId && a.Resource.UserId == id))))));
    }

    /// <summary>شعبه‌هایی که کاربر در آن‌ها عضو است (با هر نقش).</summary>
    public async Task<IQueryable<Branch>> BranchesAsync()
    {
        var platform = await PlatformUsersAsync();
        var session = await SessionUsersAsync();
        var id = await UserIdAsync();
        return db.Branches.Where(b => b.DeletedAt == null && (platform.Any() || (session.Any() && b.Business.DeletedAt == null &&
            db.BranchMemberships.IgnoreQueryFilters().Any(m => m.DeletedAt == null &&
                m.UserId == id && m.BranchId == b.Id && (m.Role == AppRoles.Owner || m.Role == AppRoles.Manager || m.Role == AppRoles.Staff)))));
    }

    /// <summary>شعبه‌هایی که کاربر در آن‌ها «مالک» یا «مدیر» است و می‌تواند تنظیماتشان را تغییر دهد.</summary>
    public async Task<IQueryable<Branch>> ManagedBranchesAsync(bool includeArchived = false)
    {
        var platform = await PlatformUsersAsync();
        var session = await SessionUsersAsync();
        var id = await UserIdAsync();
        return db.Branches.Where(b => (includeArchived || b.DeletedAt == null) && (platform.Any() || (session.Any() && b.Business.DeletedAt == null &&
            db.BranchMemberships.IgnoreQueryFilters().Any(m => m.DeletedAt == null &&
                m.UserId == id && m.BranchId == b.Id && (m.Role == AppRoles.Owner || m.Role == AppRoles.Manager)))));
    }

    /// <summary>
    /// کسب‌وکارهایی که کاربر می‌تواند مدیریت کند. مدیر سامانه همه‌ی کسب‌وکارها را می‌بیند، از جمله
    /// کسب‌وکارهای تازه‌ای که هنوز شعبه ندارند (قبلاً چنین کسب‌وکاری در هیچ لیستی نمی‌آمد و
    /// ساخت «شعبه‌ی اول» ممکن نبود).
    /// </summary>
    public async Task<List<Business>> ManagedBusinessesAsync()
    {
        if (await IsPlatformAdminAsync())
        {
            return await db.Businesses.OrderBy(x => x.Name).ToListAsync();
        }

        var branches = await ManagedBranchesAsync(includeArchived: true);
        var businessIds = await branches.Select(x => x.BusinessId).Distinct().ToListAsync();
        return await db.Businesses.Where(x => businessIds.Contains(x.Id)).OrderBy(x => x.Name).ToListAsync();
    }

    /// <summary>مالک بودن در یک کسب‌وکار (مدیر سامانه همواره مجاز است).</summary>
    public async Task<bool> IsBusinessOwnerAsync(Guid businessId)
    {
        if (await IsPlatformAdminAsync()) return await db.Businesses.AnyAsync(b => b.Id == businessId);
        if (!await IsActiveUserAsync()) return false;
        var id = await UserIdAsync();
        return await db.BranchMemberships.IgnoreQueryFilters().AnyAsync(m => m.DeletedAt == null &&
            m.Branch.Business.DeletedAt == null &&
            m.UserId == id && m.Role == AppRoles.Owner && m.Branch.BusinessId == businessId);
    }

    public async Task<bool> CanManageServiceAsync(Guid serviceId)
    {
        var service = await db.Services.IgnoreQueryFilters().AsNoTracking()
            .Where(s => s.Id == serviceId && s.Business.DeletedAt == null)
            .Select(s => new { s.BusinessId }).FirstOrDefaultAsync();
        if (service is null) return false;
        if (await IsBusinessOwnerAsync(service.BusinessId)) return true;
        var managed = await ManagedBranchesAsync();
        var links = db.BranchServices.IgnoreQueryFilters().Where(l => l.ServiceId == serviceId &&
            l.DeletedAt == null && l.Branch.DeletedAt == null && l.Branch.BusinessId == service.BusinessId);
        return await links.AnyAsync() && !await links.AnyAsync(l => !managed.Any(b => b.Id == l.BranchId));
    }

    public async Task<IQueryable<Review>> ReviewsAsync()
    {
        var platform = await PlatformUsersAsync();
        var managed = (await ManagedBranchesAsync(includeArchived: true)).IgnoreQueryFilters();
        return db.Reviews.IgnoreQueryFilters().Where(r => r.DeletedAt == null && (platform.Any() ||
            (r.BranchId != null && managed.Any(b => b.Id == r.BranchId))));
    }

    public async Task<IQueryable<CustomerReview>> CustomerReviewsAsync()
    {
        var platform = await PlatformUsersAsync();
        var managed = (await ManagedBranchesAsync(includeArchived: true)).IgnoreQueryFilters();
        return db.CustomerReviews.IgnoreQueryFilters().Where(r => r.DeletedAt == null &&
            (platform.Any() || managed.Any(b => b.Id == r.Appointment.BranchId)));
    }

    public async Task<bool> CanAccessBranchAsync(Guid branchId) =>
        await (await BranchesAsync()).AnyAsync(b => b.Id == branchId);

    public async Task<bool> CanManageBranchAsync(Guid branchId)
    {
        // Managers may restore their archived branch, but a paused business grants
        // no tenant access. Explicit predicates survive IgnoreQueryFilters callers.
        var platform = await PlatformUsersAsync();
        var session = await SessionUsersAsync();
        var id = await UserIdAsync();
        return await db.Branches.IgnoreQueryFilters().AnyAsync(b => b.Id == branchId &&
            (platform.Any() || (session.Any() && b.Business.DeletedAt == null &&
                db.BranchMemberships.IgnoreQueryFilters().Any(m => m.DeletedAt == null && m.UserId == id &&
                    m.BranchId == b.Id && (m.Role == AppRoles.Owner || m.Role == AppRoles.Manager)))));
    }

    public async Task<bool> CanManageAvailabilityAsync(Guid businessId, Guid? branchId, Guid? resourceId)
    {
        if (!await db.Businesses.AnyAsync(b => b.Id == businessId)) return false;
        if (branchId is null) return resourceId is null && await IsBusinessOwnerAsync(businessId);
        if (!await db.Branches.AnyAsync(b => b.Id == branchId && b.BusinessId == businessId) ||
            !await CanManageBranchAsync(branchId.Value)) return false;
        return resourceId is null || await db.Resources.AnyAsync(r => r.Id == resourceId && r.BranchId == branchId);
    }

    /// <summary>
    /// سلسله‌مراتب واگذاری نقش: «مالک» را فقط مدیر سامانه یا مالک همان کسب‌وکار می‌دهد؛
    /// «مدیر» و «پرسنل» را مدیر/مالک همان شعبه. (قبلاً یک مدیر شعبه می‌توانست خودش یا دیگری را مالک کند.)
    /// </summary>
    public async Task<bool> CanAssignRoleAsync(string role, Guid branchId)
    {
        if (!AppRoles.Team.Contains(role)) return false;
        if (!await CanManageBranchAsync(branchId) || !await db.Branches.AnyAsync(b => b.Id == branchId)) return false;
        if (await IsPlatformAdminAsync()) return true;
        if (role == AppRoles.Owner)
        {
            var businessId = await db.Branches.IgnoreQueryFilters()
                .Where(b => b.Id == branchId).Select(b => (Guid?)b.BusinessId).FirstOrDefaultAsync();
            return businessId is Guid id && await IsBusinessOwnerAsync(id);
        }

        return (role == AppRoles.Manager || role == AppRoles.Staff) && await CanManageBranchAsync(branchId);
    }

    /// <summary>آیا کاربر هدف در شعبه‌ای «مالک» است که من مالک آن نیستم؟ (برای جلوگیری از دستکاری مالک‌ها توسط مدیر)</summary>
    public async Task<bool> TargetOutranksMeAsync(Guid targetUserId, IEnumerable<Guid> branchIds)
    {
        if (await IsPlatformAdminAsync()) return false;
        if (!await IsActiveUserAsync()) return true;
        var ids = branchIds.ToList();
        var ownerBranches = await db.BranchMemberships.IgnoreQueryFilters()
            .Where(m => m.DeletedAt == null && m.UserId == targetUserId && m.Role == AppRoles.Owner && ids.Contains(m.BranchId))
            .Select(m => m.BranchId).ToListAsync();
        foreach (var branchId in ownerBranches)
        {
            var businessId = await db.Branches.IgnoreQueryFilters()
                .Where(b => b.Id == branchId).Select(b => (Guid?)b.BusinessId).FirstOrDefaultAsync();
            if (businessId is not Guid id || !await IsBusinessOwnerAsync(id)) return true;
        }

        return false;
    }

    /// <summary>منطقه‌ی زمانی نمایش برای پنل: منطقه‌ی اولین شعبه‌ی در دسترس کاربر (پیش‌فرض تهران).</summary>
    public async Task<string?> DisplayZoneAsync() =>
        await (await BranchesAsync()).OrderBy(b => b.Name).Select(b => b.TimeZoneId).FirstOrDefaultAsync();

    /// <summary>تغییر وضعیت نوبت با رعایت دسترسی و ماشین وضعیت. در صورت خطا پیام فارسی برمی‌گرداند، وگرنه null.</summary>
    public async Task<string?> ChangeStatusAsync(Guid appointmentId, AppointmentStatus to)
    {
        var allowed = await (await AppointmentsAsync()).AnyAsync(a => a.Id == appointmentId);
        if (!allowed) return "به این نوبت دسترسی ندارید.";
        var appointment = await db.Appointments.AsNoTracking().FirstOrDefaultAsync(a => a.Id == appointmentId);
        if (appointment is null) return "نوبت پیدا نشد.";
        var now = DateTimeOffset.UtcNow;
        if (!BookingPolicy.CanTransition(appointment.Status, to, appointment.StartsAt, now))
            return to is AppointmentStatus.Completed or AppointmentStatus.NoShow && appointment.StartsAt > now
                ? "تکمیل یا عدم حضور فقط پس از شروع نوبت ثبت می‌شود."
                : "این تغییر وضعیت برای نوبت مجاز نیست (وضعیت نوبت قبلاً نهایی شده است).";
        var updated = await (await AppointmentsAsync())
            .Where(a => a.Id == appointmentId && a.Status == appointment.Status && a.StartsAt == appointment.StartsAt)
            .ExecuteUpdateAsync(setters => setters.SetProperty(a => a.Status, to).SetProperty(a => a.UpdatedAt, now));
        if (updated == 0) return "وضعیت یا دسترسی این نوبت تغییر کرده است؛ فهرست را تازه کنید.";
        return null;
    }
}
