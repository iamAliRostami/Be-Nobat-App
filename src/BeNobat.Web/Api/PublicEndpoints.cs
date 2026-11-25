using System.Linq.Expressions;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using BeNobat.Web.Security;
using Microsoft.EntityFrameworkCore;

namespace BeNobat.Web.Api;

public static class PublicEndpoints
{
    public static readonly Expression<Func<Business, BusinessDto>> BusinessProjection = b => new BusinessDto(b.Id, b.Name, b.Slug, b.Category, b.City, b.Description, b.RequiresApproval,
        b.Branches.Count(x => x.DeletedAt == null && x.IsActive && x.IsApproved), b.Services.Count(x => x.DeletedAt == null),
        b.Reviews.Where(r => r.Status == ReviewStatus.Published && r.DeletedAt == null && (r.BranchId == null || r.Branch!.DeletedAt == null)).Average(r => (double?)r.Rating),
        b.Reviews.Count(r => r.Status == ReviewStatus.Published && r.DeletedAt == null && (r.BranchId == null || r.Branch!.DeletedAt == null)),
        b.AvatarData != null ? $"/media/business/{b.Id}/avatar" : null,
        b.HeaderData != null ? $"/media/business/{b.Id}/header" : null,
        b.DepositPercentage, b.DepositCardNumber, b.DepositIban, b.DepositAccountHolder, null,
        b.Telegram, b.Instagram, b.WhatsApp, b.Eitaa, b.Bale, b.Rubika, b.LinkedIn, b.Twitter, b.Aparat, b.Website);
    public static RouteGroupBuilder MapPublicEndpoints(this RouteGroupBuilder root)
    {
        root.MapGet("/categories", async (AppDbContext db, CancellationToken ct) => Results.Ok(new { items = await db.CategoryDefinitions.AsNoTracking().Where(c => c.IsActive).OrderBy(c => c.SortOrder).ThenBy(c => c.Name).Select(c => new { c.Id, c.Name, kind = c.Kind.ToString(), c.SortOrder }).ToListAsync(ct) })).AllowAnonymous();
        root.MapGet("/catalog", async (AppDbContext db, int? page, int? pageSize, CancellationToken ct) =>
        {
            var (p, size) = ApiResults.Pagination(page, pageSize);
            var query = db.ServiceCatalogItems.AsNoTracking()
                .Where(c => c.IsPublished && db.CategoryDefinitions.Any(cat => cat.Kind == CategoryKind.Service && cat.IsActive && cat.Name == c.Category));
            return Results.Ok(new ApiPage<object>(await query.OrderBy(c => c.Name).Skip((p - 1) * size).Take(size).Select(c => (object)new { c.Id, c.Name, c.Slug, c.Category, c.Description, c.SuggestedDurationMinutes }).ToListAsync(ct), p, size, await query.CountAsync(ct)));
        }).AllowAnonymous();
        root.MapGet("/businesses", async (AppDbContext db, string? q, string? category, string? city, int? page, int? pageSize, CancellationToken ct) =>
        {
            if ((q?.Length ?? 0) > 200 || (city?.Length ?? 0) > 100 || (category?.Length ?? 0) > 100) return ApiResults.Error("invalid_input", "Search terms are too long.");
            var query = db.Businesses.AsNoTracking().Where(b => b.DeletedAt == null && b.Branches.Any(x => x.DeletedAt == null && x.IsActive && x.IsApproved));
            var patterns = TextSearch.LikePatterns(q);
            if (patterns.Length > 0)
            {
                var a = patterns[0]; var b = patterns.Length > 1 ? patterns[1] : a;
                query = query.Where(x => EF.Functions.ILike(x.Name, a) || EF.Functions.ILike(x.Name, b) || EF.Functions.ILike(x.Description, a) || EF.Functions.ILike(x.Description, b) ||
                    x.Services.Any(s => s.DeletedAt == null && (EF.Functions.ILike(s.Name, a) || EF.Functions.ILike(s.Name, b))) ||
                    x.Branches.Any(br => br.DeletedAt == null && br.IsActive && br.IsApproved && br.Resources.Any(r => r.DeletedAt == null && (EF.Functions.ILike(r.Name, a) || EF.Functions.ILike(r.Name, b)))));
            }
            if (!string.IsNullOrWhiteSpace(category)) query = query.Where(b => b.Category == category.Trim());
            if (!string.IsNullOrWhiteSpace(city)) { var pattern = TextSearch.LikePatterns(city)[0]; query = query.Where(b => EF.Functions.ILike(b.City, pattern)); }
            var (p, size) = ApiResults.Pagination(page, pageSize);
            var orderedQuery = patterns.Length > 0
                ? query.OrderBy(b => b.Name).ThenBy(b => b.Id)
                : query.OrderByDescending(b => b.Reviews.Count(r => r.Status == ReviewStatus.Published && r.DeletedAt == null))
                       .ThenByDescending(b => b.CreatedAt);
            return Results.Ok(new ApiPage<BusinessDto>(await orderedQuery.Skip((p - 1) * size).Take(size).Select(BusinessProjection).ToListAsync(ct), p, size, await query.CountAsync(ct)));
        }).AllowAnonymous();
        root.MapGet("/businesses/{businessId:guid}", async (Guid businessId, AppDbContext db, CancellationToken ct) =>
        {
            var business = await db.Businesses.AsNoTracking().Where(b => b.Id == businessId && b.DeletedAt == null).Select(BusinessProjection).FirstOrDefaultAsync(ct);
            if (business is null) return ApiResults.Error("not_found", "Business was not found.", 404);
            var branches = await db.Branches.AsNoTracking().Where(b => b.BusinessId == businessId && b.DeletedAt == null && b.IsActive && b.IsApproved).OrderBy(b => b.Name).Select(b => new BranchDto(b.Id, b.BusinessId, b.Name, b.Address, b.TimeZoneId, b.OpenHour, b.CloseHour, b.AvatarData != null ? $"/media/branch/{b.Id}/avatar" : null, b.HeaderData != null ? $"/media/branch/{b.Id}/header" : null, b.DepositPercentage, b.DepositCardNumber, b.DepositIban, b.DepositAccountHolder, null, b.Latitude, b.Longitude, b.Telegram, b.Instagram, b.WhatsApp, b.Eitaa, b.Bale, b.Rubika, b.LinkedIn, b.Twitter, b.Aparat, b.Website, b.Phone)).ToListAsync(ct);
            var services = await db.Services.AsNoTracking().Where(s => s.BusinessId == businessId && s.DeletedAt == null && s.BranchServices.Any(bs => bs.DeletedAt == null && bs.Branch.DeletedAt == null && bs.Branch.IsActive && bs.Branch.IsApproved)).OrderBy(s => s.Name).Select(s => new ServiceDto(s.Id, s.BusinessId, s.Name, s.Description, s.DurationMinutes, s.Price, s.Currency)).ToListAsync(ct);
            var reviews = await PublicReviews(db, businessId).OrderByDescending(r => r.CreatedAt).Take(20).Select(r => new PublicReviewDto(r.Id, r.Rating, r.Comment, r.ManagerReply, r.Customer.DisplayName, r.CreatedAt)).ToListAsync(ct);
            var gallery = await db.BusinessGalleryItems.AsNoTracking().Where(g => g.BusinessId == businessId).OrderBy(g => g.SortOrder).Select(g => new GalleryItemDto(g.Id, g.Caption, g.SortOrder, $"/media/business/gallery/{g.Id}")).ToListAsync(ct);
            return Results.Ok(new BusinessDetailDto(business.Id, business.Name, business.Slug, business.Category, business.City, business.Description, business.RequiresApproval, business.BranchCount, business.ServiceCount, business.Rating, business.ReviewCount, branches, services, reviews, business.AvatarUrl, business.HeaderUrl, business.DepositPercentage, business.DepositCardNumber, business.DepositIban, business.DepositAccountHolder, gallery, business.Telegram, business.Instagram, business.WhatsApp, business.Eitaa, business.Bale, business.Rubika, business.LinkedIn, business.Twitter, business.Aparat, business.Website));
        }).AllowAnonymous();
        root.MapGet("/businesses/by-slug/{slug}", async (string slug, AppDbContext db, CancellationToken ct) =>
        {
            var normalizedSlug = slug.Trim().ToLowerInvariant();
            var business = await db.Businesses.AsNoTracking().Where(b => b.Slug == normalizedSlug && b.DeletedAt == null).Select(BusinessProjection).FirstOrDefaultAsync(ct);
            if (business is null) return ApiResults.Error("not_found", "Business was not found.", 404);
            var actualId = business.Id;
            var branches = await db.Branches.AsNoTracking().Where(b => b.BusinessId == actualId && b.DeletedAt == null && b.IsActive && b.IsApproved).OrderBy(b => b.Name).Select(b => new BranchDto(b.Id, b.BusinessId, b.Name, b.Address, b.TimeZoneId, b.OpenHour, b.CloseHour, b.AvatarData != null ? $"/media/branch/{b.Id}/avatar" : null, b.HeaderData != null ? $"/media/branch/{b.Id}/header" : null, b.DepositPercentage, b.DepositCardNumber, b.DepositIban, b.DepositAccountHolder, null, b.Latitude, b.Longitude, b.Telegram, b.Instagram, b.WhatsApp, b.Eitaa, b.Bale, b.Rubika, b.LinkedIn, b.Twitter, b.Aparat, b.Website, b.Phone)).ToListAsync(ct);
            var services = await db.Services.AsNoTracking().Where(s => s.BusinessId == actualId && s.DeletedAt == null && s.BranchServices.Any(bs => bs.DeletedAt == null && bs.Branch.DeletedAt == null && bs.Branch.IsActive && bs.Branch.IsApproved)).OrderBy(s => s.Name).Select(s => new ServiceDto(s.Id, s.BusinessId, s.Name, s.Description, s.DurationMinutes, s.Price, s.Currency)).ToListAsync(ct);
            var reviews = await PublicReviews(db, actualId).OrderByDescending(r => r.CreatedAt).Take(20).Select(r => new PublicReviewDto(r.Id, r.Rating, r.Comment, r.ManagerReply, r.Customer.DisplayName, r.CreatedAt)).ToListAsync(ct);
            var gallery = await db.BusinessGalleryItems.AsNoTracking().Where(g => g.BusinessId == actualId).OrderBy(g => g.SortOrder).Select(g => new GalleryItemDto(g.Id, g.Caption, g.SortOrder, $"/media/business/gallery/{g.Id}")).ToListAsync(ct);
            return Results.Ok(new BusinessDetailDto(business.Id, business.Name, business.Slug, business.Category, business.City, business.Description, business.RequiresApproval, business.BranchCount, business.ServiceCount, business.Rating, business.ReviewCount, branches, services, reviews, business.AvatarUrl, business.HeaderUrl, business.DepositPercentage, business.DepositCardNumber, business.DepositIban, business.DepositAccountHolder, gallery, business.Telegram, business.Instagram, business.WhatsApp, business.Eitaa, business.Bale, business.Rubika, business.LinkedIn, business.Twitter, business.Aparat, business.Website));
        }).AllowAnonymous();
        root.MapGet("/businesses/{businessId:guid}/reviews", async (Guid businessId, AppDbContext db, int? page, int? pageSize, CancellationToken ct) =>
        {
            if (!await db.Businesses.AnyAsync(b => b.Id == businessId, ct)) return ApiResults.Error("not_found", "Business was not found.", 404);
            var (p, size) = ApiResults.Pagination(page, pageSize); var query = PublicReviews(db, businessId);
            return Results.Ok(new ApiPage<PublicReviewDto>(await query.OrderByDescending(r => r.CreatedAt).Skip((p - 1) * size).Take(size).Select(r => new PublicReviewDto(r.Id, r.Rating, r.Comment, r.ManagerReply, r.Customer.DisplayName, r.CreatedAt)).ToListAsync(ct), p, size, await query.CountAsync(ct)));
        }).AllowAnonymous();
        root.MapGet("/businesses/{businessId:guid}/branches", async (Guid businessId, AppDbContext db, CancellationToken ct) =>
        {
            if (!await db.Businesses.AnyAsync(b => b.Id == businessId && b.DeletedAt == null, ct)) return ApiResults.Error("not_found", "Business was not found.", 404);
            return Results.Ok(new { items = await db.Branches.AsNoTracking().Where(b => b.BusinessId == businessId && b.DeletedAt == null && b.IsActive && b.IsApproved).OrderBy(b => b.Name).Select(b => new BranchDto(b.Id, b.BusinessId, b.Name, b.Address, b.TimeZoneId, b.OpenHour, b.CloseHour, b.AvatarData != null ? $"/media/branch/{b.Id}/avatar" : null, b.HeaderData != null ? $"/media/branch/{b.Id}/header" : null, b.DepositPercentage, b.DepositCardNumber, b.DepositIban, b.DepositAccountHolder, null, b.Latitude, b.Longitude, b.Telegram, b.Instagram, b.WhatsApp, b.Eitaa, b.Bale, b.Rubika, b.LinkedIn, b.Twitter, b.Aparat, b.Website, b.Phone)).ToListAsync(ct) });
        }).AllowAnonymous();
        root.MapGet("/branches/{branchId:guid}", async (Guid branchId, AppDbContext db, CancellationToken ct) =>
        {
            var branch = await db.Branches.AsNoTracking().Include(b => b.Business).FirstOrDefaultAsync(b => b.Id == branchId && b.DeletedAt == null && b.IsActive && b.IsApproved, ct);
            if (branch is null) return ApiResults.Error("not_found", "Branch was not found.", 404);
            var branchDto = new BranchDto(branch.Id, branch.BusinessId, branch.Name, branch.Address, branch.TimeZoneId, branch.OpenHour, branch.CloseHour, branch.AvatarData != null ? $"/media/branch/{branch.Id}/avatar" : null, branch.HeaderData != null ? $"/media/branch/{branch.Id}/header" : null, branch.DepositPercentage, branch.DepositCardNumber, branch.DepositIban, branch.DepositAccountHolder, null, branch.Latitude, branch.Longitude, branch.Telegram, branch.Instagram, branch.WhatsApp, branch.Eitaa, branch.Bale, branch.Rubika, branch.LinkedIn, branch.Twitter, branch.Aparat, branch.Website, branch.Phone);
            return Results.Ok(new { branch = branchDto, businessId = branch.BusinessId, businessName = branch.Business.Name });
        }).AllowAnonymous();
        root.MapGet("/businesses/{businessId:guid}/branches/{branchId:guid}/services", async (Guid businessId, Guid branchId, AppDbContext db, CancellationToken ct) =>
        {
            if (!await db.Branches.AnyAsync(b => b.Id == branchId && b.BusinessId == businessId && b.DeletedAt == null && b.IsActive && b.IsApproved && b.Business.DeletedAt == null, ct)) return ApiResults.Error("not_found", "Branch was not found.", 404);
            return Results.Ok(new { items = await db.BranchServices.AsNoTracking().Where(bs => bs.BranchId == branchId && bs.Service.BusinessId == businessId && bs.Service.DeletedAt == null).OrderBy(bs => bs.Service.Name).Select(bs => new ServiceDto(bs.Service.Id, businessId, bs.Service.Name, bs.Service.Description, bs.Service.DurationMinutes, bs.Price ?? bs.Service.Price, bs.Service.Currency)).ToListAsync(ct) });
        }).AllowAnonymous();
        root.MapGet("/businesses/{businessId:guid}/branches/{branchId:guid}/providers", async (Guid businessId, Guid branchId, string? serviceIds, AppDbContext db, CancellationToken ct) =>
        {
            if (!await db.Branches.AnyAsync(b => b.Id == branchId && b.BusinessId == businessId && b.DeletedAt == null && b.IsActive && b.IsApproved && b.Business.DeletedAt == null, ct)) return ApiResults.Error("not_found", "Branch was not found.", 404);
            var ids = ApiResults.ServiceIds(serviceIds);
            if (ids is null) return ApiResults.Error("invalid_input", "Select at least one service.");
            if (await db.BranchServices.CountAsync(s => s.BranchId == branchId && ids.Contains(s.ServiceId) && s.Service.BusinessId == businessId && s.Service.DeletedAt == null, ct) != ids.Length) return ApiResults.Error("service_unavailable", "A selected service is unavailable.", 409);
            var query = db.Resources.AsNoTracking().Where(r => r.BranchId == branchId && r.Kind == "staff" && r.UserId != null &&
                db.BranchMemberships.Any(m => m.BranchId == branchId && m.UserId == r.UserId && (m.Role == AppRoles.Owner || m.Role == AppRoles.Manager || m.Role == AppRoles.Staff)));
            foreach (var id in ids) query = query.Where(r => r.ServiceResources.Any(sr => sr.ServiceId == id && sr.DeletedAt == null));
            return Results.Ok(new { items = await query.OrderBy(r => r.Name).Select(r => new ProviderDto(r.Id, r.BranchId, r.Name, r.Kind, r.UserId)).ToListAsync(ct) });
        }).AllowAnonymous();

        root.MapGet("/contact-info", async (AppDbContext db, CancellationToken ct) =>
        {
            var p = await db.GetPlatformPricingAsync(ct);
            return Results.Ok(new PlatformContactInfoDto(
                Phone: p.Phone,
                SupportPhone: p.SupportPhone,
                Mobile: p.Mobile,
                Email: p.Email,
                Address: p.Address,
                WorkingHoursWeekdays: p.WorkingHoursWeekdays,
                WorkingHoursThursdays: p.WorkingHoursThursdays,
                WorkingHoursHolidays: p.WorkingHoursHolidays,
                Telegram: p.Telegram,
                Eitaa: p.Eitaa,
                Instagram: p.Instagram,
                Twitter: p.Twitter,
                Bale: p.Bale,
                Rubika: p.Rubika,
                WhatsApp: p.WhatsApp,
                LinkedIn: p.LinkedIn,
                Aparat: p.Aparat,
                Latitude: p.Latitude,
                Longitude: p.Longitude,
                InfoEmail: p.InfoEmail,
                BankName: p.BankName,
                CardNumber: p.CardNumber,
                Iban: p.Iban,
                AccountHolder: p.AccountHolder
            ));
        }).AllowAnonymous();

        root.MapPost("/contact", async (ContactMessageInput input, HttpContext http, AppDbContext db, CancellationToken ct) =>
        {
            if (string.IsNullOrWhiteSpace(input.Name) || input.Name.Trim().Length < 2)
                return ApiResults.Error("invalid_name", "نام و نام خانوادگی را وارد کنید.");
            if (string.IsNullOrWhiteSpace(input.ContactInfo) || input.ContactInfo.Trim().Length < 5)
                return ApiResults.Error("invalid_contact", "شماره تماس یا ایمیل معتبر وارد کنید.");
            if (string.IsNullOrWhiteSpace(input.Message) || input.Message.Trim().Length < 5)
                return ApiResults.Error("invalid_message", "متن پیام را وارد کنید.");

            Guid? userId = null;
            if (http.User.Identity?.IsAuthenticated == true &&
                Guid.TryParse(http.User.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value, out var uid))
            {
                userId = uid;
            }

            var msg = new ContactMessage
            {
                UserId = userId,
                Name = input.Name.Trim(),
                ContactInfo = input.ContactInfo.Trim(),
                Subject = string.IsNullOrWhiteSpace(input.Subject) ? "پشتیبانی" : input.Subject.Trim(),
                Message = input.Message.Trim(),
                CreatedAt = DateTimeOffset.UtcNow,
                UpdatedAt = DateTimeOffset.UtcNow
            };

            db.ContactMessages.Add(msg);
            await db.SaveChangesAsync(ct);

            return Results.Ok(new { message = "پیام شما با موفقیت ثبت شد و به زودی بررسی خواهد شد.", id = msg.Id });
        }).AllowAnonymous();

        root.MapGet("/app/check-update", async (int? versionCode, AppDbContext db, HttpContext http, CancellationToken ct) =>
        {
            var latestRelease = await db.AppReleases.AsNoTracking()
                .Where(r => r.IsActive)
                .OrderByDescending(r => r.VersionCode)
                .FirstOrDefaultAsync(ct);

            if (latestRelease is null)
            {
                return Results.Ok(new AppUpdateCheckDto(
                    HasUpdate: false,
                    IsForceUpdate: false,
                    VersionCode: 0,
                    VersionName: string.Empty,
                    ReleaseNotes: string.Empty,
                    DownloadUrl: string.Empty,
                    BazaarPackageName: "com.leon.be_nobat",
                    BazaarUrl: "bazaar://details?id=com.leon.be_nobat",
                    FileSize: null
                ));
            }

            var currentCode = versionCode ?? 0;
            var hasUpdate = latestRelease.VersionCode > currentCode;

            // اگر به‌روزرسانی اجباری باشد یا هر نسخه بین نسخه کاربر تا آخرین نسخه اجباری بوده باشد
            var isForce = false;
            if (hasUpdate)
            {
                isForce = await db.AppReleases.AsNoTracking()
                    .Where(r => r.IsActive && r.VersionCode > currentCode && r.IsForceUpdate)
                    .AnyAsync(ct);
            }

            var baseUrl = $"{http.Request.Scheme}://{http.Request.Host}";
            var downloadUrl = !string.IsNullOrWhiteSpace(latestRelease.ExternalDownloadUrl)
                ? latestRelease.ExternalDownloadUrl
                : $"{baseUrl}/api/v1/app/download-latest";

            var pkgName = !string.IsNullOrWhiteSpace(latestRelease.BazaarPackageName)
                ? latestRelease.BazaarPackageName
                : "com.leon.be_nobat";

            return Results.Ok(new AppUpdateCheckDto(
                HasUpdate: hasUpdate,
                IsForceUpdate: isForce,
                VersionCode: latestRelease.VersionCode,
                VersionName: latestRelease.VersionName,
                ReleaseNotes: latestRelease.ReleaseNotes,
                DownloadUrl: downloadUrl,
                BazaarPackageName: pkgName,
                BazaarUrl: $"bazaar://details?id={pkgName}",
                FileSize: latestRelease.ApkFileSize
            ));
        }).AllowAnonymous();

        root.MapGet("/app/download-latest", async (AppDbContext db, CancellationToken ct) =>
        {
            var latestRelease = await db.AppReleases.AsNoTracking()
                .Where(r => r.IsActive && r.ApkData != null)
                .OrderByDescending(r => r.VersionCode)
                .FirstOrDefaultAsync(ct);

            if (latestRelease is null || latestRelease.ApkData is null || latestRelease.ApkData.Length == 0)
            {
                return Results.NotFound(new { error = "فایل نصبی موجود نیست." });
            }

            var fileName = !string.IsNullOrWhiteSpace(latestRelease.ApkFileName)
                ? latestRelease.ApkFileName
                : $"benobat-v{latestRelease.VersionName}.apk";

            return Results.File(
                latestRelease.ApkData,
                contentType: "application/vnd.android.package-archive",
                fileDownloadName: fileName
            );
        }).AllowAnonymous();

        root.MapGet("/app/releases/{id:guid}/download", async (Guid id, AppDbContext db, CancellationToken ct) =>
        {
            var release = await db.AppReleases.AsNoTracking()
                .Where(r => r.Id == id && r.ApkData != null)
                .FirstOrDefaultAsync(ct);

            if (release is null || release.ApkData is null || release.ApkData.Length == 0)
            {
                return Results.NotFound(new { error = "فایل نصبی مورد نظر موجود نیست." });
            }

            var fileName = !string.IsNullOrWhiteSpace(release.ApkFileName)
                ? release.ApkFileName
                : $"benobat-v{release.VersionName}.apk";

            return Results.File(
                release.ApkData,
                contentType: "application/vnd.android.package-archive",
                fileDownloadName: fileName
            );
        }).AllowAnonymous();

        return root;
    }
    private static IQueryable<Review> PublicReviews(AppDbContext db, Guid businessId) => db.Reviews.AsNoTracking().Where(r => r.BusinessId == businessId && r.Status == ReviewStatus.Published && r.Business.DeletedAt == null && (r.BranchId == null || r.Branch!.DeletedAt == null));
}
public sealed record ContactMessageInput(string Name, string ContactInfo, string? Subject, string Message);
public sealed record PlatformContactInfoDto(
    string Phone,
    string SupportPhone,
    string Mobile,
    string Email,
    string Address,
    string WorkingHoursWeekdays,
    string WorkingHoursThursdays,
    string WorkingHoursHolidays,
    string Telegram,
    string Eitaa,
    string Instagram,
    string Twitter = "",
    string Bale = "",
    string Rubika = "",
    string WhatsApp = "",
    string LinkedIn = "",
    string Aparat = "",
    double? Latitude = null,
    double? Longitude = null,
    string? InfoEmail = null,
    string? BankName = null,
    string? CardNumber = null,
    string? Iban = null,
    string? AccountHolder = null
);
public sealed record BusinessDetailDto(
    Guid Id, string Name, string Slug, string Category, string City, string Description, bool RequiresApproval,
    int BranchCount, int ServiceCount, double? Rating, int ReviewCount,
    IReadOnlyList<BranchDto> Branches, IReadOnlyList<ServiceDto> Services, IReadOnlyList<PublicReviewDto> Reviews,
    string? AvatarUrl = null, string? HeaderUrl = null,
    int? DepositPercentage = null, string? DepositCardNumber = null, string? DepositIban = null, string? DepositAccountHolder = null,
    IReadOnlyList<GalleryItemDto>? Gallery = null,
    string? Telegram = null, string? Instagram = null, string? WhatsApp = null, string? Eitaa = null, string? Bale = null,
    string? Rubika = null, string? LinkedIn = null, string? Twitter = null, string? Aparat = null, string? Website = null);
public sealed record AppUpdateCheckDto(
    bool HasUpdate,
    bool IsForceUpdate,
    int VersionCode,
    string VersionName,
    string ReleaseNotes,
    string DownloadUrl,
    string BazaarPackageName,
    string BazaarUrl,
    long? FileSize
);
