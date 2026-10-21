using BeNobat.Web.Domain;
using BeNobat.Web.Application;
using BeNobat.Web.Infrastructure;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Storage;
using Xunit;

namespace BeNobat.Web.Tests;

public class CancellationIntegrationTests
{
    private static readonly DateTimeOffset Now = new(2030, 1, 1, 8, 0, 0, TimeSpan.Zero);
    private static CancellationToken Ct => TestContext.Current.CancellationToken;
    [Fact]
    public async Task SQL_and_display_share_hierarchy_and_exact_deadline()
    {
        await using var f = await Fixture.Create();
        f.Business.CancellationNoticeMinutes = 360; f.Branch.CancellationNoticeMinutes = 240;
        f.Service.CancellationNoticeMinutes = 180; f.Link.CancellationNoticeMinutes = 60;
        await f.Db.SaveChangesAsync(Ct);
        Assert.True(BookingPolicy.CanCustomerCancel(f.Appointment, Now));
        Assert.True(await f.Eligible(Now).AnyAsync(Ct));
        f.Link.CancellationNoticeMinutes = null; await f.Db.SaveChangesAsync(Ct);
        Assert.False(BookingPolicy.CanCustomerCancel(f.Appointment, Now));
        Assert.False(await f.Eligible(Now).AnyAsync(Ct));
        f.Service.CancellationNoticeMinutes = null; f.Branch.CancellationNoticeMinutes = null;
        f.Business.CancellationNoticeMinutes = 120; await f.Db.SaveChangesAsync(Ct);
        Assert.Equal(1, await f.Eligible(Now).ExecuteUpdateAsync(s => s.SetProperty(a => a.Status, AppointmentStatus.Cancelled), Ct));
        Assert.Equal(0, await f.Eligible(Now).ExecuteUpdateAsync(s => s.SetProperty(a => a.Status, AppointmentStatus.Cancelled), Ct));
    }
    [Fact]
    public async Task Zero_setting_persists_and_never_allows_cancellation_after_start()
    {
        await using var f = await Fixture.Create();
        f.Business.CancellationNoticeMinutes = 0; await f.Db.SaveChangesAsync(Ct);
        Assert.Equal(0, await f.Db.Businesses.Where(b => b.Id == f.Business.Id).Select(b => b.CancellationNoticeMinutes).SingleAsync(Ct));
        Assert.True(await f.Eligible(f.Appointment.StartsAt.AddTicks(-10)).AnyAsync(Ct));
        Assert.False(await f.Eligible(f.Appointment.StartsAt).AnyAsync(Ct));
        Assert.False(await f.Eligible(f.Appointment.StartsAt.AddHours(1)).AnyAsync(Ct));
    }
    [Fact]
    public async Task Multi_service_uses_earliest_deadline_and_archived_links_retain_booked_rules()
    {
        await using var f = await Fixture.Create();
        var second = new Service { Business = f.Business, BusinessId = f.Business.Id, Name = "second", CancellationNoticeMinutes = 180 };
        var link = new BranchService { Branch = f.Branch, Service = second, CancellationNoticeMinutes = 240, DeletedAt = Now };
        f.Db.AddRange(second, link);
        f.Db.AppointmentServices.Add(new AppointmentService { Service = f.Service, ServiceId = f.Service.Id, Appointment = f.Appointment });
        f.Db.AppointmentServices.Add(new AppointmentService { Service = second, ServiceId = second.Id, Appointment = f.Appointment });
        f.Link.CancellationNoticeMinutes = 30;
        f.Appointment.StartsAt = Now.AddHours(4); f.Appointment.EndsAt = f.Appointment.StartsAt.AddMinutes(30);
        await f.Db.SaveChangesAsync(Ct);
        Assert.Equal(240, CancellationPolicy.NoticeMinutes(f.Appointment));
        Assert.True(await f.Eligible(Now).AnyAsync(Ct));
        Assert.False(await f.Eligible(Now.AddSeconds(1)).AnyAsync(Ct));
    }
    private sealed class Fixture : IAsyncDisposable
    {
        public AppDbContext Db = null!;
        public IDbContextTransaction Transaction = null!;
        public Business Business = new() { Name = "cancellation", Slug = Guid.NewGuid().ToString("N") };
        public Branch Branch = null!;
        public Service Service = null!;
        public BranchService Link = null!;
        public Appointment Appointment = null!;
        public IQueryable<Appointment> Eligible(DateTimeOffset now) => CustomerCancellation.Eligible(Db,
            Db.Appointments.IgnoreQueryFilters().Where(a => a.Id == Appointment.Id), now);
        public static async Task<Fixture> Create()
        {
            var connection = Environment.GetEnvironmentVariable("BENOBAT_TEST_DB");
            if (string.IsNullOrWhiteSpace(connection)) Assert.Skip("Set BENOBAT_TEST_DB to a disposable PostgreSQL database.");
            var f = new Fixture { Db = new AppDbContext(new DbContextOptionsBuilder<AppDbContext>().UseNpgsql(connection).Options) };
            await f.Db.Database.EnsureCreatedAsync(Ct);
            // Schema upgrades run during application/test database initialization, outside data transactions.
            f.Transaction = await f.Db.Database.BeginTransactionAsync(Ct);
            f.Branch = new Branch { Business = f.Business, BusinessId = f.Business.Id, Name = "branch" };
            f.Service = new Service { Business = f.Business, BusinessId = f.Business.Id, Name = "service" };
            f.Link = new BranchService { Branch = f.Branch, BranchId = f.Branch.Id, Service = f.Service, ServiceId = f.Service.Id };
            var customer = new AppUser { Id = Guid.NewGuid(), UserName = Guid.NewGuid().ToString("N") };
            f.Appointment = new Appointment { Branch = f.Branch, BranchId = f.Branch.Id, Service = f.Service, ServiceId = f.Service.Id,
                Customer = customer, CustomerId = customer.Id, StartsAt = Now.AddHours(2), EndsAt = Now.AddHours(3), Status = AppointmentStatus.Confirmed };
            f.Db.AddRange(f.Business, f.Branch, f.Service, f.Link, customer, f.Appointment);
            await f.Db.SaveChangesAsync(Ct);
            return f;
        }
        public async ValueTask DisposeAsync() { await Transaction.RollbackAsync(Ct); await Transaction.DisposeAsync(); await Db.DisposeAsync(); }
    }
}
