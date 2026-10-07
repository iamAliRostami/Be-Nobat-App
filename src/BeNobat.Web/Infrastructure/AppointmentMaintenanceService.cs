using BeNobat.Web.Domain;
using Microsoft.EntityFrameworkCore;

namespace BeNobat.Web.Infrastructure;

/// <summary>
/// کارهای نگهداری دوره‌ای نوبت‌ها. نوبتی که هیچ‌وقت تأیید نشده و زمانش گذشته، تا ابد
/// «در انتظار» می‌ماند، هم در فهرست مشتری به‌عنوان نوبت باز دیده می‌شد و هم برای مدیر
/// کار معوق بود؛ این سرویس چنین نوبت‌هایی را (یک ساعت پس از شروع) «لغو شده» می‌کند.
/// </summary>
public sealed class AppointmentMaintenanceService(
    IDbContextFactory<AppDbContext> dbFactory,
    ILogger<AppointmentMaintenanceService> logger) : BackgroundService
{
    private static readonly TimeSpan Interval = TimeSpan.FromMinutes(10);
    private static readonly TimeSpan PendingGrace = TimeSpan.FromHours(1);

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                await ExpireStalePendingAsync(stoppingToken);
            }
            catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
            {
                break;
            }
            catch (Exception exception)
            {
                logger.LogError(exception, "Appointment maintenance run failed.");
            }

            try
            {
                await Task.Delay(Interval, stoppingToken);
            }
            catch (OperationCanceledException)
            {
                break;
            }
        }
    }

    public async Task<int> ExpireStalePendingAsync(CancellationToken cancellationToken)
    {
        await using var db = await dbFactory.CreateDbContextAsync(cancellationToken);
        var now = DateTimeOffset.UtcNow;
        var cutoff = now - PendingGrace;
        var count = await db.Appointments
            .Where(a => a.Status == AppointmentStatus.Pending && a.StartsAt < cutoff)
            .ExecuteUpdateAsync(setters => setters
                .SetProperty(a => a.Status, AppointmentStatus.Cancelled)
                .SetProperty(a => a.UpdatedAt, now), cancellationToken);
        if (count > 0) logger.LogInformation("{Count} stale pending appointments were expired.", count);
        return count;
    }
}
