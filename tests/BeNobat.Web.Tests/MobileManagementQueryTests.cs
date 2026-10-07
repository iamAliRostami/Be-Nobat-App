using System.Reflection;
using System.Security.Claims;
using BeNobat.Web.Api;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using BeNobat.Web.Security;
using Microsoft.AspNetCore.Http;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace BeNobat.Web.Tests;

public sealed class MobileManagementQueryTests
{
    [Fact]
    public void Service_projection_translates_nested_assignments_and_retains_tenant_scope_when_paginated()
    {
        using var db = Database();
        var userId = Guid.NewGuid();
        var scope = Scope(db, userId);
        var services = db.Services.Where(s => scope.Businesses().Any(b => b.Id == s.BusinessId));
        var query = Invoke<IQueryable<ManagementServiceDto>>("ServiceDtos", services.OrderBy(s => s.Id), scope);

        var sql = query.Skip(25).Take(25).ToQueryString();

        Assert.Contains(userId.ToString(), sql);
        Assert.Contains("benobat.\"BranchMemberships\"", sql);
        Assert.Contains("benobat.\"BranchServices\"", sql);
        Assert.Contains("benobat.\"ServiceResources\"", sql);
        Assert.Contains("\"DeletedAt\" IS NULL", sql);
        Assert.Contains("LIMIT", sql);
        Assert.Contains("OFFSET", sql);
    }

    [Fact]
    public void Appointment_projection_translates_multiple_services_and_preserves_current_authorization_for_archived_names()
    {
        using var db = Database();
        var userId = Guid.NewGuid();
        var scope = Scope(db, userId);
        var query = Invoke<IQueryable<ManagementAppointmentDto>>("AppointmentDtos", scope.Appointments().OrderBy(a => a.StartsAt));

        var sql = query.Take(25).ToQueryString();

        Assert.Contains(userId.ToString(), sql);
        Assert.Contains("benobat.\"AppointmentServices\"", sql);
        Assert.Contains("benobat.\"Services\"", sql);
        Assert.Contains("benobat.\"BranchMemberships\"", sql);
        Assert.Contains("'Staff'", sql);
        Assert.True(System.Text.RegularExpressions.Regex.Matches(sql, "\"DeletedAt\" IS NULL").Count >= 3,
            "Appointment, business and current membership must remain active while archived branch labels remain readable.");
    }

    [Fact]
    public void Platform_user_projection_translates_role_collection_with_pagination()
    {
        using var db = Database();
        var query = Invoke<IQueryable<ManagementUserDto>>("UserDtos", db.Users.OrderBy(u => u.Id), db);

        var sql = query.Skip(25).Take(25).ToQueryString();

        Assert.Contains("benobat.\"AspNetUserRoles\"", sql);
        Assert.Contains("benobat.\"AspNetRoles\"", sql);
        Assert.Contains("\"LockoutEnd\"", sql);
        Assert.Contains("LIMIT", sql);
        Assert.Contains("OFFSET", sql);
    }

    [Fact]
    public void Archived_branch_projection_requires_current_manager_membership_and_active_business()
    {
        using var db = Database();
        var userId = Guid.NewGuid();
        var query = Invoke<IQueryable<Branch>>("StoredBranches", db, Scope(db, userId), true);

        var sql = query.ToQueryString();

        Assert.Contains(userId.ToString(), sql);
        Assert.Contains("benobat.\"Businesses\"", sql);
        Assert.Contains("benobat.\"BranchMemberships\"", sql);
        Assert.Contains("'Owner'", sql);
        Assert.Contains("'Manager'", sql);
        Assert.True(System.Text.RegularExpressions.Regex.Matches(sql, "\"DeletedAt\" IS NULL").Count >= 2,
            "Archived branch access still requires an active business and a non-deleted membership.");
    }

    private static AppDbContext Database() => new(new DbContextOptionsBuilder<AppDbContext>()
        .UseNpgsql("Host=localhost;Database=unused").Options);

    private static ApiScope Scope(AppDbContext db, Guid userId)
    {
        var http = new DefaultHttpContext { User = new ClaimsPrincipal(new ClaimsIdentity([
            new Claim(ClaimTypes.NameIdentifier, userId.ToString()), new Claim(ClaimTypes.Role, AppRoles.Manager)], "MobileBearer")) };
        return new ApiScope(db, new HttpContextAccessor { HttpContext = http });
    }

    private static T Invoke<T>(string method, params object[] arguments) =>
        (T)typeof(ManagementEndpoints).GetMethod(method, BindingFlags.Static | BindingFlags.NonPublic)!.Invoke(null, arguments)!;
}
