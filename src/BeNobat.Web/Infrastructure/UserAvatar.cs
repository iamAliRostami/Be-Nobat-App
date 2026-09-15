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

    public static string Url(AppUser user) =>
        $"/media/avatar/{user.Id}?v={Uri.EscapeDataString(user.ConcurrencyStamp ?? "0")}";

    public static string Initials(string? name)
    {
        if (string.IsNullOrWhiteSpace(name)) return "؟";
        var parts = name.Split(' ', StringSplitOptions.RemoveEmptyEntries);
        return parts.Length >= 2 ? $"{parts[0][0]}‌{parts[1][0]}" : name[..Math.Min(2, name.Length)];
    }
}
