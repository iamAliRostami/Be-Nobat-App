using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using BeNobat.Web.Security;
using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Storage;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.FileProviders;
using Microsoft.Extensions.Hosting;
using Xunit;

namespace BeNobat.Web.Tests;

public sealed class DbSeederIntegrationTests
{
    [Theory]
    [InlineData("Development")]
    [InlineData("Production")]
    public async Task An_existing_customer_matching_the_seed_email_is_never_promoted(string environment)
    {
        await using var fixture = await Fixture.CreateAsync(environment);
        var customer = await fixture.CreateUserAsync(fixture.AdminEmail, AppRoles.Customer);
        var originalStamp = customer.SecurityStamp;
        var originalHash = customer.PasswordHash;

        await fixture.SeedAsync();
        await fixture.SeedAsync();
        fixture.Db.ChangeTracker.Clear();
        var saved = (await fixture.Users.FindByEmailAsync(fixture.AdminEmail))!;

        Assert.Equal(customer.Id, saved.Id);
        Assert.Equal(new[] { AppRoles.Customer }, await fixture.Users.GetRolesAsync(saved));
        Assert.Equal(originalStamp, saved.SecurityStamp);
        Assert.Equal(originalHash, saved.PasswordHash);
        Assert.False(saved.EmailConfirmed);
    }

    [Fact]
    public async Task Startup_preserves_an_explicit_admin_role_revocation_while_another_admin_remains()
    {
        await using var fixture = await Fixture.CreateAsync(Environments.Production);
        var revoked = await fixture.CreateUserAsync(fixture.AdminEmail, AppRoles.PlatformAdmin);
        Assert.True((await fixture.Users.AddToRoleAsync(revoked, AppRoles.Customer)).Succeeded);
        var active = await fixture.CreateUserAsync($"active-{Guid.NewGuid():N}@example.test", AppRoles.PlatformAdmin);
        Assert.True((await fixture.Users.RemoveFromRoleAsync(revoked, AppRoles.PlatformAdmin)).Succeeded);
        var revokedStamp = revoked.SecurityStamp;

        await fixture.SeedAsync(password: "ConfiguredAdmin123!");
        await fixture.SeedAsync(password: "ConfiguredAdmin123!");
        fixture.Db.ChangeTracker.Clear();
        var saved = (await fixture.Users.FindByEmailAsync(fixture.AdminEmail))!;

        Assert.Equal(new[] { AppRoles.Customer }, await fixture.Users.GetRolesAsync(saved));
        Assert.Equal(revokedStamp, saved.SecurityStamp);
        Assert.True(await fixture.Users.IsInRoleAsync((await fixture.Users.FindByIdAsync(active.Id.ToString()))!, AppRoles.PlatformAdmin));
    }

    [Fact]
    public async Task Reviewed_and_archived_catalog_items_survive_repeated_startup_without_duplicates()
    {
        await using var fixture = await Fixture.CreateAsync(Environments.Production);
        await fixture.SeedAsync();
        var reviewed = await fixture.Db.ServiceCatalogItems.IgnoreQueryFilters().SingleAsync(x => x.Slug == "haircut", TestContext.Current.CancellationToken);
        var archived = await fixture.Db.ServiceCatalogItems.IgnoreQueryFilters().SingleAsync(x => x.Slug == "hair-color", TestContext.Current.CancellationToken);
        reviewed.Name = "Reviewed haircut";
        reviewed.Category = "Custom reviewed category";
        reviewed.SuggestedDurationMinutes = 75;
        reviewed.Description = "Approved custom description";
        reviewed.IsPublished = false;
        reviewed.UpdatedAt = new DateTimeOffset(2024, 4, 1, 12, 0, 0, TimeSpan.Zero);
        archived.Name = "Archived reviewed color";
        archived.Category = "Custom archived category";
        archived.SuggestedDurationMinutes = 150;
        archived.DeletedAt = new DateTimeOffset(2024, 4, 2, 12, 0, 0, TimeSpan.Zero);
        await fixture.Db.SaveChangesAsync(TestContext.Current.CancellationToken);
        var reviewedUpdatedAt = reviewed.UpdatedAt;
        var archivedDeletedAt = archived.DeletedAt;
        var catalogCount = await fixture.Db.ServiceCatalogItems.IgnoreQueryFilters().CountAsync(TestContext.Current.CancellationToken);
        var categoryCount = await fixture.Db.CategoryDefinitions.IgnoreQueryFilters().CountAsync(TestContext.Current.CancellationToken);

        await fixture.SeedAsync();
        await fixture.SeedAsync();
        fixture.Db.ChangeTracker.Clear();
        var current = await fixture.Db.ServiceCatalogItems.IgnoreQueryFilters().SingleAsync(x => x.Id == reviewed.Id, TestContext.Current.CancellationToken);
        var inactive = await fixture.Db.ServiceCatalogItems.IgnoreQueryFilters().SingleAsync(x => x.Id == archived.Id, TestContext.Current.CancellationToken);

        Assert.Equal("Reviewed haircut", current.Name);
        Assert.Equal("Custom reviewed category", current.Category);
        Assert.Equal(75, current.SuggestedDurationMinutes);
        Assert.Equal("Approved custom description", current.Description);
        Assert.False(current.IsPublished);
        Assert.Equal(reviewedUpdatedAt, current.UpdatedAt);
        Assert.Equal("Archived reviewed color", inactive.Name);
        Assert.Equal("Custom archived category", inactive.Category);
        Assert.Equal(150, inactive.SuggestedDurationMinutes);
        Assert.Equal(archivedDeletedAt, inactive.DeletedAt);
        Assert.Equal(catalogCount, await fixture.Db.ServiceCatalogItems.IgnoreQueryFilters().CountAsync(TestContext.Current.CancellationToken));
        Assert.Equal(categoryCount, await fixture.Db.CategoryDefinitions.IgnoreQueryFilters().CountAsync(TestContext.Current.CancellationToken));
    }

    [Theory]
    [InlineData(CategoryKind.Business)]
    [InlineData(CategoryKind.Service)]
    public async Task Reviewed_category_names_activation_and_archival_survive_startup(CategoryKind kind)
    {
        await using var fixture = await Fixture.CreateAsync(Environments.Production);
        await fixture.SeedAsync();
        var names = kind == CategoryKind.Business ? Taxonomy.BusinessCategories : Taxonomy.ServiceCategories;
        var originalName = names[0];
        var renamed = await fixture.Db.CategoryDefinitions.IgnoreQueryFilters().SingleAsync(
            x => x.Kind == kind && x.Name == originalName, TestContext.Current.CancellationToken);
        var disabled = await fixture.Db.CategoryDefinitions.IgnoreQueryFilters().SingleAsync(
            x => x.Kind == kind && x.Name == names[1], TestContext.Current.CancellationToken);
        var archived = await fixture.Db.CategoryDefinitions.IgnoreQueryFilters().SingleAsync(
            x => x.Kind == kind && x.Name == names[2], TestContext.Current.CancellationToken);
        renamed.Name = $"Reviewed category {Guid.NewGuid():N}";
        renamed.SortOrder = 300;
        disabled.IsActive = false;
        archived.DeletedAt = new DateTimeOffset(2024, 4, 2, 12, 0, 0, TimeSpan.Zero);
        await fixture.Db.SaveChangesAsync(TestContext.Current.CancellationToken);
        var count = await fixture.Db.CategoryDefinitions.IgnoreQueryFilters().CountAsync(TestContext.Current.CancellationToken);

        await fixture.SeedAsync();
        await fixture.SeedAsync();
        fixture.Db.ChangeTracker.Clear();

        Assert.False(await fixture.Db.CategoryDefinitions.IgnoreQueryFilters().AnyAsync(
            x => x.Kind == kind && x.Name == originalName, TestContext.Current.CancellationToken));
        var current = await fixture.Db.CategoryDefinitions.IgnoreQueryFilters().SingleAsync(x => x.Id == renamed.Id, TestContext.Current.CancellationToken);
        Assert.Equal(renamed.Name, current.Name);
        Assert.Equal(300, current.SortOrder);
        Assert.False(await fixture.Db.CategoryDefinitions.IgnoreQueryFilters().Where(x => x.Id == disabled.Id)
            .Select(x => x.IsActive).SingleAsync(TestContext.Current.CancellationToken));
        Assert.Equal(archived.DeletedAt, await fixture.Db.CategoryDefinitions.IgnoreQueryFilters().Where(x => x.Id == archived.Id)
            .Select(x => x.DeletedAt).SingleAsync(TestContext.Current.CancellationToken));
        Assert.Equal(count, await fixture.Db.CategoryDefinitions.IgnoreQueryFilters().CountAsync(TestContext.Current.CancellationToken));
    }

    [Fact]
    public async Task Only_an_unconfigured_category_kind_receives_its_initial_defaults()
    {
        await using var fixture = await Fixture.CreateAsync(Environments.Production);
        await fixture.Db.CategoryDefinitions.IgnoreQueryFilters().Where(x => x.Kind == CategoryKind.Business)
            .ExecuteDeleteAsync(TestContext.Current.CancellationToken);
        var serviceIds = await fixture.Db.CategoryDefinitions.IgnoreQueryFilters().Where(x => x.Kind == CategoryKind.Service)
            .Select(x => x.Id).OrderBy(x => x).ToArrayAsync(TestContext.Current.CancellationToken);

        await fixture.SeedAsync();
        await fixture.SeedAsync();

        var businessNames = await fixture.Db.CategoryDefinitions.IgnoreQueryFilters().Where(x => x.Kind == CategoryKind.Business)
            .Select(x => x.Name).ToArrayAsync(TestContext.Current.CancellationToken);
        Assert.Equal(Taxonomy.BusinessCategories.Order(StringComparer.Ordinal), businessNames.Order(StringComparer.Ordinal));
        Assert.Equal(serviceIds, await fixture.Db.CategoryDefinitions.IgnoreQueryFilters().Where(x => x.Kind == CategoryKind.Service)
            .Select(x => x.Id).OrderBy(x => x).ToArrayAsync(TestContext.Current.CancellationToken));
    }

    [Fact]
    public async Task A_new_admin_is_provisioned_once_and_production_requires_a_configured_password()
    {
        await using var fixture = await Fixture.CreateAsync(Environments.Production);
        await fixture.SeedAsync();
        Assert.Null(await fixture.Users.FindByEmailAsync(fixture.AdminEmail));
        await fixture.SeedAsync(password: "ConfiguredAdmin123!");
        var created = (await fixture.Users.FindByEmailAsync(fixture.AdminEmail))!;
        Assert.NotEqual(Guid.Empty, created.Id);
        Assert.True(created.EmailConfirmed);
        Assert.True(await fixture.Users.IsInRoleAsync(created, AppRoles.PlatformAdmin));
        Assert.True(await fixture.Users.CheckPasswordAsync(created, "ConfiguredAdmin123!"));

        await fixture.SeedAsync(password: "DifferentConfigured123!");
        fixture.Db.ChangeTracker.Clear();
        var current = (await fixture.Users.FindByEmailAsync(fixture.AdminEmail))!;
        Assert.Equal(created.Id, current.Id);
        Assert.True(await fixture.Users.CheckPasswordAsync(current, "ConfiguredAdmin123!"));
        Assert.False(await fixture.Users.CheckPasswordAsync(current, "DifferentConfigured123!"));
        Assert.Equal(1, await fixture.Db.Users.CountAsync(u => u.NormalizedEmail == fixture.AdminEmail.ToUpperInvariant(), TestContext.Current.CancellationToken));
    }

    private sealed class Fixture : IAsyncDisposable
    {
        private readonly ServiceProvider services;
        private readonly AsyncServiceScope scope;
        private readonly IDbContextTransaction transaction;
        public AppDbContext Db { get; }
        public UserManager<AppUser> Users { get; }
        public string AdminEmail { get; } = $"seed-{Guid.NewGuid():N}@example.test";

        private Fixture(ServiceProvider services, AsyncServiceScope scope, IDbContextTransaction transaction)
        {
            this.services = services;
            this.scope = scope;
            this.transaction = transaction;
            Db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
            Users = scope.ServiceProvider.GetRequiredService<UserManager<AppUser>>();
        }

        public static async Task<Fixture> CreateAsync(string environment)
        {
            var connection = Environment.GetEnvironmentVariable("BENOBAT_TEST_DB");
            if (string.IsNullOrWhiteSpace(connection)) Assert.Skip("Set BENOBAT_TEST_DB to an initialized disposable PostgreSQL database.");
            var collection = new ServiceCollection().AddLogging().AddDbContext<AppDbContext>(options => options.UseNpgsql(connection));
            collection.AddSingleton<IHostEnvironment>(new TestEnvironment(environment));
            collection.AddIdentityCore<AppUser>(options => options.User.RequireUniqueEmail = true)
                .AddRoles<IdentityRole<Guid>>().AddEntityFrameworkStores<AppDbContext>();
            var services = collection.BuildServiceProvider();
            var scope = services.CreateAsyncScope();
            var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
            var transaction = await db.Database.BeginTransactionAsync(TestContext.Current.CancellationToken);
            return new Fixture(services, scope, transaction);
        }

        public Task SeedAsync(string? password = null)
        {
            var configuration = new ConfigurationBuilder().AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["Seed:AdminEmail"] = AdminEmail,
                ["Seed:AdminPassword"] = password,
            }).Build();
            return DbSeeder.SeedAsync(scope.ServiceProvider, configuration, TestContext.Current.CancellationToken);
        }

        public async Task<AppUser> CreateUserAsync(string email, string role)
        {
            var user = new AppUser { Id = Guid.NewGuid(), UserName = email, Email = email, DisplayName = "Seeder fixture" };
            Assert.True((await Users.CreateAsync(user, "FixturePassword123!")).Succeeded);
            Assert.True((await Users.AddToRoleAsync(user, role)).Succeeded);
            return user;
        }

        public async ValueTask DisposeAsync()
        {
            await transaction.RollbackAsync(TestContext.Current.CancellationToken);
            await transaction.DisposeAsync();
            await scope.DisposeAsync();
            await services.DisposeAsync();
        }
    }

    private sealed class TestEnvironment(string environment) : IHostEnvironment
    {
        public string EnvironmentName { get; set; } = environment;
        public string ApplicationName { get; set; } = "BeNobat.Web.Tests";
        public string ContentRootPath { get; set; } = "/tmp";
        public IFileProvider ContentRootFileProvider { get; set; } = new NullFileProvider();
    }
}
