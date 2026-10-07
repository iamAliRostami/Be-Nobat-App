using System.Reflection;
using BeNobat.Web.Components.Pages;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace BeNobat.Web.Tests;

public sealed class BookingContactTests
{
    private const BindingFlags PrivateInstance = BindingFlags.Instance | BindingFlags.NonPublic;

    [Fact]
    public void Final_step_prefills_the_saved_profile_mobile_and_refreshes_an_untouched_default()
    {
        var component = new Booking();

        ApplyProfilePhone(component, "09121234567");
        Assert.Equal("09121234567", Get(component, "PhoneInput"));
        ApplyProfilePhone(component, "09351234567");

        Assert.Equal("09351234567", Get(component, "PhoneInput"));
        Assert.Equal("09351234567", Get(component, "ExistingPhone"));
    }

    [Theory]
    [InlineData("09133333333")]
    [InlineData("")]
    public void Refreshing_the_saved_profile_does_not_replace_a_manually_edited_or_cleared_phone(string editedPhone)
    {
        var component = new Booking();
        ApplyProfilePhone(component, "09121234567");
        typeof(Booking).GetMethod("UpdatePhoneInput", PrivateInstance)!.Invoke(component, new object[] { editedPhone });

        ApplyProfilePhone(component, "09351234567");

        Assert.Equal(editedPhone, Get(component, "PhoneInput"));
        Assert.Equal("09351234567", Get(component, "ExistingPhone"));
    }

    [Fact]
    public async Task Guest_final_step_does_not_read_or_prefill_an_account_phone()
    {
        var component = new Booking();
        typeof(Booking).GetProperty("DbFactory", PrivateInstance)!.SetValue(component, new UnexpectedContextFactory());

        await (Task)typeof(Booking).GetMethod("RefreshContactPhoneAsync", PrivateInstance)!.Invoke(component, null)!;

        Assert.Equal("", Get(component, "PhoneInput"));
        Assert.Null(Get(component, "ExistingPhone"));
    }

    [Theory]
    [InlineData("")]
    [InlineData("02112345678")]
    [InlineData("0912123456")]
    public async Task Confirmation_rejects_an_invalid_edited_mobile_even_when_the_profile_has_a_saved_mobile(string editedPhone)
    {
        var business = new Business { Name = "کسب‌وکار" };
        var branch = new Branch { BusinessId = business.Id, Name = "شعبه" };
        var service = new Service { BusinessId = business.Id, Name = "خدمت", DurationMinutes = 30 };
        var component = new Booking();
        typeof(Booking).GetProperty(nameof(Booking.BusinessId))!.SetValue(component, business.Id);
        Set(component, "Business", business);
        Set(component, "Branches", new List<Branch> { branch });
        Set(component, "SelectedBranchId", branch.Id);
        Set(component, "Services", new List<Service> { service });
        Set(component, "SelectedServiceIds", new HashSet<Guid> { service.Id });
        Set(component, "SelectedSlot", DateTimeOffset.UtcNow.AddHours(1));
        Set(component, "TermsAccepted", true);
        Set(component, "IsAuthenticated", true);
        Set(component, "CurrentUserId", Guid.NewGuid());
        Set(component, "ExistingPhone", "09121234567");
        Set(component, "PhoneInput", editedPhone);

        // The old confirmation skipped phone validation whenever a profile number existed.
        // Rejection must happen before any availability reads or profile/appointment writes.
        await (Task)typeof(Booking).GetMethod("ConfirmBookingCoreAsync", PrivateInstance)!.Invoke(component, null)!;

        Assert.Equal("شماره موبایل باید ۱۱ رقم و با ۰۹ شروع شود.", Get(component, "ErrorMessage"));
        Assert.Equal("09121234567", Get(component, "ExistingPhone"));
        Assert.Equal(editedPhone, Get(component, "PhoneInput"));
    }

    private static void Set(Booking component, string field, object value) =>
        typeof(Booking).GetField(field, PrivateInstance)!.SetValue(component, value);

    private static object? Get(Booking component, string field) =>
        typeof(Booking).GetField(field, PrivateInstance)!.GetValue(component);

    private static void ApplyProfilePhone(Booking component, string? phone) =>
        typeof(Booking).GetMethod("ApplyProfilePhone", PrivateInstance)!.Invoke(component, new object?[] { phone });

    private sealed class UnexpectedContextFactory : IDbContextFactory<AppDbContext>
    {
        public AppDbContext CreateDbContext() => throw new InvalidOperationException("Guests must not load an account phone.");
        public Task<AppDbContext> CreateDbContextAsync(CancellationToken cancellationToken = default) =>
            throw new InvalidOperationException("Guests must not load an account phone.");
    }
}
