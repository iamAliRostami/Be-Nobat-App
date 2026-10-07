namespace BeNobat.Web.Domain;

/// <summary>یک بازه‌ی ساعتی در یک روز (ساعت دیواری شعبه). پایانِ «پایان روز» با <see cref="AvailabilityResolver.EndOfDay"/> نشان داده می‌شود.</summary>
public readonly record struct TimeInterval(TimeOnly Start, TimeOnly End)
{
    public bool Overlaps(TimeInterval other) => Start < other.End && other.Start < End;
}

/// <summary>
/// منطق خالص و قابل‌تست «چه ساعت‌هایی قابل رزرو است». قواعد:
/// <list type="number">
/// <item>سطح قاعده: ۰ = مخصوص همان ارائه‌دهنده، ۱ = مخصوص شعبه، ۲ = کل کسب‌وکار.</item>
/// <item>بازه‌های «قابل رزرو» از اختصاصی‌ترین سطحی که حداقل یک بازه‌ی قابل‌رزرو برای آن روز دارد گرفته می‌شود.
///       داخل یک سطح، اگر برای همان تاریخ قاعده‌ی تاریخ‌دار هست، فقط همان‌ها معتبرند (استثنا روی برنامه‌ی هفتگی)،
///       وگرنه برنامه‌ی هفتگی همان روز هفته.</item>
/// <item>اگر هیچ سطحی بازه‌ای نداشت، ساعت کاری عمومی شعبه (OpenHour..CloseHour) استفاده می‌شود.</item>
/// <item>بازه‌های «مسدود» از همه‌ی سطوح از نتیجه کم می‌شوند (تعطیلی، مرخصی، استراحت).</item>
/// </list>
/// </summary>
public static class AvailabilityResolver
{
    /// <summary>نشانگر «تا پایان روز» (۲۴:۰۰). مقدار ۲۳:۵۹:۵۹ عمداً انتخاب شده تا بعد از ذخیره در ستون time پستگرس هم سالم بماند.</summary>
    public static readonly TimeOnly EndOfDay = new(23, 59, 59);

    public static bool IsEndOfDay(TimeOnly value) => value >= EndOfDay;

    public static bool AppliesOn(AvailabilityRule rule, DateOnly date) =>
        rule.EffectiveDate is DateOnly effective ? effective == date : rule.DayOfWeek == date.DayOfWeek;

    public static IReadOnlyList<TimeInterval> Resolve(
        IEnumerable<AvailabilityRule> rules, DateOnly date, Guid branchId, Guid resourceId, int openHour, int closeHour)
    {
        var applicable = rules
            .Where(r => r.DeletedAt is null && AppliesOn(r, date) &&
                        (r.ResourceId == resourceId ||
                         (r.ResourceId is null && (r.BranchId == branchId || r.BranchId is null))))
            .ToList();

        var open = new List<TimeInterval>();
        for (var level = 0; level <= 2 && open.Count == 0; level++)
        {
            var atLevel = applicable.Where(r => r.IsAvailable && LevelOf(r, resourceId) == level).ToList();
            if (atLevel.Count == 0) continue;

            var dated = atLevel.Where(r => r.EffectiveDate is not null).ToList();
            var chosen = dated.Count > 0 ? dated : atLevel;
            open.AddRange(chosen.Select(r => new TimeInterval(r.StartsAt, r.EndsAt)));
        }

        if (open.Count == 0)
        {
            open.Add(DefaultHours(openHour, closeHour));
        }

        var blocks = applicable
            .Where(r => !r.IsAvailable)
            .Select(r => new TimeInterval(r.StartsAt, r.EndsAt))
            .ToList();

        return Subtract(open, blocks)
            .Where(x => x.Start < x.End)
            .OrderBy(x => x.Start)
            .ToList();
    }

    public static TimeInterval DefaultHours(int openHour, int closeHour)
    {
        var start = new TimeOnly(Math.Clamp(openHour, 0, 23), 0);
        var end = closeHour >= 24 ? EndOfDay : new TimeOnly(Math.Clamp(closeHour, 1, 23), 0);
        return end > start ? new TimeInterval(start, end) : new TimeInterval(start, EndOfDay);
    }

    private static int LevelOf(AvailabilityRule rule, Guid resourceId) =>
        rule.ResourceId == resourceId ? 0 : rule.BranchId is not null ? 1 : 2;

    private static List<TimeInterval> Subtract(List<TimeInterval> source, List<TimeInterval> blocks)
    {
        var result = source;
        foreach (var block in blocks)
        {
            var next = new List<TimeInterval>();
            foreach (var item in result)
            {
                if (!item.Overlaps(block))
                {
                    next.Add(item);
                    continue;
                }

                if (item.Start < block.Start) next.Add(new TimeInterval(item.Start, block.Start));
                if (block.End < item.End) next.Add(new TimeInterval(block.End, item.End));
            }

            result = next;
        }

        return result;
    }

    /// <summary>
    /// از بازه‌های ساعتی، زمان‌های شروع قابل رزرو را می‌سازد. همه‌ی خروجی‌ها UTC هستند.
    /// </summary>
    public static List<DateTimeOffset> Slots(
        IEnumerable<TimeInterval> intervals,
        DateOnly date,
        string? zoneId,
        TimeSpan duration,
        int stepMinutes,
        IEnumerable<(DateTimeOffset Start, DateTimeOffset End)> busy,
        DateTimeOffset nowUtc)
    {
        var busyList = busy.ToList();
        var step = Math.Max(5, stepMinutes);
        var result = new SortedSet<DateTimeOffset>();

        foreach (var interval in intervals)
        {
            var from = BranchClock.FromLocal(date, interval.Start, zoneId);
            var to = IsEndOfDay(interval.End)
                ? BranchClock.FromLocal(date.AddDays(1), TimeOnly.MinValue, zoneId)
                : BranchClock.FromLocal(date, interval.End, zoneId);

            for (var start = from; start + duration <= to; start = start.AddMinutes(step))
            {
                var end = start + duration;
                if (!BookingPolicy.IsBookable(start, nowUtc)) continue;
                if (busyList.Any(b => BookingPolicy.Overlaps(start, end, b.Start, b.End))) continue;
                result.Add(start);
            }
        }

        return result.ToList();
    }
}
