using BeNobat.Web.Domain;
using BeNobat.Web.Security;
using Xunit;

namespace BeNobat.Web.Tests;

public sealed class ReviewHardeningTests
{
    private static readonly DateTimeOffset Now = new(2026, 1, 10, 8, 0, 0, TimeSpan.Zero);

    [Theory]
    [InlineData(AppointmentStatus.Pending, AppointmentStatus.Confirmed, true)]
    [InlineData(AppointmentStatus.Pending, AppointmentStatus.Cancelled, true)]
    [InlineData(AppointmentStatus.Confirmed, AppointmentStatus.Cancelled, true)]
    [InlineData(AppointmentStatus.Cancelled, AppointmentStatus.Confirmed, false)]
    [InlineData(AppointmentStatus.Completed, AppointmentStatus.Cancelled, false)]
    [InlineData(AppointmentStatus.Pending, AppointmentStatus.Pending, false)]
    public void Transition_rules(AppointmentStatus from, AppointmentStatus to, bool expected) =>
        Assert.Equal(expected, BookingPolicy.CanTransition(from, to, Now.AddHours(5), Now));

    [Fact]
    public void Completion_requires_the_appointment_to_have_started()
    {
        Assert.False(BookingPolicy.CanTransition(AppointmentStatus.Confirmed, AppointmentStatus.Completed, Now.AddHours(1), Now));
        Assert.True(BookingPolicy.CanTransition(AppointmentStatus.Confirmed, AppointmentStatus.Completed, Now.AddHours(-1), Now));
        Assert.True(BookingPolicy.CanTransition(AppointmentStatus.Confirmed, AppointmentStatus.NoShow, Now.AddHours(-1), Now));
    }

    [Fact]
    public void Branch_clock_converts_between_utc_and_tehran()
    {
        var utc = new DateTimeOffset(2026, 1, 10, 20, 30, 0, TimeSpan.Zero);
        Assert.Equal(new DateOnly(2026, 1, 11), BranchClock.LocalDate(utc, "Asia/Tehran"));
        Assert.Equal(new TimeOnly(0, 0), BranchClock.LocalTime(utc, "Asia/Tehran"));
        var back = BranchClock.FromLocal(new DateOnly(2026, 1, 11), new TimeOnly(0, 0), "Asia/Tehran");
        Assert.Equal(utc, back);
    }

    [Fact]
    public void Unknown_zone_falls_back_to_tehran()
    {
        var utc = new DateTimeOffset(2026, 1, 10, 12, 0, 0, TimeSpan.Zero);
        Assert.Equal(new TimeOnly(15, 30), BranchClock.LocalTime(utc, "Nowhere/Land"));
    }

    private static AvailabilityRule Rule(Guid branch, DateOnly? date, DayOfWeek day, int from, int to, bool available = true, Guid? resource = null) => new()
    {
        BranchId = branch, ResourceId = resource, EffectiveDate = date, DayOfWeek = day,
        StartsAt = new TimeOnly(from, 0), EndsAt = new TimeOnly(to, 0), IsAvailable = available,
    };

    [Fact]
    public void Default_hours_apply_when_no_rules()
    {
        var result = AvailabilityResolver.Resolve([], new DateOnly(2026, 1, 12), Guid.NewGuid(), Guid.NewGuid(), 9, 17);
        Assert.Single(result);
        Assert.Equal(new TimeOnly(9, 0), result[0].Start);
        Assert.Equal(new TimeOnly(17, 0), result[0].End);
    }

    [Fact]
    public void Dated_rule_overrides_weekly_and_block_is_subtracted()
    {
        var branch = Guid.NewGuid();
        var resource = Guid.NewGuid();
        var date = new DateOnly(2026, 1, 12);
        var rules = new[]
        {
            Rule(branch, null, date.DayOfWeek, 8, 20),
            Rule(branch, date, date.DayOfWeek, 10, 14),
            Rule(branch, date, date.DayOfWeek, 12, 13, available: false),
        };
        var result = AvailabilityResolver.Resolve(rules, date, branch, resource, 9, 17);
        Assert.Equal(2, result.Count);
        Assert.Equal(new TimeInterval(new TimeOnly(10, 0), new TimeOnly(12, 0)), result[0]);
        Assert.Equal(new TimeInterval(new TimeOnly(13, 0), new TimeOnly(14, 0)), result[1]);
    }

    [Fact]
    public void Slots_skip_busy_and_past_times()
    {
        var date = new DateOnly(2026, 1, 12);
        var intervals = new[] { new TimeInterval(new TimeOnly(9, 0), new TimeOnly(11, 0)) };
        var busyStart = BranchClock.FromLocal(date, new TimeOnly(9, 30), "Asia/Tehran");
        var now = BranchClock.FromLocal(date, new TimeOnly(0, 0), "Asia/Tehran");
        var slots = AvailabilityResolver.Slots(intervals, date, "Asia/Tehran", TimeSpan.FromMinutes(30), 30,
            [(busyStart, busyStart.AddMinutes(30))], now);
        Assert.DoesNotContain(busyStart, slots);
        Assert.Contains(BranchClock.FromLocal(date, new TimeOnly(9, 0), "Asia/Tehran"), slots);
        Assert.Contains(BranchClock.FromLocal(date, new TimeOnly(10, 30), "Asia/Tehran"), slots);
        Assert.Equal(3, slots.Count);
    }

    [Fact]
    public void Text_search_unifies_arabic_and_persian_letters()
    {
        Assert.Equal("کیان", TextSearch.Normalize("  كيان  "));
        Assert.True(TextSearch.Matches("آرایشگاه کیان", "كيان"));
        Assert.Contains("%100\\%%", TextSearch.LikePatterns("100%"));
    }

    [Theory]
    [InlineData("/appointments", "/appointments")]
    [InlineData("https://evil.example", "/")]
    [InlineData("//evil.example", "/")]
    [InlineData("/\\evil.example", "/")]
    [InlineData("", "/")]
    [InlineData(null, "/")]
    public void Safe_redirect_only_allows_local_paths(string? input, string expected) =>
        Assert.Equal(expected, SafeRedirect.Local(input));
}
