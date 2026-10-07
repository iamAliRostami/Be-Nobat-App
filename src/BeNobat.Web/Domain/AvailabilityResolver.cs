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
/// <item>بازه‌های «قابل رزرو» از اختصاصی‌ترین سطحی که برنامه‌ی هفتگی یا بازه‌ی تاریخ‌دار همان روز دارد گرفته می‌شود.
///       داخل یک سطح، اگر برای همان تاریخ قاعده‌ی تاریخ‌دار هست، فقط همان‌ها معتبرند (استثنا روی برنامه‌ی هفتگی)،
///       وگرنه برنامه‌ی هفتگی همان روز هفته.</item>
/// <item>روز بدون برنامه در یک برنامه‌ی هفتگی تعطیل است. اگر هیچ برنامه‌ای تعریف نشده، ساعت کاری عمومی شعبه استفاده می‌شود.</item>
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
        var scoped = rules
            .Where(r => r.DeletedAt is null && r.StartsAt < r.EndsAt &&
                        ((r.ResourceId == resourceId && (r.BranchId is null || r.BranchId == branchId)) ||
                         (r.ResourceId is null && (r.BranchId == branchId || r.BranchId is null))))
            .ToList();
        var applicable = scoped.Where(r => AppliesOn(r, date)).ToList();

        var open = new List<TimeInterval>();
        var hasSchedule = false;
        for (var level = 0; level <= 2; level++)
        {
            var atLevel = applicable.Where(r => r.IsAvailable && LevelOf(r, resourceId) == level).ToList();
            var dated = atLevel.Where(r => r.EffectiveDate is not null).ToList();
            // A weekly schedule defines offdays too. Dated exceptions at another date do not.
            var recurring = scoped.Any(r => r.IsAvailable && r.EffectiveDate is null && LevelOf(r, resourceId) == level);
            if (atLevel.Count == 0 && !recurring) continue;
            hasSchedule = true;
            var chosen = dated.Count > 0 ? dated : atLevel.Where(r => r.EffectiveDate is null).ToList();
            open.AddRange(chosen.Select(r => new TimeInterval(r.StartsAt, r.EndsAt)));
            break;
        }

        if (!hasSchedule)
        {
            open.Add(DefaultHours(openHour, closeHour));
        }

        var blocks = applicable
            .Where(r => !r.IsAvailable && (r.EffectiveDate is not null ||
                !applicable.Any(d => d.EffectiveDate is not null && LevelOf(d, resourceId) == LevelOf(r, resourceId))))
            .Select(r => new TimeInterval(r.StartsAt, r.EndsAt))
            .ToList();

        return Merge(Subtract(Merge(open), blocks));
    }

    public static TimeInterval DefaultHours(int openHour, int closeHour)
    {
        var start = new TimeOnly(Math.Clamp(openHour, 0, 23), 0);
        var end = closeHour >= 24 ? EndOfDay : new TimeOnly(Math.Clamp(closeHour, 1, 23), 0);
        return new TimeInterval(start, end);
    }

    private static int LevelOf(AvailabilityRule rule, Guid resourceId) =>
        rule.ResourceId == resourceId ? 0 : rule.BranchId is not null ? 1 : 2;

    private static List<TimeInterval> Merge(IEnumerable<TimeInterval> intervals)
    {
        var result = new List<TimeInterval>();
        foreach (var interval in intervals.Where(x => x.Start < x.End).OrderBy(x => x.Start))
        {
            if (result.Count == 0 || result[^1].End < interval.Start) result.Add(interval);
            else if (interval.End > result[^1].End) result[^1] = new TimeInterval(result[^1].Start, interval.End);
        }
        return result;
    }

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
        if (duration <= TimeSpan.Zero || duration > TimeSpan.FromDays(1)) return [];
        var busyList = busy.ToList();
        var step = Math.Max(5, stepMinutes);
        var result = new SortedSet<DateTimeOffset>();
        var merged = Merge(intervals);
        var zone = BranchClock.Zone(zoneId);
        var openUtc = UtcIntervals(merged, date, zone);

        foreach (var interval in merged)
        {
            var from = date.ToDateTime(interval.Start, DateTimeKind.Unspecified);
            var to = IsEndOfDay(interval.End) ? date.AddDays(1).ToDateTime(TimeOnly.MinValue) : date.ToDateTime(interval.End);

            // Iterate wall-clock starts: skip the spring gap and offer both occurrences of a repeated hour.
            for (var local = from; local < to; local = local.AddMinutes(step))
            {
                foreach (var start in BranchClock.LocalInstants(local, zone))
                {
                    var end = start + duration;
                    if (!openUtc.Any(x => x.Start <= start && end <= x.End)) continue;
                    if (!BookingPolicy.IsBookable(start, nowUtc)) continue;
                    if (busyList.Any(b => BookingPolicy.Overlaps(start, end, b.Start, b.End))) continue;
                    result.Add(start);
                }
            }
        }

        return result.ToList();
    }

    private static List<(DateTimeOffset Start, DateTimeOffset End)> UtcIntervals(
        IEnumerable<TimeInterval> intervals, DateOnly date, TimeZoneInfo zone)
    {
        var (dayStart, dayEnd) = BranchClock.DayWindow(date, zone);
        var segments = new List<(DateTimeOffset Start, DateTimeOffset End, TimeSpan Offset)>();
        var segmentStart = dayStart;
        var cursor = dayStart;
        var offset = zone.GetUtcOffset(cursor);
        while (cursor < dayEnd)
        {
            var probe = cursor.AddMinutes(30) < dayEnd ? cursor.AddMinutes(30) : dayEnd;
            if (zone.GetUtcOffset(probe.AddTicks(-1)) != offset)
            {
                var low = cursor.UtcTicks;
                var high = probe.UtcTicks;
                while (high - low > 1)
                {
                    var middle = low + (high - low) / 2;
                    if (zone.GetUtcOffset(new DateTimeOffset(middle, TimeSpan.Zero)) == offset) low = middle;
                    else high = middle;
                }
                var transition = new DateTimeOffset(high, TimeSpan.Zero);
                segments.Add((segmentStart, transition, offset));
                segmentStart = transition;
                offset = zone.GetUtcOffset(transition);
            }
            cursor = probe;
            // Handle a transition exactly at a sampling boundary.
            if (cursor < dayEnd && zone.GetUtcOffset(cursor) != offset)
            {
                segments.Add((segmentStart, cursor, offset));
                segmentStart = cursor;
                offset = zone.GetUtcOffset(cursor);
            }
        }
        if (segmentStart < dayEnd) segments.Add((segmentStart, dayEnd, offset));
        var pieces = new List<(DateTimeOffset Start, DateTimeOffset End)>();
        foreach (var segment in segments)
        foreach (var interval in intervals)
        {
            var from = new DateTimeOffset(date.ToDateTime(interval.Start), segment.Offset).ToUniversalTime();
            var toLocal = IsEndOfDay(interval.End) ? date.AddDays(1).ToDateTime(TimeOnly.MinValue) : date.ToDateTime(interval.End);
            var to = new DateTimeOffset(toLocal, segment.Offset).ToUniversalTime();
            var start = from > segment.Start ? from : segment.Start;
            var end = to < segment.End ? to : segment.End;
            if (start < end) pieces.Add((start, end));
        }
        var result = new List<(DateTimeOffset Start, DateTimeOffset End)>();
        foreach (var piece in pieces.OrderBy(x => x.Start))
        {
            if (result.Count == 0 || result[^1].End < piece.Start) result.Add(piece);
            else if (piece.End > result[^1].End) result[^1] = (result[^1].Start, piece.End);
        }
        return result;
    }
}
