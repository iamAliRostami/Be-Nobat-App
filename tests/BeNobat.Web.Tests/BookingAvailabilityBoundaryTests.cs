using BeNobat.Web.Domain;
using Xunit;

namespace BeNobat.Web.Tests;

public sealed class BookingAvailabilityBoundaryTests
{
    private static readonly Guid Business = Guid.NewGuid();
    private static readonly Guid Branch = Guid.NewGuid();
    private static readonly Guid Provider = Guid.NewGuid();
    private static readonly DateOnly Monday = new(2026, 10, 12);

    [Fact]
    public void A_configured_weekly_schedule_keeps_unscheduled_days_closed()
    {
        var weekly = Rule(new TimeOnly(9, 0), new TimeOnly(18, 0));
        Assert.Empty(AvailabilityResolver.Resolve([weekly], Monday.AddDays(1), Branch, Provider, 9, 18));
        Assert.NotEmpty(AvailabilityResolver.Resolve([weekly], Monday, Branch, Provider, 9, 18));
    }

    [Fact]
    public void A_dated_opening_can_override_a_weekly_offday()
    {
        var exception = Rule(new TimeOnly(12, 0), new TimeOnly(14, 0));
        exception.EffectiveDate = Monday.AddDays(1);
        var intervals = AvailabilityResolver.Resolve([Rule(new TimeOnly(9, 0), new TimeOnly(18, 0)), exception],
            Monday.AddDays(1), Branch, Provider, 9, 18);
        Assert.Equal(new[] { new TimeInterval(new TimeOnly(12, 0), new TimeOnly(14, 0)) }, intervals);
    }

    [Fact]
    public void Adjacent_openings_allow_a_service_to_cross_their_shared_boundary()
    {
        var rules = new[] { Rule(new TimeOnly(9, 0), new TimeOnly(10, 0)), Rule(new TimeOnly(10, 0), new TimeOnly(11, 0)) };
        var intervals = AvailabilityResolver.Resolve(rules, Monday, Branch, Provider, 9, 18);
        var slots = AvailabilityResolver.Slots(intervals, Monday, "Etc/UTC", TimeSpan.FromMinutes(90), 30, [], DateTimeOffset.UnixEpoch);
        Assert.Equal(new[] { BranchClock.FromLocal(Monday, new TimeOnly(9, 0), "Etc/UTC"),
            BranchClock.FromLocal(Monday, new TimeOnly(9, 30), "Etc/UTC") }, slots);
    }

    [Fact]
    public void All_scope_blocks_apply_and_deleted_blocks_do_not()
    {
        var businessBlock = Rule(new TimeOnly(10, 0), new TimeOnly(11, 0), false);
        businessBlock.BranchId = null;
        var deleted = Rule(new TimeOnly(9, 0), new TimeOnly(18, 0), false);
        deleted.DeletedAt = DateTimeOffset.UtcNow;
        var rules = new[] { Rule(new TimeOnly(9, 0), new TimeOnly(18, 0)), businessBlock, deleted };
        var intervals = AvailabilityResolver.Resolve(rules, Monday, Branch, Provider, 9, 18);
        Assert.Equal(2, intervals.Count);
        Assert.Equal(new TimeOnly(10, 0), intervals[0].End);
        Assert.Equal(new TimeOnly(11, 0), intervals[1].Start);
    }

    [Fact]
    public void A_dated_opening_replaces_recurring_blocks_at_its_scope_but_not_other_scope_blocks()
    {
        var dated = Rule(new TimeOnly(9, 0), new TimeOnly(18, 0));
        dated.EffectiveDate = Monday;
        var lunch = Rule(new TimeOnly(12, 0), new TimeOnly(13, 0), false);
        var businessClosure = Rule(new TimeOnly(15, 0), new TimeOnly(16, 0), false);
        businessClosure.BranchId = null;
        var intervals = AvailabilityResolver.Resolve([dated, lunch, businessClosure], Monday, Branch, Provider, 9, 18);
        Assert.Equal(new[] { new TimeInterval(new TimeOnly(9, 0), new TimeOnly(15, 0)),
            new TimeInterval(new TimeOnly(16, 0), new TimeOnly(18, 0)) }, intervals);
    }

    [Fact]
    public void A_partial_dated_closure_preserves_the_weekly_opening_hours()
    {
        var weekly = Rule(new TimeOnly(8, 0), new TimeOnly(20, 0));
        var closure = Rule(new TimeOnly(12, 0), new TimeOnly(13, 0), false);
        closure.EffectiveDate = Monday;
        var intervals = AvailabilityResolver.Resolve([weekly, closure], Monday, Branch, Provider, 9, 18);
        Assert.Equal(new TimeOnly(8, 0), intervals[0].Start);
        Assert.Equal(new TimeOnly(20, 0), intervals[^1].End);
    }

    [Fact]
    public void Spring_gap_never_creates_or_shifts_a_nonexistent_slot()
    {
        var date = new DateOnly(2027, 3, 14);
        var slots = AvailabilityResolver.Slots([new TimeInterval(new TimeOnly(2, 0), new TimeOnly(4, 0))],
            date, "America/New_York", TimeSpan.FromMinutes(30), 30, [], DateTimeOffset.UnixEpoch);
        Assert.Equal(2, slots.Count);
        Assert.All(slots, x => Assert.Equal(3, BranchClock.LocalTime(x, "America/New_York").Hour));
        Assert.Throws<ArgumentException>(() => BranchClock.FromLocal(date, new TimeOnly(2, 30), "America/New_York"));
    }

    [Fact]
    public void Repeated_hour_offers_both_occurrences_and_respects_busy_absolute_instants()
    {
        var date = new DateOnly(2026, 11, 1);
        var intervals = new[] { new TimeInterval(new TimeOnly(1, 0), new TimeOnly(2, 0)) };
        var slots = AvailabilityResolver.Slots(intervals, date, "America/New_York", TimeSpan.FromMinutes(30), 30, [], DateTimeOffset.UnixEpoch);
        Assert.Equal(4, slots.Count);
        Assert.Equal(TimeSpan.FromHours(1), slots[2] - slots[0]);
        var filtered = AvailabilityResolver.Slots(intervals, date, "America/New_York", TimeSpan.FromMinutes(30), 30,
            [(slots[0], slots[0].AddMinutes(30))], DateTimeOffset.UnixEpoch);
        Assert.DoesNotContain(slots[0], filtered);
        Assert.Contains(slots[2], filtered);
    }

    [Fact]
    public void A_service_cannot_cross_a_closed_wall_clock_segment_during_the_repeated_hour()
    {
        var slots = AvailabilityResolver.Slots([new TimeInterval(new TimeOnly(1, 15), new TimeOnly(1, 45))],
            new DateOnly(2026, 11, 1), "America/New_York", TimeSpan.FromMinutes(60), 30, [], DateTimeOffset.UnixEpoch);
        Assert.Empty(slots);
    }

    [Theory]
    [InlineData(2027, 3, 14, 23)]
    [InlineData(2026, 11, 1, 25)]
    public void Day_windows_follow_the_actual_length_of_a_local_day(int year, int month, int day, int hours)
    {
        var (start, end) = BranchClock.DayWindow(new DateOnly(year, month, day), "America/New_York");
        Assert.Equal(TimeSpan.FromHours(hours), end - start);
    }

    [Theory]
    [InlineData(0)]
    [InlineData(-30)]
    [InlineData(1441)]
    public void Invalid_service_duration_produces_no_slots(int minutes) => Assert.Empty(AvailabilityResolver.Slots(
        [new TimeInterval(new TimeOnly(9, 0), new TimeOnly(18, 0))], Monday, "Etc/UTC", TimeSpan.FromMinutes(minutes), 30, [], DateTimeOffset.UnixEpoch));

    [Fact]
    public void Invalid_default_hours_do_not_silently_open_until_midnight() =>
        Assert.Empty(AvailabilityResolver.Resolve([], Monday, Branch, Provider, 18, 9));

    [Fact]
    public void Horizon_excludes_past_dates_and_includes_only_the_configured_last_day()
    {
        Assert.False(BookingPolicy.IsWithinHorizon(Monday.AddDays(-1), Monday));
        Assert.True(BookingPolicy.IsWithinHorizon(Monday.AddDays(BookingPolicy.MaxAdvanceDays), Monday));
        Assert.False(BookingPolicy.IsWithinHorizon(Monday.AddDays(BookingPolicy.MaxAdvanceDays + 1), Monday));
    }

    private static AvailabilityRule Rule(TimeOnly start, TimeOnly end, bool available = true) => new()
    {
        BusinessId = Business, BranchId = Branch, DayOfWeek = Monday.DayOfWeek,
        StartsAt = start, EndsAt = end, IsAvailable = available,
    };
}
