using BeNobat.Web.Domain;
using BeNobat.Web.Security;
using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;

namespace BeNobat.Web.Infrastructure;

public static class DbSeeder
{
    // [fix] Kept for backward compatibility with anything still referencing the old
    // simple role names; the real seeded/authoritative role set is BeNobat.Web.Security.AppRoles.
    public const string AdminRole = AppRoles.PlatformAdmin;
    public const string CustomerRole = AppRoles.Customer;

    public static async Task SeedAsync(IServiceProvider services, IConfiguration configuration, CancellationToken cancellationToken = default)
    {
        var userManager = services.GetRequiredService<UserManager<AppUser>>();
        var roleManager = services.GetRequiredService<RoleManager<IdentityRole<Guid>>>();

        foreach (var role in AppRoles.All)
        {
            if (!await roleManager.RoleExistsAsync(role))
            {
                await roleManager.CreateAsync(new IdentityRole<Guid>(role));
            }
        }

        var adminEmail = configuration["Seed:AdminEmail"] ?? "admin@benobat.local";
        var adminPassword = configuration["Seed:AdminPassword"] ?? "Admin123!";

        var admin = await userManager.FindByEmailAsync(adminEmail);
        if (admin is null)
        {
            admin = new AppUser
            {
                UserName = adminEmail,
                Email = adminEmail,
                EmailConfirmed = true,
                DisplayName = "مدیر سیستم",
            };

            var createResult = await userManager.CreateAsync(admin, adminPassword);
            if (createResult.Succeeded)
            {
                await userManager.AddToRoleAsync(admin, AppRoles.PlatformAdmin);
            }
        }
        else if (!await userManager.IsInRoleAsync(admin, AppRoles.PlatformAdmin))
        {
            await userManager.AddToRoleAsync(admin, AppRoles.PlatformAdmin);
        }

    }
}
