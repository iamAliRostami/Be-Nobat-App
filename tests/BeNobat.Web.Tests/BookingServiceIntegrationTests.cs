using BeNobat.Web.Application;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using BeNobat.Web.Security;
using System.Security.Claims;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace BeNobat.Web.Tests;

/// <summary>Run against a disposable PostgreSQL database via BENOBAT_TEST_DB after normal schema initialization.</summary>
public sealed class BookingServiceIntegrationTests
{
    [Fact]
    public async Task Customer_action_authorization_checks_current_security_stamp_and_lockout()
    {
        await using var fixture = await Fixture.CreateAsync();
        var user = fixture.Customers[0];
        var principal = new ClaimsPrincipal(new ClaimsIdentity([new Claim(ClaimTypes.NameIdentifier, user.Id.ToString()),
            new Claim("AspNet.Identity.SecurityStamp", user.SecurityStamp!)], "Cookie"));
        await using var db = fixture.Factory.CreateDbContext();
        Assert.True(await UserContext.CurrentSessionUsers(db, principal).AnyAsync(TestContext.Current.CancellationToken));
        await db.Users.Where(x => x.Id == user.Id).ExecuteUpdateAsync(s => s.SetProperty(x => x.SecurityStamp, "revoked"), TestContext.Current.CancellationToken);
        Assert.False(await UserContext.CurrentSessionUsers(db, principal).AnyAsync(TestContext.Current.CancellationToken));
        await db.Users.Where(x => x.Id == user.Id).ExecuteUpdateAsync(s => s.SetProperty(x => x.SecurityStamp, user.SecurityStamp)
            .SetProperty(x => x.LockoutEnabled, true).SetProperty(x => x.LockoutEnd, DateTimeOffset.UtcNow.AddDays(1)), TestContext.Current.CancellationToken);
        Assert.False(await UserContext.CurrentSessionUsers(db, principal).AnyAsync(TestContext.Current.CancellationToken));
    }

    [Fact]
    public async Task An_account_disabled_after_the_booking_page_opened_cannot_book()
    {
        await using var fixture = await Fixture.CreateAsync();
        await using var db = fixture.Factory.CreateDbContext();
        await db.Users.Where(x => x.Id == fixture.Customers[0].Id).ExecuteUpdateAsync(s => s
            .SetProperty(x => x.LockoutEnabled, true).SetProperty(x => x.LockoutEnd, DateTimeOffset.UtcNow.AddDays(1)), TestContext.Current.CancellationToken);
        var result = await fixture.Booking.BookAsync(fixture.Customers[0].Id, fixture.Request(fixture.Start), TestContext.Current.CancellationToken);
        Assert.Equal(BookingFailure.CustomerNotFound, result.Failure);
        Assert.Equal(0, await fixture.CountAsync());
    }

    [Fact]
    public async Task A_revoked_web_cookie_stamp_cannot_book_after_a_password_reset()
    {
        await using var fixture = await Fixture.CreateAsync();
        var result = await fixture.Booking.BookAsync(fixture.Customers[0].Id,
            fixture.Request(fixture.Start) with { ExpectedSecurityStamp = "revoked-stamp" }, TestContext.Current.CancellationToken);
        Assert.Equal(BookingFailure.CustomerNotFound, result.Failure);
        Assert.Equal(0, await fixture.CountAsync());
    }

    [Fact]
    public async Task Confirmation_reloads_terms_and_snapshots_the_branch_price_and_duration()
    {
        await using var fixture = await Fixture.CreateAsync();
        await using (var db = fixture.Factory.CreateDbContext())
        {
            await db.Services.Where(x => x.Id == fixture.Service.Id).ExecuteUpdateAsync(s => s.SetProperty(x => x.DurationMinutes, 60), TestContext.Current.CancellationToken);
            await db.BranchServices.Where(x => x.ServiceId == fixture.Service.Id).ExecuteUpdateAsync(s => s.SetProperty(x => x.Price, 1950m), TestContext.Current.CancellationToken);
        }
        var stale = await fixture.Booking.BookAsync(fixture.Customers[0].Id,
            fixture.Request(fixture.Start) with { ExpectedPrice = 1250, ExpectedDurationMinutes = 30 }, TestContext.Current.CancellationToken);
        Assert.Equal(BookingFailure.TermsChanged, stale.Failure);
        var fresh = await fixture.Booking.BookAsync(fixture.Customers[0].Id, fixture.Request(fixture.Start), TestContext.Current.CancellationToken);
        Assert.True(fresh.Success);
        Assert.Equal(1950, fresh.Appointment!.FinalPrice);
        Assert.Equal(TimeSpan.FromMinutes(60), fresh.Appointment.EndsAt - fresh.Appointment.StartsAt);
        var snapshot = Assert.Single(fresh.Appointment.Services);
        Assert.Equal(1950, snapshot.Price);
        Assert.Equal(60, snapshot.DurationMinutes);
        await using var verification = fixture.Factory.CreateDbContext();
        var user = await verification.Users.SingleAsync(x => x.Id == fixture.Customers[0].Id, TestContext.Current.CancellationToken);
        Assert.Equal("09121234567", user.PhoneNumber);
        Assert.False(user.PhoneNumberConfirmed);
    }

    [Fact]
    public async Task A_service_removed_from_the_branch_after_selection_cannot_be_booked()
    {
        await using var fixture = await Fixture.CreateAsync();
        await using var db = fixture.Factory.CreateDbContext();
        await db.BranchServices.Where(x => x.BranchId == fixture.Branch.Id).ExecuteUpdateAsync(s => s.SetProperty(x => x.DeletedAt, DateTimeOffset.UtcNow), TestContext.Current.CancellationToken);
        var result = await fixture.Booking.BookAsync(fixture.Customers[0].Id, fixture.Request(fixture.Start), TestContext.Current.CancellationToken);
        Assert.Equal(BookingFailure.ServiceUnavailable, result.Failure);
        Assert.Equal(0, await fixture.CountAsync());
    }

    [Fact]
    public async Task A_removed_business_and_a_provider_without_explicit_qualification_are_rejected()
    {
        await using var fixture = await Fixture.CreateAsync();
        await using var db = fixture.Factory.CreateDbContext();
        await db.ServiceResources.Where(x => x.ResourceId == fixture.Resources[0].Id).ExecuteUpdateAsync(s => s.SetProperty(x => x.DeletedAt, DateTimeOffset.UtcNow), TestContext.Current.CancellationToken);
        var ineligible = await fixture.Booking.BookAsync(fixture.Customers[0].Id,
            fixture.Request(fixture.Start) with { ResourceId = fixture.Resources[0].Id }, TestContext.Current.CancellationToken);
        Assert.Equal(BookingFailure.NoEligibleProvider, ineligible.Failure);
        await db.Businesses.Where(x => x.Id == fixture.Business.Id).ExecuteUpdateAsync(s => s.SetProperty(x => x.DeletedAt, DateTimeOffset.UtcNow), TestContext.Current.CancellationToken);
        var archived = await fixture.Booking.BookAsync(fixture.Customers[0].Id, fixture.Request(fixture.Start), TestContext.Current.CancellationToken);
        Assert.Equal(BookingFailure.BusinessUnavailable, archived.Failure);
    }

    [Fact]
    public async Task Two_simultaneous_customers_using_any_provider_get_different_providers()
    {
        await using var fixture = await Fixture.CreateAsync();
        var results = await Task.WhenAll(fixture.Customers.Take(2).Select(customer =>
            fixture.Booking.BookAsync(customer.Id, fixture.Request(fixture.Start), TestContext.Current.CancellationToken)));
        Assert.All(results, result => Assert.True(result.Success, result.Failure?.ToString()));
        Assert.Equal(2, results.Select(x => x.Appointment!.ResourceId).Distinct().Count());
        var full = await fixture.Booking.BookAsync(fixture.Customers[2].Id, fixture.Request(fixture.Start), TestContext.Current.CancellationToken);
        Assert.Equal(BookingFailure.SlotUnavailable, full.Failure);
        Assert.Equal(2, await fixture.CountAsync());
    }

    [Fact]
    public async Task Simultaneous_customer_requests_on_different_providers_cannot_overlap()
    {
        await using var fixture = await Fixture.CreateAsync();
        var results = await Task.WhenAll(fixture.Resources.Select(resource => fixture.Booking.BookAsync(
            fixture.Customers[0].Id, fixture.Request(fixture.Start) with { ResourceId = resource.Id }, TestContext.Current.CancellationToken)));
        Assert.Single(results, x => x.Success);
        Assert.Single(results, x => x.Failure == BookingFailure.CustomerOverlap);
        Assert.Equal(1, await fixture.CountAsync());
    }

    [Fact]
    public async Task Simultaneous_customer_requests_cannot_exceed_the_active_booking_limit()
    {
        await using var fixture = await Fixture.CreateAsync();
        await using (var db = fixture.Factory.CreateDbContext())
        {
            for (var index = 0; index < BookingPolicy.MaxActiveAppointmentsPerCustomer - 1; index++)
                db.Appointments.Add(new Appointment
                {
                    BranchId = fixture.Branch.Id, ServiceId = fixture.Service.Id, ResourceId = fixture.Resources[0].Id,
                    CustomerId = fixture.Customers[0].Id, StartsAt = fixture.Start.AddDays(2).AddMinutes(index * 30),
                    EndsAt = fixture.Start.AddDays(2).AddMinutes((index + 1) * 30), Status = AppointmentStatus.Confirmed,
                });
            await db.SaveChangesAsync(TestContext.Current.CancellationToken);
        }
        var results = await Task.WhenAll(fixture.Booking.BookAsync(fixture.Customers[0].Id, fixture.Request(fixture.Start), TestContext.Current.CancellationToken),
            fixture.Booking.BookAsync(fixture.Customers[0].Id, fixture.Request(fixture.Start.AddHours(1)), TestContext.Current.CancellationToken));
        Assert.Single(results, x => x.Success);
        Assert.Single(results, x => x.Failure == BookingFailure.ActiveLimitReached);
        Assert.Equal(BookingPolicy.MaxActiveAppointmentsPerCustomer, await fixture.CountAsync());
    }

    [Fact]
    public async Task Exact_slots_weekly_offdays_and_cross_business_service_selection_are_enforced()
    {
        await using var fixture = await Fixture.CreateAsync();
        var arbitrary = await fixture.Booking.BookAsync(fixture.Customers[0].Id, fixture.Request(fixture.Start.AddMinutes(1)), TestContext.Current.CancellationToken);
        Assert.Equal(BookingFailure.SlotUnavailable, arbitrary.Failure);
        var invalidServices = await fixture.Booking.BookAsync(fixture.Customers[0].Id,
            fixture.Request(fixture.Start) with { ServiceIds = new[] { Guid.NewGuid() } }, TestContext.Current.CancellationToken);
        Assert.Equal(BookingFailure.ServiceUnavailable, invalidServices.Failure);
        await using var db = fixture.Factory.CreateDbContext();
        db.AvailabilityRules.Add(new AvailabilityRule
        {
            BusinessId = fixture.Business.Id, BranchId = fixture.Branch.Id, DayOfWeek = fixture.Date.AddDays(1).DayOfWeek,
            StartsAt = new TimeOnly(9, 0), EndsAt = new TimeOnly(18, 0),
        });
        await db.SaveChangesAsync(TestContext.Current.CancellationToken);
        var closed = await fixture.Booking.GetAvailabilityAsync(fixture.Business.Id, fixture.Branch.Id, [fixture.Service.Id], null, fixture.Date, TestContext.Current.CancellationToken);
        Assert.True(closed.Success);
        Assert.All(closed.SlotsByResource.Values, Assert.Empty);
        var cannotBook = await fixture.Booking.BookAsync(fixture.Customers[0].Id, fixture.Request(fixture.Start), TestContext.Current.CancellationToken);
        Assert.Equal(BookingFailure.SlotUnavailable, cannotBook.Failure);
    }

    [Fact]
    public async Task Approval_mode_is_reloaded_at_confirmation_and_customer_history_survives_archival()
    {
        await using var fixture = await Fixture.CreateAsync();
        await using var db = fixture.Factory.CreateDbContext();
        await db.Businesses.Where(x => x.Id == fixture.Business.Id).ExecuteUpdateAsync(s => s.SetProperty(x => x.RequiresApproval, false), TestContext.Current.CancellationToken);
        var result = await fixture.Booking.BookAsync(fixture.Customers[0].Id, fixture.Request(fixture.Start), TestContext.Current.CancellationToken);
        Assert.Equal(AppointmentStatus.Confirmed, result.Appointment!.Status);
        await db.Services.Where(x => x.Id == fixture.Service.Id).ExecuteUpdateAsync(s => s.SetProperty(x => x.DeletedAt, DateTimeOffset.UtcNow), TestContext.Current.CancellationToken);
        await db.Branches.Where(x => x.Id == fixture.Branch.Id).ExecuteUpdateAsync(s => s.SetProperty(x => x.DeletedAt, DateTimeOffset.UtcNow), TestContext.Current.CancellationToken);
        var history = await db.Appointments.IgnoreQueryFilters().AsNoTracking().Include(x => x.Service)
            .Include(x => x.Services).ThenInclude(x => x.Service).Include(x => x.Branch).ThenInclude(x => x.Business)
            .Where(x => x.CustomerId == fixture.Customers[0].Id && x.DeletedAt == null).ToListAsync(TestContext.Current.CancellationToken);
        Assert.Equal(fixture.Service.Name, Assert.Single(history).Service.Name);
        Assert.Equal(fixture.Branch.Name, history[0].Branch.Name);
    }

    private sealed class Fixture : IAsyncDisposable
    {
        public readonly Business Business = new() { Name = "Booking integration fixture", Slug = "booking-test-" + Guid.NewGuid().ToString("N") };
        public Branch Branch { get; private set; } = null!;
        public Service Service { get; private set; } = null!;
        public Resource[] Resources { get; private set; } = [];
        public AppUser[] Customers { get; private set; } = [];
        public ContextFactory Factory { get; private set; } = null!;
        public AppointmentBookingService Booking { get; private set; } = null!;
        public DateOnly Date => BranchClock.Today("Etc/UTC").AddDays(7);
        public DateTimeOffset Start => BranchClock.FromLocal(Date, new TimeOnly(9, 0), "Etc/UTC");

        public static async Task<Fixture> CreateAsync()
        {
            var connection = Environment.GetEnvironmentVariable("BENOBAT_TEST_DB");
            if (string.IsNullOrWhiteSpace(connection)) Assert.Skip("Set BENOBAT_TEST_DB to an initialized disposable PostgreSQL database.");
            var fixture = new Fixture { Factory = new ContextFactory(new DbContextOptionsBuilder<AppDbContext>().UseNpgsql(connection).Options) };
            fixture.Booking = new AppointmentBookingService(fixture.Factory);
            fixture.Branch = new Branch { Name = "Test branch", BusinessId = fixture.Business.Id, TimeZoneId = "Etc/UTC" };
            fixture.Service = new Service { Name = "Test service", BusinessId = fixture.Business.Id, DurationMinutes = 30, Price = 1000 };
            fixture.Resources = Enumerable.Range(0, 2).Select(index => new Resource { Name = "Provider " + index, BranchId = fixture.Branch.Id }).ToArray();
            fixture.Customers = Enumerable.Range(0, 3).Select(index => new AppUser
            {
                Id = Guid.NewGuid(), UserName = "booking-test-" + Guid.NewGuid().ToString("N"), DisplayName = "Test customer " + index,
                SecurityStamp = Guid.NewGuid().ToString("N"),
                PhoneNumber = "09351234567", PhoneNumberConfirmed = true,
            }).ToArray();
            await using var db = fixture.Factory.CreateDbContext();
            db.AddRange(fixture.Business, fixture.Branch, fixture.Service);
            db.Resources.AddRange(fixture.Resources);
            db.Users.AddRange(fixture.Customers);
            db.BranchServices.Add(new BranchService { BranchId = fixture.Branch.Id, ServiceId = fixture.Service.Id, Price = 1250 });
            db.ServiceResources.AddRange(fixture.Resources.Select(x => new ServiceResource { ResourceId = x.Id, ServiceId = fixture.Service.Id }));
            await db.SaveChangesAsync(TestContext.Current.CancellationToken);
            return fixture;
        }

        public AppointmentBookingRequest Request(DateTimeOffset start) => new(Business.Id, Branch.Id, [Service.Id], null,
            start, "09121234567", "Test note", true);

        public async Task<int> CountAsync()
        {
            await using var db = Factory.CreateDbContext();
            return await db.Appointments.IgnoreQueryFilters().CountAsync(x => x.BranchId == Branch.Id, TestContext.Current.CancellationToken);
        }

        public async ValueTask DisposeAsync()
        {
            await using var db = Factory.CreateDbContext();
            await db.Appointments.IgnoreQueryFilters().Where(x => x.BranchId == Branch.Id).ExecuteDeleteAsync(TestContext.Current.CancellationToken);
            await db.AvailabilityRules.IgnoreQueryFilters().Where(x => x.BusinessId == Business.Id).ExecuteDeleteAsync(TestContext.Current.CancellationToken);
            var ids = Resources.Select(x => x.Id).ToList();
            await db.ServiceResources.IgnoreQueryFilters().Where(x => ids.Contains(x.ResourceId)).ExecuteDeleteAsync(TestContext.Current.CancellationToken);
            await db.BranchServices.IgnoreQueryFilters().Where(x => x.BranchId == Branch.Id).ExecuteDeleteAsync(TestContext.Current.CancellationToken);
            await db.Resources.IgnoreQueryFilters().Where(x => x.BranchId == Branch.Id).ExecuteDeleteAsync(TestContext.Current.CancellationToken);
            await db.Services.IgnoreQueryFilters().Where(x => x.BusinessId == Business.Id).ExecuteDeleteAsync(TestContext.Current.CancellationToken);
            await db.Branches.IgnoreQueryFilters().Where(x => x.BusinessId == Business.Id).ExecuteDeleteAsync(TestContext.Current.CancellationToken);
            await db.Businesses.IgnoreQueryFilters().Where(x => x.Id == Business.Id).ExecuteDeleteAsync(TestContext.Current.CancellationToken);
            var customers = Customers.Select(x => x.Id).ToList();
            await db.Users.Where(x => customers.Contains(x.Id)).ExecuteDeleteAsync(TestContext.Current.CancellationToken);
        }
    }

    private sealed class ContextFactory(DbContextOptions<AppDbContext> options) : IDbContextFactory<AppDbContext>
    {
        public AppDbContext CreateDbContext() => new(options);
        public Task<AppDbContext> CreateDbContextAsync(CancellationToken cancellationToken = default) => Task.FromResult(CreateDbContext());
    }
}
