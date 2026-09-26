using BeNobat.Web.Domain;
using BeNobat.Web.Security;
using Xunit;

namespace BeNobat.Web.Tests;

public sealed class DomainTests
{
    [Fact]
    public void New_appointment_starts_pending()
    {
        var appointment = new Appointment();
        Assert.Equal(AppointmentStatus.Pending, appointment.Status);
    }

    [Fact]
    public void Entities_use_time_ordered_identifiers()
    {
        var business = new Business { Name = "Test" };
        Assert.NotEqual(Guid.Empty, business.Id);
        Assert.Equal(7, business.Id.Version);
    }

    [Fact]
    public void Platform_administrator_is_a_distinct_supported_role()
    {
        Assert.Contains(AppRoles.PlatformAdmin, AppRoles.All);
        Assert.DoesNotContain(AppRoles.Customer, new[] { AppRoles.Owner, AppRoles.Manager, AppRoles.Staff });
    }

    [Fact]
    public void Platform_administrator_inherits_every_management_capability()
    {
        Assert.Contains(AppRoles.PlatformAdmin, AppRoles.BusinessManagers);
        Assert.Contains(AppRoles.PlatformAdmin, AppRoles.AppointmentManagers);
        Assert.Contains(AppRoles.Owner, AppRoles.BusinessManagers);
        Assert.Contains(AppRoles.Manager, AppRoles.BusinessManagers);
        Assert.DoesNotContain(AppRoles.Staff, AppRoles.BusinessManagers);
        Assert.Contains(AppRoles.Staff, AppRoles.AppointmentManagers);
        Assert.DoesNotContain(AppRoles.Customer, AppRoles.AppointmentManagers);
    }

    [Fact]
    public void Branch_can_track_booking_resources()
    {
        var branch = new Branch { Name = "مرکزی" };
        branch.Resources.Add(new Resource { Name = "اتاق یک", BranchId = branch.Id });
        Assert.Single(branch.Resources);
    }

    [Fact]
    public void Multi_service_booking_sums_duration_and_price()
    {
        var services = new[]
        {
            new Service { Name = "اصلاح", DurationMinutes = 30, Price = 200_000 },
            new Service { Name = "رنگ", DurationMinutes = 60, Price = 500_000 },
        };
        Assert.Equal(90, BookingPolicy.TotalDuration(services));
        Assert.Equal(700_000, BookingPolicy.TotalPrice(services));
    }

    [Fact]
    public void Past_slots_are_never_bookable()
    {
        var now = DateTimeOffset.UtcNow;
        Assert.False(BookingPolicy.IsBookable(now.AddSeconds(-1), now));
        Assert.True(BookingPolicy.IsBookable(now, now));
    }

    [Theory]
    [InlineData(10, 11, 10, 11, true)]
    [InlineData(10, 11, 11, 12, false)]
    [InlineData(10, 12, 11, 13, true)]
    public void Appointment_overlap_uses_half_open_intervals(int start, int end, int otherStart, int otherEnd, bool expected)
    {
        var day = new DateTimeOffset(2026, 1, 1, 0, 0, 0, TimeSpan.Zero);
        Assert.Equal(expected, BookingPolicy.Overlaps(day.AddHours(start), day.AddHours(end), day.AddHours(otherStart), day.AddHours(otherEnd)));
    }

    [Theory]
    [InlineData("09121234567", "09121234567")]
    [InlineData("۰۹۱۲۱۲۳۴۵۶۷", "09121234567")]
    [InlineData("+989121234567", "09121234567")]
    public void Iranian_mobile_numbers_are_normalized(string input, string expected)
    {
        Assert.True(UserInputValidation.TryNormalizeIranianMobile(input, out var normalized));
        Assert.Equal(expected, normalized);
    }

    [Theory]
    [InlineData("0912123456")]
    [InlineData("02112345678")]
    [InlineData("0912ABC4567")]
    public void Invalid_mobile_numbers_are_rejected(string input) =>
        Assert.False(UserInputValidation.TryNormalizeIranianMobile(input, out _));
}
