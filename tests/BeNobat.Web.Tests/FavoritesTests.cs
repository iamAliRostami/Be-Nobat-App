using System.Data;
using System.Reflection;
using BeNobat.Web.Components.Pages;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace BeNobat.Web.Tests;

public sealed class FavoritesTests
{
    [Fact]
    public void Favorites_query_translates_with_related_data_user_scope_and_newest_first_order()
    {
        using var db = new AppDbContext(new DbContextOptionsBuilder<AppDbContext>()
            .UseNpgsql("Host=localhost;Database=unused").Options);
        var userId = Guid.NewGuid();
        var query = (IQueryable<FavoriteBusiness>)typeof(Favorites)
            .GetMethod("FavoritesQuery", BindingFlags.Static | BindingFlags.NonPublic)!
            .Invoke(null, new object[] { db, userId })!;

        // PostgreSQL translation fails on the original Include-after-Select query,
        // even without opening a connection or loading any favorites.
        var sql = query.ToQueryString();

        Assert.Contains(userId.ToString(), sql);
        Assert.Contains("\"UserId\" =", sql);
        Assert.Contains("benobat.\"Branches\"", sql);
        Assert.Contains("benobat.\"Reviews\"", sql);
        Assert.Contains("'Published'", sql);
        Assert.Contains("\"DeletedAt\" IS NULL", sql);
        Assert.Contains("\"CreatedAt\" DESC", sql);
        Assert.Equal(ConnectionState.Closed, db.Database.GetDbConnection().State);
    }
}
