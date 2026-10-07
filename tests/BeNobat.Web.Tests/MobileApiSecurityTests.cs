using System.Security.Claims;
using BeNobat.Web.Api;
using BeNobat.Web.Infrastructure;
using BeNobat.Web.Security;
using Microsoft.AspNetCore.Http;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace BeNobat.Web.Tests;

public sealed class MobileApiSecurityTests
{
    [Fact]
    public void Platform_business_scope_retains_active_business_query()
    {
        using var db = new AppDbContext(new DbContextOptionsBuilder<AppDbContext>().UseNpgsql("Host=localhost;Database=unused").Options);
        var http = new DefaultHttpContext { User = new ClaimsPrincipal(new ClaimsIdentity([new Claim(ClaimTypes.NameIdentifier, Guid.NewGuid().ToString()), new Claim(ClaimTypes.Role, AppRoles.PlatformAdmin)], "MobileBearer")) };
        var scope = new ApiScope(db, new HttpContextAccessor { HttpContext = http });
        Assert.True(scope.IsPlatform);
        var sql = scope.Businesses(true).ToQueryString();
        Assert.DoesNotContain("WHERE FALSE", sql);
        Assert.Contains("\"DeletedAt\" IS NULL", sql);
    }

    [Fact]
    public void Explicit_soft_delete_predicates_remain_compatible_with_the_generic_model_filter()
    {
        using var db = new AppDbContext(new DbContextOptionsBuilder<AppDbContext>().UseNpgsql("Host=localhost;Database=unused").Options);
        var sql = db.Businesses.Where(b => b.DeletedAt == null).ToQueryString();
        Assert.DoesNotContain("WHERE FALSE", sql);
        Assert.Contains("\"DeletedAt\" IS NULL", sql);
    }

    [Fact]
    public void Mobile_scope_queries_filter_current_branch_membership_and_active_parents()
    {
        using var db = new AppDbContext(new DbContextOptionsBuilder<AppDbContext>().UseNpgsql("Host=localhost;Database=unused").Options);
        var user = Guid.NewGuid();
        var http = new DefaultHttpContext { User = new ClaimsPrincipal(new ClaimsIdentity([new Claim(ClaimTypes.NameIdentifier, user.ToString()), new Claim(ClaimTypes.Role, AppRoles.Manager)], "MobileBearer")) };
        var scope = new ApiScope(db, new HttpContextAccessor { HttpContext = http });

        var branches = scope.Branches(true).ToQueryString();
        var appointments = scope.Appointments().ToQueryString();
        var reviews = scope.Reviews().ToQueryString();

        Assert.Contains(user.ToString(), branches);
        Assert.Contains("benobat.\"BranchMemberships\"", branches);
        Assert.Contains("'Owner'", branches);
        Assert.Contains("'Manager'", branches);
        Assert.Contains("benobat.\"Businesses\"", branches);
        Assert.Contains("\"DeletedAt\" IS NULL", branches);
        Assert.Contains("\"UserId\"", appointments);
        Assert.Contains("'Staff'", appointments);
        Assert.Contains("\"BranchId\"", reviews);
        Assert.False(scope.IsPlatform);
    }

    [Fact]
    public void Public_business_projection_translates_on_Postgres_and_only_averages_published_reviews()
    {
        using var db = new AppDbContext(new DbContextOptionsBuilder<AppDbContext>().UseNpgsql("Host=localhost;Database=unused").Options);
        var query = db.Businesses.Select(PublicEndpoints.BusinessProjection).ToQueryString();
        Assert.Contains("'Published'", query);
        Assert.Contains("avg(", query);
        Assert.Contains("benobat.\"Branches\"", query);
        Assert.Contains("\"DeletedAt\" IS NULL", query);
    }

    [Fact]
    public void Avatar_validation_rejects_mislabeled_bytes_and_accepts_supported_signatures()
    {
        Assert.False(AuthenticationEndpoints.ValidImage("<svg onload=alert(1)>"u8.ToArray(), "image/png"));
        Assert.True(AuthenticationEndpoints.ValidImage([137, 80, 78, 71, 13, 10, 26, 10], "image/png"));
        Assert.False(AuthenticationEndpoints.ValidImage([255, 216, 255], "image/webp"));
    }
}
