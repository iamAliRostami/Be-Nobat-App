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
    public void Dashboard_date_can_be_formatted_while_access_scope_is_loading()
    {
        using var db = new AppDbContext(new DbContextOptionsBuilder<AppDbContext>()
            .UseNpgsql("Host=localhost;Database=unused").Options);
        var dashboard = new AdminDashboard();
        const BindingFlags flags = BindingFlags.Instance | BindingFlags.NonPublic;
        typeof(AdminDashboard).GetProperty("Access", flags)!.SetValue(dashboard,
            new AdminAccessScope(db, new PendingAuthentication()));

        var initialization = (Task)typeof(AdminDashboard)
            .GetMethod("OnInitializedAsync", flags)!.Invoke(dashboard, null)!;
        Assert.False(initialization.IsCompleted);

        // This is the value consumed by the first render, before DisplayZoneAsync completes.
        var date = (DateOnly)typeof(AdminDashboard).GetField("TodayDate", flags)!.GetValue(dashboard)!;
        Assert.NotEqual(DateOnly.MinValue, date);
        Assert.NotEmpty(LocalizedDate.Format(date, "fa"));
    }

    private sealed class PendingAuthentication : AuthenticationStateProvider
    {
        private readonly TaskCompletionSource<AuthenticationState> state = new();
        public override Task<AuthenticationState> GetAuthenticationStateAsync() => state.Task;
    }
}
