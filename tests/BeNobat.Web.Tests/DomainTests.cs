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
        Assert.False(BookingPolicy.IsBookable(now.AddMinutes(29), now));
        Assert.True(BookingPolicy.IsBookable(now.AddMinutes(30), now));
    }

    [Theory]
    [InlineData(AppointmentStatus.Pending, 120, true)]
    [InlineData(AppointmentStatus.Confirmed, 119, false)]
    [InlineData(AppointmentStatus.Completed, 180, false)]
    [InlineData(AppointmentStatus.Cancelled, 180, false)]
    public void Customer_cancellation_requires_active_status_and_two_hours_notice(AppointmentStatus status, int minutes, bool expected)
    {
        var now = DateTimeOffset.UtcNow;
        var appointment = new Appointment { Status = status, StartsAt = now.AddMinutes(minutes) };
        Assert.Equal(expected, BookingPolicy.CanCustomerCancel(appointment, now));
    }

    [Theory]
    [InlineData(AppointmentStatus.Completed, 60, true)]
    [InlineData(AppointmentStatus.Confirmed, -1, true)]
    [InlineData(AppointmentStatus.Confirmed, 1, false)]
    [InlineData(AppointmentStatus.Pending, -60, false)]
    [InlineData(AppointmentStatus.Cancelled, -60, false)]
    [InlineData(AppointmentStatus.NoShow, -60, false)]
    public void Customer_review_requires_a_completed_or_past_confirmed_appointment(
        AppointmentStatus status, int endOffsetMinutes, bool expected)
    {
        var now = DateTimeOffset.UtcNow;
        var appointment = new Appointment { Status = status, EndsAt = now.AddMinutes(endOffsetMinutes) };

        Assert.Equal(expected, BookingPolicy.CanCustomerReview(appointment, now));
    }

    [Fact]
    public void Appointment_query_day_window_is_always_utc()
    {
        var zone = TimeZoneInfo.FindSystemTimeZoneById("Asia/Tehran");
        var (start, end) = BookingPolicy.UtcDayWindow(new DateOnly(2026, 9, 29), zone);

        Assert.Equal(TimeSpan.Zero, start.Offset);
        Assert.Equal(TimeSpan.Zero, end.Offset);
        Assert.Equal(TimeSpan.FromHours(24), end - start);
        Assert.Equal(new DateTimeOffset(2026, 9, 28, 20, 30, 0, TimeSpan.Zero), start);
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

    [Theory]
    [InlineData("fa")]
    [InlineData("ar")]
    [InlineData("en")]
    public void Localized_calendar_parts_round_trip(string language)
    {
        var date = new DateOnly(2026, 9, 29);
        var parts = LocalizedDate.GetParts(date, language);

        Assert.Equal(date, LocalizedDate.FromParts(parts.Year, parts.Month, parts.Day, language));
    }

    [Theory]
    [InlineData("fa", "۱۴۰۵")]
    [InlineData("ar", "١٤٤٨")]
    [InlineData("en", "2026")]
    public void Calendar_year_digits_follow_language(string language, string expected) =>
        Assert.Equal(expected, LocalizedDate.FormatNumber(LocalizedDate.GetParts(new DateOnly(2026, 9, 29), language).Year, language));
}
