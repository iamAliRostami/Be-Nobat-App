using BeNobat.Web.Domain;
using BeNobat.Web.Security;
using Xunit;

namespace BeNobat.Web.Tests;

public sealed class BookingEngineTests
{
    private static readonly Guid Branch = Guid.NewGuid();
    private static readonly Guid Resource = Guid.NewGuid();
    private static readonly DateOnly Day = new(2026, 10, 10);

    private static AvailabilityRule Rule(bool available, int fromHour, int toHour, DateOnly? date = null, Guid? branch = null, Guid? resource = null) => new()
    {
        BusinessId = Guid.NewGuid(),
        BranchId = branch,
        ResourceId = resource,
        EffectiveDate = date,
        DayOfWeek = (date ?? Day).DayOfWeek,
        StartsAt = new TimeOnly(fromHour, 0),
        EndsAt = toHour >= 24 ? AvailabilityResolver.EndOfDay : new TimeOnly(toHour, 0),
        IsAvailable = available,
    };

    [Fact]
    public void Without_rules_the_branch_default_hours_are_used()
    {
        var result = AvailabilityResolver.Resolve([], Day, Branch, Resource, 9, 18);
        Assert.Equal(new[] { new TimeInterval(new TimeOnly(9, 0), new TimeOnly(18, 0)) }, result);
    }

    [Fact]
    public void Resource_rule_overrides_branch_rule()
    {
        var rules = new[] { Rule(true, 8, 20, Day, Branch), Rule(true, 10, 12, Day, Branch, Resource) };
        var result = AvailabilityResolver.Resolve(rules, Day, Branch, Resource, 9, 18);
        Assert.Single(result);
        Assert.Equal(new TimeOnly(10, 0), result[0].Start);
        Assert.Equal(new TimeOnly(12, 0), result[0].End);
    }

    [Fact]
    public void Blocked_rule_splits_the_open_interval()
    {
        var rules = new[] { Rule(true, 9, 18, Day, Branch), Rule(false, 12, 13, Day, Branch) };
        var result = AvailabilityResolver.Resolve(rules, Day, Branch, Resource, 9, 18);
        Assert.Equal(2, result.Count);
        Assert.Equal(new TimeOnly(12, 0), result[0].End);
        Assert.Equal(new TimeOnly(13, 0), result[1].Start);
    }

    [Fact]
    public void Dated_rule_beats_weekly_rule_at_the_same_level()
    {
        var weekly = Rule(true, 8, 9, null, Branch);
        weekly.DayOfWeek = Day.DayOfWeek;
        var dated = Rule(true, 14, 16, Day, Branch);
        var result = AvailabilityResolver.Resolve([weekly, dated], Day, Branch, Resource, 9, 18);
        Assert.Single(result);
        Assert.Equal(new TimeOnly(14, 0), result[0].Start);
    }

    [Fact]
    public void Slots_are_generated_in_branch_time_and_returned_in_utc()
    {
        var intervals = new[] { new TimeInterval(new TimeOnly(9, 0), new TimeOnly(11, 0)) };
        var slots = AvailabilityResolver.Slots(intervals, Day, "Asia/Tehran", TimeSpan.FromMinutes(60), 30, [], DateTimeOffset.UnixEpoch);
        Assert.Equal(3, slots.Count); // 09:00, 09:30, 10:00
        Assert.Equal(TimeSpan.Zero, slots[0].Offset);
        Assert.Equal(new DateTimeOffset(2026, 10, 10, 5, 30, 0, TimeSpan.Zero), slots[0]); // 09:00 Tehran = 05:30 UTC
    }

    [Fact]
    public void Busy_periods_and_past_times_are_excluded()
    {
        var intervals = new[] { new TimeInterval(new TimeOnly(9, 0), new TimeOnly(12, 0)) };
        var busyStart = new DateTimeOffset(2026, 10, 10, 6, 30, 0, TimeSpan.Zero); // 10:00 Tehran
        var now = new DateTimeOffset(2026, 10, 10, 5, 30, 0, TimeSpan.Zero);       // 09:00 Tehran
        var slots = AvailabilityResolver.Slots(intervals, Day, "Asia/Tehran", TimeSpan.FromMinutes(30), 30,
            [(busyStart, busyStart.AddMinutes(30))], now);
        Assert.DoesNotContain(slots, s => s == busyStart);
        Assert.All(slots, s => Assert.True(s >= now.Add(BookingPolicy.MinimumBookingLeadTime)));
    }

    [Fact]
    public void End_of_day_interval_reaches_midnight()
    {
        var intervals = new[] { new TimeInterval(new TimeOnly(22, 0), AvailabilityResolver.EndOfDay) };
        var slots = AvailabilityResolver.Slots(intervals, Day, "Asia/Tehran", TimeSpan.FromMinutes(60), 60, [], DateTimeOffset.UnixEpoch);
        Assert.Equal(2, slots.Count); // 22:00 and 23:00
    }
}

public sealed class BranchClockTests
{
    [Fact]
    public void Tehran_local_date_can_differ_from_utc_date()
    {
        var utc = new DateTimeOffset(2026, 10, 10, 21, 0, 0, TimeSpan.Zero); // 00:30 next day in Tehran
        Assert.Equal(new DateOnly(2026, 10, 11), BranchClock.LocalDate(utc, "Asia/Tehran"));
        Assert.Equal(new TimeOnly(0, 30), BranchClock.LocalTime(utc, "Asia/Tehran"));
    }

    [Fact]
    public void FromLocal_round_trips()
    {
        var date = new DateOnly(2026, 10, 10);
        var utc = BranchClock.FromLocal(date, new TimeOnly(9, 0), "Asia/Tehran");
        Assert.Equal(TimeSpan.Zero, utc.Offset);
        Assert.Equal(date, BranchClock.LocalDate(utc, "Asia/Tehran"));
        Assert.Equal(new TimeOnly(9, 0), BranchClock.LocalTime(utc, "Asia/Tehran"));
    }

    [Fact]
    public void Day_window_is_24_hours_in_tehran()
    {
        var (start, end) = BranchClock.DayWindow(new DateOnly(2026, 10, 10), "Asia/Tehran");
        Assert.Equal(TimeSpan.FromHours(24), end - start);
    }

    [Fact]
    public void Unknown_zone_falls_back_instead_of_throwing()
    {
        var zone = BranchClock.Zone("Not/AZone");
        Assert.NotNull(zone);
    }
}

public sealed class AppointmentStateMachineTests
{
    private static readonly DateTimeOffset Now = new(2026, 10, 10, 12, 0, 0, TimeSpan.Zero);

    [Theory]
    [InlineData(AppointmentStatus.Pending, AppointmentStatus.Confirmed, 60, true)]
    [InlineData(AppointmentStatus.Pending, AppointmentStatus.Cancelled, 60, true)]
    [InlineData(AppointmentStatus.Pending, AppointmentStatus.Completed, 60, false)]
    [InlineData(AppointmentStatus.Pending, AppointmentStatus.Completed, -60, true)]
    [InlineData(AppointmentStatus.Confirmed, AppointmentStatus.NoShow, 60, false)]
    [InlineData(AppointmentStatus.Confirmed, AppointmentStatus.NoShow, -5, true)]
    [InlineData(AppointmentStatus.Confirmed, AppointmentStatus.Pending, 60, false)]
    [InlineData(AppointmentStatus.Completed, AppointmentStatus.Cancelled, -60, false)]
    [InlineData(AppointmentStatus.Cancelled, AppointmentStatus.Confirmed, 60, false)]
    [InlineData(AppointmentStatus.NoShow, AppointmentStatus.Completed, -60, false)]
    public void Transitions(AppointmentStatus from, AppointmentStatus to, int startOffsetMinutes, bool expected) =>
        Assert.Equal(expected, BookingPolicy.CanTransition(from, to, Now.AddMinutes(startOffsetMinutes), Now));

    [Fact]
    public void Same_status_is_not_a_transition() =>
        Assert.False(BookingPolicy.CanTransition(AppointmentStatus.Pending, AppointmentStatus.Pending, Now, Now));
}

public sealed class TextAndRedirectTests
{
    [Fact]
    public void Arabic_letters_are_normalized_to_persian()
    {
        Assert.Equal("کیمیا ی", TextSearch.Normalize("كيميا  ى"));
        Assert.True(TextSearch.Matches("سالن زیبایی", "زيبايي"));
    }

    [Fact]
    public void Like_patterns_escape_wildcards_and_cover_both_spellings()
    {
        var patterns = TextSearch.LikePatterns("کیف_%");
        Assert.Contains(patterns, p => p.Contains("\\_") && p.Contains("\\%"));
        Assert.Equal(2, patterns.Length);
        Assert.Empty(TextSearch.LikePatterns("   "));
    }

    [Theory]
    [InlineData("/appointments", "/appointments")]
    [InlineData("/book/1?branch=2", "/book/1?branch=2")]
    [InlineData("//evil.example", "/")]
    [InlineData("https://evil.example", "/")]
    [InlineData("/\\evil.example", "/")]
    [InlineData("javascript:alert(1)", "/")]
    [InlineData("", "/")]
    [InlineData(null, "/")]
    public void Redirects_stay_local(string? input, string expected) =>
        Assert.Equal(expected, SafeRedirect.Local(input));

    [Fact]
    public void Tracking_code_is_the_last_eight_hex_chars_uppercase()
    {
        var appointment = new Appointment { Id = Guid.Parse("11111111-2222-3333-4444-5555abcdef01") };
        Assert.Equal("ABCDEF01", appointment.TrackingCode);
    }
}
