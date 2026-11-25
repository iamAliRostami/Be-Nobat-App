using System;
using System.IO;
using System.Threading.Tasks;
using Microsoft.EntityFrameworkCore;
using BeNobat.Web.Infrastructure;
using BeNobat.Web.Domain;
using Xunit;

namespace BeNobat.Web.Tests;

public class UploadReleaseTest
{
    [Fact]
    public async Task UploadLatestApk()
    {
        var cs = "Host=services.irn4.chabokan.net;Port=46612;Database=isabell;Username=postgres;Password=g7P9IjpYRGrlHFXe;SSL Mode=Prefer;Trust Server Certificate=true";
        var options = new DbContextOptionsBuilder<AppDbContext>()
            .UseNpgsql(cs)
            .Options;

        await using var db = new AppDbContext(options);
        var apkPath = @"D:\AndroidStudioProjects\Be Nobat\app\build\outputs\apk\prod\releaseChabokan\app-prod-releaseChabokan.apk";
        var apkBytes = await File.ReadAllBytesAsync(apkPath, TestContext.Current.CancellationToken);

        var existing = await db.AppReleases.FirstOrDefaultAsync(r => r.VersionCode == 1, TestContext.Current.CancellationToken);
        if (existing == null)
        {
            existing = new AppRelease
            {
                Id = Guid.NewGuid(),
                VersionName = "1.0.0",
                VersionCode = 1,
                ReleaseNotes = "نسخه رسمی به‌نوبت برای سرور چابکان",
                IsForceUpdate = false,
                IsActive = true,
                ApkFileName = "be-nobat-latest.apk",
                ApkFileSize = apkBytes.Length,
                ApkContentType = "application/vnd.android.package-archive",
                ApkData = apkBytes,
                ExternalDownloadUrl = "https://benobat.chbkn.dev/downloads/be-nobat-latest.apk",
                BazaarPackageName = "com.leon.be_nobat",
                CreatedAt = DateTime.UtcNow,
                UpdatedAt = DateTime.UtcNow
            };
            db.AppReleases.Add(existing);
        }
        else
        {
            existing.ApkData = apkBytes;
            existing.ApkFileSize = apkBytes.Length;
            existing.ApkFileName = "be-nobat-latest.apk";
            existing.ExternalDownloadUrl = "https://benobat.chbkn.dev/downloads/be-nobat-latest.apk";
            existing.IsActive = true;
            existing.UpdatedAt = DateTime.UtcNow;
        }

        await db.SaveChangesAsync(TestContext.Current.CancellationToken);
    }
}
