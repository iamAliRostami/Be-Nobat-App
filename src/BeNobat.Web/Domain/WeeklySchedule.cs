namespace BeNobat.Web.Domain;

public sealed class WeeklyTimeRange
{
    public int DayOfWeek { get; set; }
    public TimeOnly StartsAt { get; set; } = new(8, 0);
    public TimeOnly EndsAt { get; set; } = new(20, 0);
}

/// <summary>Branch recurring hours; dated exceptions and resource rules remain independent.</summary>
public static class WeeklySchedule
{
    public static List<WeeklyTimeRange> Default() => new[] { 6, 0, 1, 2, 3, 4 }
        .Select(day => new WeeklyTimeRange { DayOfWeek = day }).ToList();

    public static string? Validate(IReadOnlyList<WeeklyTimeRange>? hours)
    {
        if (hours is null) return null; // Older clients may omit the schedule.
        if (hours.Count > 56) return "حداکثر ۵۶ بازه در هفته مجاز است.";
        if (hours.Any(x => x is null || x.DayOfWeek is < 0 or > 6 || x.StartsAt >= x.EndsAt))
            return "روز و زمان بازه معتبر نیست؛ پایان باید بعد از شروع باشد.";
        foreach (var day in hours.GroupBy(x => x.DayOfWeek))
        {
            var ranges = day.OrderBy(x => x.StartsAt).ToList();
            for (var i = 1; i < ranges.Count; i++)
                if (ranges[i].StartsAt < ranges[i - 1].EndsAt) return "بازه‌های یک روز نباید هم‌پوشانی داشته باشند.";
        }
        return null;
    }

    public static List<WeeklyTimeRange> Read(Branch branch)
    {
        var rules = branch.AvailabilityRules.Where(IsWeeklyBranchRule).ToList();
        if (rules.Count == 0) return Enumerable.Range(0, 7).Select(day => new WeeklyTimeRange {
            DayOfWeek = day, StartsAt = new TimeOnly(branch.OpenHour, 0),
            EndsAt = branch.CloseHour == 24 ? AvailabilityResolver.EndOfDay : new TimeOnly(branch.CloseHour, 0)
        }).ToList();
        return rules.Where(x => x.IsAvailable).Select(x => new WeeklyTimeRange {
            DayOfWeek = (int)x.DayOfWeek, StartsAt = x.StartsAt, EndsAt = x.EndsAt
        }).ToList();
    }

    public static bool IsWeeklyBranchRule(AvailabilityRule rule) => rule.DeletedAt is null && rule.ResourceId is null && rule.EffectiveDate is null;

    public static void Replace(Branch branch, IReadOnlyList<WeeklyTimeRange> hours)
    {
        if (Validate(hours) is { } error) throw new ArgumentException(error);
        foreach (var rule in branch.AvailabilityRules.Where(IsWeeklyBranchRule).ToList()) rule.DeletedAt = DateTimeOffset.UtcNow;
        // A fully closed week needs explicit blocks, otherwise the legacy fallback opens it.
        if (hours.Count == 0)
        {
            foreach (var day in Enumerable.Range(0, 7)) Add(branch, day, TimeOnly.MinValue, AvailabilityResolver.EndOfDay, false);
        }
        else foreach (var range in hours) Add(branch, range.DayOfWeek, range.StartsAt, range.EndsAt, true);
    }

    private static void Add(Branch branch, int day, TimeOnly start, TimeOnly end, bool available) => branch.AvailabilityRules.Add(new AvailabilityRule {
        BusinessId = branch.BusinessId, Branch = branch, BranchId = branch.Id, DayOfWeek = (DayOfWeek)day,
        StartsAt = start, EndsAt = end, IsAvailable = available
    });
}
