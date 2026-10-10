using BeNobat.Web.Domain;
using Xunit;

namespace BeNobat.Web.Tests;

public sealed class FlexibleSlotTests
{
    private const string Zone = "Asia/Tehran";
    private static readonly DateOnly Day = new(2026, 10, 10);
    private static readonly DateTimeOffset Epoch = DateTimeOffset.UnixEpoch;
    private static DateTimeOffset At(int hour, int minute = 0) => BranchClock.FromLocal(Day, new TimeOnly(hour, minute), Zone);

    private static readonly TimeInterval[] WholeDay = [new TimeInterval(new TimeOnly(10, 0), new TimeOnly(18, 0))];

    [Fact]
    public void Service_longer_than_the_free_gap_is_offered_with_overrun()
    {
        // 10:00-11:00 is free, 11:00-12:00 is booked; a 75-minute service does not fit exactly.
        var busy = new[] { (At(11), At(12)) };
        var duration = TimeSpan.FromMinutes(75);
        var strict = AvailabilityResolver.Slots(WholeDay, Day, Zone, duration, 30, busy, Epoch);
        var flexible = AvailabilityResolver.FlexibleSlots(WholeDay, Day, Zone, duration, 30, busy, Epoch, strict);

        Assert.DoesNotContain(At(10), strict);
        Assert.Equal(15, flexible[At(10)]);
        Assert.Contains(At(12), strict);
        Assert.DoesNotContain(At(12), flexible.Keys);
    }

    [Fact]
    public void Overrun_never_exceeds_the_tolerance()
    {
        // Only 40 free minutes before the next appointment: a 75-minute service is 35 minutes too long.
        var busy = new[] { (At(10, 40), At(18)) };
        var duration = TimeSpan.FromMinutes(75);
        var strict = AvailabilityResolver.Slots(WholeDay, Day, Zone, duration, 30, busy, Epoch);
        var flexible = AvailabilityResolver.FlexibleSlots(WholeDay, Day, Zone, duration, 30, busy, Epoch, strict);

        Assert.Empty(flexible);
    }

    [Fact]
    public void Short_services_get_no_overrun()
    {
        var busy = new[] { (At(10, 40), At(18)) };
        var duration = TimeSpan.FromMinutes(BookingPolicy.MinDurationForOverrunMinutes - 5);
        var flexible = AvailabilityResolver.FlexibleSlots(WholeDay, Day, Zone, duration, 30, busy, Epoch, []);

        Assert.Empty(flexible);
    }

    [Fact]
    public void Smallest_sufficient_overrun_is_chosen()
    {
        // 70 free minutes for a 75-minute service: five minutes of overrun is enough.
        var busy = new[] { (At(11, 10), At(12)) };
        var duration = TimeSpan.FromMinutes(75);
        var strict = AvailabilityResolver.Slots(WholeDay, Day, Zone, duration, 30, busy, Epoch);
        var flexible = AvailabilityResolver.FlexibleSlots(WholeDay, Day, Zone, duration, 30, busy, Epoch, strict);

        Assert.Equal(5, flexible[At(10)]);
    }

    [Fact]
    public void Flexible_slots_still_respect_the_booking_lead_time()
    {
        var busy = new[] { (At(11), At(12)) };
        var now = At(10).AddMinutes(-10); // less than the 30-minute lead time before 10:00
        var duration = TimeSpan.FromMinutes(75);
        var flexible = AvailabilityResolver.FlexibleSlots(WholeDay, Day, Zone, duration, 30, busy, now, []);

        Assert.DoesNotContain(At(10), flexible.Keys);
    }
}
