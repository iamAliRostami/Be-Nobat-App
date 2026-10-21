using BeNobat.Web.Domain;
using BeNobat.Web.Application;
using BeNobat.Web.Infrastructure;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace BeNobat.Web.Tests;

public class CancellationPolicyTests
{
    private static readonly DateTimeOffset Now = new(2030, 1, 1, 8, 0, 0, TimeSpan.Zero);
    [Theory]
    [InlineData(7201, true)] [InlineData(7200, true)] [InlineData(7199, false)]
    [InlineData(0, false)] [InlineData(-3600, false)]
    public void Default_is_two_hours_and_boundary_is_inclusive(int seconds, bool expected)
    {
        Assert.Equal(120, new Business { Name = "test" }.CancellationNoticeMinutes);
        var appointment = new Appointment { StartsAt = Now.AddSeconds(seconds), Status = AppointmentStatus.Confirmed };
        Assert.Equal(expected, BookingPolicy.CanCustomerCancel(appointment, Now));
    }
    [Theory]
    [InlineData(AppointmentStatus.Pending, true)] [InlineData(AppointmentStatus.Confirmed, true)]
    [InlineData(AppointmentStatus.Cancelled, false)] [InlineData(AppointmentStatus.Completed, false)] [InlineData(AppointmentStatus.NoShow, false)]
    public void Only_pending_and_confirmed_appointments_can_be_cancelled(AppointmentStatus status, bool expected) =>
        Assert.Equal(expected, BookingPolicy.CanCustomerCancel(new Appointment { Status = status, StartsAt = Now.AddHours(3) }, Now));
    [Fact]
    public void More_specific_setting_wins_and_null_restores_inheritance()
    {
        Assert.Equal(300, CancellationPolicy.Resolve(300, null, null, null));
        Assert.Equal(240, CancellationPolicy.Resolve(300, 240, null, null));
        Assert.Equal(180, CancellationPolicy.Resolve(300, 240, 180, null));
        Assert.Equal(30, CancellationPolicy.Resolve(300, 240, 180, 30));
        Assert.Equal(0, CancellationPolicy.Resolve(300, 240, 180, 0));
    }
    [Theory] [InlineData(-1, false)] [InlineData(0, true)] [InlineData(129600, true)] [InlineData(129601, false)]
    public void Settings_are_bounded(int value, bool valid) => Assert.Equal(valid, CancellationPolicy.IsValid(value));
    [Fact]
    public void Atomic_cancellation_query_translates_to_Postgres()
    {
        using var db = new AppDbContext(new DbContextOptionsBuilder<AppDbContext>().UseNpgsql("Host=localhost;Database=unused").Options);
        var sql = CustomerCancellation.Eligible(db, db.Appointments.IgnoreQueryFilters(), Now).ToQueryString();
        Assert.Contains("CancellationNoticeMinutes", sql);
        Assert.Contains("BranchServices", sql);
        Assert.Contains("MAX", sql, StringComparison.OrdinalIgnoreCase);
    }
}
