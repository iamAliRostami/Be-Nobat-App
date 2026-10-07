using System.Data;
using System.Security.Claims;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using BeNobat.Web.Security;
using Microsoft.AspNetCore.Components.Authorization;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace BeNobat.Web.Tests;

public sealed class AdminAccessScopeTests
{
    [Fact]
    public async Task Claimed_platform_role_does_not_remove_current_persisted_role_or_membership_authorization()
    {
        using var db = Context();
        var id = Guid.NewGuid();
        var authentication = new MutableAuthentication(Principal(id, AppRoles.PlatformAdmin, "old-stamp"));
        var access = new AdminAccessScope(db, authentication);

        var claimedAdmin = (await access.ManagedBranchesAsync()).ToQueryString();
        authentication.User = Principal(id, AppRoles.Customer, "old-stamp");
        var claimedCustomer = (await access.ManagedBranchesAsync()).ToQueryString();

        Assert.Equal(SqlBody(claimedAdmin), SqlBody(claimedCustomer));
        Assert.Contains("AspNetUserRoles", claimedAdmin);
        Assert.Contains("AspNetRoles", claimedAdmin);
        Assert.Contains("PlatformAdmin", claimedAdmin);
        Assert.Contains("BranchMemberships", claimedAdmin);
        Assert.Contains("LockoutEnabled", claimedAdmin);
        Assert.Contains("LockoutEnd", claimedAdmin);
        Assert.Contains("SecurityStamp", claimedAdmin);
        Assert.Contains("old-stamp", claimedAdmin);
        Assert.Contains("'Owner'", claimedAdmin);
        Assert.Contains("'Manager'", claimedAdmin);
        Assert.DoesNotContain("'Staff'", claimedAdmin);
        Assert.Equal(ConnectionState.Closed, db.Database.GetDbConnection().State);
    }

    [Fact]
    public async Task A_replaced_authentication_state_is_used_for_every_scope_query()
    {
        using var db = Context();
        var first = Guid.NewGuid();
        var second = Guid.NewGuid();
        var authentication = new MutableAuthentication(Principal(first, AppRoles.Manager));
        var access = new AdminAccessScope(db, authentication);
        Assert.Equal(first, await access.UserIdAsync());

        authentication.User = Principal(second, AppRoles.Staff);
        Assert.Equal(second, await access.UserIdAsync());
        var sql = (await access.AppointmentsAsync()).ToQueryString();
        Assert.Contains(second.ToString(), sql);
        Assert.DoesNotContain(first.ToString(), sql);
    }

    [Fact]
    public async Task Historical_appointment_scope_keeps_current_membership_session_and_resource_branch_checks_when_filters_are_ignored()
    {
        using var db = Context();
        var id = Guid.NewGuid();
        var access = new AdminAccessScope(db, new MutableAuthentication(Principal(id, AppRoles.Staff)));
        var sql = (await access.AppointmentsAsync()).IgnoreQueryFilters().ToQueryString();

        Assert.Contains("BranchMemberships", sql);
        Assert.Contains("AspNetUsers", sql);
        Assert.Contains("'Staff'", sql);
        Assert.Contains("\"UserId\"", sql);
        Assert.Contains("\"BranchId\"", sql);
        // Both the appointment and membership retain explicit active predicates;
        // archived labels may be loaded without restoring a removed membership.
        Assert.True(System.Text.RegularExpressions.Regex.Matches(sql, "\\\"DeletedAt\\\" IS NULL").Count >= 3);
        Assert.Equal(ConnectionState.Closed, db.Database.GetDbConnection().State);
    }

    [Fact]
    public async Task Production_history_queries_load_archived_parent_labels_without_active_catalog_filters()
    {
        using var db = Context();
        var access = new AdminAccessScope(db, new MutableAuthentication(Principal(Guid.NewGuid(), AppRoles.Manager, "history-session")));
        var appointments = (await access.AppointmentsAsync())
            .Include(a => a.Service).Include(a => a.Resource).Include(a => a.Branch).ThenInclude(b => b.Business);
        var outgoing = (await access.CustomerReviewsAsync())
            .Include(r => r.Appointment).ThenInclude(a => a.Resource)
            .Include(r => r.Appointment).ThenInclude(a => a.Branch);
        var incoming = (await access.ReviewsAsync()).Include(r => r.Branch).Include(r => r.Business);

        foreach (var sql in new[] { appointments.ToQueryString(), outgoing.ToQueryString() })
        {
            // A filtered resource subquery would drop an archived/disabled provider.
            // Historical readers must join the persisted resource directly instead.
            Assert.Contains("JOIN benobat.\"Resources\"", sql);
            Assert.DoesNotContain("FROM benobat.\"Resources\"", sql);
            Assert.Contains("BranchMemberships", sql);
            Assert.Contains("\"DeletedAt\" IS NULL", sql);
            Assert.Contains("SecurityStamp", sql);
        }
        Assert.Contains("\"DeletedAt\" IS NULL", incoming.ToQueryString());
        Assert.Contains("BranchMemberships", incoming.ToQueryString());
        Assert.Equal(ConnectionState.Closed, db.Database.GetDbConnection().State);
    }

    [Theory]
    [InlineData(AppRoles.PlatformAdmin)]
    [InlineData(AppRoles.Customer)]
    [InlineData("unknown")]
    public async Task Even_a_claimed_platform_admin_cannot_assign_a_non_team_membership_role(string role)
    {
        using var db = Context();
        var access = new AdminAccessScope(db, new MutableAuthentication(Principal(Guid.NewGuid(), AppRoles.PlatformAdmin)));
        Assert.False(await access.CanAssignRoleAsync(role, Guid.NewGuid()));
        Assert.Equal(ConnectionState.Closed, db.Database.GetDbConnection().State);
    }

    [Fact]
    public void Unauthenticated_or_empty_identity_ids_are_not_session_users()
    {
        var id = Guid.NewGuid();
        var unauthenticated = new ClaimsPrincipal(new ClaimsIdentity([new Claim(ClaimTypes.NameIdentifier, id.ToString())]));
        Assert.Null(UserContext.IdOf(unauthenticated));
        Assert.Null(UserContext.IdOf(Principal(Guid.Empty, AppRoles.PlatformAdmin)));
        Assert.Equal(id, UserContext.IdOf(Principal(id, AppRoles.Customer)));
    }

    private static string SqlBody(string sql) => string.Join("\n", sql.Split('\n').Where(line => !line.StartsWith("--", StringComparison.Ordinal)));

    private static AppDbContext Context() => new(new DbContextOptionsBuilder<AppDbContext>()
        .UseNpgsql("Host=localhost;Database=unused").Options);

    private static ClaimsPrincipal Principal(Guid id, string role, string? stamp = null)
    {
        var claims = new List<Claim> { new(ClaimTypes.NameIdentifier, id.ToString()), new(ClaimTypes.Role, role) };
        if (stamp is not null) claims.Add(new Claim("AspNet.Identity.SecurityStamp", stamp));
        return new ClaimsPrincipal(new ClaimsIdentity(claims, "Test"));
    }

    private sealed class MutableAuthentication(ClaimsPrincipal principal) : AuthenticationStateProvider
    {
        public ClaimsPrincipal User { get; set; } = principal;
        public override Task<AuthenticationState> GetAuthenticationStateAsync() => Task.FromResult(new AuthenticationState(User));
    }
}
