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
