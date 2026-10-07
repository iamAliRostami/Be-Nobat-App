using BeNobat.Web.Domain;
using Xunit;

namespace BeNobat.Web.Tests;

public sealed class LocalizedDateTests
{
    [Theory]
    [InlineData("fa")]
    [InlineData("ar")]
    public void Uninitialized_dates_never_crash_localized_rendering(string language)
    {
        Assert.Equal("—", LocalizedDate.Format(DateOnly.MinValue, language));
        Assert.Equal(("—", "—"), LocalizedDate.DayAndMonth(DateOnly.MinValue, language));
        var (year, month, day) = LocalizedDate.GetParts(DateOnly.MinValue, language);
        Assert.Equal(LocalizedDate.MinSupported(language), LocalizedDate.FromParts(year, month, day, language));
    }

    [Fact]
    public void Arabic_calendar_clamps_picker_but_does_not_mislabel_unsupported_dates()
    {
        Assert.Equal("—", LocalizedDate.Format(DateOnly.MaxValue, "ar"));
        Assert.Equal(LocalizedDate.MaxSupported("ar"), LocalizedDate.Clamp(DateOnly.MaxValue, "ar"));
        Assert.Equal("9999/12/31", LocalizedDate.Format(DateOnly.MaxValue, "en"));
    }

    [Fact]
    public void Persian_parts_match_a_known_date()
    {
        // 3 Oct 2026 is 11 Mehr 1405 (what the booking calendar shows for that day).
        Assert.Equal((1405, 7, 11), LocalizedDate.GetParts(new DateOnly(2026, 10, 3), "fa"));
    }

    [Theory]
    [InlineData("fa")]
    [InlineData("ar")]
    [InlineData("en")]
    public void Parts_round_trip_in_every_calendar(string language)
    {
        foreach (var date in new[] { new DateOnly(2026, 9, 29), new DateOnly(2026, 12, 31), new DateOnly(2027, 3, 21) })
        {
            var (year, month, day) = LocalizedDate.GetParts(date, language);
            Assert.Equal(date, LocalizedDate.FromParts(year, month, day, language));
        }
    }

    [Fact]
    public void Adding_months_rolls_over_year_boundaries()
    {
        Assert.Equal((1406, 1), LocalizedDate.AddMonths(1405, 12, 1));
        Assert.Equal((1405, 12), LocalizedDate.AddMonths(1406, 1, -1));
        Assert.Equal((2027, 1), LocalizedDate.AddMonths(2026, 12, 1));
        Assert.Equal((2026, 12), LocalizedDate.AddMonths(2027, 1, -1));
        Assert.Equal((1405, 7), LocalizedDate.AddMonths(1405, 7, 0));
    }

    [Fact]
    public void Only_the_persian_week_starts_on_saturday()
    {
        Assert.Equal(DayOfWeek.Saturday, LocalizedDate.FirstDayOfWeek("fa"));
        Assert.Equal(DayOfWeek.Sunday, LocalizedDate.FirstDayOfWeek("ar"));
        Assert.Equal(DayOfWeek.Sunday, LocalizedDate.FirstDayOfWeek("en"));
    }

    [Theory]
    [InlineData("fa")]
    [InlineData("ar")]
    [InlineData("en")]
    public void Short_weekday_labels_are_distinct(string language)
    {
        var labels = Enum.GetValues<DayOfWeek>().Select(day => LocalizedDate.ShortWeekday(day, language)).ToList();
        Assert.Equal(7, labels.Distinct().Count());
    }

    [Fact]
    public void Month_and_year_use_the_language_digits()
    {
        Assert.Equal("مهر ۱۴۰۵", LocalizedDate.MonthYear(1405, 7, "fa"));
        Assert.Equal("October 2026", LocalizedDate.MonthYear(2026, 10, "en"));
    }

    [Fact]
    public void Choose_falls_back_to_persian()
    {
        Assert.Equal("الف", LocalizedDate.Choose("fa", "الف", "ب", "c"));
        Assert.Equal("ب", LocalizedDate.Choose("ar", "الف", "ب", "c"));
        Assert.Equal("c", LocalizedDate.Choose("en", "الف", "ب", "c"));
        Assert.Equal("الف", LocalizedDate.Choose("de", "الف", "ب", "c"));
    }
}
