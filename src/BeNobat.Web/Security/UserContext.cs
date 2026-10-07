using System.Security.Claims;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using Microsoft.EntityFrameworkCore;

namespace BeNobat.Web.Security;

public static class UserContext
{
    public static Guid? IdOf(ClaimsPrincipal? user)
    {
        if (user?.Identity?.IsAuthenticated != true) return null;
        var value = user.FindFirst(ClaimTypes.NameIdentifier)?.Value;
        return Guid.TryParse(value, out var id) && id != Guid.Empty ? id : null;
    }

    /// <summary>Fresh persisted authorization for customer actions in a long-lived web circuit.</summary>
    public static IQueryable<AppUser> CurrentSessionUsers(AppDbContext db, ClaimsPrincipal? principal)
    {
        var id = IdOf(principal) ?? Guid.Empty;
        var stamp = principal?.FindFirst("AspNet.Identity.SecurityStamp")?.Value;
        var now = DateTimeOffset.UtcNow;
        return db.Users.AsNoTracking().Where(user => id != Guid.Empty && user.Id == id && stamp != null
            && user.SecurityStamp == stamp && (!user.LockoutEnabled || user.LockoutEnd == null || user.LockoutEnd <= now));
    }

    /// <summary>آیا کاربر هر نقش مدیریتی/پرسنلی دارد؟ (برای انتخاب صفحه‌ی فرود بعد از ورود)</summary>
    public static bool IsBackOffice(ClaimsPrincipal? user) =>
        user is not null && AppRoles.AppointmentManagers.Any(user.IsInRole);
}
