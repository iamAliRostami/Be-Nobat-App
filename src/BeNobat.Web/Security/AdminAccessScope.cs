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
    AuthenticationStateProvider authenticationStateProvider)
{
    private ClaimsPrincipal? principal;
    private Guid? userId;

    public async Task<ClaimsPrincipal> PrincipalAsync() =>
        principal ??= (await authenticationStateProvider.GetAuthenticationStateAsync()).User;

    public async Task<Guid> UserIdAsync()
    {
        if (userId is not null) return userId.Value;
        userId = UserContext.IdOf(await PrincipalAsync()) ?? Guid.Empty;
        return userId.Value;
    }

    public async Task<bool> IsPlatformAdminAsync() =>
        (await PrincipalAsync()).IsInRole(AppRoles.PlatformAdmin);

    /// <summary>نوبت‌هایی که کاربر اجازه‌ی دیدنشان را دارد: مدیر/مالک شعبه همه‌ی نوبت‌های شعبه، پرسنل فقط نوبت‌های خودش.</summary>
    public async Task<IQueryable<Appointment>> AppointmentsAsync()
    {
        var query = db.Appointments.AsQueryable();
        if (await IsPlatformAdminAsync()) return query;
        var id = await UserIdAsync();
        return query.Where(a => db.BranchMemberships.Any(m =>
            m.UserId == id && m.BranchId == a.BranchId &&
            (m.Role == AppRoles.Owner || m.Role == AppRoles.Manager ||
             (m.Role == AppRoles.Staff && a.Resource != null && a.Resource.UserId == id))));
    }

    /// <summary>شعبه‌هایی که کاربر در آن‌ها عضو است (با هر نقش).</summary>
    public async Task<IQueryable<Branch>> BranchesAsync()
    {
        var query = db.Branches.AsQueryable();
        if (await IsPlatformAdminAsync()) return query;
        var id = await UserIdAsync();
        return query.Where(b => db.BranchMemberships.Any(m => m.UserId == id && m.BranchId == b.Id));
    }

    /// <summary>شعبه‌هایی که کاربر در آن‌ها «مالک» یا «مدیر» است و می‌تواند تنظیماتشان را تغییر دهد.</summary>
    public async Task<IQueryable<Branch>> ManagedBranchesAsync()
    {
        var query = db.Branches.AsQueryable();
        if (await IsPlatformAdminAsync()) return query;
        var id = await UserIdAsync();
        return query.Where(b => db.BranchMemberships.Any(m =>
            m.UserId == id && m.BranchId == b.Id && (m.Role == AppRoles.Owner || m.Role == AppRoles.Manager)));
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

        var branches = await ManagedBranchesAsync();
        var businessIds = await branches.Select(x => x.BusinessId).Distinct().ToListAsync();
        return await db.Businesses.Where(x => businessIds.Contains(x.Id)).OrderBy(x => x.Name).ToListAsync();
    }

    /// <summary>مالک بودن در یک کسب‌وکار (مدیر سامانه همواره مجاز است).</summary>
    public async Task<bool> IsBusinessOwnerAsync(Guid businessId)
    {
        if (await IsPlatformAdminAsync()) return true;
        var id = await UserIdAsync();
        return await db.BranchMemberships.AnyAsync(m =>
            m.UserId == id && m.Role == AppRoles.Owner && m.Branch.BusinessId == businessId);
    }

    public async Task<IQueryable<Review>> ReviewsAsync()
    {
        var query = db.Reviews.IgnoreQueryFilters();
        if (await IsPlatformAdminAsync()) return query;
        var id = await UserIdAsync();
        return query.Where(r => r.BranchId != null &&
            db.BranchMemberships.Any(m => m.UserId == id && m.BranchId == r.BranchId &&
                (m.Role == AppRoles.Owner || m.Role == AppRoles.Manager)));
    }

    public async Task<IQueryable<CustomerReview>> CustomerReviewsAsync()
    {
        var query = db.CustomerReviews.AsQueryable();
        if (await IsPlatformAdminAsync()) return query;
        var id = await UserIdAsync();
        return query.Where(r => db.BranchMemberships.Any(m => m.UserId == id &&
            m.BranchId == r.Appointment.BranchId && (m.Role == AppRoles.Owner || m.Role == AppRoles.Manager)));
    }

    public async Task<bool> CanAccessBranchAsync(Guid branchId)
    {
        if (await IsPlatformAdminAsync()) return true;
        var id = await UserIdAsync();
        return await db.BranchMemberships.AnyAsync(m => m.UserId == id && m.BranchId == branchId);
    }

    public async Task<bool> CanManageBranchAsync(Guid branchId)
    {
        if (await IsPlatformAdminAsync()) return true;
        var id = await UserIdAsync();
        return await db.BranchMemberships.AnyAsync(m =>
            m.UserId == id && m.BranchId == branchId && (m.Role == AppRoles.Owner || m.Role == AppRoles.Manager));
    }

    /// <summary>
    /// سلسله‌مراتب واگذاری نقش: «مالک» را فقط مدیر سامانه یا مالک همان کسب‌وکار می‌دهد؛
    /// «مدیر» و «پرسنل» را مدیر/مالک همان شعبه. (قبلاً یک مدیر شعبه می‌توانست خودش یا دیگری را مالک کند.)
    /// </summary>
    public async Task<bool> CanAssignRoleAsync(string role, Guid branchId)
    {
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
        var ids = branchIds.ToList();
        var ownerBranches = await db.BranchMemberships
            .Where(m => m.UserId == targetUserId && m.Role == AppRoles.Owner && ids.Contains(m.BranchId))
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
        var appointment = await db.Appointments.FirstOrDefaultAsync(a => a.Id == appointmentId);
        if (appointment is null) return "نوبت پیدا نشد.";
        var now = DateTimeOffset.UtcNow;
        if (!BookingPolicy.CanTransition(appointment.Status, to, appointment.StartsAt, now))
            return to is AppointmentStatus.Completed or AppointmentStatus.NoShow && appointment.StartsAt > now
                ? "تکمیل یا عدم حضور فقط پس از شروع نوبت ثبت می‌شود."
                : "این تغییر وضعیت برای نوبت مجاز نیست (وضعیت نوبت قبلاً نهایی شده است).";
        appointment.Status = to;
        appointment.UpdatedAt = now;
        await db.SaveChangesAsync();
        return null;
    }
}
