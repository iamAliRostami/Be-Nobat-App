namespace BeNobat.Web.Domain;

/// <summary>
/// تبدیل زمان بین «لحظه‌ی مطلق» (UTC که در دیتابیس ذخیره می‌شود) و «ساعت دیوار شعبه».
/// Npgsql مقدارهای timestamptz را همیشه با offset صفر برمی‌گرداند؛ بنابراین هر جا
/// ساعت یا روز یک نوبت به کاربر نشان داده می‌شود (یا «امروز» حساب می‌شود) باید با
/// این کلاس به منطقه‌ی زمانی شعبه تبدیل شود، وگرنه ساعت‌ها ۳:۳۰ ساعت عقب نمایش داده می‌شوند.
/// </summary>
public static class BranchClock
{
    public const string DefaultZoneId = "Asia/Tehran";

    /// <summary>اگر tzdata روی سرور نبود، تهران با offset ثابت +۰۳:۳۰ (بدون ساعت تابستانی) در نظر گرفته می‌شود.</summary>
    private static readonly TimeZoneInfo TehranFallback =
        TimeZoneInfo.CreateCustomTimeZone(DefaultZoneId, TimeSpan.FromMinutes(210), "Tehran", "Tehran");

    public static TimeZoneInfo Zone(string? zoneId)
    {
        if (!string.IsNullOrWhiteSpace(zoneId))
        {
            try
            {
                return TimeZoneInfo.FindSystemTimeZoneById(zoneId);
            }
            catch (Exception ex) when (ex is TimeZoneNotFoundException or InvalidTimeZoneException)
            {
                // به منطقه‌ی پیش‌فرض برمی‌گردیم.
            }
        }

        try
        {
            return TimeZoneInfo.FindSystemTimeZoneById(DefaultZoneId);
        }
        catch (Exception ex) when (ex is TimeZoneNotFoundException or InvalidTimeZoneException)
        {
            return TehranFallback;
        }
    }

    public static DateTimeOffset ToLocal(DateTimeOffset value, string? zoneId) =>
        TimeZoneInfo.ConvertTime(value, Zone(zoneId));

    public static DateOnly LocalDate(DateTimeOffset value, string? zoneId) =>
        DateOnly.FromDateTime(ToLocal(value, zoneId).DateTime);

    public static TimeOnly LocalTime(DateTimeOffset value, string? zoneId) =>
        TimeOnly.FromDateTime(ToLocal(value, zoneId).DateTime);

    public static DateOnly Today(string? zoneId) => LocalDate(DateTimeOffset.UtcNow, zoneId);

    /// <summary>لحظه‌ی (UTC) متناظر با یک تاریخ و ساعت دیواری در منطقه‌ی شعبه.</summary>
    public static DateTimeOffset FromLocal(DateOnly date, TimeOnly time, string? zoneId)
    {
        var instants = LocalInstants(date.ToDateTime(time, DateTimeKind.Unspecified), Zone(zoneId));
        if (instants.Count == 0) throw new ArgumentException("The local time does not exist in the branch time zone.", nameof(time));
        return instants[0];
    }

    /// <summary>Nonexistent clock times have no instant; repeated clock times have two, ordered by UTC.</summary>
    public static IReadOnlyList<DateTimeOffset> LocalInstants(DateTime local, TimeZoneInfo zone)
    {
        local = DateTime.SpecifyKind(local, DateTimeKind.Unspecified);
        if (zone.IsInvalidTime(local)) return [];
        var offsets = zone.IsAmbiguousTime(local) ? zone.GetAmbiguousTimeOffsets(local) : [zone.GetUtcOffset(local)];
        return offsets.Select(offset => new DateTimeOffset(local, offset).ToUniversalTime()).OrderBy(x => x).ToList();
    }

    /// <summary>بازه‌ی UTC که کل یک روز محلی شعبه را می‌پوشاند: [شروع، پایان).</summary>
    public static (DateTimeOffset Start, DateTimeOffset End) DayWindow(DateOnly date, string? zoneId) => DayWindow(date, Zone(zoneId));

    public static (DateTimeOffset Start, DateTimeOffset End) DayWindow(DateOnly date, TimeZoneInfo zone) =>
        (DayBoundary(date, zone), DayBoundary(date.AddDays(1), zone));

    private static DateTimeOffset DayBoundary(DateOnly date, TimeZoneInfo zone)
    {
        var local = date.ToDateTime(TimeOnly.MinValue, DateTimeKind.Unspecified);
        // Some zones jump at midnight, or skip a whole date. The day begins at the first valid instant.
        while (zone.IsInvalidTime(local)) local = local.AddMinutes(1);
        return LocalInstants(local, zone)[0];
    }

    public static string FormatTime(DateTimeOffset value, string? zoneId, string language) =>
        LocalizedDate.FormatTime(ToLocal(value, zoneId), language);

    public static string FormatDate(DateTimeOffset value, string? zoneId, string language) =>
        LocalizedDate.Format(LocalDate(value, zoneId), language);

    /// <summary>مثلاً «۱۱ مهر ۱۴۰۵، ساعت ۱۶:۳۰» در تقویم زبان فعال.</summary>
    public static string FormatDateTime(DateTimeOffset value, string? zoneId, string language)
    {
        var date = LocalDate(value, zoneId);
        var parts = LocalizedDate.DayAndMonth(date, language);
        var year = LocalizedDate.FormatNumber(LocalizedDate.GetParts(date, language).Year, language);
        var time = FormatTime(value, zoneId, language);
        return language switch
        {
            "en" => $"{parts.Day} {parts.Month} {year} at {time}",
            "ar" => $"{parts.Day} {parts.Month} {year} هـ، الساعة {time}",
            _ => $"{parts.Day} {parts.Month} {year}، ساعت {time}",
        };
    }

    /// <summary>روز هفته و تاریخ کوتاه برای کارت‌ها، مثلاً «یکشنبه ۱۱ مهر».</summary>
    public static string FormatDayLabel(DateTimeOffset value, string? zoneId, string language)
    {
        var date = LocalDate(value, zoneId);
        var parts = LocalizedDate.DayAndMonth(date, language);
        return $"{LocalizedDate.Weekday(date, language)} {parts.Day} {parts.Month}";
    }
}
