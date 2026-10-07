using System.Linq.Expressions;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using BeNobat.Web.Security;
using Microsoft.EntityFrameworkCore;

namespace BeNobat.Web.Api;

public static class PublicEndpoints
{
    public static readonly Expression<Func<Business, BusinessDto>> BusinessProjection = b => new BusinessDto(b.Id, b.Name, b.Slug, b.Category, b.City, b.Description, b.RequiresApproval,
        b.Branches.Count(x => x.DeletedAt == null), b.Services.Count(x => x.DeletedAt == null),
        b.Reviews.Where(r => r.Status == ReviewStatus.Published && r.DeletedAt == null && (r.BranchId == null || r.Branch!.DeletedAt == null)).Average(r => (double?)r.Rating),
        b.Reviews.Count(r => r.Status == ReviewStatus.Published && r.DeletedAt == null && (r.BranchId == null || r.Branch!.DeletedAt == null)));
    public static RouteGroupBuilder MapPublicEndpoints(this RouteGroupBuilder root)
    {
        root.MapGet("/categories", async (AppDbContext db, CancellationToken ct) => Results.Ok(new { items = await db.CategoryDefinitions.AsNoTracking().Where(c => c.IsActive).OrderBy(c => c.SortOrder).ThenBy(c => c.Name).Select(c => new { c.Id, c.Name, kind = c.Kind.ToString(), c.SortOrder }).ToListAsync(ct) })).AllowAnonymous();
        root.MapGet("/catalog", async (AppDbContext db, int? page, int? pageSize, CancellationToken ct) =>
        {
            var (p, size) = ApiResults.Pagination(page, pageSize);
            var query = db.ServiceCatalogItems.AsNoTracking().Where(c => c.IsPublished);
            return Results.Ok(new ApiPage<object>(await query.OrderBy(c => c.Name).Skip((p - 1) * size).Take(size).Select(c => (object)new { c.Id, c.Name, c.Slug, c.Category, c.Description, c.SuggestedDurationMinutes }).ToListAsync(ct), p, size, await query.CountAsync(ct)));
        }).AllowAnonymous();
        root.MapGet("/businesses", async (AppDbContext db, string? q, string? category, string? city, int? page, int? pageSize, CancellationToken ct) =>
        {
            if ((q?.Length ?? 0) > 200 || (city?.Length ?? 0) > 100 || (category?.Length ?? 0) > 100) return ApiResults.Error("invalid_input", "Search terms are too long.");
            var query = db.Businesses.AsNoTracking().Where(b => b.Branches.Any(x => x.DeletedAt == null));
            var patterns = TextSearch.LikePatterns(q);
            if (patterns.Length > 0)
            {
                var a = patterns[0]; var b = patterns.Length > 1 ? patterns[1] : a;
                query = query.Where(x => EF.Functions.ILike(x.Name, a) || EF.Functions.ILike(x.Name, b) || EF.Functions.ILike(x.Description, a) || EF.Functions.ILike(x.Description, b) ||
                    x.Services.Any(s => s.DeletedAt == null && (EF.Functions.ILike(s.Name, a) || EF.Functions.ILike(s.Name, b))) ||
                    x.Branches.Any(br => br.DeletedAt == null && br.Resources.Any(r => r.DeletedAt == null && (EF.Functions.ILike(r.Name, a) || EF.Functions.ILike(r.Name, b)))));
            }
            if (!string.IsNullOrWhiteSpace(category)) query = query.Where(b => b.Category == category.Trim());
            if (!string.IsNullOrWhiteSpace(city)) { var pattern = TextSearch.LikePatterns(city)[0]; query = query.Where(b => EF.Functions.ILike(b.City, pattern)); }
            var (p, size) = ApiResults.Pagination(page, pageSize);
            return Results.Ok(new ApiPage<BusinessDto>(await query.OrderBy(b => b.Name).ThenBy(b => b.Id).Skip((p - 1) * size).Take(size).Select(BusinessProjection).ToListAsync(ct), p, size, await query.CountAsync(ct)));
        }).AllowAnonymous();
        root.MapGet("/businesses/{businessId:guid}", async (Guid businessId, AppDbContext db, CancellationToken ct) =>
        {
            var business = await db.Businesses.AsNoTracking().Where(b => b.Id == businessId).Select(BusinessProjection).FirstOrDefaultAsync(ct);
            if (business is null) return ApiResults.Error("not_found", "Business was not found.", 404);
            var branches = await db.Branches.AsNoTracking().Where(b => b.BusinessId == businessId).OrderBy(b => b.Name).Select(b => new BranchDto(b.Id, b.BusinessId, b.Name, b.Address, b.TimeZoneId, b.OpenHour, b.CloseHour)).ToListAsync(ct);
            var services = await db.Services.AsNoTracking().Where(s => s.BusinessId == businessId && s.BranchServices.Any(bs => bs.DeletedAt == null && bs.Branch.DeletedAt == null)).OrderBy(s => s.Name).Select(s => new ServiceDto(s.Id, s.BusinessId, s.Name, s.Description, s.DurationMinutes, s.Price, s.Currency)).ToListAsync(ct);
            var reviews = await PublicReviews(db, businessId).OrderByDescending(r => r.CreatedAt).Take(20).Select(r => new PublicReviewDto(r.Id, r.Rating, r.Comment, r.ManagerReply, r.Customer.DisplayName, r.CreatedAt)).ToListAsync(ct);
            return Results.Ok(new BusinessDetailDto(business.Id, business.Name, business.Slug, business.Category, business.City, business.Description, business.RequiresApproval, business.BranchCount, business.ServiceCount, business.Rating, business.ReviewCount, branches, services, reviews));
        }).AllowAnonymous();
        root.MapGet("/businesses/{businessId:guid}/reviews", async (Guid businessId, AppDbContext db, int? page, int? pageSize, CancellationToken ct) =>
        {
            if (!await db.Businesses.AnyAsync(b => b.Id == businessId, ct)) return ApiResults.Error("not_found", "Business was not found.", 404);
            var (p, size) = ApiResults.Pagination(page, pageSize); var query = PublicReviews(db, businessId);
            return Results.Ok(new ApiPage<PublicReviewDto>(await query.OrderByDescending(r => r.CreatedAt).Skip((p - 1) * size).Take(size).Select(r => new PublicReviewDto(r.Id, r.Rating, r.Comment, r.ManagerReply, r.Customer.DisplayName, r.CreatedAt)).ToListAsync(ct), p, size, await query.CountAsync(ct)));
        }).AllowAnonymous();
        root.MapGet("/businesses/{businessId:guid}/branches", async (Guid businessId, AppDbContext db, CancellationToken ct) =>
        {
            if (!await db.Businesses.AnyAsync(b => b.Id == businessId, ct)) return ApiResults.Error("not_found", "Business was not found.", 404);
            return Results.Ok(new { items = await db.Branches.AsNoTracking().Where(b => b.BusinessId == businessId).OrderBy(b => b.Name).Select(b => new BranchDto(b.Id, b.BusinessId, b.Name, b.Address, b.TimeZoneId, b.OpenHour, b.CloseHour)).ToListAsync(ct) });
        }).AllowAnonymous();
        root.MapGet("/businesses/{businessId:guid}/branches/{branchId:guid}/services", async (Guid businessId, Guid branchId, AppDbContext db, CancellationToken ct) =>
        {
            if (!await db.Branches.AnyAsync(b => b.Id == branchId && b.BusinessId == businessId && b.Business.DeletedAt == null, ct)) return ApiResults.Error("not_found", "Branch was not found.", 404);
            return Results.Ok(new { items = await db.BranchServices.AsNoTracking().Where(bs => bs.BranchId == branchId && bs.Service.BusinessId == businessId && bs.Service.DeletedAt == null).OrderBy(bs => bs.Service.Name).Select(bs => new ServiceDto(bs.Service.Id, businessId, bs.Service.Name, bs.Service.Description, bs.Service.DurationMinutes, bs.Price ?? bs.Service.Price, bs.Service.Currency)).ToListAsync(ct) });
        }).AllowAnonymous();
        root.MapGet("/businesses/{businessId:guid}/branches/{branchId:guid}/providers", async (Guid businessId, Guid branchId, string? serviceIds, AppDbContext db, CancellationToken ct) =>
        {
            if (!await db.Branches.AnyAsync(b => b.Id == branchId && b.BusinessId == businessId && b.Business.DeletedAt == null, ct)) return ApiResults.Error("not_found", "Branch was not found.", 404);
            var ids = ApiResults.ServiceIds(serviceIds);
            if (ids is null) return ApiResults.Error("invalid_input", "Select at least one service.");
            if (await db.BranchServices.CountAsync(s => s.BranchId == branchId && ids.Contains(s.ServiceId) && s.Service.BusinessId == businessId && s.Service.DeletedAt == null, ct) != ids.Length) return ApiResults.Error("service_unavailable", "A selected service is unavailable.", 409);
            var query = db.Resources.AsNoTracking().Where(r => r.BranchId == branchId && r.Kind == "staff" && r.UserId != null &&
                db.BranchMemberships.Any(m => m.BranchId == branchId && m.UserId == r.UserId && (m.Role == AppRoles.Owner || m.Role == AppRoles.Manager || m.Role == AppRoles.Staff)));
            foreach (var id in ids) query = query.Where(r => r.ServiceResources.Any(sr => sr.ServiceId == id && sr.DeletedAt == null));
            return Results.Ok(new { items = await query.OrderBy(r => r.Name).Select(r => new ProviderDto(r.Id, r.BranchId, r.Name, r.Kind, r.UserId)).ToListAsync(ct) });
        }).AllowAnonymous();
        return root;
    }
    private static IQueryable<Review> PublicReviews(AppDbContext db, Guid businessId) => db.Reviews.AsNoTracking().Where(r => r.BusinessId == businessId && r.Status == ReviewStatus.Published && r.Business.DeletedAt == null && (r.BranchId == null || r.Branch!.DeletedAt == null));
}
public sealed record BusinessDetailDto(Guid Id, string Name, string Slug, string Category, string City, string Description, bool RequiresApproval, int BranchCount, int ServiceCount, double? Rating, int ReviewCount, IReadOnlyList<BranchDto> Branches, IReadOnlyList<ServiceDto> Services, IReadOnlyList<PublicReviewDto> Reviews);
