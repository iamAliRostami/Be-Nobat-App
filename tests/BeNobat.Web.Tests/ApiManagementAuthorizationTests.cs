using System.Reflection;
using System.Security.Claims;
using BeNobat.Web.Api;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using BeNobat.Web.Security;
using Microsoft.AspNetCore.Http;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Storage;
using Xunit;

namespace BeNobat.Web.Tests;

public sealed class ApiManagementAuthorizationTests
{
    [Theory]
    [InlineData(AppRoles.Owner)]
    [InlineData(AppRoles.Manager)]
    [InlineData(AppRoles.Staff)]
    public async Task Availability_reads_include_inherited_business_hours_without_exposing_other_branches_or_businesses(string role)
    {
        await using var fixture = await Fixture.CreateAsync();
        var actor = role == AppRoles.Owner ? fixture.Owner : fixture.Manager;
        if (role == AppRoles.Staff)
            await fixture.Db.BranchMemberships.Where(m => m.Id == fixture.ManagerMembership.Id)
                .ExecuteUpdateAsync(s => s.SetProperty(m => m.Role, AppRoles.Staff), TestContext.Current.CancellationToken);
        var scope = fixture.Scope(actor, role);
        var rules = await AddAvailabilityAsync(fixture);

        var visible = await ListAvailabilityAsync(fixture.Db, scope);
        Assert.Equal(new[] { rules.Business.Id, rules.FirstBranch.Id }.Order(), visible.Items.Select(r => r.Id).Order());
        var branch = await ListAvailabilityAsync(fixture.Db, scope, branchId: fixture.FirstBranch.Id);
        Assert.Equal(new[] { rules.Business.Id, rules.FirstBranch.Id }.Order(), branch.Items.Select(r => r.Id).Order());
        Assert.Equal(2, branch.Total);
        Assert.Empty((await ListAvailabilityAsync(fixture.Db, scope, branchId: fixture.SecondBranch.Id)).Items);
        Assert.Empty((await ListAvailabilityAsync(fixture.Db, scope, branchId: fixture.OtherBranch.Id)).Items);
        Assert.Empty((await ListAvailabilityAsync(fixture.Db, scope, businessId: fixture.OtherBranch.BusinessId)).Items);
        Assert.Empty((await ListAvailabilityAsync(fixture.Db, scope,
            businessId: fixture.OtherBranch.BusinessId, branchId: fixture.FirstBranch.Id)).Items);
    }

    [Theory]
    [InlineData(AppRoles.Owner)]
    [InlineData(AppRoles.Manager)]
    public async Task Reading_inherited_availability_does_not_grant_business_wide_write_access(string role)
    {
        await using var fixture = await Fixture.CreateAsync();
        var actor = role == AppRoles.Owner ? fixture.Owner : fixture.Manager;
        var scope = fixture.Scope(actor, role);
        var rules = await AddAvailabilityAsync(fixture);
        var input = new ManagementAvailabilityInput(fixture.Business.Id, DateOnly.FromDateTime(DateTime.UtcNow).AddDays(2),
            new TimeOnly(12, 0), new TimeOnly(13, 0));

        Assert.False(await Invoke<Task<bool>>("CanEditAvailability", rules.Business, scope));
        Assert.True(await Invoke<Task<bool>>("CanEditAvailability", rules.Business, fixture.Scope(actor, AppRoles.PlatformAdmin)));
        foreach (var result in new[]
        {
            await Invoke<Task<IResult>>("CreateAvailability", input, fixture.Db, scope, TestContext.Current.CancellationToken),
            await Invoke<Task<IResult>>("UpdateAvailability", rules.Business.Id, input, fixture.Db, scope, TestContext.Current.CancellationToken),
            await Invoke<Task<IResult>>("DeleteAvailability", rules.Business.Id, fixture.Db, scope, TestContext.Current.CancellationToken),
        }) Assert.Equal(StatusCodes.Status403Forbidden, Assert.IsAssignableFrom<IStatusCodeHttpResult>(result).StatusCode);
        Assert.Null(await fixture.Db.AvailabilityRules.Where(r => r.Id == rules.Business.Id)
            .Select(r => r.DeletedAt).SingleAsync(TestContext.Current.CancellationToken));
    }

    [Fact]
    public async Task Inherited_availability_access_tracks_current_membership_revocation_and_business_archival()
    {
        await using var fixture = await Fixture.CreateAsync();
        var scope = fixture.Scope(fixture.Manager, AppRoles.Manager);
        await AddAvailabilityAsync(fixture);
        Assert.Equal(2, (await ListAvailabilityAsync(fixture.Db, scope)).Total);
        await fixture.ArchiveAsync(fixture.ManagerMembership);
        Assert.Empty((await ListAvailabilityAsync(fixture.Db, scope)).Items);
        await fixture.RestoreAsync(fixture.ManagerMembership);
        Assert.Equal(2, (await ListAvailabilityAsync(fixture.Db, scope)).Total);
        await fixture.ArchiveAsync(fixture.Business);
        Assert.Empty((await ListAvailabilityAsync(fixture.Db, scope)).Items);
    }

    [Fact]
    public async Task Shared_service_requires_current_management_of_every_live_linked_branch()
    {
        await using var fixture = await Fixture.CreateAsync();
        // A stale global Owner claim cannot replace the current Manager membership.
        var scope = fixture.Scope(fixture.Manager, AppRoles.Owner);
        var platform = fixture.Scope(fixture.Manager, AppRoles.PlatformAdmin);
        Assert.True(await platform.Businesses().AnyAsync(b => b.Id == fixture.Business.Id, TestContext.Current.CancellationToken));
        Assert.True(await platform.CanManageBusiness(fixture.Business.Id));
        Assert.True(await scope.CanManageBusiness(fixture.Business.Id));
        Assert.False(await scope.CanOwnBusiness(fixture.Business.Id));
        Assert.False(await scope.CanManageService(fixture.Service.Id, TestContext.Current.CancellationToken));

        var membership = new BranchMembership
        {
            BranchId = fixture.SecondBranch.Id, UserId = fixture.Manager.Id, Role = AppRoles.Manager,
        };
        fixture.Db.BranchMemberships.Add(membership);
        await fixture.Db.SaveChangesAsync(TestContext.Current.CancellationToken);
        fixture.Db.ChangeTracker.Clear();
        Assert.True(await scope.CanManageService(fixture.Service.Id, TestContext.Current.CancellationToken));

        await fixture.Db.BranchMemberships.Where(m => m.Id == membership.Id)
            .ExecuteUpdateAsync(s => s.SetProperty(m => m.Role, AppRoles.Staff), TestContext.Current.CancellationToken);
        Assert.False(await scope.CanManageService(fixture.Service.Id, TestContext.Current.CancellationToken));
        await fixture.ArchiveAsync(fixture.SecondLink);
        Assert.True(await scope.CanManageService(fixture.Service.Id, TestContext.Current.CancellationToken));
        await fixture.ArchiveAsync(fixture.ManagerMembership);
        Assert.False(await scope.CanManageService(fixture.Service.Id, TestContext.Current.CancellationToken));
        Assert.True(await fixture.Scope(fixture.Owner, AppRoles.Owner)
            .CanManageService(fixture.Service.Id, TestContext.Current.CancellationToken));
    }

    [Fact]
    public async Task Service_restore_authorization_ignores_archived_and_cross_business_links_and_requires_an_active_business()
    {
        await using var fixture = await Fixture.CreateAsync();
        var manager = fixture.Scope(fixture.Manager, AppRoles.Manager);
        var owner = fixture.Scope(fixture.Owner, AppRoles.Owner);
        await fixture.ArchiveAsync(fixture.SecondBranch);
        fixture.Db.BranchServices.Add(new BranchService
        {
            BranchId = fixture.OtherBranch.Id, ServiceId = fixture.Service.Id,
        });
        await fixture.Db.SaveChangesAsync(TestContext.Current.CancellationToken);
        fixture.Db.ChangeTracker.Clear();
        Assert.True(await manager.CanManageService(fixture.Service.Id, TestContext.Current.CancellationToken));

        // Only the invalid foreign-business link remains live; it grants no access.
        await fixture.ArchiveAsync(fixture.FirstLink);
        Assert.False(await manager.CanManageService(fixture.Service.Id, TestContext.Current.CancellationToken));
        Assert.True(await owner.CanManageService(fixture.Service.Id, TestContext.Current.CancellationToken));

        await fixture.RestoreAsync(fixture.FirstLink);
        await fixture.ArchiveAsync(fixture.Service);
        Assert.True(await manager.CanManageService(fixture.Service.Id, TestContext.Current.CancellationToken));
        Assert.True(await owner.CanManageService(fixture.Service.Id, TestContext.Current.CancellationToken));
        await fixture.ArchiveAsync(fixture.Business);
        Assert.False(await manager.CanManageService(fixture.Service.Id, TestContext.Current.CancellationToken));
        Assert.False(await owner.CanManageService(fixture.Service.Id, TestContext.Current.CancellationToken));
        Assert.False(await owner.CanManageService(Guid.NewGuid(), TestContext.Current.CancellationToken));
    }

    [Fact]
    public async Task Last_owner_query_excludes_the_target_archived_owners_managers_and_owners_of_other_branches()
    {
        await using var fixture = await Fixture.CreateAsync();
        var otherOwners = Invoke<IQueryable<BranchMembership>>("OtherOwners", fixture.Db,
            fixture.FirstBranch.Id, fixture.OwnerMembership.Id);
        Assert.False(await otherOwners.AnyAsync(TestContext.Current.CancellationToken));

        await fixture.RestoreAsync(fixture.ArchivedOwnerMembership);
        Assert.Equal(fixture.ArchivedOwnerMembership.Id,
            Assert.Single(await otherOwners.Select(m => m.Id).ToListAsync(TestContext.Current.CancellationToken)));
    }

    [Theory]
    [InlineData(AppRoles.Owner)]
    [InlineData(AppRoles.Manager)]
    [InlineData(AppRoles.Staff)]
    [InlineData("Unsupported")]
    public void Global_role_requests_cannot_grant_membership_derived_team_roles(string role)
    {
        Assert.False(ValidGlobalRoles([role]));
        Assert.False(ValidGlobalRoles([AppRoles.Customer, role]));
    }

    [Fact]
    public void Global_role_requests_require_bounded_platform_or_customer_grants()
    {
        Assert.False(ValidGlobalRoles(null));
        Assert.False(ValidGlobalRoles([]));
        Assert.False(ValidGlobalRoles([AppRoles.Customer, AppRoles.Customer, AppRoles.Customer]));
        Assert.True(ValidGlobalRoles([AppRoles.Customer]));
        Assert.True(ValidGlobalRoles([AppRoles.PlatformAdmin]));
        Assert.True(ValidGlobalRoles([AppRoles.PlatformAdmin, AppRoles.Customer]));
    }

    [Fact]
    public void Last_platform_admin_guard_counts_only_other_accounts_that_can_still_sign_in()
    {
        var now = DateTimeOffset.UtcNow;
        var target = new AppUser { Id = Guid.NewGuid() };
        var locked = new AppUser { Id = Guid.NewGuid(), LockoutEnabled = true, LockoutEnd = now.AddHours(1) };
        var expired = new AppUser { Id = Guid.NewGuid(), LockoutEnabled = true, LockoutEnd = now };
        var disabledLockout = new AppUser { Id = Guid.NewGuid(), LockoutEnabled = false, LockoutEnd = DateTimeOffset.MaxValue };
        Assert.False(Invoke<bool>("HasOtherActivePlatformAdmin", new[] { target }, target.Id, now));
        Assert.False(Invoke<bool>("HasOtherActivePlatformAdmin", new[] { target, locked }, target.Id, now));
        Assert.True(Invoke<bool>("HasOtherActivePlatformAdmin", new[] { target, expired }, target.Id, now));
        Assert.True(Invoke<bool>("HasOtherActivePlatformAdmin", new[] { target, disabledLockout }, target.Id, now));
    }

    private static bool ValidGlobalRoles(string[]? roles) => Invoke<bool>("ValidGlobalRoles", new object?[] { roles });

    private static async Task<ApiPage<ManagementAvailabilityDto>> ListAvailabilityAsync(AppDbContext db, ApiScope scope,
        Guid? businessId = null, Guid? branchId = null)
    {
        var result = await Invoke<Task<IResult>>("ListAvailability", db, scope, TestContext.Current.CancellationToken,
            businessId, branchId, null, null, null, 1, 100);
        return Assert.IsType<ApiPage<ManagementAvailabilityDto>>(Assert.IsAssignableFrom<IValueHttpResult>(result).Value);
    }

    private static async Task<(AvailabilityRule Business, AvailabilityRule FirstBranch)> AddAvailabilityAsync(Fixture fixture)
    {
        AvailabilityRule Rule(Guid businessId, Guid? branchId = null, bool archived = false) => new()
        {
            BusinessId = businessId, BranchId = branchId,
            EffectiveDate = DateOnly.FromDateTime(DateTime.UtcNow).AddDays(2), DayOfWeek = DayOfWeek.Monday,
            StartsAt = new TimeOnly(9, 0), EndsAt = new TimeOnly(10, 0),
            DeletedAt = archived ? DateTimeOffset.UtcNow : null,
        };
        var inherited = Rule(fixture.Business.Id);
        var first = Rule(fixture.Business.Id, fixture.FirstBranch.Id);
        fixture.Db.AddRange(inherited, first, Rule(fixture.Business.Id, fixture.SecondBranch.Id),
            Rule(fixture.OtherBranch.BusinessId), Rule(fixture.OtherBranch.BusinessId, fixture.OtherBranch.Id),
            Rule(fixture.Business.Id, archived: true));
        await fixture.Db.SaveChangesAsync(TestContext.Current.CancellationToken);
        fixture.Db.ChangeTracker.Clear();
        return (inherited, first);
    }

    private static T Invoke<T>(string method, params object?[] arguments) =>
        (T)typeof(ManagementEndpoints).GetMethod(method, BindingFlags.Static | BindingFlags.NonPublic)!
            .Invoke(null, arguments)!;

    // HttpContextAccessor uses an ambient AsyncLocal shared by every instance.
    // Independent actors in one fixture need independent accessor state instead.
    private sealed class IsolatedAccessor : IHttpContextAccessor
    {
        public HttpContext? HttpContext { get; set; }
    }

    private sealed class Fixture : IAsyncDisposable
    {
        public AppDbContext Db { get; private init; } = null!;
        private IDbContextTransaction Transaction { get; init; } = null!;
        public Business Business { get; } = new() { Name = "API authorization fixture", Slug = "api-access-" + Guid.NewGuid().ToString("N") };
        private Business OtherBusiness { get; } = new() { Name = "Other API business", Slug = "api-other-" + Guid.NewGuid().ToString("N") };
        public AppUser Manager { get; } = User("Manager");
        public AppUser Owner { get; } = User("Owner");
        private AppUser AlternateOwner { get; } = User("Alternate owner");
        public Branch FirstBranch { get; private set; } = null!;
        public Branch SecondBranch { get; private set; } = null!;
        public Branch OtherBranch { get; private set; } = null!;
        public Service Service { get; private set; } = null!;
        public BranchService FirstLink { get; private set; } = null!;
        public BranchService SecondLink { get; private set; } = null!;
        public BranchMembership ManagerMembership { get; private set; } = null!;
        public BranchMembership OwnerMembership { get; private set; } = null!;
        public BranchMembership ArchivedOwnerMembership { get; private set; } = null!;

        public static async Task<Fixture> CreateAsync()
        {
            var connection = Environment.GetEnvironmentVariable("BENOBAT_TEST_DB");
            if (string.IsNullOrWhiteSpace(connection)) Assert.Skip("Set BENOBAT_TEST_DB to an initialized disposable PostgreSQL database.");
            var db = new AppDbContext(new DbContextOptionsBuilder<AppDbContext>().UseNpgsql(connection).Options);
            var fixture = new Fixture { Db = db, Transaction = await db.Database.BeginTransactionAsync(TestContext.Current.CancellationToken) };
            fixture.FirstBranch = new Branch { Name = "Managed branch", BusinessId = fixture.Business.Id, TimeZoneId = "UTC" };
            fixture.SecondBranch = new Branch { Name = "Other linked branch", BusinessId = fixture.Business.Id, TimeZoneId = "UTC" };
            fixture.OtherBranch = new Branch { Name = "Foreign business branch", BusinessId = fixture.OtherBusiness.Id, TimeZoneId = "UTC" };
            fixture.Service = new Service { Name = "Shared service", BusinessId = fixture.Business.Id, DurationMinutes = 30 };
            fixture.FirstLink = new BranchService { BranchId = fixture.FirstBranch.Id, ServiceId = fixture.Service.Id };
            fixture.SecondLink = new BranchService { BranchId = fixture.SecondBranch.Id, ServiceId = fixture.Service.Id };
            fixture.ManagerMembership = new BranchMembership { BranchId = fixture.FirstBranch.Id, UserId = fixture.Manager.Id, Role = AppRoles.Manager };
            fixture.OwnerMembership = new BranchMembership { BranchId = fixture.FirstBranch.Id, UserId = fixture.Owner.Id, Role = AppRoles.Owner };
            fixture.ArchivedOwnerMembership = new BranchMembership
            {
                BranchId = fixture.FirstBranch.Id, UserId = fixture.AlternateOwner.Id, Role = AppRoles.Owner, DeletedAt = DateTimeOffset.UtcNow,
            };
            db.AddRange(fixture.Business, fixture.OtherBusiness, fixture.Manager, fixture.Owner, fixture.AlternateOwner,
                fixture.FirstBranch, fixture.SecondBranch, fixture.OtherBranch, fixture.Service, fixture.FirstLink, fixture.SecondLink,
                fixture.ManagerMembership, fixture.OwnerMembership, fixture.ArchivedOwnerMembership,
                new BranchMembership { BranchId = fixture.SecondBranch.Id, UserId = fixture.AlternateOwner.Id, Role = AppRoles.Owner });
            try { await db.SaveChangesAsync(TestContext.Current.CancellationToken); db.ChangeTracker.Clear(); return fixture; }
            catch { await fixture.DisposeAsync(); throw; }
        }

        public ApiScope Scope(AppUser user, string role) => new(Db, new IsolatedAccessor
        {
            HttpContext = new DefaultHttpContext
            {
                User = new ClaimsPrincipal(new ClaimsIdentity([
                    new Claim(ClaimTypes.NameIdentifier, user.Id.ToString()), new Claim(ClaimTypes.Role, role),
                    new Claim("AspNet.Identity.SecurityStamp", user.SecurityStamp!),
                ], "MobileBearer")),
            },
        });

        public Task<int> ArchiveAsync<TEntity>(TEntity entity) where TEntity : Entity => SetArchivedAsync(entity, DateTimeOffset.UtcNow);
        public Task<int> RestoreAsync<TEntity>(TEntity entity) where TEntity : Entity => SetArchivedAsync(entity, null);
        private Task<int> SetArchivedAsync<TEntity>(TEntity entity, DateTimeOffset? value) where TEntity : Entity =>
            Db.Set<TEntity>().IgnoreQueryFilters().Where(e => e.Id == entity.Id)
                .ExecuteUpdateAsync(s => s.SetProperty(e => e.DeletedAt, value), TestContext.Current.CancellationToken);

        public async ValueTask DisposeAsync()
        {
            await Transaction.RollbackAsync(TestContext.Current.CancellationToken);
            await Transaction.DisposeAsync();
            await Db.DisposeAsync();
        }

        private static AppUser User(string label)
        {
            var name = "api-access-" + Guid.NewGuid().ToString("N");
            return new AppUser
            {
                Id = Guid.NewGuid(), UserName = name, NormalizedUserName = name.ToUpperInvariant(),
                DisplayName = label, SecurityStamp = Guid.NewGuid().ToString("N"),
            };
        }
    }
}
