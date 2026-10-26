using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.FileProviders;
using Microsoft.Extensions.Hosting;
using Xunit;

namespace BeNobat.Web.Tests;

public sealed class DemoSeederTests
{
    [Fact]
    public async Task CommercialDemoSeeder_Throws_In_Production_When_DemoPassword_Is_Missing()
    {
        var services = new ServiceCollection();
        services.AddLogging();
        services.AddDbContext<AppDbContext>(options => options.UseNpgsql("Host=localhost;Database=dummy"));
        services.AddIdentityCore<AppUser>().AddRoles<IdentityRole<Guid>>().AddEntityFrameworkStores<AppDbContext>();
        services.AddSingleton<IHostEnvironment>(new TestEnv(Environments.Production));
        services.AddSingleton<IConfiguration>(new ConfigurationBuilder().Build());

        using var provider = services.BuildServiceProvider();
        var ex = await Assert.ThrowsAsync<InvalidOperationException>(() =>
            CommercialDemoSeeder.SeedAsync(provider, TestContext.Current.CancellationToken));

        Assert.Contains("Seed__DemoPassword", ex.Message);
    }

    [Fact]
    public void AppSettings_Contains_DemoPassword()
    {
        var basePath = AppContext.BaseDirectory;
        var candidatePath = Path.GetFullPath(Path.Combine(basePath, "../../../../../src/BeNobat.Web/appsettings.json"));
        if (!File.Exists(candidatePath))
        {
            candidatePath = @"d:\Rostami\Projects\Be-Nobat-App\src\BeNobat.Web\appsettings.json";
        }

        Assert.True(File.Exists(candidatePath), $"appsettings.json not found at {candidatePath}");
        var config = new ConfigurationBuilder().AddJsonFile(candidatePath).Build();
        var demoPassword = config["Seed:DemoPassword"];

        Assert.False(string.IsNullOrWhiteSpace(demoPassword));
        Assert.Equal("DemoPreview2026!", demoPassword);
    }

    private sealed class TestEnv(string environment) : IHostEnvironment
    {
        public string EnvironmentName { get; set; } = environment;
        public string ApplicationName { get; set; } = "BeNobat.Web.Tests";
        public string ContentRootPath { get; set; } = "/tmp";
        public IFileProvider ContentRootFileProvider { get; set; } = new NullFileProvider();
    }
}
