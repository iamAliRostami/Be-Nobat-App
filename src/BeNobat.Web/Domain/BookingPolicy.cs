namespace BeNobat.Web.Domain;

public static class BookingPolicy
{
    public static int TotalDuration(IEnumerable<Service> services) => services.Sum(x => x.DurationMinutes);
    public static decimal TotalPrice(IEnumerable<Service> services) => services.Sum(x => x.Price);

    public static bool IsBookable(DateTimeOffset startsAt, DateTimeOffset now) => startsAt >= now;

    public static bool Overlaps(DateTimeOffset startsAt, DateTimeOffset endsAt,
        DateTimeOffset otherStartsAt, DateTimeOffset otherEndsAt) =>
        startsAt < otherEndsAt && otherStartsAt < endsAt;
}
