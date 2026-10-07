using System.Security.Claims;
using BeNobat.Web.Application;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using BeNobat.Web.Security;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Identity.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging.Abstractions;
using Microsoft.Extensions.Options;
using Xunit;

namespace BeNobat.Web.Tests;

public sealed class AccountCredentialIntegrationTests
{
    [Fact]
    public async Task Email_change_commits_normalized_login_email_unconfirmed_status_and_new_stamp_together()
    {
        await using var fixture = await Fixture.CreateAsync();
        var oldStamp = fixture.User.SecurityStamp;
        var email = $"new-{Guid.NewGuid():N}@example.test";
        var result = await AccountCredentialChanges.ChangeEmailAsync(fixture.Db, fixture.Users, fixture.Principal,
            "  " + email + "  ", TestContext.Current.CancellationToken);
        Assert.True(result.Success);
        var saved = await fixture.ReadAsync();
        Assert.Equal(email, saved.Email);
        Assert.Equal(email, saved.UserName);
        Assert.Equal(email.ToUpperInvariant(), saved.NormalizedEmail);
        Assert.Equal(email.ToUpperInvariant(), saved.NormalizedUserName);
        Assert.False(saved.EmailConfirmed);
        Assert.NotEqual(oldStamp, saved.SecurityStamp);
        await using var fresh = fixture.NewContext();
        Assert.False(await UserContext.CurrentSessionUsers(fresh, fixture.Principal).AnyAsync(TestContext.Current.CancellationToken));
    }

    [Fact]
    public async Task Failure_to_rotate_the_stamp_rolls_back_email_username_and_confirmation()
    {
        await using var fixture = await Fixture.CreateAsync();
        using var failing = new FailingStampManager(fixture.Db, fixture.Options, fixture.Scope.ServiceProvider);
        var result = await AccountCredentialChanges.ChangeEmailAsync(fixture.Db, failing, fixture.Principal,
            $"rejected-{Guid.NewGuid():N}@example.test", TestContext.Current.CancellationToken);
        Assert.Equal(CredentialChangeFailure.SaveFailed, result.Failure);
        await AssertOriginalIdentityAsync(fixture);
    }

    [Fact]
    public async Task A_failed_post_update_step_cannot_leave_changed_credentials_or_a_partial_session()
    {
        await using var fixture = await Fixture.CreateAsync();
        await Assert.ThrowsAsync<InvalidOperationException>(() => AccountCredentialChanges.ChangeEmailAsync(fixture.Db,
            fixture.Users, fixture.Principal, $"rollback-{Guid.NewGuid():N}@example.test", TestContext.Current.CancellationToken,
            beforeCommit: async (user, cancellationToken) =>
            {
                fixture.Db.UserTokens.Add(new IdentityUserToken<Guid> { UserId = user.Id, LoginProvider = "credential-test", Name = "session", Value = "temporary" });
                await fixture.Db.SaveChangesAsync(cancellationToken);
                throw new InvalidOperationException("Injected session persistence failure");
            }));
        await AssertOriginalIdentityAsync(fixture);
        await using var fresh = fixture.NewContext();
        Assert.False(await fresh.UserTokens.AnyAsync(x => x.UserId == fixture.User.Id, TestContext.Current.CancellationToken));
    }

    [Theory]
    [InlineData(false, false)]
    [InlineData(false, true)]
    [InlineData(true, false)]
    [InlineData(true, true)]
    public async Task Revoked_and_disabled_sessions_cannot_change_either_credential(bool password, bool disabled)
    {
        await using var fixture = await Fixture.CreateAsync();
        if (disabled)
            await fixture.Db.Users.Where(x => x.Id == fixture.User.Id).ExecuteUpdateAsync(s => s.SetProperty(x => x.LockoutEnabled, true)
                .SetProperty(x => x.LockoutEnd, DateTimeOffset.UtcNow.AddDays(1)), TestContext.Current.CancellationToken);
        else
            await fixture.Db.Users.Where(x => x.Id == fixture.User.Id).ExecuteUpdateAsync(s => s.SetProperty(x => x.SecurityStamp, "revoked"), TestContext.Current.CancellationToken);
        var before = await fixture.ReadAsync();
        var result = password
            ? await AccountCredentialChanges.ChangePasswordAsync(fixture.Db, fixture.Users, fixture.Principal, "oldPass123", "newPass321", TestContext.Current.CancellationToken)
            : await AccountCredentialChanges.ChangeEmailAsync(fixture.Db, fixture.Users, fixture.Principal, $"blocked-{Guid.NewGuid():N}@example.test", TestContext.Current.CancellationToken);
        Assert.Equal(CredentialChangeFailure.SessionExpired, result.Failure);
        var after = await fixture.ReadAsync();
        Assert.Equal(before.Email, after.Email);
        Assert.Equal(before.UserName, after.UserName);
        Assert.Equal(before.PasswordHash, after.PasswordHash);
        Assert.Equal(before.SecurityStamp, after.SecurityStamp);
    }

    [Fact]
    public async Task Duplicate_email_validation_keeps_the_original_identity_unchanged()
    {
        await using var fixture = await Fixture.CreateAsync();
        var other = new AppUser { Id = Guid.NewGuid(), Email = $"duplicate-{Guid.NewGuid():N}@example.test" };
        other.UserName = other.Email;
        Assert.True((await fixture.Users.CreateAsync(other, "otherPass123")).Succeeded);
        fixture.ExtraUser = other.Id;
        var result = await AccountCredentialChanges.ChangeEmailAsync(fixture.Db, fixture.Users, fixture.Principal, other.Email, TestContext.Current.CancellationToken);
        Assert.Equal(CredentialChangeFailure.DuplicateEmail, result.Failure);
        await AssertOriginalIdentityAsync(fixture);
    }

    [Fact]
    public async Task Password_change_requires_the_current_password_and_revokes_the_old_session_after_success()
    {
        await using var fixture = await Fixture.CreateAsync();
        var wrong = await AccountCredentialChanges.ChangePasswordAsync(fixture.Db, fixture.Users, fixture.Principal,
            "wrongPass123", "newPass321", TestContext.Current.CancellationToken);
        Assert.Equal(CredentialChangeFailure.PasswordMismatch, wrong.Failure);
        await AssertOriginalIdentityAsync(fixture);
        var changed = await AccountCredentialChanges.ChangePasswordAsync(fixture.Db, fixture.Users, fixture.Principal,
            "oldPass123", "newPass321", TestContext.Current.CancellationToken);
        Assert.True(changed.Success);
        var saved = await fixture.ReadAsync();
        Assert.NotEqual(fixture.User.SecurityStamp, saved.SecurityStamp);
        Assert.False(await fixture.Users.CheckPasswordAsync(saved, "oldPass123"));
        Assert.True(await fixture.Users.CheckPasswordAsync(saved, "newPass321"));
        await using var fresh = fixture.NewContext();
        Assert.False(await UserContext.CurrentSessionUsers(fresh, fixture.Principal).AnyAsync(TestContext.Current.CancellationToken));
    }

    private static async Task AssertOriginalIdentityAsync(Fixture fixture)
    {
        var saved = await fixture.ReadAsync();
        Assert.Equal(fixture.User.Email, saved.Email);
        Assert.Equal(fixture.User.UserName, saved.UserName);
        Assert.Equal(fixture.User.NormalizedEmail, saved.NormalizedEmail);
        Assert.Equal(fixture.User.NormalizedUserName, saved.NormalizedUserName);
        Assert.Equal(fixture.User.EmailConfirmed, saved.EmailConfirmed);
        Assert.Equal(fixture.User.SecurityStamp, saved.SecurityStamp);
        Assert.Equal(fixture.User.PasswordHash, saved.PasswordHash);
    }

    private sealed class FailingStampManager(AppDbContext db, IdentityOptions options, IServiceProvider services) : UserManager<AppUser>(
        new UserStore<AppUser, IdentityRole<Guid>, AppDbContext, Guid>(db), Microsoft.Extensions.Options.Options.Create(options),
        new PasswordHasher<AppUser>(), [new UserValidator<AppUser>()], [new PasswordValidator<AppUser>()], new UpperInvariantLookupNormalizer(),
        new IdentityErrorDescriber(), services, NullLogger<UserManager<AppUser>>.Instance)
    {
        public override Task<IdentityResult> UpdateSecurityStampAsync(AppUser user) =>
            Task.FromResult(IdentityResult.Failed(new IdentityError { Code = "InjectedFailure", Description = "Injected stamp failure" }));
    }

    private sealed class Fixture : IAsyncDisposable
    {
        private readonly ServiceProvider services;
        private readonly DbContextOptions<AppDbContext> contextOptions;
        public AsyncServiceScope Scope { get; }
        public AppDbContext Db { get; }
        public UserManager<AppUser> Users { get; }
        public IdentityOptions Options { get; }
        public AppUser User { get; } = new() { Id = Guid.NewGuid(), Email = $"credential-{Guid.NewGuid():N}@example.test", EmailConfirmed = true };
        public Guid? ExtraUser { get; set; }
        public ClaimsPrincipal Principal => new(new ClaimsIdentity([new Claim(ClaimTypes.NameIdentifier, User.Id.ToString()),
            new Claim("AspNet.Identity.SecurityStamp", User.SecurityStamp!)], "Cookie"));

        private Fixture(string connection)
        {
            contextOptions = new DbContextOptionsBuilder<AppDbContext>().UseNpgsql(connection).Options;
            var collection = new ServiceCollection().AddLogging().AddDbContext<AppDbContext>(options => options.UseNpgsql(connection));
            collection.AddIdentityCore<AppUser>(options =>
            {
                options.User.RequireUniqueEmail = true;
                options.Password.RequireNonAlphanumeric = false;
                options.Password.RequireUppercase = false;
                options.Password.RequiredLength = 6;
            }).AddEntityFrameworkStores<AppDbContext>();
            services = collection.BuildServiceProvider();
            Scope = services.CreateAsyncScope();
            Db = Scope.ServiceProvider.GetRequiredService<AppDbContext>();
            Users = Scope.ServiceProvider.GetRequiredService<UserManager<AppUser>>();
            Options = Scope.ServiceProvider.GetRequiredService<IOptions<IdentityOptions>>().Value;
            User.UserName = User.Email;
        }

        public static async Task<Fixture> CreateAsync()
        {
            var connection = Environment.GetEnvironmentVariable("BENOBAT_TEST_DB");
            if (string.IsNullOrWhiteSpace(connection)) Assert.Skip("Set BENOBAT_TEST_DB to an initialized disposable PostgreSQL database.");
            var fixture = new Fixture(connection);
            Assert.True((await fixture.Users.CreateAsync(fixture.User, "oldPass123")).Succeeded);
            return fixture;
        }

        public AppDbContext NewContext() => new(contextOptions);
        public async Task<AppUser> ReadAsync()
        {
            await using var fresh = NewContext();
            return await fresh.Users.AsNoTracking().SingleAsync(x => x.Id == User.Id, TestContext.Current.CancellationToken);
        }
        public async ValueTask DisposeAsync()
        {
            await Scope.DisposeAsync();
            await using var fresh = NewContext();
            await fresh.Users.Where(x => x.Id == User.Id || x.Id == ExtraUser).ExecuteDeleteAsync(TestContext.Current.CancellationToken);
            await services.DisposeAsync();
        }
    }
}
