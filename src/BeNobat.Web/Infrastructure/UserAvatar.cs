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

    /// <summary>Validate the supported image signature against the declared type before storing untrusted bytes.</summary>
    public static bool ValidImage(ReadOnlySpan<byte> bytes, string? contentType)
    {
        if (bytes.Length == 0 || bytes.Length > MaxBytes) return false;
        return contentType?.Trim().ToLowerInvariant() switch
        {
            "image/png" => bytes.Length >= 8 && bytes[..8].SequenceEqual(new byte[] { 137, 80, 78, 71, 13, 10, 26, 10 }),
            "image/jpeg" => bytes.Length >= 3 && bytes[0] == 255 && bytes[1] == 216 && bytes[2] == 255,
            "image/webp" => bytes.Length >= 12 && bytes[..4].SequenceEqual("RIFF"u8) && bytes.Slice(8, 4).SequenceEqual("WEBP"u8),
            _ => false,
        };
    }

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
