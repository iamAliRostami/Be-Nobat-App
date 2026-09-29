using System.Globalization;

namespace BeNobat.Web.Domain;

public static class LocalizedDate
{
    private static readonly PersianCalendar Persian = new();
    private static readonly UmAlQuraCalendar Hijri = new();
    private static readonly string[] PersianMonths = ["فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"];
    private static readonly string[] ArabicMonths = ["محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"];

    public static (int Year, int Month, int Day) GetParts(DateOnly date, string language)
    {
        var value = date.ToDateTime(TimeOnly.MinValue);
        return language switch
        {
            "fa" => (Persian.GetYear(value), Persian.GetMonth(value), Persian.GetDayOfMonth(value)),
            "ar" => (Hijri.GetYear(value), Hijri.GetMonth(value), Hijri.GetDayOfMonth(value)),
            _ => (date.Year, date.Month, date.Day),
        };
    }

    public static DateOnly FromParts(int year, int month, int day, string language)
    {
        var calendar = language switch { "fa" => (Calendar)Persian, "ar" => Hijri, _ => new GregorianCalendar() };
        day = Math.Min(day, calendar.GetDaysInMonth(year, month));
        return DateOnly.FromDateTime(calendar.ToDateTime(year, month, day, 0, 0, 0, 0));
    }

    public static int DaysInMonth(int year, int month, string language)
    {
        var calendar = language switch { "fa" => (Calendar)Persian, "ar" => Hijri, _ => new GregorianCalendar() };
        return calendar.GetDaysInMonth(year, month);
    }

    public static string MonthName(int month, string language) => language switch
    {
        "fa" => PersianMonths[month - 1],
        "ar" => ArabicMonths[month - 1],
        _ => CultureInfo.InvariantCulture.DateTimeFormat.GetMonthName(month),
    };

    public static string Format(DateOnly date, string language)
    {
        var value = date.ToDateTime(TimeOnly.MinValue);
        return language switch
        {
            "fa" => LocalizeDigits($"{Persian.GetYear(value):0000}/{Persian.GetMonth(value):00}/{Persian.GetDayOfMonth(value):00}", "fa"),
            "ar" => LocalizeDigits($"{Hijri.GetYear(value):0000}/{Hijri.GetMonth(value):00}/{Hijri.GetDayOfMonth(value):00} هـ", "ar"),
            _ => value.ToString("yyyy/MM/dd", CultureInfo.InvariantCulture),
        };
    }

    public static (string Day, string Month) DayAndMonth(DateOnly date, string language)
    {
        var value = date.ToDateTime(TimeOnly.MinValue);
        return language switch
        {
            "fa" => (LocalizeDigits(Persian.GetDayOfMonth(value).ToString(CultureInfo.InvariantCulture), "fa"), PersianMonths[Persian.GetMonth(value) - 1]),
            "ar" => (LocalizeDigits(Hijri.GetDayOfMonth(value).ToString(CultureInfo.InvariantCulture), "ar"), ArabicMonths[Hijri.GetMonth(value) - 1]),
            _ => (date.Day.ToString(CultureInfo.InvariantCulture), value.ToString("MMM", CultureInfo.InvariantCulture)),
        };
    }

    public static string Weekday(DateOnly date, string language, bool isToday = false)
    {
        if (isToday) return language switch { "en" => "Today", "ar" => "اليوم", _ => "امروز" };
        var culture = CultureInfo.GetCultureInfo(language switch { "en" => "en-US", "ar" => "ar-SA", _ => "fa-IR" });
        return culture.DateTimeFormat.GetDayName(date.DayOfWeek);
    }

    public static string FormatTime(TimeOnly time, string language) => LocalizeDigits(time.ToString("HH:mm", CultureInfo.InvariantCulture), language);

    public static string FormatTime(DateTimeOffset value, string language) => LocalizeDigits(value.ToString("HH:mm", CultureInfo.InvariantCulture), language);

    public static string FormatNumber(IFormattable value, string language, string? format = null) =>
        LocalizeDigits(value.ToString(format, CultureInfo.InvariantCulture) ?? "", language);

    public static string LocalizeDigits(string value, string language)
    {
        const string latin = "0123456789";
        var target = language switch { "fa" => "۰۱۲۳۴۵۶۷۸۹", "ar" => "٠١٢٣٤٥٦٧٨٩", _ => latin };
        if (target == latin) return value;
        return string.Concat(value.Select(character => char.IsAsciiDigit(character) ? target[character - '0'] : character));
    }
}
