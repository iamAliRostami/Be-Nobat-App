using BeNobat.Web.Domain;

namespace BeNobat.Web.Infrastructure;

/// <summary>
/// [feature] کمک‌متدهای مشترک عکس پروفایل. آدرس تصویر عمداً شامل ConcurrencyStamp
/// است تا بعد از آپلود عکس جدید، نسخه‌ی قدیمی از کش مرورگر نمایش داده نشود.
/// </summary>
public static class UserAvatar
{
    /// <summary>حداکثر حجم مجاز عکس پروفایل (۲ مگابایت).</summary>
    public const long MaxBytes = 2 * 1024 * 1024;

    public static readonly string[] AllowedContentTypes = ["image/png", "image/jpeg", "image/webp"];

    public static bool Has(AppUser? user) => !string.IsNullOrEmpty(user?.AvatarContentType);

    public static bool Has(string? contentType) => !string.IsNullOrEmpty(contentType);

    public static string Url(AppUser user) => Url(user.Id, user.ConcurrencyStamp);

    /// <summary>برای جاهایی که فقط ستون‌های سبک کاربر خوانده شده (بدون بارگذاری بایت‌های عکس).</summary>
    public static string Url(Guid userId, string? concurrencyStamp) =>
        $"/media/avatar/{userId}?v={Uri.EscapeDataString(concurrencyStamp ?? "0")}";

    public static string Initials(string? name)
    {
        if (string.IsNullOrWhiteSpace(name)) return "؟";
        var parts = name.Split(' ', StringSplitOptions.RemoveEmptyEntries);
        return parts.Length >= 2 ? $"{parts[0][0]}‌{parts[1][0]}" : name[..Math.Min(2, name.Length)];
    }
}
