namespace BeNobat.Web.Domain;

/// <summary>
/// کمک‌متدهای جست‌وجوی فارسی. بسیاری از کاربران با کیبورد عربی یا کپی‌شده از جاهای دیگر
/// «ي» و «ك» تایپ می‌کنند، در حالی که داده با «ی» و «ک» ثبت شده (یا برعکس).
/// </summary>
public static class TextSearch
{
    /// <summary>یکسان‌سازی حروف عربی/فارسی و ارقام، و حذف فاصله‌های اضافه.</summary>
    public static string Normalize(string? value)
    {
        if (string.IsNullOrWhiteSpace(value)) return string.Empty;
        var chars = value.Trim().Select(c => c switch
        {
            'ي' or 'ى' => 'ی',
            'ك' => 'ک',
            'ۀ' => 'ه',
            >= '٠' and <= '٩' => (char)('۰' + (c - '٠')),
            _ => c,
        });
        return string.Join(' ', new string(chars.ToArray()).Split(' ', StringSplitOptions.RemoveEmptyEntries));
    }

    /// <summary>الگوهای LIKE برای هر دو املای فارسی و عربی (wildcardهای ورودی کاربر escape می‌شوند).</summary>
    public static string[] LikePatterns(string? value)
    {
        var persian = Normalize(value);
        if (persian.Length == 0) return [];
        var arabic = persian.Replace('ی', 'ي').Replace('ک', 'ك');
        var patterns = new List<string> { $"%{EscapeLike(persian)}%" };
        if (arabic != persian) patterns.Add($"%{EscapeLike(arabic)}%");
        return patterns.ToArray();
    }

    public static string EscapeLike(string value) =>
        value.Replace("\\", "\\\\").Replace("%", "\\%").Replace("_", "\\_");

    /// <summary>برای فیلتر در حافظه (روی لیست‌های کوچک پنل مدیریت): هر دو طرف نرمال می‌شوند.</summary>
    public static bool Matches(string? haystack, string? needle)
    {
        var n = Normalize(needle);
        if (n.Length == 0) return true;
        return Normalize(haystack).Contains(n, StringComparison.OrdinalIgnoreCase);
    }
}
