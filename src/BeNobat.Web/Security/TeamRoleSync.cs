using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;

namespace BeNobat.Web.Security;

/// <summary>
/// عضویت‌های شعبه (BranchMembership) «منبع حقیقت» نقش تیمی هستند و نقش سراسری Identity
/// (Owner/Manager/Staff) فقط برای ورود به صفحه‌های پنل لازم است. این کلاس نقش سراسری را
/// بر اساس بالاترین نقش عضویت‌های فعلی هم‌گام می‌کند؛ بنابراین حذف کسی از آخرین شعبه‌اش،
/// دسترسی پنل او را هم می‌گیرد (قبلاً نقش سراسری برای همیشه می‌ماند).
/// نقش‌های PlatformAdmin و Customer دست‌نخورده می‌مانند.
/// </summary>
public static class TeamRoleSync
{
    private static readonly string[] TeamRoles = [AppRoles.Owner, AppRoles.Manager, AppRoles.Staff];

    public static int Rank(string? role) => role switch
    {
        AppRoles.Owner => 3,
        AppRoles.Manager => 2,
        AppRoles.Staff => 1,
        _ => 0,
    };

    public static async Task SyncAsync(UserManager<AppUser> users, AppDbContext db, AppUser user)
    {
        var membershipRoles = await db.BranchMemberships
            .Where(m => m.UserId == user.Id && m.DeletedAt == null)
            .Select(m => m.Role)
            .ToListAsync();

        var targetRoles = membershipRoles
            .SelectMany(r => r.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
            .Distinct()
            .Where(r => TeamRoles.Contains(r))
            .ToList();

        var current = await users.GetRolesAsync(user);
        var toRemove = current.Where(r => TeamRoles.Contains(r) && !targetRoles.Contains(r)).ToList();
        var toAdd = targetRoles.Where(r => !current.Contains(r)).ToList();

        if (toRemove.Count > 0) Ensure(await users.RemoveFromRolesAsync(user, toRemove));
        if (toAdd.Count > 0) Ensure(await users.AddToRolesAsync(user, toAdd));
        if (toRemove.Count > 0 || toAdd.Count > 0) Ensure(await users.UpdateSecurityStampAsync(user));
    }

    private static void Ensure(IdentityResult result)
    {
        if (!result.Succeeded) throw new InvalidOperationException(string.Join("; ", result.Errors.Select(e => e.Description)));
    }
}
