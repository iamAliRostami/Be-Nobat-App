namespace BeNobat.Web.Domain;

public static class BookingPolicy
{
    public static readonly TimeSpan MinimumBookingLeadTime = TimeSpan.FromMinutes(30);
    public static readonly TimeSpan CustomerCancellationNotice = TimeSpan.FromHours(2);

    public static int TotalDuration(IEnumerable<Service> services) => services.Sum(x => x.DurationMinutes);
    public static decimal TotalPrice(IEnumerable<Service> services) => services.Sum(x => x.Price);

    public static bool IsBookable(DateTimeOffset startsAt, DateTimeOffset now) => startsAt >= now.Add(MinimumBookingLeadTime);

    public static bool CanCustomerCancel(Appointment appointment, DateTimeOffset now) =>
        (appointment.Status is AppointmentStatus.Pending or AppointmentStatus.Confirmed) &&
        appointment.StartsAt >= now.Add(CustomerCancellationNotice);

    public static (DateTimeOffset Start, DateTimeOffset End) UtcDayWindow(DateOnly date, TimeZoneInfo zone)
    {
        var local = date.ToDateTime(TimeOnly.MinValue, DateTimeKind.Unspecified);
        var start = new DateTimeOffset(local, zone.GetUtcOffset(local)).ToUniversalTime();
        var nextLocal = date.AddDays(1).ToDateTime(TimeOnly.MinValue, DateTimeKind.Unspecified);
        var end = new DateTimeOffset(nextLocal, zone.GetUtcOffset(nextLocal)).ToUniversalTime();
        return (start, end);
    }

    public static bool Overlaps(DateTimeOffset startsAt, DateTimeOffset endsAt,
        DateTimeOffset otherStartsAt, DateTimeOffset otherEndsAt) =>
        startsAt < otherEndsAt && otherStartsAt < endsAt;
}
