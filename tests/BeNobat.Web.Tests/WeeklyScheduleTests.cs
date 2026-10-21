using System.Reflection;
using BeNobat.Web.Api;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace BeNobat.Web.Tests;

public class WeeklyScheduleTests
{
    private static Branch Branch() => new() { Name = "مرکزی", BusinessId = Guid.NewGuid() };

    [Fact]
    public void Defaults_open_Saturday_through_Thursday_and_close_Friday()
    {
        var branch = Branch();
        WeeklySchedule.Replace(branch, WeeklySchedule.Default());
        Assert.Equal(6, branch.AvailabilityRules.Count);
        var saturday = AvailabilityResolver.Resolve(branch.AvailabilityRules, new DateOnly(2026, 10, 24), branch.Id, Guid.NewGuid(), 9, 18);
        Assert.Equal(new TimeInterval(new TimeOnly(8, 0), new TimeOnly(20, 0)), Assert.Single(saturday));
        Assert.Empty(AvailabilityResolver.Resolve(branch.AvailabilityRules, new DateOnly(2026, 10, 23), branch.Id, Guid.NewGuid(), 9, 18));
    }

    [Fact]
    public void Split_hours_leave_a_break_and_reject_overlaps()
    {
        var hours = new List<WeeklyTimeRange> {
            new() { DayOfWeek = 6, StartsAt = new(8, 0), EndsAt = new(12, 0) },
            new() { DayOfWeek = 6, StartsAt = new(14, 0), EndsAt = new(20, 0) }
        };
        var branch = Branch();
        WeeklySchedule.Replace(branch, hours);
        var result = AvailabilityResolver.Resolve(branch.AvailabilityRules, new(2026, 10, 24), branch.Id, Guid.NewGuid(), 9, 18);
        Assert.Equal(2, result.Count);
        Assert.Equal(new TimeOnly(12, 0), result[0].End);
        Assert.Equal(new TimeOnly(14, 0), result[1].Start);
        hours[1].StartsAt = new(11, 30);
        Assert.NotNull(WeeklySchedule.Validate(hours));
    }

    [Fact]
    public void Closing_every_day_does_not_fall_back_to_legacy_hours()
    {
        var branch = Branch();
        WeeklySchedule.Replace(branch, WeeklySchedule.Default());
        WeeklySchedule.Replace(branch, []);
        Assert.Empty(WeeklySchedule.Read(branch));
        for (var day = 0; day < 7; day++)
            Assert.Empty(AvailabilityResolver.Resolve(branch.AvailabilityRules, new DateOnly(2026, 10, 24).AddDays(day), branch.Id, Guid.NewGuid(), 9, 18));
    }

    [Fact]
    public void Updating_weekly_hours_preserves_dated_and_resource_rules()
    {
        var branch = Branch();
        var dated = new AvailabilityRule { BusinessId = branch.BusinessId, BranchId = branch.Id, EffectiveDate = new(2026, 10, 24) };
        var resource = new AvailabilityRule { BusinessId = branch.BusinessId, BranchId = branch.Id, ResourceId = Guid.NewGuid() };
        branch.AvailabilityRules.Add(dated); branch.AvailabilityRules.Add(resource);
        WeeklySchedule.Replace(branch, WeeklySchedule.Default());
        WeeklySchedule.Replace(branch, [new() { DayOfWeek = 6, StartsAt = new(10, 30), EndsAt = new(16, 30) }]);
        Assert.Null(dated.DeletedAt); Assert.Null(resource.DeletedAt);
        Assert.Single(WeeklySchedule.Read(branch));
        Assert.Equal(new TimeOnly(10, 30), WeeklySchedule.Read(branch)[0].StartsAt);
    }

    [Fact]
    public void Branch_and_business_API_projections_translate_on_Postgres()
    {
        using var db = new AppDbContext(new DbContextOptionsBuilder<AppDbContext>().UseNpgsql("Host=localhost;Database=unused").Options);
        var branchQuery = (IQueryable<ManagementBranchDto>)typeof(ManagementEndpoints).GetMethod("BranchDtos", BindingFlags.NonPublic | BindingFlags.Static)!.Invoke(null, [db.Branches])!;
        var businessQuery = (IQueryable<ManagementBusinessDto>)typeof(ManagementEndpoints).GetMethod("BusinessDtos", BindingFlags.NonPublic | BindingFlags.Static)!.Invoke(null, [db.Businesses])!;
        Assert.Contains("AvailabilityRules", branchQuery.ToQueryString());
        Assert.Contains("CreatedAt", businessQuery.ToQueryString());
    }
}
