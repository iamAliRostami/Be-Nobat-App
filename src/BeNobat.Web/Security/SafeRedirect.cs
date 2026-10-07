namespace BeNobat.Web.Security;

/// <summary>
/// جلوگیری از open redirect: فقط مسیرهای محلیِ سایت پذیرفته می‌شوند.
/// قبلاً مقدار ReturnUrl بدون بررسی به NavigateTo داده می‌شد و می‌شد کاربر را بعد از ورود
/// به یک سایت بیرونی هدایت کرد.
/// </summary>
public static class SafeRedirect
{
    public static string Local(string? url, string fallback = "/")
    {
        if (string.IsNullOrWhiteSpace(url)) return fallback;
        var value = url.Trim();
        if (value.Length > 2000) return fallback;
        if (!value.StartsWith('/')) return fallback;
        if (value.StartsWith("//", StringComparison.Ordinal) || value.Contains('\\')) return fallback;
        if (value.Any(char.IsControl)) return fallback;
        return value;
    }
}
