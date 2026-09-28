using System.Globalization;

namespace BeNobat.Web.Domain;

public static class LocalizedDate
{
    private static readonly PersianCalendar Persian = new();
    private static readonly UmAlQuraCalendar Hijri = new();
    private static readonly string[] PersianMonths = ["فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"];
    private static readonly string[] ArabicMonths = ["محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"];

    public static string Format(DateOnly date, string language)
    {
        var value = date.ToDateTime(TimeOnly.MinValue);
        return language switch
        {
            "fa" => $"{Persian.GetYear(value):0000}/{Persian.GetMonth(value):00}/{Persian.GetDayOfMonth(value):00}",
            "ar" => $"{Hijri.GetYear(value):0000}/{Hijri.GetMonth(value):00}/{Hijri.GetDayOfMonth(value):00} هـ",
            _ => value.ToString("yyyy/MM/dd", CultureInfo.InvariantCulture),
        };
    }

    public static (string Day, string Month) DayAndMonth(DateOnly date, string language)
    {
        var value = date.ToDateTime(TimeOnly.MinValue);
        return language switch
        {
            "fa" => (Persian.GetDayOfMonth(value).ToString(CultureInfo.InvariantCulture), PersianMonths[Persian.GetMonth(value) - 1]),
            "ar" => (Hijri.GetDayOfMonth(value).ToString(CultureInfo.InvariantCulture), ArabicMonths[Hijri.GetMonth(value) - 1]),
            _ => (date.Day.ToString(CultureInfo.InvariantCulture), value.ToString("MMM", CultureInfo.InvariantCulture)),
        };
    }

    public static string Weekday(DateOnly date, string language, bool isToday = false)
    {
        if (isToday) return language switch { "en" => "Today", "ar" => "اليوم", _ => "امروز" };
        var culture = CultureInfo.GetCultureInfo(language switch { "en" => "en-US", "ar" => "ar-SA", _ => "fa-IR" });
        return culture.DateTimeFormat.GetDayName(date.DayOfWeek);
    }
}
