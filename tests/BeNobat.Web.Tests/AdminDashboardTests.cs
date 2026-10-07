using System.Reflection;
using BeNobat.Web.Components.Pages;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using BeNobat.Web.Security;
using Microsoft.AspNetCore.Components.Authorization;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace BeNobat.Web.Tests;

public sealed class AdminDashboardTests
{
    [Fact]
    public void Availability_initial_form_date_can_be_localized_before_loading_finishes()
    {
        var component = new AdminAvailability();
        const BindingFlags flags = BindingFlags.Instance | BindingFlags.NonPublic;
        // First interactive render localizes the form, even before the Add button is pressed.
        typeof(AdminAvailability).GetMethod("SetLocalizedDateParts", flags)!.Invoke(component, null);
        var form = typeof(AdminAvailability).GetField("Form", flags)!.GetValue(component)!;
        var date = (DateOnly)form.GetType().GetProperty("EffectiveDate")!.GetValue(form)!;
        Assert.NotEqual(DateOnly.MinValue, date);
        Assert.NotEmpty(LocalizedDate.Format(date, "fa"));
    }

    [Theory]
    [InlineData(typeof(AdminDashboard), "TodayDate")]
    [InlineData(typeof(AdminCalendar), "SelectedDate")]
    public void Admin_date_can_be_formatted_while_access_scope_is_loading(Type componentType, string dateField)
    {
        using var db = new AppDbContext(new DbContextOptionsBuilder<AppDbContext>()
            .UseNpgsql("Host=localhost;Database=unused").Options);
        var component = Activator.CreateInstance(componentType)!;
        const BindingFlags flags = BindingFlags.Instance | BindingFlags.NonPublic;
        componentType.GetProperty("Access", flags)!.SetValue(component,
            new AdminAccessScope(db, new PendingAuthentication()));

        var initialization = (Task)componentType
            .GetMethod("OnInitializedAsync", flags)!.Invoke(component, null)!;
        Assert.False(initialization.IsCompleted);

        // This is the value consumed by the first render, before DisplayZoneAsync completes.
        var date = (DateOnly)componentType.GetField(dateField, flags)!.GetValue(component)!;
        Assert.NotEqual(DateOnly.MinValue, date);
        Assert.NotEmpty(LocalizedDate.Format(date, "fa"));
    }

    private sealed class PendingAuthentication : AuthenticationStateProvider
    {
        private readonly TaskCompletionSource<AuthenticationState> state = new();
        public override Task<AuthenticationState> GetAuthenticationStateAsync() => state.Task;
    }
}
