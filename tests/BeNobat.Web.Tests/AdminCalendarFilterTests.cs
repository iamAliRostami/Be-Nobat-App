using System.Globalization;
using System.Reflection;
using System.Security.Claims;
using BeNobat.Web.Components.Pages;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using BeNobat.Web.Security;
using Microsoft.AspNetCore.Components.Authorization;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace BeNobat.Web.Tests;

public sealed class AdminCalendarFilterTests
{
    private const BindingFlags PrivateInstance = BindingFlags.Instance | BindingFlags.NonPublic;
    private const BindingFlags PrivateStatic = BindingFlags.Static | BindingFlags.NonPublic;

    [Fact]
    public void Day_and_status_filters_preserve_the_authorized_source_and_local_day_boundaries()
    {
        var component = new AdminCalendar();
        var date = new DateOnly(2030, 1, 2);
        var (start, end) = BranchClock.DayWindow(date, "Asia/Tehran");
        Set(component, "SelectedDate", date);
        Set(component, "StatusFilter", "Pending");
        var first = AppointmentAt(start);
        var last = AppointmentAt(end.AddTicks(-1));
        var before = AppointmentAt(start.AddTicks(-1));
        var nextDay = AppointmentAt(end);
        var confirmed = AppointmentAt(start, AppointmentStatus.Confirmed);
        var unauthorized = AppointmentAt(start);

        // Access.AppointmentsAsync supplies this already scoped query. Filters must only narrow it.
        var authorized = new[] { first, last, before, nextDay, confirmed, unauthorized }
            .AsQueryable().Where(a => a.Id != unauthorized.Id);
        var result = Filter(component, authorized).ToList();

        Assert.Equal(new[] { first.Id, last.Id }, result.Select(a => a.Id));
    }

    [Fact]
    public void Exact_entity_filters_combine_and_match_services_inside_a_multi_service_booking()
    {
        var matching = AppointmentAt(DateTimeOffset.UtcNow);
        var extraService = new Service { Name = "خدمت دوم" };
        matching.Services.Add(new AppointmentService { ServiceId = extraService.Id, Service = extraService });
        var sameServiceOtherCustomer = AppointmentAt(DateTimeOffset.UtcNow);
        sameServiceOtherCustomer.Services.Add(new AppointmentService { ServiceId = extraService.Id, Service = extraService });
        var component = new AdminCalendar
        {
            CustomerQuery = matching.CustomerId.ToString(),
            ServiceQuery = extraService.Id.ToString(),
            BranchQuery = matching.BranchId.ToString(),
            ResourceQuery = matching.ResourceId.ToString(),
        };
        Set(component, "RangeFilter", "all");
        Set(component, "BusinessFilter", matching.Branch.BusinessId.ToString());

        var result = Filter(component, new[] { matching, sameServiceOtherCustomer }.AsQueryable()).ToList();

        Assert.Equal(matching.Id, Assert.Single(result).Id);
    }

    [Fact]
    public void Invalid_filter_values_do_not_throw_or_change_the_scoped_all_dates_query()
    {
        var appointment = AppointmentAt(DateTimeOffset.UtcNow);
        var component = new AdminCalendar
        {
            CustomerQuery = "invalid", ServiceQuery = "invalid", BranchQuery = "invalid", ResourceQuery = "invalid",
        };
        Set(component, "RangeFilter", "all");
        Set(component, "StatusFilter", "999");
        Set(component, "BusinessFilter", "invalid");

        Assert.Equal(appointment.Id, Assert.Single(Filter(component, new[] { appointment }.AsQueryable())).Id);
    }

    [Theory]
    [InlineData("0001-01-01")]
    [InlineData("0622-03-21")]
    [InlineData("9999-12-31")]
    [InlineData("not-a-date")]
    public void Date_query_outside_the_calendar_or_day_window_range_falls_back_to_a_safe_date(string value)
    {
        var fallback = new DateOnly(2030, 1, 2);
        var parsed = (DateOnly)typeof(AdminCalendar).GetMethod("ParseSelectedDate", PrivateStatic)!
            .Invoke(null, new object?[] { value, fallback })!;

        Assert.Equal(fallback, parsed);
        Assert.NotEmpty(LocalizedDate.Format(parsed, "fa"));
    }

    [Fact]
    public void Dashboard_links_use_gregorian_url_dates_even_with_a_persian_current_culture()
    {
        var component = new AdminDashboard();
        Set(component, "TodayDate", new DateOnly(2030, 1, 2));
        var originalCulture = CultureInfo.CurrentCulture;
        try
        {
            CultureInfo.CurrentCulture = CultureInfo.GetCultureInfo("fa-IR");
            var link = (string)typeof(AdminDashboard).GetMethod("CalendarUrl", PrivateInstance)!
                .Invoke(component, new object?[] { AppointmentStatus.Completed, false })!;

            Assert.Equal("/admin/calendar?date=2030-01-02&status=Completed", link);
        }
        finally { CultureInfo.CurrentCulture = originalCulture; }
    }

    [Theory]
    [InlineData(typeof(AdminCalendar))]
    [InlineData(typeof(AdminDashboard))]
    public void Repeated_customer_and_provider_labels_keep_a_distinct_provider_role_label(Type componentType)
    {
        var appointment = AppointmentAt(DateTimeOffset.UtcNow);
        appointment.Customer.DisplayName = "علی کریمی";
        appointment.Resource = new Resource { Name = "علي كريمي" };

        var label = (string)componentType.GetMethod("ResourceLabel", PrivateStatic)!.Invoke(null, new object[] { appointment })!;

        Assert.Equal("ارائه‌دهنده", label);
    }

    [Theory]
    [InlineData(typeof(AdminCalendar))]
    [InlineData(typeof(AdminDashboard))]
    public async Task Navigation_disposal_cancels_page_owned_context_creation_without_using_the_circuit_context(Type componentType)
    {
        using var circuitDb = new AppDbContext(new DbContextOptionsBuilder<AppDbContext>()
            .UseNpgsql("Host=localhost;Database=unused").Options);
        var auth = new AdminAuthentication();
        var factory = new BlockingContextFactory();
        var component = Activator.CreateInstance(componentType)!;
        componentType.GetProperty("Access", PrivateInstance)!.SetValue(component, new AdminAccessScope(circuitDb, auth));
        componentType.GetProperty("Authentication", PrivateInstance)!.SetValue(component, auth);
        componentType.GetProperty("DbFactory", PrivateInstance)!.SetValue(component, factory);

        var initialization = (Task)componentType.GetMethod("OnInitializedAsync", PrivateInstance)!.Invoke(component, null)!;
        await factory.Started.Task.WaitAsync(TimeSpan.FromSeconds(3), TestContext.Current.CancellationToken);
        Assert.False(initialization.IsCompleted);
        ((IDisposable)component).Dispose();

        await initialization.WaitAsync(TimeSpan.FromSeconds(3), TestContext.Current.CancellationToken);
        Assert.True(Assert.Single(factory.Tokens).IsCancellationRequested);
        // No connection is needed: the circuit-scoped context is never used for these reads.
        Assert.Equal(System.Data.ConnectionState.Closed, circuitDb.Database.GetDbConnection().State);
    }

    [Fact]
    public async Task A_new_calendar_filter_load_cancels_the_previous_request_and_navigation_cancels_the_latest()
    {
        var factory = new BlockingContextFactory();
        var component = new AdminCalendar();
        typeof(AdminCalendar).GetProperty("DbFactory", PrivateInstance)!.SetValue(component, factory);
        var load = typeof(AdminCalendar).GetMethod("LoadAsync", PrivateInstance)!;
        var first = (Task)load.Invoke(component, null)!;
        var latest = (Task)load.Invoke(component, null)!;

        Assert.Equal(2, factory.Tokens.Count);
        Assert.True(factory.Tokens[0].IsCancellationRequested);
        Assert.False(factory.Tokens[1].IsCancellationRequested);
        await first.WaitAsync(TimeSpan.FromSeconds(3), TestContext.Current.CancellationToken);
        component.Dispose();
        await latest.WaitAsync(TimeSpan.FromSeconds(3), TestContext.Current.CancellationToken);
        Assert.True(factory.Tokens[1].IsCancellationRequested);
    }

    [Theory]
    [InlineData(typeof(AdminCalendar))]
    [InlineData(typeof(AdminDashboard))]
    public async Task Overlapping_status_actions_are_blocked_per_appointment_and_the_guard_is_released_on_cancellation(Type componentType)
    {
        var factory = new BlockingContextFactory();
        var component = Activator.CreateInstance(componentType)!;
        componentType.GetProperty("DbFactory", PrivateInstance)!.SetValue(component, factory);
        var update = componentType.GetMethod("UpdateStatus", PrivateInstance)!;
        var appointmentId = Guid.NewGuid();
        var first = (Task)update.Invoke(component, new object[] { appointmentId, AppointmentStatus.Completed })!;
        var duplicate = (Task)update.Invoke(component, new object[] { appointmentId, AppointmentStatus.NoShow })!;

        await duplicate.WaitAsync(TimeSpan.FromSeconds(3), TestContext.Current.CancellationToken);
        Assert.Single(factory.Tokens);
        var busy = (HashSet<Guid>)componentType.GetField("UpdatingAppointments", PrivateInstance)!.GetValue(component)!;
        Assert.Contains(appointmentId, busy);

        ((IDisposable)component).Dispose();
        await first.WaitAsync(TimeSpan.FromSeconds(3), TestContext.Current.CancellationToken);
        Assert.Empty(busy);
    }

    private sealed class AdminAuthentication : AuthenticationStateProvider
    {
        public override Task<AuthenticationState> GetAuthenticationStateAsync() => Task.FromResult(new AuthenticationState(
            new ClaimsPrincipal(new ClaimsIdentity(new[] { new Claim(ClaimTypes.Role, AppRoles.PlatformAdmin) }, "Test"))));
    }

    private sealed class BlockingContextFactory : IDbContextFactory<AppDbContext>
    {
        private readonly TaskCompletionSource<AppDbContext> context = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public TaskCompletionSource<bool> Started { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public List<CancellationToken> Tokens { get; } = [];
        public AppDbContext CreateDbContext() => throw new InvalidOperationException("Use the asynchronous factory.");
        public Task<AppDbContext> CreateDbContextAsync(CancellationToken cancellationToken = default)
        {
            Tokens.Add(cancellationToken);
            Started.TrySetResult(true);
            return context.Task.WaitAsync(cancellationToken);
        }
    }

    private static IQueryable<Appointment> Filter(AdminCalendar component, IQueryable<Appointment> query) =>
        (IQueryable<Appointment>)typeof(AdminCalendar).GetMethod("FilterAppointments", PrivateInstance)!
            .Invoke(component, new object[] { query })!;

    private static void Set(object component, string field, object value) =>
        component.GetType().GetField(field, PrivateInstance)!.SetValue(component, value);

    private static Appointment AppointmentAt(DateTimeOffset start, AppointmentStatus status = AppointmentStatus.Pending)
    {
        var business = new Business { Name = "کسب‌وکار" };
        var branch = new Branch { Name = "شعبه", BusinessId = business.Id, Business = business };
        var service = new Service { Name = "خدمت", BusinessId = business.Id, Business = business };
        var customer = new AppUser { Id = Guid.NewGuid(), DisplayName = "مشتری" };
        return new Appointment
        {
            BranchId = branch.Id, Branch = branch, ServiceId = service.Id, Service = service,
            CustomerId = customer.Id, Customer = customer, ResourceId = Guid.NewGuid(),
            StartsAt = start, EndsAt = start.AddMinutes(30), Status = status,
        };
    }
}
