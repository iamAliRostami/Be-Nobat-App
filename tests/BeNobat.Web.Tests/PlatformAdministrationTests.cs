using System.Collections;
using System.Data;
using System.Reflection;
using BeNobat.Web.Components.Pages;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using BeNobat.Web.Security;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace BeNobat.Web.Tests;

public sealed class PlatformAdministrationTests
{
    private const BindingFlags PrivateStatic = BindingFlags.NonPublic | BindingFlags.Static;
    private const BindingFlags PrivateInstance = BindingFlags.NonPublic | BindingFlags.Instance;

    [Theory]
    [InlineData("../service")]
    [InlineData("service/name")]
    [InlineData("service--name")]
    [InlineData("-service")]
    [InlineData("service-")]
    [InlineData("service_name")]
    [InlineData("خدمت")]
    [InlineData("service?x=1")]
    public void Catalog_rejects_noncanonical_slugs_on_the_server(string slug)
    {
        var form = CatalogForm(slug, 30);
        Assert.NotNull(Validate<PlatformServices>(form));
    }

    [Theory]
    [InlineData(4)]
    [InlineData(0)]
    [InlineData(-10)]
    [InlineData(1441)]
    [InlineData(int.MaxValue)]
    public void Catalog_rejects_durations_outside_a_single_day(int duration)
    {
        Assert.NotNull(Validate<PlatformServices>(CatalogForm("consultation", duration)));
    }

    [Theory]
    [InlineData(5)]
    [InlineData(1440)]
    public void Catalog_accepts_duration_boundaries_and_normalizes_whitespace_and_slug_case(int duration)
    {
        var form = CatalogForm("  CONSULTATION-2  ", duration);
        Set(form, "Name", "  مشاوره  ");
        Set(form, "Description", "  توضیح  ");

        Assert.Null(Validate<PlatformServices>(form));
        Assert.Equal("consultation-2", Get(form, "Slug"));
        Assert.Equal("مشاوره", Get(form, "Name"));
        Assert.Equal("توضیح", Get(form, "Description"));
    }

    [Fact]
    public void Catalog_rejects_empty_category_and_overlong_fields_even_without_browser_validation()
    {
        var form = CatalogForm("consultation", 30);
        Set(form, "Category", "  ");
        Assert.NotNull(Validate<PlatformServices>(form));
        Set(form, "Category", "عمومی");
        Set(form, "Slug", new string('s', 101));
        Assert.NotNull(Validate<PlatformServices>(form));
        Set(form, "Slug", "consultation");
        Set(form, "Description", new string('ا', 5001));
        Assert.NotNull(Validate<PlatformServices>(form));
    }

    [Theory]
    [InlineData("کارگاه سلامت")]
    [InlineData("  Example__business  ")]
    public void Automatic_business_slugs_remain_safe_for_names_without_ascii_characters(string name)
    {
        var form = NewInput<PlatformBusinesses>("BusinessInput");
        Set(form, "Name", name);
        var arguments = new object?[] { form, null };

        Assert.Null(typeof(PlatformBusinesses).GetMethod("ValidateInput", PrivateStatic)!.Invoke(null, arguments));
        Assert.Matches("^[a-z0-9]+(-[a-z0-9]+)*$", Assert.IsType<string>(arguments[1]));
    }

    [Fact]
    public void Automatic_business_slug_is_bounded_and_explicit_unsafe_slug_is_rejected()
    {
        var form = NewInput<PlatformBusinesses>("BusinessInput");
        Set(form, "Name", new string('a', 200));
        var arguments = new object?[] { form, null };
        var validator = typeof(PlatformBusinesses).GetMethod("ValidateInput", PrivateStatic)!;

        Assert.Null(validator.Invoke(null, arguments));
        Assert.Equal(100, Assert.IsType<string>(arguments[1]).Length);
        Set(form, "Slug", "../unsafe");
        Assert.NotNull(validator.Invoke(null, new object?[] { form, null }));
        Set(form, "Slug", "safe");
        Set(form, "Name", "  ");
        Assert.NotNull(validator.Invoke(null, new object?[] { form, null }));
    }

    [Theory]
    [InlineData(-1, 0)]
    [InlineData(100, 0)]
    [InlineData((int)CategoryKind.Business, -1)]
    public void Category_validates_disabled_kind_and_sort_order_on_the_server(int kind, int order)
    {
        var form = NewInput<PlatformCategories>("Input");
        Set(form, "Name", "عمومی");
        Set(form, "Kind", (CategoryKind)kind);
        Set(form, "SortOrder", order);
        Assert.NotNull(Validate<PlatformCategories>(form));
    }

    [Fact]
    public void Uniqueness_queries_reserve_archived_business_catalog_and_category_identifiers()
    {
        using var db = new AppDbContext(new DbContextOptionsBuilder<AppDbContext>()
            .UseNpgsql("Host=localhost;Database=unused").Options);
        var editingId = Guid.NewGuid();
        var business = (IQueryable<Business>)typeof(PlatformBusinesses).GetMethod("SlugConflicts", PrivateStatic)!
            .Invoke(null, new object?[] { db, "reserved", editingId })!;
        var catalog = (IQueryable<ServiceCatalogItem>)typeof(PlatformServices).GetMethod("SlugConflicts", PrivateStatic)!
            .Invoke(null, new object?[] { db, "reserved", editingId })!;
        var category = (IQueryable<CategoryDefinition>)typeof(PlatformCategories).GetMethod("NameConflicts", PrivateStatic)!
            .Invoke(null, new object?[] { db, CategoryKind.Business, "reserved", editingId })!;

        foreach (var sql in new[] { business.ToQueryString(), catalog.ToQueryString(), category.ToQueryString() })
        {
            Assert.Contains("reserved", sql);
            Assert.Contains(editingId.ToString(), sql);
            Assert.Contains("lower(", sql);
            Assert.DoesNotContain("\"DeletedAt\" IS NULL", sql);
        }
        Assert.Contains("'Business'", category.ToQueryString());
        Assert.Equal(ConnectionState.Closed, db.Database.GetDbConnection().State);
    }

    [Fact]
    public void Last_admin_protection_ignores_target_and_locked_accounts_but_counts_expired_lockouts()
    {
        var now = DateTimeOffset.UtcNow;
        var target = new AppUser { Id = Guid.NewGuid() };
        var locked = new AppUser { Id = Guid.NewGuid(), LockoutEnabled = true, LockoutEnd = now.AddHours(1) };
        var expired = new AppUser { Id = Guid.NewGuid(), LockoutEnabled = true, LockoutEnd = now };
        var lockoutNotEnabled = new AppUser { Id = Guid.NewGuid(), LockoutEnabled = false, LockoutEnd = DateTimeOffset.MaxValue };

        Assert.False(HasOtherAdmin([target], target.Id, now));
        Assert.False(HasOtherAdmin([target, locked], target.Id, now));
        Assert.True(HasOtherAdmin([target, expired], target.Id, now));
        Assert.True(HasOtherAdmin([target, lockoutNotEnabled], target.Id, now));
    }

    [Fact]
    public void Access_role_filter_includes_membership_role_when_user_also_has_customer_grant()
    {
        var component = new PlatformAccess();
        var accessType = typeof(PlatformAccess).GetNestedType("UserAccess", BindingFlags.NonPublic)!;
        var owner = new AppUser { Id = Guid.NewGuid(), DisplayName = "مالک" };
        var customer = new AppUser { Id = Guid.NewGuid(), DisplayName = "مشتری" };
        var first = Activator.CreateInstance(accessType, owner, new[] { AppRoles.Owner, AppRoles.Customer })!;
        var second = Activator.CreateInstance(accessType, customer, new[] { AppRoles.Customer })!;
        var list = (IList)Activator.CreateInstance(typeof(List<>).MakeGenericType(accessType))!;
        list.Add(first);
        list.Add(second);
        typeof(PlatformAccess).GetField("Items", PrivateInstance)!.SetValue(component, list);
        typeof(PlatformAccess).GetField("RoleFilter", PrivateInstance)!.SetValue(component, AppRoles.Owner);

        var filtered = (IEnumerable)typeof(PlatformAccess).GetProperty("Filtered", PrivateInstance)!.GetValue(component)!;
        Assert.Same(first, Assert.Single(filtered.Cast<object>()));
        Assert.Equal(AppRoles.Customer, Get(first, "SelectedRole"));
    }

    private static bool HasOtherAdmin(AppUser[] users, Guid targetId, DateTimeOffset now) =>
        (bool)typeof(PlatformAccess).GetMethod("HasOtherActivePlatformAdmin", PrivateStatic)!
            .Invoke(null, new object[] { users, targetId, now })!;

    private static object CatalogForm(string slug, int duration)
    {
        var form = NewInput<PlatformServices>("CatalogInput");
        Set(form, "Name", "مشاوره");
        Set(form, "Slug", slug);
        Set(form, "Duration", duration);
        return form;
    }

    private static object NewInput<T>(string name) =>
        Activator.CreateInstance(typeof(T).GetNestedType(name, BindingFlags.NonPublic)!, nonPublic: true)!;

    private static object? Validate<T>(object form) =>
        typeof(T).GetMethod("ValidateInput", PrivateStatic)!.Invoke(null, new[] { form });

    private static void Set(object input, string name, object value) => input.GetType().GetProperty(name)!.SetValue(input, value);
    private static object? Get(object input, string name) => input.GetType().GetProperty(name)!.GetValue(input);
}
