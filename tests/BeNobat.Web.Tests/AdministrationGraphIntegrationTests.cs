using System.Security.Claims;
using BeNobat.Web.Api;
using Microsoft.AspNetCore.Http;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using BeNobat.Web.Security;
using Microsoft.AspNetCore.Components.Authorization;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Storage;
using Xunit;

namespace BeNobat.Web.Tests;

/// <summary>Behavioral checks against an initialized disposable PostgreSQL database, using BENOBAT_TEST_DB.</summary>
public sealed class AdministrationGraphIntegrationTests
{
    [Fact]
    public async Task Business_suspension_hides_descendants_and_restore_preserves_an_independently_archived_resource()
    {
        await using var fixture = await Fixture.CreateAsync();
        var db = fixture.Db;
        Assert.Equal(2, await db.Resources.CountAsync(r => r.BranchId == fixture.Branch.Id, TestContext.Current.CancellationToken));
        await fixture.ArchiveAsync(fixture.Anonymous);
        await fixture.ArchiveAsync(fixture.Business);

        Assert.False(await db.Branches.AnyAsync(b => b.Id == fixture.Branch.Id, TestContext.Current.CancellationToken));
        Assert.False(await db.Services.AnyAsync(s => s.Id == fixture.Service.Id, TestContext.Current.CancellationToken));
        Assert.False(await db.Resources.AnyAsync(r => r.BranchId == fixture.Branch.Id, TestContext.Current.CancellationToken));
        Assert.False(await db.BranchMemberships.AnyAsync(m => m.BranchId == fixture.Branch.Id, TestContext.Current.CancellationToken));
        Assert.False(await db.BranchServices.AnyAsync(l => l.BranchId == fixture.Branch.Id, TestContext.Current.CancellationToken));
        Assert.False(await db.ServiceResources.AnyAsync(l => l.ServiceId == fixture.Service.Id, TestContext.Current.CancellationToken));
        Assert.False(await db.AvailabilityRules.AnyAsync(r => r.BusinessId == fixture.Business.Id, TestContext.Current.CancellationToken));

        await fixture.RestoreAsync(fixture.Business);
        Assert.True(await db.Branches.AnyAsync(b => b.Id == fixture.Branch.Id, TestContext.Current.CancellationToken));
        Assert.True(await db.Services.AnyAsync(s => s.Id == fixture.Service.Id, TestContext.Current.CancellationToken));
        Assert.Equal(fixture.Staff.Id, Assert.Single(await db.Resources.AsNoTracking()
            .Where(r => r.BranchId == fixture.Branch.Id).Select(r => r.Id).ToListAsync(TestContext.Current.CancellationToken)));
        Assert.True(await db.BranchServices.AnyAsync(l => l.ServiceId == fixture.Service.Id, TestContext.Current.CancellationToken));
        Assert.False(await db.ServiceResources.AnyAsync(l => l.ResourceId == fixture.Anonymous.Id, TestContext.Current.CancellationToken));
        Assert.NotNull(await db.Resources.IgnoreQueryFilters().Where(r => r.Id == fixture.Anonymous.Id).Select(r => r.DeletedAt).SingleAsync(TestContext.Current.CancellationToken));
    }

    [Fact]
    public async Task Membership_and_lockout_changes_hide_account_providers_while_anonymous_resources_remain_available()
    {
        await using var fixture = await Fixture.CreateAsync();
        var db = fixture.Db;
        Assert.True(await db.Resources.AnyAsync(r => r.Id == fixture.Staff.Id, TestContext.Current.CancellationToken));
        await fixture.ArchiveAsync(fixture.StaffMembership);
        await AssertStaffHiddenAsync(fixture);
        await fixture.RestoreAsync(fixture.StaffMembership);
        Assert.True(await db.Resources.AnyAsync(r => r.Id == fixture.Staff.Id, TestContext.Current.CancellationToken));
        await db.BranchMemberships.IgnoreQueryFilters().Where(m => m.Id == fixture.StaffMembership.Id)
            .ExecuteUpdateAsync(s => s.SetProperty(m => m.Role, AppRoles.Customer), TestContext.Current.CancellationToken);
        await AssertStaffHiddenAsync(fixture);
        await db.BranchMemberships.IgnoreQueryFilters().Where(m => m.Id == fixture.StaffMembership.Id)
            .ExecuteUpdateAsync(s => s.SetProperty(m => m.Role, AppRoles.Staff), TestContext.Current.CancellationToken);

        // Identity ignores LockoutEnd while LockoutEnabled is false; resource
        // eligibility must follow the same account-state rule as authentication.
        await db.Users.Where(u => u.Id == fixture.Provider.Id).ExecuteUpdateAsync(s => s
            .SetProperty(u => u.LockoutEnabled, false).SetProperty(u => u.LockoutEnd, DateTimeOffset.UtcNow.AddDays(1)), TestContext.Current.CancellationToken);
        Assert.True(await db.Resources.AnyAsync(r => r.Id == fixture.Staff.Id, TestContext.Current.CancellationToken));
        await db.Users.Where(u => u.Id == fixture.Provider.Id).ExecuteUpdateAsync(s => s.SetProperty(u => u.LockoutEnabled, true), TestContext.Current.CancellationToken);
        await AssertStaffHiddenAsync(fixture);
        await db.Users.Where(u => u.Id == fixture.Provider.Id).ExecuteUpdateAsync(s => s.SetProperty(u => u.LockoutEnd, DateTimeOffset.UtcNow.AddMinutes(-1)), TestContext.Current.CancellationToken);
        Assert.True(await db.Resources.AnyAsync(r => r.Id == fixture.Staff.Id, TestContext.Current.CancellationToken));
        Assert.True(await db.ServiceResources.AnyAsync(l => l.ResourceId == fixture.Staff.Id, TestContext.Current.CancellationToken));
        Assert.True(await db.AvailabilityRules.AnyAsync(r => r.ResourceId == fixture.Staff.Id, TestContext.Current.CancellationToken));
    }

    [Fact]
    public async Task Cross_business_assignments_and_mismatched_schedule_scopes_are_stored_but_hidden_from_the_live_graph()
    {
        await using var fixture = await Fixture.CreateAsync();
        var db = fixture.Db;
        var wrongBranchService = new BranchService { BranchId = fixture.Branch.Id, ServiceId = fixture.OtherService.Id };
        var wrongProvider = new ServiceResource { ServiceId = fixture.Service.Id, ResourceId = fixture.OtherResource.Id };
        AvailabilityRule[] wrongRules =
        [
            new() { BusinessId = fixture.Business.Id, BranchId = fixture.OtherBranch.Id },
            new() { BusinessId = fixture.Business.Id, BranchId = fixture.Branch.Id, ResourceId = fixture.OtherResource.Id },
            new() { BusinessId = fixture.Business.Id, ResourceId = fixture.OtherResource.Id },
            new() { BusinessId = fixture.OtherBusiness.Id, BranchId = fixture.OtherBranch.Id, ResourceId = fixture.Staff.Id },
        ];
        db.AddRange(wrongBranchService, wrongProvider);
        db.AvailabilityRules.AddRange(wrongRules);
        await db.SaveChangesAsync(TestContext.Current.CancellationToken);
        db.ChangeTracker.Clear();

        Assert.True(await db.BranchServices.IgnoreQueryFilters().AnyAsync(l => l.Id == wrongBranchService.Id, TestContext.Current.CancellationToken));
        Assert.False(await db.BranchServices.AnyAsync(l => l.Id == wrongBranchService.Id, TestContext.Current.CancellationToken));
        Assert.True(await db.ServiceResources.IgnoreQueryFilters().AnyAsync(l => l.Id == wrongProvider.Id, TestContext.Current.CancellationToken));
        Assert.False(await db.ServiceResources.AnyAsync(l => l.Id == wrongProvider.Id, TestContext.Current.CancellationToken));
        var wrongIds = wrongRules.Select(r => r.Id).ToList();
        Assert.Equal(wrongIds.Count, await db.AvailabilityRules.IgnoreQueryFilters().CountAsync(r => wrongIds.Contains(r.Id), TestContext.Current.CancellationToken));
        Assert.False(await db.AvailabilityRules.AnyAsync(r => wrongIds.Contains(r.Id), TestContext.Current.CancellationToken));
        var branch = await db.Branches.AsNoTracking().Include(b => b.BranchServices).ThenInclude(l => l.Service)
            .SingleAsync(b => b.Id == fixture.Branch.Id, TestContext.Current.CancellationToken);
        Assert.Equal(fixture.Service.Id, Assert.Single(branch.BranchServices).ServiceId);
        var service = await db.Services.AsNoTracking().Include(s => s.ServiceResources).ThenInclude(l => l.Resource)
            .SingleAsync(s => s.Id == fixture.Service.Id, TestContext.Current.CancellationToken);
        Assert.Equal(2, service.ServiceResources.Count);
        Assert.All(service.ServiceResources, l => Assert.Equal(fixture.Branch.Id, l.Resource.BranchId));
    }

    [Fact]
    public async Task Catalog_unpublication_preserves_existing_business_terms_and_archived_history_preserves_labels_until_membership_is_revoked()
    {
        await using var fixture = await Fixture.CreateAsync();
        var db = fixture.Db;
        await db.ServiceCatalogItems.Where(c => c.Id == fixture.Catalog.Id)
            .ExecuteUpdateAsync(s => s.SetProperty(c => c.IsPublished, false), TestContext.Current.CancellationToken);
        await fixture.ArchiveAsync(fixture.Catalog);
        var service = await db.Services.AsNoTracking().Include(s => s.CatalogItem).SingleAsync(s => s.Id == fixture.Service.Id, TestContext.Current.CancellationToken);
        Assert.Null(service.CatalogItem);
        Assert.Equal(fixture.Service.Price, service.Price);
        Assert.Equal(fixture.Service.DurationMinutes, service.DurationMinutes);
        Assert.True(await db.BranchServices.AnyAsync(l => l.ServiceId == fixture.Service.Id, TestContext.Current.CancellationToken));

        await fixture.ArchiveAsync(fixture.Branch);
        await fixture.ArchiveAsync(fixture.Service);
        await fixture.ArchiveAsync(fixture.Staff);
        await db.Users.Where(u => u.Id == fixture.Provider.Id).ExecuteUpdateAsync(s => s
            .SetProperty(u => u.LockoutEnabled, true).SetProperty(u => u.LockoutEnd, DateTimeOffset.UtcNow.AddDays(1)), TestContext.Current.CancellationToken);
        Assert.False(await db.Branches.AnyAsync(b => b.Id == fixture.Branch.Id, TestContext.Current.CancellationToken));
        Assert.False(await db.BranchServices.AnyAsync(l => l.BranchId == fixture.Branch.Id, TestContext.Current.CancellationToken));
        Assert.False(await db.Resources.AnyAsync(r => r.BranchId == fixture.Branch.Id, TestContext.Current.CancellationToken));
        Assert.False(await db.ServiceResources.AnyAsync(l => l.ServiceId == fixture.Service.Id, TestContext.Current.CancellationToken));
        // Business-wide hours stay active when only this branch is archived.
        Assert.Equal(1, await db.AvailabilityRules.CountAsync(r => r.BusinessId == fixture.Business.Id, TestContext.Current.CancellationToken));

        var access = new AdminAccessScope(db, new FixedAuthentication(fixture.Owner));
        var appointment = await (await access.AppointmentsAsync()).AsNoTracking()
            .Include(a => a.Service).Include(a => a.Resource).Include(a => a.Branch).ThenInclude(b => b.Business)
            .SingleAsync(a => a.Id == fixture.Appointment.Id, TestContext.Current.CancellationToken);
        Assert.Equal(fixture.Service.Name, appointment.Service.Name);
        Assert.Equal(fixture.Staff.Name, appointment.Resource!.Name);
        Assert.Equal(fixture.Branch.Name, appointment.Branch.Name);
        var outgoing = await (await access.CustomerReviewsAsync()).AsNoTracking()
            .Include(r => r.Appointment).ThenInclude(a => a.Resource).SingleAsync(r => r.Id == fixture.Outgoing.Id, TestContext.Current.CancellationToken);
        Assert.Equal(fixture.Staff.Name, outgoing.Appointment.Resource!.Name);
        var incoming = await (await access.ReviewsAsync()).AsNoTracking().Include(r => r.Branch).SingleAsync(r => r.Id == fixture.Incoming.Id, TestContext.Current.CancellationToken);
        Assert.Equal(fixture.Branch.Name, incoming.Branch!.Name);
        var principal = (await new FixedAuthentication(fixture.Owner).GetAuthenticationStateAsync()).User;
        var api = new ApiScope(db, new HttpContextAccessor { HttpContext = new DefaultHttpContext { User = principal } });
        var apiHistory = await api.Appointments().AsNoTracking().Include(a => a.Resource).Include(a => a.Service)
            .SingleAsync(a => a.Id == fixture.Appointment.Id, TestContext.Current.CancellationToken);
        Assert.Equal(fixture.Staff.Name, apiHistory.Resource!.Name);
        Assert.Equal(fixture.Service.Name, apiHistory.Service.Name);
        var apiReview = await api.CustomerReviews().AsNoTracking().Include(r => r.Appointment).ThenInclude(a => a.Resource)
            .SingleAsync(r => r.Id == fixture.Outgoing.Id, TestContext.Current.CancellationToken);
        Assert.Equal(fixture.Staff.Name, apiReview.Appointment.Resource!.Name);
        Assert.False(await api.CanManageBranch(fixture.Branch.Id));
        var apiIncoming = await api.Reviews().AsNoTracking().Include(r => r.Branch)
            .SingleAsync(r => r.Id == fixture.Incoming.Id, TestContext.Current.CancellationToken);
        Assert.Equal(fixture.Branch.Name, apiIncoming.Branch!.Name);

        await fixture.ArchiveAsync(fixture.Incoming);
        await fixture.ArchiveAsync(fixture.Outgoing);
        Assert.False(await (await access.ReviewsAsync()).AnyAsync(r => r.Id == fixture.Incoming.Id, TestContext.Current.CancellationToken));
        Assert.False(await (await access.CustomerReviewsAsync()).AnyAsync(r => r.Id == fixture.Outgoing.Id, TestContext.Current.CancellationToken));
        await fixture.RestoreAsync(fixture.Incoming);
        await fixture.RestoreAsync(fixture.Outgoing);
        await fixture.ArchiveAsync(fixture.OwnerMembership);
        // The still-claimed Owner role cannot restore a removed membership.
        Assert.False(await (await access.AppointmentsAsync()).AnyAsync(a => a.Id == fixture.Appointment.Id, TestContext.Current.CancellationToken));
        Assert.False(await (await access.ReviewsAsync()).AnyAsync(r => r.Id == fixture.Incoming.Id, TestContext.Current.CancellationToken));
        Assert.False(await (await access.CustomerReviewsAsync()).AnyAsync(r => r.Id == fixture.Outgoing.Id, TestContext.Current.CancellationToken));
        Assert.False(await api.Appointments().AnyAsync(a => a.Id == fixture.Appointment.Id, TestContext.Current.CancellationToken));
        Assert.False(await api.CustomerReviews().AnyAsync(r => r.Id == fixture.Outgoing.Id, TestContext.Current.CancellationToken));
        Assert.False(await api.Reviews().AnyAsync(r => r.Id == fixture.Incoming.Id, TestContext.Current.CancellationToken));
    }

    private static async Task AssertStaffHiddenAsync(Fixture fixture)
    {
        Assert.False(await fixture.Db.Resources.AnyAsync(r => r.Id == fixture.Staff.Id, TestContext.Current.CancellationToken));
        Assert.False(await fixture.Db.ServiceResources.AnyAsync(l => l.ResourceId == fixture.Staff.Id, TestContext.Current.CancellationToken));
        Assert.False(await fixture.Db.AvailabilityRules.AnyAsync(r => r.ResourceId == fixture.Staff.Id, TestContext.Current.CancellationToken));
        Assert.True(await fixture.Db.Resources.AnyAsync(r => r.Id == fixture.Anonymous.Id, TestContext.Current.CancellationToken));
        Assert.True(await fixture.Db.ServiceResources.AnyAsync(l => l.ResourceId == fixture.Anonymous.Id, TestContext.Current.CancellationToken));
    }

    private sealed class Fixture : IAsyncDisposable
    {
        public AppDbContext Db { get; private init; } = null!;
        private IDbContextTransaction Transaction { get; init; } = null!;
        public Business Business { get; } = new() { Name = "Administration graph", Slug = "admin-graph-" + Guid.NewGuid().ToString("N") };
        public Business OtherBusiness { get; } = new() { Name = "Other administration graph", Slug = "admin-other-" + Guid.NewGuid().ToString("N") };
        public ServiceCatalogItem Catalog { get; } = new() { Name = "Global catalog fixture", Slug = "admin-catalog-" + Guid.NewGuid().ToString("N") };
        public AppUser Owner { get; } = User("Owner");
        public AppUser Provider { get; } = User("Provider");
        public AppUser Customer { get; } = User("Customer");
        public Branch Branch { get; private set; } = null!;
        public Branch OtherBranch { get; private set; } = null!;
        public Service Service { get; private set; } = null!;
        public Service OtherService { get; private set; } = null!;
        public Resource Staff { get; private set; } = null!;
        public Resource Anonymous { get; private set; } = null!;
        public Resource OtherResource { get; private set; } = null!;
        public BranchMembership OwnerMembership { get; private set; } = null!;
        public BranchMembership StaffMembership { get; private set; } = null!;
        public Appointment Appointment { get; private set; } = null!;
        public Review Incoming { get; private set; } = null!;
        public CustomerReview Outgoing { get; private set; } = null!;

        public static async Task<Fixture> CreateAsync()
        {
            var connection = Environment.GetEnvironmentVariable("BENOBAT_TEST_DB");
            if (string.IsNullOrWhiteSpace(connection)) Assert.Skip("Set BENOBAT_TEST_DB to an initialized disposable PostgreSQL database.");
            var db = new AppDbContext(new DbContextOptionsBuilder<AppDbContext>().UseNpgsql(connection).Options);
            var fixture = new Fixture { Db = db, Transaction = await db.Database.BeginTransactionAsync(TestContext.Current.CancellationToken) };
            fixture.Branch = new Branch { Name = "Archive-safe branch", BusinessId = fixture.Business.Id, TimeZoneId = "UTC" };
            fixture.OtherBranch = new Branch { Name = "Other branch", BusinessId = fixture.OtherBusiness.Id, TimeZoneId = "UTC" };
            fixture.Service = new Service { Name = "Archive-safe service", BusinessId = fixture.Business.Id, CatalogItemId = fixture.Catalog.Id, Price = 1250, DurationMinutes = 30 };
            fixture.OtherService = new Service { Name = "Other service", BusinessId = fixture.OtherBusiness.Id };
            fixture.Staff = new Resource { Name = "Archive-safe provider", BranchId = fixture.Branch.Id, UserId = fixture.Provider.Id };
            fixture.Anonymous = new Resource { Name = "Anonymous provider", BranchId = fixture.Branch.Id };
            fixture.OtherResource = new Resource { Name = "Other provider", BranchId = fixture.OtherBranch.Id };
            fixture.OwnerMembership = new BranchMembership { BranchId = fixture.Branch.Id, UserId = fixture.Owner.Id, Role = AppRoles.Owner };
            fixture.StaffMembership = new BranchMembership { BranchId = fixture.Branch.Id, UserId = fixture.Provider.Id, Role = AppRoles.Staff };
            fixture.Appointment = new Appointment
            {
                BranchId = fixture.Branch.Id, ServiceId = fixture.Service.Id, ResourceId = fixture.Staff.Id,
                CustomerId = fixture.Customer.Id, StartsAt = DateTimeOffset.UtcNow.AddDays(-1),
                EndsAt = DateTimeOffset.UtcNow.AddDays(-1).AddMinutes(30), Status = AppointmentStatus.Completed,
            };
            fixture.Incoming = new Review
            {
                BusinessId = fixture.Business.Id, BranchId = fixture.Branch.Id, CustomerId = fixture.Customer.Id,
                AppointmentId = fixture.Appointment.Id, Rating = 4, Status = ReviewStatus.Published,
            };
            fixture.Outgoing = new CustomerReview
            {
                BusinessId = fixture.Business.Id, CustomerId = fixture.Customer.Id, AppointmentId = fixture.Appointment.Id,
                AuthorId = fixture.Owner.Id, Rating = 3,
            };
            db.AddRange(fixture.Business, fixture.OtherBusiness, fixture.Catalog, fixture.Owner, fixture.Provider, fixture.Customer,
                fixture.Branch, fixture.OtherBranch, fixture.Service, fixture.OtherService, fixture.Staff, fixture.Anonymous,
                fixture.OtherResource, fixture.OwnerMembership, fixture.StaffMembership, fixture.Appointment, fixture.Incoming, fixture.Outgoing);
            db.BranchServices.Add(new BranchService { BranchId = fixture.Branch.Id, ServiceId = fixture.Service.Id });
            db.ServiceResources.AddRange(new ServiceResource { ServiceId = fixture.Service.Id, ResourceId = fixture.Staff.Id },
                new ServiceResource { ServiceId = fixture.Service.Id, ResourceId = fixture.Anonymous.Id });
            db.AvailabilityRules.AddRange(new AvailabilityRule { BusinessId = fixture.Business.Id },
                new AvailabilityRule { BusinessId = fixture.Business.Id, BranchId = fixture.Branch.Id },
                new AvailabilityRule { BusinessId = fixture.Business.Id, BranchId = fixture.Branch.Id, ResourceId = fixture.Staff.Id });
            try { await db.SaveChangesAsync(TestContext.Current.CancellationToken); db.ChangeTracker.Clear(); return fixture; }
            catch { await fixture.DisposeAsync(); throw; }
        }

        public Task<int> ArchiveAsync<TEntity>(TEntity entity) where TEntity : Entity => SetArchivedAsync(entity, DateTimeOffset.UtcNow);
        public Task<int> RestoreAsync<TEntity>(TEntity entity) where TEntity : Entity => SetArchivedAsync(entity, null);
        private Task<int> SetArchivedAsync<TEntity>(TEntity entity, DateTimeOffset? value) where TEntity : Entity =>
            Db.Set<TEntity>().IgnoreQueryFilters().Where(e => e.Id == entity.Id).ExecuteUpdateAsync(s => s.SetProperty(e => e.DeletedAt, value), TestContext.Current.CancellationToken);

        public async ValueTask DisposeAsync()
        {
            await Transaction.RollbackAsync(TestContext.Current.CancellationToken);
            await Transaction.DisposeAsync();
            await Db.DisposeAsync();
        }

        private static AppUser User(string label)
        {
            var name = "admin-graph-" + Guid.NewGuid().ToString("N");
            return new AppUser { Id = Guid.NewGuid(), UserName = name, NormalizedUserName = name.ToUpperInvariant(),
                DisplayName = label, SecurityStamp = Guid.NewGuid().ToString("N") };
        }
    }

    private sealed class FixedAuthentication(AppUser user) : AuthenticationStateProvider
    {
        public override Task<AuthenticationState> GetAuthenticationStateAsync() => Task.FromResult(new AuthenticationState(
            new ClaimsPrincipal(new ClaimsIdentity([
                new Claim(ClaimTypes.NameIdentifier, user.Id.ToString()), new Claim(ClaimTypes.Role, AppRoles.Owner),
                new Claim("AspNet.Identity.SecurityStamp", user.SecurityStamp!),
            ], "Test"))));
    }
}
