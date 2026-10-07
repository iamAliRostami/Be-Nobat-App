using System.Security.Claims;

namespace BeNobat.Web.Security;

public static class UserContext
{
    public static Guid? IdOf(ClaimsPrincipal? user)
    {
        var value = user?.FindFirst(ClaimTypes.NameIdentifier)?.Value;
        return Guid.TryParse(value, out var id) ? id : null;
    }

    /// <summary>آیا کاربر هر نقش مدیریتی/پرسنلی دارد؟ (برای انتخاب صفحه‌ی فرود بعد از ورود)</summary>
    public static bool IsBackOffice(ClaimsPrincipal? user) =>
        user is not null && AppRoles.AppointmentManagers.Any(user.IsInRole);
}
