namespace BeNobat.Web.Infrastructure;

/// <summary>The validated display preference is captured for this HTTP request/Blazor circuit.</summary>
public sealed class UiLocale(IHttpContextAccessor context)
{
    public const string CookieName = "benobat-language";
    public string Language { get; } = Normalize(context.HttpContext?.Request.Cookies[CookieName]);
    public string Direction => Language == "en" ? "ltr" : "rtl";
    public static string Normalize(string? language) => language is "fa" or "ar" or "en" ? language : "fa";
}
