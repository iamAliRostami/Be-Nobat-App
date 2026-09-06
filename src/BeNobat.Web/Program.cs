using BeNobat.Web.Components;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);
var connectionString = builder.Configuration.GetConnectionString("Default")
    ?? throw new InvalidOperationException("ConnectionStrings:Default is required.");

builder.Services.AddDbContext<AppDbContext>(options => options.UseNpgsql(connectionString));
builder.Services.AddAuthorization();
builder.Services.AddIdentityApiEndpoints<AppUser>()
    .AddRoles<IdentityRole<Guid>>()
    .AddEntityFrameworkStores<AppDbContext>();
builder.Services.AddRazorComponents().AddInteractiveServerComponents();
builder.Services.AddOpenApi();
builder.Services.AddHealthChecks().AddDbContextCheck<AppDbContext>();

var app = builder.Build();

// Foundation bootstrap; this is replaced by reviewed EF migrations before cut-over.
await using (var scope = app.Services.CreateAsyncScope())
{
    var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
    await db.Database.EnsureCreatedAsync();
}

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

app.UseHttpsRedirection();
app.UseStaticFiles();
app.UseAntiforgery();

app.MapGroup("/api/auth").MapIdentityApi<AppUser>();
app.MapGet("/api/dashboard", async (AppDbContext db, CancellationToken cancellationToken) =>
    new DashboardSummary(
        await db.Businesses.CountAsync(cancellationToken),
        await db.Branches.CountAsync(cancellationToken),
        await db.Services.CountAsync(cancellationToken),
        await db.Appointments.CountAsync(cancellationToken)))
    .RequireAuthorization();
app.MapHealthChecks("/health");
app.MapRazorComponents<App>().AddInteractiveServerRenderMode();

app.Run();

public sealed record DashboardSummary(int Businesses, int Branches, int Services, int Appointments);

public partial class Program;
