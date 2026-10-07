using System.Security.Cryptography;
using System.Text.RegularExpressions;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using BeNobat.Web.Security;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;

namespace BeNobat.Web.Api;

/// <summary>Tenant-scoped JSON operations used by mobile business and platform managers.</summary>
public static class ManagementEndpoints
{
    private static readonly string[] TeamRoles = [AppRoles.Owner, AppRoles.Manager, AppRoles.Staff];

    public static RouteGroupBuilder MapManagementEndpoints(this RouteGroupBuilder root)
    {
        var admin = root.MapGroup("/admin").WithTags("Management")
            .RequireAuthorization(policy => policy.AddAuthenticationSchemes("MobileBearer").RequireAuthenticatedUser());
        admin.MapGet("/dashboard", Dashboard);
        admin.MapGet("/context", ManagementContext);
        admin.MapGet("/accounts", LookupAccount);
        admin.MapGet("/businesses", ListBusinesses);
        admin.MapGet("/businesses/{id:guid}", GetBusiness);
        admin.MapPut("/businesses/{id:guid}", UpdateBusiness);
        admin.MapGet("/branches", ListBranches);
        admin.MapGet("/branches/{id:guid}", GetBranch);
        admin.MapPost("/branches", CreateBranch);
        admin.MapPut("/branches/{id:guid}", UpdateBranch);
        admin.MapPut("/branches/{id:guid}/active", SetBranchActive);
        admin.MapDelete("/branches/{id:guid}", DeleteBranch);
        admin.MapGet("/resources", ListResources);
        admin.MapGet("/resources/{id:guid}", GetResource);
        admin.MapPost("/resources", CreateResource);
        admin.MapPut("/resources/{id:guid}", UpdateResource);
        admin.MapPut("/resources/{id:guid}/active", SetResourceActive);
        admin.MapDelete("/resources/{id:guid}", DeleteResource);
        admin.MapGet("/memberships", ListMemberships);
        admin.MapPost("/memberships", CreateMembership);
        admin.MapPut("/memberships/{id:guid}", UpdateMembership);
        admin.MapDelete("/memberships/{id:guid}", DeleteMembership);
        admin.MapGet("/team", ListTeam);
        admin.MapGet("/customers", ListCustomers);
        admin.MapGet("/services", ListServices);
        admin.MapGet("/services/{id:guid}", GetService);
        admin.MapPost("/services", CreateService);
        admin.MapPut("/services/{id:guid}", UpdateService);
        admin.MapPut("/services/{id:guid}/active", SetServiceActive);
        admin.MapDelete("/services/{id:guid}", DeleteService);
        admin.MapGet("/branches/{branchId:guid}/services", ListBranchServices);
        admin.MapPut("/branches/{branchId:guid}/services/{serviceId:guid}", AssignBranchService);
        admin.MapDelete("/branches/{branchId:guid}/services/{serviceId:guid}", RemoveBranchService);
        admin.MapGet("/resources/{resourceId:guid}/services", ListResourceServices);
        admin.MapPut("/resources/{resourceId:guid}/services", AssignResourceServices);
        admin.MapGet("/availability", ListAvailability);
        admin.MapPost("/availability", CreateAvailability);
        admin.MapPut("/availability/{id:guid}", UpdateAvailability);
        admin.MapDelete("/availability/{id:guid}", DeleteAvailability);
        admin.MapGet("/appointments", ListAppointments);
        admin.MapGet("/calendar", ListAppointments);
        admin.MapGet("/appointments/{id:guid}", GetAppointment);
        admin.MapPut("/appointments/{id:guid}/status", ChangeAppointmentStatus);
        admin.MapGet("/reviews", ListReviews);
        admin.MapPut("/reviews/{id:guid}/moderation", ModerateReview);
        admin.MapPut("/reviews/{id:guid}/reply", ReplyToReview);
        admin.MapDelete("/reviews/{id:guid}", DeleteReview);
        admin.MapPut("/reviews/{id:guid}/active", SetReviewActive);
        admin.MapGet("/customer-reviews", ListCustomerReviews);
        admin.MapGet("/customer-reviews/pending", PendingCustomerReviews);
        admin.MapPost("/customer-reviews", CreateCustomerReview);
        admin.MapPut("/customer-reviews/{id:guid}", UpdateCustomerReview);
        admin.MapDelete("/customer-reviews/{id:guid}", DeleteCustomerReview);

        var platform = root.MapGroup("/platform").WithTags("Platform")
            .RequireAuthorization(policy => policy.AddAuthenticationSchemes("MobileBearer")
                .RequireAuthenticatedUser().RequireRole(AppRoles.PlatformAdmin));
        platform.MapGet("/businesses", ListPlatformBusinesses);
        platform.MapGet("/businesses/{id:guid}", GetPlatformBusiness);
        platform.MapPost("/businesses", CreateBusiness);
        platform.MapPut("/businesses/{id:guid}", UpdatePlatformBusiness);
        platform.MapPut("/businesses/{id:guid}/active", SetBusinessActive);
        platform.MapDelete("/businesses/{id:guid}", DeleteBusiness);
        platform.MapGet("/catalog", ListCatalog);
        platform.MapGet("/catalog/{id:guid}", GetCatalog);
        platform.MapPost("/catalog", CreateCatalog);
        platform.MapPut("/catalog/{id:guid}", UpdateCatalog);
        platform.MapPut("/catalog/{id:guid}/active", SetCatalogActive);
        platform.MapDelete("/catalog/{id:guid}", DeleteCatalog);
        platform.MapGet("/categories", ListCategories);
        platform.MapPost("/categories", CreateCategory);
        platform.MapPut("/categories/{id:guid}", UpdateCategory);
        platform.MapPut("/categories/{id:guid}/active", SetCategoryActive);
        platform.MapDelete("/categories/{id:guid}", DeleteCategory);
        platform.MapGet("/users", ListUsers);
        platform.MapGet("/users/{id:guid}", GetUser);
        platform.MapPost("/users", CreateUser);
        platform.MapPut("/users/{id:guid}", UpdateUser);
        platform.MapPut("/users/{id:guid}/roles", SetUserRoles);
        platform.MapPut("/users/{id:guid}/active", SetUserActive);
        platform.MapPost("/users/{id:guid}/reset-password", ResetUserPassword);
        return root;
    }

    private static IResult Missing() => ApiResults.Error("not_found", "The requested record was not found.", StatusCodes.Status404NotFound);
    private static IResult Denied() => ApiResults.Error("forbidden", "You do not have permission for this operation.", StatusCodes.Status403Forbidden);
    private static IResult Invalid(string message) => ApiResults.Error("validation_failed", message);
    private static IResult Conflict(string message) => ApiResults.Error("conflict", message, StatusCodes.Status409Conflict);
    private static bool Text(string? value, int max, bool required = false) =>
        (value?.Trim().Length ?? 0) <= max && (!required || !string.IsNullOrWhiteSpace(value));
    private static string Clean(string? value) => value?.Trim() ?? "";
    private static bool StrictEnum<T>(string? value, out T parsed) where T : struct, Enum =>
        Enum.TryParse(value, true, out parsed) && Enum.IsDefined(parsed) && !int.TryParse(value, out _);

    private static bool ValidGlobalRoles(string[]? roles) => roles is { Length: > 0 and <= 2 } &&
        roles.All(r => r is AppRoles.Customer or AppRoles.PlatformAdmin);

    private static bool HasOtherActivePlatformAdmin(IEnumerable<AppUser> administrators, Guid targetId, DateTimeOffset now) =>
        administrators.Any(u => u.Id != targetId && (!u.LockoutEnabled || u.LockoutEnd == null || u.LockoutEnd <= now));

    private static IQueryable<BranchMembership> OtherOwners(AppDbContext db, Guid branchId, Guid membershipId) =>
        db.BranchMemberships.IgnoreQueryFilters().Where(m => m.DeletedAt == null && m.BranchId == branchId &&
            m.Id != membershipId && m.Role == AppRoles.Owner);

    // Membership, derived Identity roles and last-owner/admin checks must commit as
    // one operation. Serializable snapshots prevent two owners removing each other.
    private static async Task<IResult> Atomic(AppDbContext db, Func<Task<IResult>> operation, CancellationToken ct)
    {
        try
        {
            await using var transaction = await db.Database.BeginTransactionAsync(System.Data.IsolationLevel.Serializable, ct);
            var result = await operation();
            if (result is IStatusCodeHttpResult { StatusCode: >= 400 }) return result;
            await transaction.CommitAsync(ct);
            return result;
        }
        catch (InvalidOperationException ex) { return Conflict(ex.Message); }
        catch (DbUpdateConcurrencyException) { return Conflict("The record changed concurrently. Refresh and try again."); }
        catch (DbUpdateException ex) when (ex.InnerException is Npgsql.PostgresException { SqlState: "40001" or "23505" })
        { return Conflict("The records changed concurrently or the identifier already exists. Refresh and try again."); }
        catch (Npgsql.PostgresException ex) when (ex.SqlState == "40001")
        { return Conflict("The records changed concurrently. Refresh and try again."); }
    }

    private static async Task<IResult> Page<T>(IQueryable<T> query, int page, int pageSize, CancellationToken ct)
    {
        if (page < 1 || page > 100000 || pageSize < 1 || pageSize > 100)
            return Invalid("page must be positive and pageSize must be between 1 and 100.");
        var total = await query.CountAsync(ct);
        var items = await query.Skip((page - 1) * pageSize).Take(pageSize).ToListAsync(ct);
        return Results.Ok(new ApiPage<T>(items, page, pageSize, total));
    }

    private static async Task<IResult> Dashboard(AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        var appointments = scope.Appointments();
        var now = DateTimeOffset.UtcNow;
        return Results.Ok(new
        {
            businesses = await scope.Businesses().CountAsync(ct),
            branches = await scope.Branches().CountAsync(ct),
            appointments = await appointments.CountAsync(ct),
            pending = await appointments.CountAsync(a => a.Status == AppointmentStatus.Pending, ct),
            upcoming = await appointments.CountAsync(a => (a.Status == AppointmentStatus.Pending || a.Status == AppointmentStatus.Confirmed) && a.EndsAt >= now, ct),
            completed = await appointments.CountAsync(a => a.Status == AppointmentStatus.Completed, ct),
            revenue = await appointments.Where(a => a.Status == AppointmentStatus.Completed).SumAsync(a => a.FinalPrice, ct),
            pendingReviews = await scope.Reviews().CountAsync(r => r.DeletedAt == null && r.Status == ReviewStatus.Pending, ct),
        });
    }

    private static async Task<IResult> ManagementContext(AppDbContext db, ApiScope scope, HttpContext http, CancellationToken ct)
    {
        var memberships = await db.BranchMemberships.Where(m => m.UserId == scope.UserId && m.Branch.DeletedAt == null && m.Branch.Business.DeletedAt == null)
            .OrderBy(m => m.Branch.Name).Select(m => new ManagementContextMembership(m.BranchId, m.Branch.BusinessId, m.Branch.Name, m.Role,
                m.Role == AppRoles.Owner || m.Role == AppRoles.Manager, m.Role == AppRoles.Owner || m.Role == AppRoles.Manager || m.Role == AppRoles.Staff)).ToListAsync(ct);
        return Results.Ok(new
        {
            userId = scope.UserId,
            roles = AppRoles.All.Where(http.User.IsInRole).ToArray(),
            isPlatformAdmin = scope.IsPlatform,
            canManageBusiness = scope.IsPlatform || memberships.Any(m => m.CanManageBusiness),
            canManageAppointments = scope.IsPlatform || memberships.Any(m => m.CanManageAppointments),
            memberships,
        });
    }

    private static async Task<IResult> LookupAccount(string email, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        if (!scope.IsPlatform && !await scope.Branches(true).AnyAsync(ct)) return Denied();
        if (!Text(email, 256, true) || !new System.ComponentModel.DataAnnotations.EmailAddressAttribute().IsValid(email)) return Invalid("Provide an exact valid email address.");
        var normalized = Clean(email).ToUpperInvariant();
        var items = await db.Users.Where(u => u.NormalizedEmail == normalized).Select(u => new ManagementAccountDto(u.Id, u.DisplayName, u.Email)).ToListAsync(ct);
        return Results.Ok(new ApiPage<ManagementAccountDto>(items, 1, 1, items.Count));
    }

    private static IQueryable<ManagementBusinessDto> BusinessDtos(IQueryable<Business> query) => query.Select(x =>
        new ManagementBusinessDto(x.Id, x.Name, x.Slug, x.Category, x.City, x.Description, x.RequiresApproval, x.DeletedAt == null));
    private static ManagementBusinessDto BusinessDto(Business x) => new(x.Id, x.Name, x.Slug, x.Category, x.City, x.Description, x.RequiresApproval, x.DeletedAt == null);

    private static Task<IResult> ListBusinesses(ApiScope scope, CancellationToken ct, string? q = null, int page = 1, int pageSize = 25)
    {
        var query = scope.Businesses();
        if (!string.IsNullOrWhiteSpace(q)) query = query.Where(x => x.Name.Contains(q) || x.Slug.Contains(q) || x.City.Contains(q));
        return Page(BusinessDtos(query.OrderBy(x => x.Name).ThenBy(x => x.Id)), page, pageSize, ct);
    }

    private static async Task<IResult> GetBusiness(Guid id, ApiScope scope, CancellationToken ct)
    {
        var item = await BusinessDtos(scope.Businesses().Where(x => x.Id == id)).SingleOrDefaultAsync(ct);
        return item is null ? Missing() : Results.Ok(item);
    }

    private static Task<IResult> ListPlatformBusinesses(AppDbContext db, CancellationToken ct, string? q = null, bool includeInactive = true, int page = 1, int pageSize = 25)
    {
        var query = includeInactive ? db.Businesses.IgnoreQueryFilters() : db.Businesses;
        if (!string.IsNullOrWhiteSpace(q)) query = query.Where(x => x.Name.Contains(q) || x.Slug.Contains(q) || x.City.Contains(q));
        return Page(BusinessDtos(query.OrderBy(x => x.Name).ThenBy(x => x.Id)), page, pageSize, ct);
    }

    private static async Task<IResult> GetPlatformBusiness(Guid id, AppDbContext db, CancellationToken ct)
    {
        var item = await BusinessDtos(db.Businesses.IgnoreQueryFilters().Where(x => x.Id == id)).SingleOrDefaultAsync(ct);
        return item is null ? Missing() : Results.Ok(item);
    }

    private static bool ValidBusiness(ManagementBusinessInput input) => Text(input.Name, 200, true) && Text(input.Slug, 100) &&
        Text(input.Category, 100, true) && Text(input.City, 100, true) && Text(input.Description, 5000) &&
        Text(input.FirstBranchName, 200) && (string.IsNullOrWhiteSpace(input.Slug) || Regex.IsMatch(input.Slug.Trim(), "^[a-z0-9]+(-[a-z0-9]+)*$"));

    private static void ApplyBusiness(Business x, ManagementBusinessInput input, string slug)
    {
        x.Name = Clean(input.Name); x.Slug = slug; x.Category = Clean(input.Category); x.City = Clean(input.City);
        x.Description = Clean(input.Description); x.RequiresApproval = input.RequiresApproval; x.UpdatedAt = DateTimeOffset.UtcNow;
    }

    private static async Task<IResult> CreateBusiness(ManagementBusinessInput input, AppDbContext db, UserManager<AppUser> users, CancellationToken ct)
    {
        return await Atomic(db, async () =>
        {
            if (!ValidBusiness(input)) return Invalid("Provide a name, category, city and a valid lowercase URL slug.");
            var slug = string.IsNullOrWhiteSpace(input.Slug) ? "biz-" + Guid.NewGuid().ToString("N")[..12] : Clean(input.Slug);
            if (await db.Businesses.IgnoreQueryFilters().AnyAsync(x => x.Slug == slug, ct)) return Conflict("The slug is already in use.");
            AppUser? owner = null;
            if (input.OwnerId is Guid ownerId)
            {
                owner = await users.FindByIdAsync(ownerId.ToString());
                if (owner is null || (owner.LockoutEnabled && owner.LockoutEnd > DateTimeOffset.UtcNow)) return Invalid("The owner account does not exist.");
            }
            var business = new Business { Name = Clean(input.Name) };
            ApplyBusiness(business, input, slug);
            var branch = new Branch { Business = business, Name = string.IsNullOrWhiteSpace(input.FirstBranchName) ? "شعبه مرکزی" : Clean(input.FirstBranchName) };
            db.Businesses.Add(business); db.Branches.Add(branch);
            if (owner is not null) db.BranchMemberships.Add(new BranchMembership { Branch = branch, UserId = owner.Id, Role = AppRoles.Owner });
            await db.SaveChangesAsync(ct);
            if (owner is not null) await TeamRoleSync.SyncAsync(users, db, owner);
            return Results.Created($"/api/v1/platform/businesses/{business.Id}", BusinessDto(business));
        }, ct);
    }

    private static async Task<IResult> UpdateBusiness(Guid id, ManagementBusinessInput input, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        if (!await scope.CanManageBusiness(id)) return Missing();
        return await SaveBusiness(id, input, db, false, ct);
    }

    private static Task<IResult> UpdatePlatformBusiness(Guid id, ManagementBusinessInput input, AppDbContext db, CancellationToken ct) => SaveBusiness(id, input, db, true, ct);

    private static async Task<IResult> SaveBusiness(Guid id, ManagementBusinessInput input, AppDbContext db, bool archived, CancellationToken ct)
    {
        if (!ValidBusiness(input)) return Invalid("Provide a name, category, city and a valid lowercase URL slug.");
        var item = await (archived ? db.Businesses.IgnoreQueryFilters() : db.Businesses).SingleOrDefaultAsync(x => x.Id == id, ct);
        if (item is null) return Missing();
        var slug = string.IsNullOrWhiteSpace(input.Slug) ? item.Slug : Clean(input.Slug);
        if (await db.Businesses.IgnoreQueryFilters().AnyAsync(x => x.Slug == slug && x.Id != id, ct)) return Conflict("The slug is already in use.");
        ApplyBusiness(item, input, slug); await db.SaveChangesAsync(ct); return Results.Ok(BusinessDto(item));
    }

    private static async Task<IResult> SetBusinessActive(Guid id, ManagementActiveInput input, AppDbContext db, CancellationToken ct)
    {
        var item = await db.Businesses.IgnoreQueryFilters().SingleOrDefaultAsync(x => x.Id == id, ct);
        if (item is null) return Missing();
        var now = DateTimeOffset.UtcNow;
        if (!input.Active && await db.Appointments.IgnoreQueryFilters().AnyAsync(a => a.DeletedAt == null && a.Branch.BusinessId == id &&
            (a.Status == AppointmentStatus.Pending || a.Status == AppointmentStatus.Confirmed) && a.EndsAt >= now, ct))
            return Conflict("Cancel or complete upcoming appointments before disabling this business.");
        item.DeletedAt = input.Active ? null : now; item.UpdatedAt = now; await db.SaveChangesAsync(ct);
        return Results.Ok(BusinessDto(item));
    }

    private static Task<IResult> DeleteBusiness(Guid id, AppDbContext db, CancellationToken ct) => SetBusinessActive(id, new(false), db, ct);

    // IgnoreQueryFilters is bounded by active parents and current, non-deleted memberships.
    // This permits a manager to reactivate their branch without exposing another tenant.
    private static IQueryable<Branch> StoredBranches(AppDbContext db, ApiScope scope, bool managed = false) =>
        db.Branches.IgnoreQueryFilters().Where(b => b.Business.DeletedAt == null && (scope.IsPlatform ||
            db.BranchMemberships.IgnoreQueryFilters().Any(m => m.DeletedAt == null && m.BranchId == b.Id && m.UserId == scope.UserId &&
                ((m.Role == AppRoles.Owner || m.Role == AppRoles.Manager) || (!managed && m.Role == AppRoles.Staff)))));

    private static IQueryable<ManagementBranchDto> BranchDtos(IQueryable<Branch> query) => query.Select(x =>
        new ManagementBranchDto(x.Id, x.BusinessId, x.Name, x.Address, x.TimeZoneId, x.OpenHour, x.CloseHour, x.DeletedAt == null));
    private static ManagementBranchDto BranchDto(Branch x) => new(x.Id, x.BusinessId, x.Name, x.Address, x.TimeZoneId, x.OpenHour, x.CloseHour, x.DeletedAt == null);

    private static Task<IResult> ListBranches(AppDbContext db, ApiScope scope, CancellationToken ct, Guid? businessId = null, string? q = null, bool includeInactive = false, int page = 1, int pageSize = 25)
    {
        var query = includeInactive ? StoredBranches(db, scope) : scope.Branches();
        if (businessId is Guid business) query = query.Where(x => x.BusinessId == business);
        if (!string.IsNullOrWhiteSpace(q)) query = query.Where(x => x.Name.Contains(q) || x.Address.Contains(q));
        return Page(BranchDtos(query.OrderBy(x => x.Name).ThenBy(x => x.Id)), page, pageSize, ct);
    }

    private static async Task<IResult> GetBranch(Guid id, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        var item = await BranchDtos(StoredBranches(db, scope).Where(x => x.Id == id)).SingleOrDefaultAsync(ct);
        return item is null ? Missing() : Results.Ok(item);
    }

    private static bool ValidBranch(ManagementBranchInput input)
    {
        if (!Text(input.Name, 200, true) || !Text(input.Address, 1000) || !Text(input.TimeZoneId, 100, true) ||
            input.OpenHour is < 0 or > 23 || input.CloseHour is < 1 or > 24 || input.CloseHour <= input.OpenHour) return false;
        try { _ = TimeZoneInfo.FindSystemTimeZoneById(input.TimeZoneId); return true; }
        catch (TimeZoneNotFoundException) { return false; }
        catch (InvalidTimeZoneException) { return false; }
    }

    private static void ApplyBranch(Branch item, ManagementBranchInput input)
    {
        item.Name = Clean(input.Name); item.Address = Clean(input.Address); item.TimeZoneId = input.TimeZoneId;
        item.OpenHour = input.OpenHour; item.CloseHour = input.CloseHour; item.UpdatedAt = DateTimeOffset.UtcNow;
    }

    private static async Task<IResult> CreateBranch(ManagementBranchInput input, AppDbContext db, ApiScope scope, UserManager<AppUser> users, CancellationToken ct)
    {
        return await Atomic(db, async () =>
        {
            if (!ValidBranch(input)) return Invalid("Provide a name, valid time zone and opening hours between 0 and 24.");
            if (!await scope.CanOwnBusiness(input.BusinessId)) return Missing();
            var item = new Branch { BusinessId = input.BusinessId, Name = Clean(input.Name) }; ApplyBranch(item, input); db.Branches.Add(item);
            if (!scope.IsPlatform) db.BranchMemberships.Add(new BranchMembership { Branch = item, UserId = scope.UserId, Role = AppRoles.Owner });
            await db.SaveChangesAsync(ct);
            if (!scope.IsPlatform && await users.FindByIdAsync(scope.UserId.ToString()) is { } user) await TeamRoleSync.SyncAsync(users, db, user);
            return Results.Created($"/api/v1/admin/branches/{item.Id}", BranchDto(item));
        }, ct);
    }

    private static async Task<IResult> UpdateBranch(Guid id, ManagementBranchInput input, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        if (!ValidBranch(input)) return Invalid("Provide a name, valid time zone and opening hours between 0 and 24.");
        var item = await StoredBranches(db, scope, true).SingleOrDefaultAsync(x => x.Id == id, ct);
        if (item is null) return Missing();
        if (item.BusinessId != input.BusinessId) return Invalid("A branch cannot be moved to another business.");
        ApplyBranch(item, input); await db.SaveChangesAsync(ct); return Results.Ok(BranchDto(item));
    }

    private static async Task<IResult> SetBranchActive(Guid id, ManagementActiveInput input, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        var item = await StoredBranches(db, scope, true).SingleOrDefaultAsync(x => x.Id == id, ct);
        if (item is null) return Missing();
        var now = DateTimeOffset.UtcNow;
        if (!input.Active && await db.Appointments.IgnoreQueryFilters().AnyAsync(a => a.DeletedAt == null && a.BranchId == id &&
            (a.Status == AppointmentStatus.Pending || a.Status == AppointmentStatus.Confirmed) && a.EndsAt >= now, ct))
            return Conflict("Cancel or complete upcoming appointments before disabling this branch.");
        item.DeletedAt = input.Active ? null : now; item.UpdatedAt = now; await db.SaveChangesAsync(ct); return Results.Ok(BranchDto(item));
    }
    private static Task<IResult> DeleteBranch(Guid id, AppDbContext db, ApiScope scope, CancellationToken ct) => SetBranchActive(id, new(false), db, scope, ct);

    private static IQueryable<Resource> ScopedResources(AppDbContext db, ApiScope scope, bool managed = false, bool includeInactive = false) =>
        (includeInactive ? db.Resources.IgnoreQueryFilters() : db.Resources).Where(x => x.Branch.DeletedAt == null && x.Branch.Business.DeletedAt == null && scope.Branches(managed).Any(b => b.Id == x.BranchId));
    private static IQueryable<ManagementResourceDto> ResourceDtos(IQueryable<Resource> query) => query.Select(x =>
        new ManagementResourceDto(x.Id, x.BranchId, x.Name, x.Kind, x.UserId, x.DeletedAt == null));
    private static ManagementResourceDto ResourceDto(Resource x) => new(x.Id, x.BranchId, x.Name, x.Kind, x.UserId, x.DeletedAt == null);

    private static Task<IResult> ListResources(AppDbContext db, ApiScope scope, CancellationToken ct, Guid? branchId = null, Guid? businessId = null, bool includeInactive = false, int page = 1, int pageSize = 25)
    {
        var query = ScopedResources(db, scope, includeInactive: includeInactive);
        if (branchId is Guid branch) query = query.Where(x => x.BranchId == branch);
        if (businessId is Guid business) query = query.Where(x => x.Branch.BusinessId == business);
        return Page(ResourceDtos(query.OrderBy(x => x.Name).ThenBy(x => x.Id)), page, pageSize, ct);
    }

    private static async Task<IResult> GetResource(Guid id, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        var item = await ResourceDtos(ScopedResources(db, scope).Where(x => x.Id == id)).SingleOrDefaultAsync(ct);
        return item is null ? Missing() : Results.Ok(item);
    }

    private static async Task<IResult?> ValidateResource(ManagementResourceInput input, Guid? id, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        if (!Text(input.Name, 200, true) || input.Kind is not ("staff" or "room" or "equipment") ||
            (input.Kind == "staff" && input.UserId is null) || (input.Kind != "staff" && input.UserId is not null))
            return Invalid("Provide a name and kind (staff, room or equipment); staff must reference a branch member.");
        if (!await scope.CanManageBranch(input.BranchId)) return Missing();
        if (input.UserId is Guid userId)
        {
            if (!await db.BranchMemberships.AnyAsync(m => m.BranchId == input.BranchId && m.UserId == userId &&
                (m.Role == AppRoles.Owner || m.Role == AppRoles.Manager || m.Role == AppRoles.Staff) &&
                (!m.User.LockoutEnabled || m.User.LockoutEnd == null || m.User.LockoutEnd <= DateTimeOffset.UtcNow), ct))
                return Invalid("The staff account must be a member of the same branch.");
            if (await db.Resources.AnyAsync(r => r.Id != id && r.BranchId == input.BranchId && r.UserId == userId, ct))
                return Conflict("The member already has a resource in this branch.");
        }
        return null;
    }

    private static async Task<IResult> CreateResource(ManagementResourceInput input, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        if (await ValidateResource(input, null, db, scope, ct) is { } error) return error;
        var item = new Resource { BranchId = input.BranchId, Name = Clean(input.Name), Kind = input.Kind, UserId = input.UserId };
        db.Resources.Add(item); await db.SaveChangesAsync(ct);
        return Results.Created($"/api/v1/admin/resources/{item.Id}", ResourceDto(item));
    }

    private static async Task<IResult> UpdateResource(Guid id, ManagementResourceInput input, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        var item = await ScopedResources(db, scope, true).SingleOrDefaultAsync(x => x.Id == id, ct);
        if (item is null) return Missing();
        if (input.BranchId != item.BranchId) return Invalid("A resource cannot be moved to another branch.");
        if (await ValidateResource(input, id, db, scope, ct) is { } error) return error;
        var now = DateTimeOffset.UtcNow;
        if ((item.UserId != input.UserId || item.Kind != input.Kind) && await db.Appointments.AnyAsync(a => a.ResourceId == id &&
            (a.Status == AppointmentStatus.Pending || a.Status == AppointmentStatus.Confirmed) && a.EndsAt >= now, ct))
            return Conflict("Cancel or complete this resource's upcoming appointments before changing its account or kind.");
        item.Name = Clean(input.Name); item.Kind = input.Kind; item.UserId = input.UserId; item.UpdatedAt = now;
        await db.SaveChangesAsync(ct); return Results.Ok(ResourceDto(item));
    }

    private static async Task<IResult> DeleteResource(Guid id, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        var item = await ScopedResources(db, scope, true).SingleOrDefaultAsync(x => x.Id == id, ct);
        if (item is null) return Missing();
        var now = DateTimeOffset.UtcNow;
        if (await db.Appointments.AnyAsync(a => a.ResourceId == id &&
            (a.Status == AppointmentStatus.Pending || a.Status == AppointmentStatus.Confirmed) && a.EndsAt >= now, ct))
            return Conflict("Cancel or complete this resource's upcoming appointments before deleting it.");
        item.DeletedAt = now; item.UpdatedAt = now; await db.SaveChangesAsync(ct); return Results.NoContent();
    }

    private static async Task<IResult> SetResourceActive(Guid id, ManagementActiveInput input, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        if (!input.Active) return await DeleteResource(id, db, scope, ct);
        var item = await db.Resources.IgnoreQueryFilters().Where(r => r.Id == id && r.Branch.DeletedAt == null && r.Branch.Business.DeletedAt == null &&
            scope.Branches(true).Any(b => b.Id == r.BranchId)).SingleOrDefaultAsync(ct);
        if (item is null) return Missing();
        if (item.Kind == "staff" && (item.UserId is null || !await db.BranchMemberships.AnyAsync(m => m.BranchId == item.BranchId && m.UserId == item.UserId &&
            (m.Role == AppRoles.Owner || m.Role == AppRoles.Manager || m.Role == AppRoles.Staff) &&
            (!m.User.LockoutEnabled || m.User.LockoutEnd == null || m.User.LockoutEnd <= DateTimeOffset.UtcNow), ct)))
            return Conflict("Restore the staff account's branch membership first.");
        if (item.UserId is Guid user && await db.Resources.AnyAsync(r => r.Id != id && r.BranchId == item.BranchId && r.UserId == user, ct))
            return Conflict("The member already has an active resource in this branch.");
        item.DeletedAt = null; item.UpdatedAt = DateTimeOffset.UtcNow; await db.SaveChangesAsync(ct); return Results.Ok(ResourceDto(item));
    }

    private static IQueryable<BranchMembership> ScopedMemberships(AppDbContext db, ApiScope scope) =>
        db.BranchMemberships.Where(m => scope.Branches(true).Any(b => b.Id == m.BranchId));
    private static IQueryable<ManagementMembershipDto> MembershipDtos(IQueryable<BranchMembership> query) => query.Select(x =>
        new ManagementMembershipDto(x.Id, x.BranchId, x.UserId, x.Role, x.User.DisplayName, x.User.Email));

    private static Task<IResult> ListMemberships(AppDbContext db, ApiScope scope, CancellationToken ct, Guid? branchId = null, Guid? userId = null, int page = 1, int pageSize = 25)
    {
        var query = ScopedMemberships(db, scope);
        if (branchId is Guid branch) query = query.Where(x => x.BranchId == branch);
        if (userId is Guid user) query = query.Where(x => x.UserId == user);
        return Page(MembershipDtos(query.OrderBy(x => x.User.DisplayName).ThenBy(x => x.Id)), page, pageSize, ct);
    }

    private static async Task<IResult> CreateMembership(ManagementMembershipInput input, AppDbContext db, ApiScope scope, UserManager<AppUser> users, CancellationToken ct)
    {
        return await Atomic(db, async () =>
        {
            if (!TeamRoles.Contains(input.Role)) return Invalid("Membership role must be Owner, Manager or Staff.");
            if (!await scope.CanManageBranch(input.BranchId) || !await scope.CanAssignRole(input.Role, input.BranchId)) return Denied();
            var user = await users.FindByIdAsync(input.UserId.ToString());
            if (user is null || (user.LockoutEnabled && user.LockoutEnd > DateTimeOffset.UtcNow)) return Invalid("The active account does not exist.");
            var item = await db.BranchMemberships.IgnoreQueryFilters().SingleOrDefaultAsync(m => m.BranchId == input.BranchId && m.UserId == input.UserId, ct);
            if (item is { DeletedAt: null }) return Conflict("The account is already a member of this branch.");
            if (item is null) { item = new BranchMembership { BranchId = input.BranchId, UserId = input.UserId }; db.BranchMemberships.Add(item); }
            item.Role = input.Role; item.DeletedAt = null; item.UpdatedAt = DateTimeOffset.UtcNow;
            await db.SaveChangesAsync(ct); await TeamRoleSync.SyncAsync(users, db, user);
            return Results.Created($"/api/v1/admin/memberships/{item.Id}", new ManagementMembershipDto(item.Id, item.BranchId, item.UserId, item.Role, user.DisplayName, user.Email));
        }, ct);
    }

    private static async Task<IResult> UpdateMembership(Guid id, ManagementMembershipInput input, AppDbContext db, ApiScope scope, UserManager<AppUser> users, CancellationToken ct)
    {
        return await Atomic(db, async () =>
        {
            if (!TeamRoles.Contains(input.Role)) return Invalid("Membership role must be Owner, Manager or Staff.");
            var item = await ScopedMemberships(db, scope).SingleOrDefaultAsync(m => m.Id == id, ct);
            if (item is null) return Missing();
            if (item.BranchId != input.BranchId || item.UserId != input.UserId) return Invalid("Membership branch and account cannot be changed.");
            if (!await scope.CanAssignRole(input.Role, item.BranchId) ||
                (item.Role == AppRoles.Owner && !await scope.CanAssignRole(AppRoles.Owner, item.BranchId))) return Denied();
            if (item.Role == AppRoles.Owner && input.Role != AppRoles.Owner &&
                !await OtherOwners(db, item.BranchId, id).AnyAsync(ct))
                return Conflict("A branch's last owner cannot be demoted.");
            item.Role = input.Role; item.UpdatedAt = DateTimeOffset.UtcNow; await db.SaveChangesAsync(ct);
            var user = await users.FindByIdAsync(item.UserId.ToString());
            if (user is not null) await TeamRoleSync.SyncAsync(users, db, user);
            return Results.Ok(new ManagementMembershipDto(item.Id, item.BranchId, item.UserId, item.Role, user?.DisplayName ?? "", user?.Email));
        }, ct);
    }

    private static async Task<IResult> DeleteMembership(Guid id, AppDbContext db, ApiScope scope, UserManager<AppUser> users, CancellationToken ct)
    {
        return await Atomic(db, async () =>
        {
            var item = await ScopedMemberships(db, scope).SingleOrDefaultAsync(m => m.Id == id, ct);
            if (item is null) return Missing();
            if (item.Role == AppRoles.Owner && !await scope.CanAssignRole(AppRoles.Owner, item.BranchId)) return Denied();
            if (item.Role == AppRoles.Owner && !await OtherOwners(db, item.BranchId, id).AnyAsync(ct))
                return Conflict("A branch's last owner cannot be removed.");
            var resources = await db.Resources.IgnoreQueryFilters().Where(r => r.DeletedAt == null && r.BranchId == item.BranchId && r.UserId == item.UserId).ToListAsync(ct);
            var resourceIds = resources.Select(r => r.Id).ToArray(); var now = DateTimeOffset.UtcNow;
            if (await db.Appointments.AnyAsync(a => a.ResourceId != null && resourceIds.Contains(a.ResourceId.Value) &&
                (a.Status == AppointmentStatus.Pending || a.Status == AppointmentStatus.Confirmed) && a.EndsAt >= now, ct))
                return Conflict("Cancel or complete the member's upcoming appointments before removing them.");
            foreach (var resource in resources) { resource.DeletedAt = now; resource.UpdatedAt = now; }
            item.DeletedAt = now; item.UpdatedAt = now; await db.SaveChangesAsync(ct);
            if (await users.FindByIdAsync(item.UserId.ToString()) is { } user) await TeamRoleSync.SyncAsync(users, db, user);
            return Results.NoContent();
        }, ct);
    }

    private static Task<IResult> ListTeam(AppDbContext db, ApiScope scope, CancellationToken ct, string? q = null, Guid? branchId = null, int page = 1, int pageSize = 25)
    {
        var memberships = ScopedMemberships(db, scope);
        if (branchId is Guid branch) memberships = memberships.Where(m => m.BranchId == branch);
        var query = db.Users.Where(u => memberships.Any(m => m.UserId == u.Id));
        if (!string.IsNullOrWhiteSpace(q)) query = query.Where(u => u.DisplayName.Contains(q) || (u.Email != null && u.Email.Contains(q)) || (u.PhoneNumber != null && u.PhoneNumber.Contains(q)));
        var now = DateTimeOffset.UtcNow;
        return Page(query.OrderBy(u => u.DisplayName).ThenBy(u => u.Id).Select(u => new ManagementPersonDto(u.Id, u.DisplayName, u.Email, u.PhoneNumber, !u.LockoutEnabled || u.LockoutEnd == null || u.LockoutEnd <= now)), page, pageSize, ct);
    }

    private static Task<IResult> ListCustomers(AppDbContext db, ApiScope scope, CancellationToken ct, string? q = null, Guid? branchId = null, int page = 1, int pageSize = 25)
    {
        var appointments = scope.Appointments();
        if (branchId is Guid branch) appointments = appointments.Where(a => a.BranchId == branch);
        var query = db.Users.Where(u => appointments.Any(a => a.CustomerId == u.Id));
        if (!string.IsNullOrWhiteSpace(q)) query = query.Where(u => u.DisplayName.Contains(q) || (u.Email != null && u.Email.Contains(q)) || (u.PhoneNumber != null && u.PhoneNumber.Contains(q)));
        var now = DateTimeOffset.UtcNow;
        return Page(query.OrderBy(u => u.DisplayName).ThenBy(u => u.Id).Select(u => new ManagementPersonDto(u.Id, u.DisplayName, u.Email, u.PhoneNumber, !u.LockoutEnabled || u.LockoutEnd == null || u.LockoutEnd <= now)), page, pageSize, ct);
    }

    private static IQueryable<Service> ScopedServices(AppDbContext db, ApiScope scope, bool managed = false, bool includeInactive = false) =>
        (includeInactive ? db.Services.IgnoreQueryFilters() : db.Services).Where(s => s.Business.DeletedAt == null && scope.Businesses(managed).Any(b => b.Id == s.BusinessId));
    private static IQueryable<ManagementServiceDto> ServiceDtos(IQueryable<Service> query, ApiScope scope) => query.Select(s =>
        new ManagementServiceDto(s.Id, s.BusinessId, s.CatalogItemId, s.Name, s.Description, s.DurationMinutes, s.Price, s.Currency,
            s.BranchServices.Where(bs => bs.DeletedAt == null && scope.Branches().Any(b => b.Id == bs.BranchId)).Select(bs => bs.BranchId).ToArray(),
            s.ServiceResources.Where(sr => sr.DeletedAt == null && scope.Branches().Any(b => b.Id == sr.Resource.BranchId) && sr.Resource.DeletedAt == null).Select(sr => sr.ResourceId).ToArray(), s.DeletedAt == null));

    private static Task<IResult> ListServices(AppDbContext db, ApiScope scope, CancellationToken ct, Guid? businessId = null, Guid? branchId = null, string? q = null, bool includeInactive = false, int page = 1, int pageSize = 25)
    {
        var query = ScopedServices(db, scope, includeInactive: includeInactive);
        if (businessId is Guid business) query = query.Where(s => s.BusinessId == business);
        if (branchId is Guid branch) query = query.Where(s => s.BranchServices.Any(bs => bs.BranchId == branch && scope.Branches().Any(b => b.Id == branch)));
        if (!string.IsNullOrWhiteSpace(q)) query = query.Where(s => s.Name.Contains(q) || s.Description.Contains(q));
        return Page(ServiceDtos(query.OrderBy(s => s.Name).ThenBy(s => s.Id), scope), page, pageSize, ct);
    }

    private static async Task<IResult> GetService(Guid id, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        var item = await ServiceDtos(ScopedServices(db, scope).Where(s => s.Id == id), scope).SingleOrDefaultAsync(ct);
        return item is null ? Missing() : Results.Ok(item);
    }

    private static async Task<IResult?> ValidateService(ManagementServiceInput input, Guid? id, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        if (!Text(input.Name, 200, true) || !Text(input.Description, 5000) || input.DurationMinutes is < 5 or > 1440 ||
            input.Price is < 0 or > 9999999999999999.99m || decimal.Round(input.Price, 2) != input.Price || !Regex.IsMatch(input.Currency ?? "", "^[A-Z]{3}$"))
            return Invalid("Provide a name, duration from 5 to 1440, nonnegative price and a three-letter currency.");
        if (!await scope.CanManageBusiness(input.BusinessId)) return Missing();
        if (input.CatalogItemId is Guid catalog)
        {
            if (!await db.ServiceCatalogItems.AnyAsync(c => c.Id == catalog && c.IsPublished, ct) &&
                !await db.Services.IgnoreQueryFilters().AnyAsync(s => s.Id == id && s.CatalogItemId == catalog, ct)) return Invalid("The catalog service is not available.");
            if (await db.Services.IgnoreQueryFilters().AnyAsync(s => s.BusinessId == input.BusinessId && s.CatalogItemId == catalog && s.Id != id, ct))
                return Conflict("This catalog service already belongs to the business.");
        }
        if (input.ResourceIds is { Length: > 100 }) return Invalid("At most 100 resources can be assigned.");
        if (input.BranchId is Guid branch)
        {
            if (!await scope.CanManageBranch(branch) || !await db.Branches.AnyAsync(b => b.Id == branch && b.BusinessId == input.BusinessId, ct)) return Missing();
            var ids = (input.ResourceIds ?? []).Distinct().ToArray();
            if (await db.Resources.CountAsync(r => ids.Contains(r.Id) && r.BranchId == branch && r.Kind == "staff", ct) != ids.Length)
                return Invalid("Every assigned resource must be active staff in the selected branch.");
        }
        else if (input.ResourceIds is { Length: > 0 }) return Invalid("Select a branch when assigning resources.");
        return null;
    }

    private static void ApplyService(Service item, ManagementServiceInput input)
    {
        item.Name = Clean(input.Name); item.Description = Clean(input.Description); item.CatalogItemId = input.CatalogItemId;
        item.DurationMinutes = input.DurationMinutes; item.Price = input.Price; item.Currency = input.Currency; item.UpdatedAt = DateTimeOffset.UtcNow;
    }

    private static async Task SetServiceAssignments(Service item, ManagementServiceInput input, AppDbContext db, CancellationToken ct)
    {
        if (input.BranchId is not Guid branch) return;
        var assignment = await db.BranchServices.IgnoreQueryFilters().SingleOrDefaultAsync(bs => bs.BranchId == branch && bs.ServiceId == item.Id, ct);
        if (assignment is null) { assignment = new BranchService { BranchId = branch, Service = item }; db.BranchServices.Add(assignment); }
        assignment.DeletedAt = null; assignment.Price = null; assignment.UpdatedAt = DateTimeOffset.UtcNow;
        if (input.ResourceIds is null) return;
        var resources = await db.Resources.Where(r => r.BranchId == branch).Select(r => r.Id).ToArrayAsync(ct);
        var existing = await db.ServiceResources.IgnoreQueryFilters().Where(sr => sr.ServiceId == item.Id && resources.Contains(sr.ResourceId)).ToListAsync(ct);
        var selected = input.ResourceIds.Distinct().ToHashSet();
        foreach (var link in existing) { link.DeletedAt = selected.Contains(link.ResourceId) ? null : DateTimeOffset.UtcNow; link.UpdatedAt = DateTimeOffset.UtcNow; }
        var allIds = existing.Select(sr => sr.ResourceId).ToHashSet();
        db.ServiceResources.AddRange(selected.Where(id => !allIds.Contains(id)).Select(id => new ServiceResource { Service = item, ResourceId = id }));
    }

    private static async Task<IResult> CreateService(ManagementServiceInput input, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        if (await ValidateService(input, null, db, scope, ct) is { } error) return error;
        var item = new Service { BusinessId = input.BusinessId, Name = Clean(input.Name) }; ApplyService(item, input); db.Services.Add(item);
        await SetServiceAssignments(item, input, db, ct); await db.SaveChangesAsync(ct);
        return Results.Created($"/api/v1/admin/services/{item.Id}", await ServiceDtos(ScopedServices(db, scope).Where(s => s.Id == item.Id), scope).SingleAsync(ct));
    }

    private static async Task<IResult> UpdateService(Guid id, ManagementServiceInput input, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        if (!await scope.CanManageService(id, ct)) return Missing();
        var item = await ScopedServices(db, scope, true).SingleOrDefaultAsync(s => s.Id == id, ct);
        if (item is null) return Missing();
        if (input.BusinessId != item.BusinessId) return Invalid("A service cannot be moved to another business.");
        if (await ValidateService(input, id, db, scope, ct) is { } error) return error;
        if (input.BranchId is Guid branch && item.Price != input.Price)
        {
            foreach (var assignment in await db.BranchServices.Where(bs => bs.ServiceId == id && bs.BranchId != branch && bs.Price == null).ToListAsync(ct))
                assignment.Price = item.Price;
        }
        ApplyService(item, input); await SetServiceAssignments(item, input, db, ct); await db.SaveChangesAsync(ct);
        return await GetService(id, db, scope, ct);
    }

    private static async Task<IResult> DeleteService(Guid id, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        if (!await scope.CanManageService(id, ct)) return Missing();
        var item = await ScopedServices(db, scope, true).SingleOrDefaultAsync(s => s.Id == id, ct);
        if (item is null) return Missing();
        var now = DateTimeOffset.UtcNow;
        if (await db.Appointments.IgnoreQueryFilters().AnyAsync(a => a.DeletedAt == null && (a.ServiceId == id || a.Services.Any(s => s.ServiceId == id)) &&
            (a.Status == AppointmentStatus.Pending || a.Status == AppointmentStatus.Confirmed) && a.EndsAt >= now, ct))
            return Conflict("Cancel or complete upcoming appointments before deleting this service.");
        item.DeletedAt = now; item.UpdatedAt = now; await db.SaveChangesAsync(ct); return Results.NoContent();
    }

    private static async Task<IResult> SetServiceActive(Guid id, ManagementActiveInput input, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        if (!input.Active) return await DeleteService(id, db, scope, ct);
        if (!await scope.CanManageService(id, ct)) return Missing();
        var item = await ScopedServices(db, scope, true, true).SingleOrDefaultAsync(s => s.Id == id, ct);
        if (item is null) return Missing();
        item.DeletedAt = null; item.UpdatedAt = DateTimeOffset.UtcNow; await db.SaveChangesAsync(ct); return await GetService(id, db, scope, ct);
    }

    private static async Task<IResult> ListBranchServices(Guid branchId, AppDbContext db, ApiScope scope, CancellationToken ct, int page = 1, int pageSize = 25)
    {
        if (!await scope.Branches().AnyAsync(b => b.Id == branchId, ct)) return Missing();
        return await Page(db.BranchServices.Where(bs => bs.BranchId == branchId && bs.Service.DeletedAt == null && bs.Service.Business.DeletedAt == null)
            .OrderBy(bs => bs.Service.Name).ThenBy(bs => bs.Id).Select(bs => new ManagementBranchServiceDto(bs.Id, bs.BranchId, bs.ServiceId, bs.Price, bs.Price ?? bs.Service.Price, bs.Service.Name)), page, pageSize, ct);
    }

    private static async Task<IResult> AssignBranchService(Guid branchId, Guid serviceId, ManagementBranchServiceInput input, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        if (input.Price is < 0 or > 9999999999999999.99m || (input.Price is decimal price && decimal.Round(price, 2) != price)) return Invalid("The branch price must be nonnegative and have at most two decimal places.");
        if (!await scope.CanManageBranch(branchId)) return Missing();
        if (!await db.Services.AnyAsync(s => s.Id == serviceId && db.Branches.Any(b => b.Id == branchId && b.BusinessId == s.BusinessId), ct))
            return Invalid("The active service and branch must belong to the same business.");
        var item = await db.BranchServices.IgnoreQueryFilters().SingleOrDefaultAsync(bs => bs.BranchId == branchId && bs.ServiceId == serviceId, ct);
        if (item is null) { item = new BranchService { BranchId = branchId, ServiceId = serviceId }; db.BranchServices.Add(item); }
        item.DeletedAt = null; item.Price = input.Price; item.UpdatedAt = DateTimeOffset.UtcNow; await db.SaveChangesAsync(ct);
        var service = await db.Services.SingleAsync(s => s.Id == serviceId, ct);
        return Results.Ok(new ManagementBranchServiceDto(item.Id, branchId, serviceId, item.Price, item.Price ?? service.Price, service.Name));
    }

    private static async Task<IResult> RemoveBranchService(Guid branchId, Guid serviceId, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        if (!await scope.CanManageBranch(branchId)) return Missing();
        var item = await db.BranchServices.SingleOrDefaultAsync(bs => bs.BranchId == branchId && bs.ServiceId == serviceId, ct);
        if (item is null) return Missing();
        var now = DateTimeOffset.UtcNow;
        if (await db.Appointments.AnyAsync(a => a.BranchId == branchId && (a.ServiceId == serviceId || a.Services.Any(s => s.ServiceId == serviceId)) &&
            (a.Status == AppointmentStatus.Pending || a.Status == AppointmentStatus.Confirmed) && a.EndsAt >= now, ct))
            return Conflict("Cancel or complete upcoming appointments before removing this assignment.");
        item.DeletedAt = now; item.UpdatedAt = now; await db.SaveChangesAsync(ct); return Results.NoContent();
    }

    private static async Task<IResult> ListResourceServices(Guid resourceId, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        var resource = await ScopedResources(db, scope).SingleOrDefaultAsync(r => r.Id == resourceId, ct);
        if (resource is null) return Missing();
        var serviceIds = await db.ServiceResources.Where(sr => sr.ResourceId == resourceId && sr.Service.DeletedAt == null &&
            sr.Service.BranchServices.Any(bs => bs.BranchId == resource.BranchId)).OrderBy(sr => sr.ServiceId).Select(sr => sr.ServiceId).ToArrayAsync(ct);
        return Results.Ok(new ManagementResourceServicesInput(serviceIds));
    }

    private static async Task<IResult> AssignResourceServices(Guid resourceId, ManagementResourceServicesInput input, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        var resource = await ScopedResources(db, scope, true).Include(r => r.Branch).SingleOrDefaultAsync(r => r.Id == resourceId, ct);
        if (resource is null) return Missing();
        if (resource.Kind != "staff" || input.ServiceIds is null || input.ServiceIds.Length > 100) return Invalid("Select up to 100 services for an active staff resource.");
        var selected = input.ServiceIds.Distinct().ToHashSet();
        if (await db.Services.CountAsync(s => selected.Contains(s.Id) && s.BusinessId == resource.Branch.BusinessId &&
            s.BranchServices.Any(bs => bs.BranchId == resource.BranchId), ct) != selected.Count)
            return Invalid("Every service must be active and assigned to the resource's branch.");
        var existing = await db.ServiceResources.IgnoreQueryFilters().Where(sr => sr.ResourceId == resourceId).ToListAsync(ct);
        foreach (var link in existing) { link.DeletedAt = selected.Contains(link.ServiceId) ? null : DateTimeOffset.UtcNow; link.UpdatedAt = DateTimeOffset.UtcNow; }
        var allIds = existing.Select(sr => sr.ServiceId).ToHashSet();
        db.ServiceResources.AddRange(selected.Where(id => !allIds.Contains(id)).Select(id => new ServiceResource { ResourceId = resourceId, ServiceId = id }));
        await db.SaveChangesAsync(ct); return Results.Ok(new ManagementResourceServicesInput(selected.Order().ToArray()));
    }

    private static IQueryable<AvailabilityRule> ScopedAvailability(AppDbContext db, ApiScope scope) => db.AvailabilityRules.Where(r =>
        scope.Businesses().Any(b => b.Id == r.BusinessId) &&
        (r.BranchId == null || scope.Branches().Any(b => b.Id == r.BranchId)) &&
        (r.ResourceId == null || db.Resources.Any(resource => resource.Id == r.ResourceId && resource.BranchId == r.BranchId)));
    private static IQueryable<ManagementAvailabilityDto> AvailabilityDtos(IQueryable<AvailabilityRule> query) => query.Select(r =>
        new ManagementAvailabilityDto(r.Id, r.BusinessId, r.BranchId, r.ResourceId, r.EffectiveDate, r.DayOfWeek.ToString(), r.StartsAt, r.EndsAt, r.IsAvailable));

    private static Task<IResult> ListAvailability(AppDbContext db, ApiScope scope, CancellationToken ct, Guid? businessId = null, Guid? branchId = null,
        Guid? resourceId = null, DateOnly? from = null, DateOnly? to = null, int page = 1, int pageSize = 25)
    {
        var query = ScopedAvailability(db, scope);
        if (businessId is Guid business) query = query.Where(r => r.BusinessId == business);
        // Business-wide intervals also govern the selected branch. The branch
        // must itself be accessible, even when the actor can read its business
        // through a different branch membership. Mutation checks stay separate.
        if (branchId is Guid branch) query = query.Where(r => (r.BranchId == null || r.BranchId == branch) &&
            scope.Branches().Any(b => b.Id == branch && b.BusinessId == r.BusinessId));
        if (resourceId is Guid resource) query = query.Where(r => r.ResourceId == resource);
        if (from is DateOnly start) query = query.Where(r => r.EffectiveDate == null || r.EffectiveDate >= start);
        if (to is DateOnly end) query = query.Where(r => r.EffectiveDate == null || r.EffectiveDate <= end);
        return Page(AvailabilityDtos(query.OrderBy(r => r.EffectiveDate).ThenBy(r => r.StartsAt).ThenBy(r => r.Id)), page, pageSize, ct);
    }

    private static async Task<IResult?> ValidateAvailability(ManagementAvailabilityInput input, Guid? id, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        if (input.EndsAt <= input.StartsAt) return Invalid("The availability end must be after the start.");
        if (!await scope.CanManageBusiness(input.BusinessId)) return Missing();
        if (input.BranchId is Guid branch)
        {
            if (!await scope.CanManageBranch(branch) || !await db.Branches.AnyAsync(b => b.Id == branch && b.BusinessId == input.BusinessId, ct)) return Missing();
        }
        else if (!scope.IsPlatform || input.ResourceId is not null) return Denied();
        if (input.ResourceId is Guid resource && !await db.Resources.AnyAsync(r => r.Id == resource && r.BranchId == input.BranchId && r.Kind == "staff", ct))
            return Invalid("The staff resource must belong to the selected active branch.");
        var zoneId = input.BranchId is Guid branchId ? await db.Branches.Where(b => b.Id == branchId).Select(b => b.TimeZoneId).SingleAsync(ct) : "Asia/Tehran";
        var localNow = TimeZoneInfo.ConvertTime(DateTimeOffset.UtcNow, BranchClock.Zone(zoneId));
        var today = DateOnly.FromDateTime(localNow.DateTime);
        if (input.EffectiveDate < today || (input.EffectiveDate == today && input.StartsAt <= TimeOnly.FromDateTime(localNow.DateTime)))
            return Invalid("Availability must start in the future in the branch's time zone.");
        if (await db.AvailabilityRules.AnyAsync(r => r.Id != id && r.BusinessId == input.BusinessId && r.BranchId == input.BranchId && r.ResourceId == input.ResourceId &&
            r.EffectiveDate == input.EffectiveDate && r.IsAvailable == input.IsAvailable && r.StartsAt < input.EndsAt && input.StartsAt < r.EndsAt, ct))
            return Conflict("An availability interval of the same type already overlaps this date and scope.");
        return null;
    }

    private static async Task<bool> CanEditAvailability(AvailabilityRule rule, ApiScope scope) =>
        rule.BranchId is Guid branch ? await scope.CanManageBranch(branch) : scope.IsPlatform && await scope.CanManageBusiness(rule.BusinessId);

    private static void ApplyAvailability(AvailabilityRule item, ManagementAvailabilityInput input)
    {
        item.BusinessId = input.BusinessId; item.BranchId = input.BranchId; item.ResourceId = input.ResourceId;
        item.EffectiveDate = input.EffectiveDate; item.DayOfWeek = input.EffectiveDate.DayOfWeek; item.StartsAt = input.StartsAt;
        item.EndsAt = input.EndsAt; item.IsAvailable = input.IsAvailable; item.UpdatedAt = DateTimeOffset.UtcNow;
    }

    private static ManagementAvailabilityDto AvailabilityDto(AvailabilityRule r) => new(r.Id, r.BusinessId, r.BranchId, r.ResourceId, r.EffectiveDate, r.DayOfWeek.ToString(), r.StartsAt, r.EndsAt, r.IsAvailable);

    private static async Task<IResult> CreateAvailability(ManagementAvailabilityInput input, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        if (await ValidateAvailability(input, null, db, scope, ct) is { } error) return error;
        var item = new AvailabilityRule(); ApplyAvailability(item, input); db.AvailabilityRules.Add(item); await db.SaveChangesAsync(ct);
        return Results.Created($"/api/v1/admin/availability/{item.Id}", AvailabilityDto(item));
    }

    private static async Task<IResult> UpdateAvailability(Guid id, ManagementAvailabilityInput input, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        var item = await ScopedAvailability(db, scope).SingleOrDefaultAsync(r => r.Id == id, ct);
        if (item is null) return Missing();
        if (!await CanEditAvailability(item, scope)) return Denied();
        if (input.BusinessId != item.BusinessId || input.BranchId != item.BranchId || input.ResourceId != item.ResourceId)
            return Invalid("An availability interval cannot be moved to another scope.");
        if (await ValidateAvailability(input, id, db, scope, ct) is { } error) return error;
        ApplyAvailability(item, input); await db.SaveChangesAsync(ct); return Results.Ok(AvailabilityDto(item));
    }

    private static async Task<IResult> DeleteAvailability(Guid id, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        var item = await ScopedAvailability(db, scope).Include(r => r.Branch).SingleOrDefaultAsync(r => r.Id == id, ct);
        if (item is null) return Missing();
        if (!await CanEditAvailability(item, scope)) return Denied();
        var today = BranchClock.Today(item.Branch?.TimeZoneId);
        if (item.EffectiveDate is DateOnly date && date < today) return Conflict("Past availability intervals cannot be deleted.");
        item.DeletedAt = DateTimeOffset.UtcNow; item.UpdatedAt = DateTimeOffset.UtcNow; await db.SaveChangesAsync(ct); return Results.NoContent();
    }

    // Scope predicates remain explicit, while archived service/resource names are retained for appointment history.
    private static IQueryable<ManagementAppointmentDto> AppointmentDtos(IQueryable<Appointment> query) => query.IgnoreQueryFilters().Select(a =>
        new ManagementAppointmentDto(a.Id, a.BranchId, a.Branch.BusinessId, a.ServiceId, a.ResourceId, a.CustomerId, a.StartsAt, a.EndsAt,
            a.Status.ToString(), a.FinalPrice, a.Currency, a.CustomerNote, a.Branch.Name, a.Service.Name, a.Resource == null ? null : a.Resource.Name,
            a.Customer.DisplayName, a.Customer.Email, a.Customer.PhoneNumber, a.Branch.Business.Name, a.Branch.TimeZoneId,
            a.Services.Where(s => s.DeletedAt == null).OrderBy(s => s.Id).Select(s => s.Service.Name).ToArray(), a.TrackingCode));

    private static async Task<IResult> ListAppointments(ApiScope scope, CancellationToken ct, Guid? businessId = null, Guid? branchId = null, Guid? resourceId = null,
        Guid? customerId = null, DateTimeOffset? from = null, DateTimeOffset? to = null, DateOnly? date = null, string? status = null, int page = 1, int pageSize = 25)
    {
        var query = scope.Appointments();
        if (businessId is Guid business) query = query.Where(a => a.Branch.BusinessId == business);
        if (branchId is Guid branch) query = query.Where(a => a.BranchId == branch);
        if (resourceId is Guid resource) query = query.Where(a => a.ResourceId == resource);
        if (customerId is Guid customer) query = query.Where(a => a.CustomerId == customer);
        if (from is DateTimeOffset start) query = query.Where(a => a.EndsAt > start);
        if (to is DateTimeOffset end) query = query.Where(a => a.StartsAt < end);
        if (from is not null && to is not null && from >= to) return Invalid("from must be before to.");
        if (date is DateOnly day)
        {
            if (day.Year is < 1900 or > 9998) return Invalid("The calendar date is out of range.");
            var branches = scope.Branches();
            if (businessId is Guid dateBusiness) branches = branches.Where(b => b.BusinessId == dateBusiness);
            if (branchId is Guid dateBranch) branches = branches.Where(b => b.Id == dateBranch);
            var zones = await branches.Select(b => new { b.Id, b.TimeZoneId }).ToListAsync(ct);
            var parameter = System.Linq.Expressions.Expression.Parameter(typeof(Appointment), "appointment");
            System.Linq.Expressions.Expression predicate = System.Linq.Expressions.Expression.Constant(false);
            foreach (var zone in zones)
            {
                var (windowStart, windowEnd) = BookingPolicy.UtcDayWindow(day, BranchClock.Zone(zone.TimeZoneId));
                var inBranch = System.Linq.Expressions.Expression.Equal(System.Linq.Expressions.Expression.Property(parameter, nameof(Appointment.BranchId)), System.Linq.Expressions.Expression.Constant(zone.Id));
                var overlapsStart = System.Linq.Expressions.Expression.GreaterThan(System.Linq.Expressions.Expression.Property(parameter, nameof(Appointment.EndsAt)), System.Linq.Expressions.Expression.Constant(windowStart));
                var overlapsEnd = System.Linq.Expressions.Expression.LessThan(System.Linq.Expressions.Expression.Property(parameter, nameof(Appointment.StartsAt)), System.Linq.Expressions.Expression.Constant(windowEnd));
                predicate = System.Linq.Expressions.Expression.OrElse(predicate, System.Linq.Expressions.Expression.AndAlso(inBranch,
                    System.Linq.Expressions.Expression.AndAlso(overlapsStart, overlapsEnd)));
            }
            query = query.Where(System.Linq.Expressions.Expression.Lambda<Func<Appointment, bool>>(predicate, parameter));
        }
        if (status is not null)
        {
            if (!StrictEnum<AppointmentStatus>(status, out var parsed)) return Invalid("The appointment status is invalid.");
            query = query.Where(a => a.Status == parsed);
        }
        return await Page(AppointmentDtos(query.OrderBy(a => a.StartsAt).ThenBy(a => a.Id)), page, pageSize, ct);
    }

    private static async Task<IResult> GetAppointment(Guid id, ApiScope scope, CancellationToken ct)
    {
        var item = await AppointmentDtos(scope.Appointments().Where(a => a.Id == id)).SingleOrDefaultAsync(ct);
        return item is null ? Missing() : Results.Ok(item);
    }

    private static async Task<IResult> ChangeAppointmentStatus(Guid id, ManagementStatusInput input, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        if (!StrictEnum<AppointmentStatus>(input.Status, out var status)) return Invalid("The appointment status is invalid.");
        var item = await scope.Appointments().SingleOrDefaultAsync(a => a.Id == id, ct);
        if (item is null) return Missing();
        var now = DateTimeOffset.UtcNow;
        if (!BookingPolicy.CanTransition(item.Status, status, item.StartsAt, now)) return Conflict("This appointment status transition is not allowed.");
        var previous = item.Status;
        var changed = await scope.Appointments().Where(a => a.Id == id && a.Status == previous).ExecuteUpdateAsync(setters =>
            setters.SetProperty(a => a.Status, status).SetProperty(a => a.UpdatedAt, now), ct);
        if (changed != 1) return Conflict("The appointment changed while you were updating it. Refresh and try again.");
        await db.Entry(item).ReloadAsync(ct);
        return await GetAppointment(id, scope, ct);
    }

    private static IQueryable<ManagementReviewDto> ReviewDtos(IQueryable<Review> query) => query.Select(r =>
        new ManagementReviewDto(r.Id, r.BusinessId, r.BranchId, r.CustomerId, r.AppointmentId, r.Rating, r.Comment, r.ManagerReply,
            r.Status.ToString(), r.Customer.DisplayName, r.DeletedAt == null, r.CreatedAt));

    private static Task<IResult> ListReviews(ApiScope scope, CancellationToken ct, Guid? businessId = null, Guid? branchId = null, Guid? customerId = null,
        string? status = null, int? rating = null, bool includeInactive = false, int page = 1, int pageSize = 25)
    {
        var query = scope.Reviews();
        if (!includeInactive) query = query.Where(r => r.DeletedAt == null);
        if (businessId is Guid business) query = query.Where(r => r.BusinessId == business);
        if (branchId is Guid branch) query = query.Where(r => r.BranchId == branch);
        if (customerId is Guid customer) query = query.Where(r => r.CustomerId == customer);
        if (rating is int score)
        {
            if (score is < 1 or > 5) return Task.FromResult(Invalid("Rating must be between 1 and 5."));
            query = query.Where(r => r.Rating == score);
        }
        if (status is not null)
        {
            if (!StrictEnum<ReviewStatus>(status, out var parsed)) return Task.FromResult(Invalid("The review status is invalid."));
            query = query.Where(r => r.Status == parsed);
        }
        return Page(ReviewDtos(query.OrderByDescending(r => r.CreatedAt).ThenBy(r => r.Id)), page, pageSize, ct);
    }

    private static async Task<IResult> ModerateReview(Guid id, ManagementModerationInput input, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        if (!StrictEnum<ReviewStatus>(input.Status, out var status) || !Text(input.ManagerReply, 2000)) return Invalid("Provide a valid review status and a reply of at most 2000 characters.");
        var item = await scope.Reviews().SingleOrDefaultAsync(r => r.Id == id, ct);
        if (item is null) return Missing();
        item.Status = status;
        if (input.ManagerReply is not null) item.ManagerReply = Clean(input.ManagerReply);
        item.UpdatedAt = DateTimeOffset.UtcNow; await db.SaveChangesAsync(ct);
        return Results.Ok(await ReviewDtos(scope.Reviews().Where(r => r.Id == id)).SingleAsync(ct));
    }

    private static async Task<IResult> ReplyToReview(Guid id, ManagementReplyInput input, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        if (!Text(input.ManagerReply, 2000)) return Invalid("A reply must have at most 2000 characters.");
        var item = await scope.Reviews().SingleOrDefaultAsync(r => r.Id == id, ct);
        if (item is null) return Missing();
        item.ManagerReply = Clean(input.ManagerReply); item.UpdatedAt = DateTimeOffset.UtcNow; await db.SaveChangesAsync(ct);
        return Results.Ok(await ReviewDtos(scope.Reviews().Where(r => r.Id == id)).SingleAsync(ct));
    }

    private static async Task<IResult> SetReviewActive(Guid id, ManagementActiveInput input, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        var item = await scope.Reviews().SingleOrDefaultAsync(r => r.Id == id, ct);
        if (item is null) return Missing();
        item.DeletedAt = input.Active ? null : DateTimeOffset.UtcNow; item.UpdatedAt = DateTimeOffset.UtcNow; await db.SaveChangesAsync(ct);
        return Results.Ok(await ReviewDtos(scope.Reviews().Where(r => r.Id == id)).SingleAsync(ct));
    }
    private static async Task<IResult> DeleteReview(Guid id, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        var item = await scope.Reviews().SingleOrDefaultAsync(r => r.Id == id, ct);
        if (item is null) return Missing();
        item.DeletedAt = DateTimeOffset.UtcNow; item.UpdatedAt = DateTimeOffset.UtcNow; await db.SaveChangesAsync(ct); return Results.NoContent();
    }

    private static IQueryable<ManagementCustomerReviewDto> CustomerReviewDtos(IQueryable<CustomerReview> query) => query.Select(r =>
        new ManagementCustomerReviewDto(r.Id, r.AppointmentId, r.BusinessId, r.Appointment.BranchId, r.CustomerId, r.AuthorId, r.Rating, r.Comment, r.Customer.DisplayName, r.Author.DisplayName, r.CreatedAt));

    private static Task<IResult> ListCustomerReviews(ApiScope scope, CancellationToken ct, Guid? businessId = null, Guid? branchId = null, Guid? customerId = null, int page = 1, int pageSize = 25)
    {
        var query = scope.CustomerReviews();
        if (businessId is Guid business) query = query.Where(r => r.BusinessId == business);
        if (branchId is Guid branch) query = query.Where(r => r.Appointment.BranchId == branch);
        if (customerId is Guid customer) query = query.Where(r => r.CustomerId == customer);
        return Page(CustomerReviewDtos(query.OrderByDescending(r => r.CreatedAt).ThenBy(r => r.Id)), page, pageSize, ct);
    }

    private static Task<IResult> PendingCustomerReviews(AppDbContext db, ApiScope scope, CancellationToken ct, int page = 1, int pageSize = 25)
    {
        var query = scope.Appointments().Where(a => a.Status == AppointmentStatus.Completed && scope.Branches(true).Any(b => b.Id == a.BranchId) &&
            !db.CustomerReviews.IgnoreQueryFilters().Any(r => r.AppointmentId == a.Id));
        return Page(AppointmentDtos(query.OrderByDescending(a => a.StartsAt).ThenBy(a => a.Id)), page, pageSize, ct);
    }

    private static bool ValidEvaluation(ManagementCustomerReviewInput input) => input.Rating is >= 1 and <= 5 && Text(input.Comment, 2000);

    private static async Task<IResult> CreateCustomerReview(ManagementCustomerReviewInput input, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        if (!ValidEvaluation(input)) return Invalid("Rating must be 1 to 5 and the comment at most 2000 characters.");
        var appointment = await scope.Appointments().Include(a => a.Branch).SingleOrDefaultAsync(a => a.Id == input.AppointmentId && a.Status == AppointmentStatus.Completed, ct);
        if (appointment is null || !await scope.CanManageBranch(appointment.BranchId)) return Missing();
        if (await db.CustomerReviews.IgnoreQueryFilters().AnyAsync(r => r.AppointmentId == appointment.Id, ct)) return Conflict("This appointment already has a customer evaluation.");
        var item = new CustomerReview { AppointmentId = appointment.Id, BusinessId = appointment.Branch.BusinessId, CustomerId = appointment.CustomerId,
            AuthorId = scope.UserId, Rating = input.Rating, Comment = Clean(input.Comment) };
        db.CustomerReviews.Add(item); await db.SaveChangesAsync(ct);
        return Results.Created($"/api/v1/admin/customer-reviews/{item.Id}", await CustomerReviewDtos(scope.CustomerReviews().Where(r => r.Id == item.Id)).SingleAsync(ct));
    }

    private static async Task<IResult> UpdateCustomerReview(Guid id, ManagementCustomerReviewInput input, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        if (!ValidEvaluation(input)) return Invalid("Rating must be 1 to 5 and the comment at most 2000 characters.");
        var item = await scope.CustomerReviews().SingleOrDefaultAsync(r => r.Id == id, ct);
        if (item is null) return Missing();
        if (item.AppointmentId != input.AppointmentId) return Invalid("An evaluation cannot be moved to another appointment.");
        item.Rating = input.Rating; item.Comment = Clean(input.Comment); item.UpdatedAt = DateTimeOffset.UtcNow; await db.SaveChangesAsync(ct);
        return Results.Ok(await CustomerReviewDtos(scope.CustomerReviews().Where(r => r.Id == id)).SingleAsync(ct));
    }

    private static async Task<IResult> DeleteCustomerReview(Guid id, AppDbContext db, ApiScope scope, CancellationToken ct)
    {
        var item = await scope.CustomerReviews().SingleOrDefaultAsync(r => r.Id == id, ct);
        if (item is null) return Missing();
        db.CustomerReviews.Remove(item); await db.SaveChangesAsync(ct); return Results.NoContent();
    }

    private static IQueryable<ManagementCatalogDto> CatalogDtos(IQueryable<ServiceCatalogItem> query) => query.Select(c =>
        new ManagementCatalogDto(c.Id, c.Name, c.Slug, c.Category, c.Description, c.SuggestedDurationMinutes, c.IsPublished, c.IsPublished && c.DeletedAt == null));
    private static ManagementCatalogDto CatalogDto(ServiceCatalogItem c) => new(c.Id, c.Name, c.Slug, c.Category, c.Description, c.SuggestedDurationMinutes, c.IsPublished, c.IsPublished && c.DeletedAt == null);

    private static Task<IResult> ListCatalog(AppDbContext db, CancellationToken ct, string? q = null, string? category = null, bool includeInactive = true, int page = 1, int pageSize = 25)
    {
        var query = includeInactive ? db.ServiceCatalogItems.IgnoreQueryFilters() : db.ServiceCatalogItems;
        if (!string.IsNullOrWhiteSpace(q)) query = query.Where(c => c.Name.Contains(q) || c.Slug.Contains(q));
        if (!string.IsNullOrWhiteSpace(category)) query = query.Where(c => c.Category == category);
        return Page(CatalogDtos(query.OrderBy(c => c.Category).ThenBy(c => c.Name).ThenBy(c => c.Id)), page, pageSize, ct);
    }

    private static async Task<IResult> GetCatalog(Guid id, AppDbContext db, CancellationToken ct)
    {
        var item = await CatalogDtos(db.ServiceCatalogItems.IgnoreQueryFilters().Where(c => c.Id == id)).SingleOrDefaultAsync(ct);
        return item is null ? Missing() : Results.Ok(item);
    }

    private static bool ValidCatalog(ManagementCatalogInput input) => Text(input.Name, 200, true) && Text(input.Slug, 100, true) &&
        Regex.IsMatch(input.Slug ?? "", "^[a-z0-9]+(-[a-z0-9]+)*$") && Text(input.Category, 100, true) && Text(input.Description, 5000) && input.SuggestedDurationMinutes is >= 5 and <= 1440;

    private static void ApplyCatalog(ServiceCatalogItem item, ManagementCatalogInput input)
    {
        item.Name = Clean(input.Name); item.Slug = Clean(input.Slug); item.Category = Clean(input.Category); item.Description = Clean(input.Description);
        item.SuggestedDurationMinutes = input.SuggestedDurationMinutes; item.IsPublished = input.IsPublished; item.UpdatedAt = DateTimeOffset.UtcNow;
    }

    private static async Task<IResult> CreateCatalog(ManagementCatalogInput input, AppDbContext db, CancellationToken ct)
    {
        if (!ValidCatalog(input)) return Invalid("Provide name, lowercase URL slug, category and a duration from 5 to 1440 minutes.");
        if (await db.ServiceCatalogItems.IgnoreQueryFilters().AnyAsync(c => c.Slug == Clean(input.Slug), ct)) return Conflict("The catalog slug is already in use.");
        var item = new ServiceCatalogItem { Name = Clean(input.Name) }; ApplyCatalog(item, input); db.ServiceCatalogItems.Add(item); await db.SaveChangesAsync(ct);
        return Results.Created($"/api/v1/platform/catalog/{item.Id}", CatalogDto(item));
    }

    private static async Task<IResult> UpdateCatalog(Guid id, ManagementCatalogInput input, AppDbContext db, CancellationToken ct)
    {
        if (!ValidCatalog(input)) return Invalid("Provide name, lowercase URL slug, category and a duration from 5 to 1440 minutes.");
        var item = await db.ServiceCatalogItems.IgnoreQueryFilters().SingleOrDefaultAsync(c => c.Id == id, ct);
        if (item is null) return Missing();
        if (await db.ServiceCatalogItems.IgnoreQueryFilters().AnyAsync(c => c.Id != id && c.Slug == Clean(input.Slug), ct)) return Conflict("The catalog slug is already in use.");
        ApplyCatalog(item, input); await db.SaveChangesAsync(ct); return Results.Ok(CatalogDto(item));
    }

    private static async Task<IResult> SetCatalogActive(Guid id, ManagementActiveInput input, AppDbContext db, CancellationToken ct)
    {
        var item = await db.ServiceCatalogItems.IgnoreQueryFilters().SingleOrDefaultAsync(c => c.Id == id, ct);
        if (item is null) return Missing();
        item.DeletedAt = null; item.IsPublished = input.Active; item.UpdatedAt = DateTimeOffset.UtcNow; await db.SaveChangesAsync(ct); return Results.Ok(CatalogDto(item));
    }
    private static Task<IResult> DeleteCatalog(Guid id, AppDbContext db, CancellationToken ct) => SetCatalogActive(id, new(false), db, ct);

    private static IQueryable<ManagementCategoryDto> CategoryDtos(IQueryable<CategoryDefinition> query) => query.Select(c =>
        new ManagementCategoryDto(c.Id, c.Name, c.Kind.ToString(), c.SortOrder, c.IsActive && c.DeletedAt == null));

    private static Task<IResult> ListCategories(AppDbContext db, CancellationToken ct, string? kind = null, bool includeInactive = true, int page = 1, int pageSize = 25)
    {
        var query = includeInactive ? db.CategoryDefinitions.IgnoreQueryFilters() : db.CategoryDefinitions.Where(c => c.IsActive);
        if (kind is not null)
        {
            if (!StrictEnum<CategoryKind>(kind, out var parsed)) return Task.FromResult(Invalid("The category kind must be Business or Service."));
            query = query.Where(c => c.Kind == parsed);
        }
        return Page(CategoryDtos(query.OrderBy(c => c.Kind).ThenBy(c => c.SortOrder).ThenBy(c => c.Name).ThenBy(c => c.Id)), page, pageSize, ct);
    }

    private static async Task<IResult> CreateCategory(ManagementCategoryInput input, AppDbContext db, CancellationToken ct)
    {
        if (!Text(input.Name, 100, true) || !StrictEnum<CategoryKind>(input.Kind, out var kind) || input.SortOrder is < 0 or > 100000)
            return Invalid("Provide a category name, Business or Service kind, and a nonnegative sort order.");
        if (await db.CategoryDefinitions.IgnoreQueryFilters().AnyAsync(c => c.Kind == kind && c.Name == Clean(input.Name), ct)) return Conflict("This category already exists for the selected kind.");
        var item = new CategoryDefinition { Name = Clean(input.Name), Kind = kind, SortOrder = input.SortOrder, IsActive = input.IsActive };
        db.CategoryDefinitions.Add(item); await db.SaveChangesAsync(ct);
        return Results.Created($"/api/v1/platform/categories/{item.Id}", new ManagementCategoryDto(item.Id, item.Name, item.Kind.ToString(), item.SortOrder, item.IsActive));
    }

    private static async Task<IResult> UpdateCategory(Guid id, ManagementCategoryInput input, AppDbContext db, CancellationToken ct)
    {
        if (!Text(input.Name, 100, true) || !StrictEnum<CategoryKind>(input.Kind, out var kind) || input.SortOrder is < 0 or > 100000)
            return Invalid("Provide a category name, Business or Service kind, and a nonnegative sort order.");
        var item = await db.CategoryDefinitions.IgnoreQueryFilters().SingleOrDefaultAsync(c => c.Id == id, ct);
        if (item is null) return Missing();
        if (kind != item.Kind) return Invalid("The kind of an existing category cannot be changed.");
        var name = Clean(input.Name);
        if (await db.CategoryDefinitions.IgnoreQueryFilters().AnyAsync(c => c.Id != id && c.Kind == kind && c.Name == name, ct)) return Conflict("This category already exists for the selected kind.");
        var oldName = item.Name;
        await using var transaction = await db.Database.BeginTransactionAsync(ct);
        if (oldName != name)
        {
            if (kind == CategoryKind.Business) await db.Businesses.IgnoreQueryFilters().Where(b => b.Category == oldName).ExecuteUpdateAsync(s => s.SetProperty(b => b.Category, name), ct);
            else await db.ServiceCatalogItems.IgnoreQueryFilters().Where(c => c.Category == oldName).ExecuteUpdateAsync(s => s.SetProperty(c => c.Category, name), ct);
        }
        item.Name = name; item.SortOrder = input.SortOrder; item.IsActive = input.IsActive; item.UpdatedAt = DateTimeOffset.UtcNow;
        await db.SaveChangesAsync(ct); await transaction.CommitAsync(ct);
        return Results.Ok(new ManagementCategoryDto(item.Id, item.Name, item.Kind.ToString(), item.SortOrder, item.IsActive && item.DeletedAt == null));
    }

    private static async Task<IResult> SetCategoryActive(Guid id, ManagementActiveInput input, AppDbContext db, CancellationToken ct)
    {
        var item = await db.CategoryDefinitions.IgnoreQueryFilters().SingleOrDefaultAsync(c => c.Id == id, ct);
        if (item is null) return Missing();
        item.DeletedAt = null; item.IsActive = input.Active; item.UpdatedAt = DateTimeOffset.UtcNow; await db.SaveChangesAsync(ct);
        return Results.Ok(new ManagementCategoryDto(item.Id, item.Name, item.Kind.ToString(), item.SortOrder, item.IsActive));
    }
    private static Task<IResult> DeleteCategory(Guid id, AppDbContext db, CancellationToken ct) => SetCategoryActive(id, new(false), db, ct);

    private static IQueryable<ManagementUserDto> UserDtos(IQueryable<AppUser> query, AppDbContext db)
    {
        var now = DateTimeOffset.UtcNow;
        return query.Select(u => new ManagementUserDto(u.Id, u.DisplayName, u.Email, u.PhoneNumber, !u.LockoutEnabled || u.LockoutEnd == null || u.LockoutEnd <= now,
            db.UserRoles.Where(ur => ur.UserId == u.Id).Join(db.Roles, ur => ur.RoleId, r => r.Id, (ur, r) => r.Name!).OrderBy(r => r).ToArray()));
    }

    private static Task<IResult> ListUsers(AppDbContext db, CancellationToken ct, string? q = null, string? role = null, bool? active = null, int page = 1, int pageSize = 25)
    {
        var query = db.Users.AsQueryable();
        if (!string.IsNullOrWhiteSpace(q)) query = query.Where(u => u.DisplayName.Contains(q) || (u.Email != null && u.Email.Contains(q)) || (u.PhoneNumber != null && u.PhoneNumber.Contains(q)));
        if (role is not null)
        {
            if (!AppRoles.All.Contains(role)) return Task.FromResult(Invalid("The user role is invalid."));
            query = query.Where(u => db.UserRoles.Any(ur => ur.UserId == u.Id && db.Roles.Any(r => r.Id == ur.RoleId && r.Name == role)));
        }
        if (active is bool enabled)
        {
            var now = DateTimeOffset.UtcNow;
            query = enabled ? query.Where(u => !u.LockoutEnabled || u.LockoutEnd == null || u.LockoutEnd <= now) : query.Where(u => u.LockoutEnabled && u.LockoutEnd > now);
        }
        return Page(UserDtos(query.OrderBy(u => u.DisplayName).ThenBy(u => u.Id), db), page, pageSize, ct);
    }

    private static async Task<IResult> GetUser(Guid id, AppDbContext db, CancellationToken ct)
    {
        var item = await UserDtos(db.Users.Where(u => u.Id == id), db).SingleOrDefaultAsync(ct);
        return item is null ? Missing() : Results.Ok(item);
    }

    private static IResult IdentityError(IdentityResult result) => ApiResults.Error("identity_error", string.Join(" ", result.Errors.Select(e => e.Description).Distinct()));
    private static bool ValidUser(ManagementUserInput input) => Text(input.DisplayName, 100, true) && Text(input.Email, 256, true) &&
        new System.ComponentModel.DataAnnotations.EmailAddressAttribute().IsValid(input.Email) && UserInputValidation.TryNormalizeIranianMobile(input.PhoneNumber, out _);

    private static string TemporaryPassword()
    {
        const string alphabet = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        return "Bn" + new string(Enumerable.Range(0, 14).Select(_ => alphabet[RandomNumberGenerator.GetInt32(alphabet.Length)]).ToArray()) + "@7";
    }

    private static async Task<IResult> CreateUser(ManagementUserInput input, AppDbContext db, UserManager<AppUser> users, CancellationToken ct)
    {
        if (!ValidUser(input) || !ValidGlobalRoles([input.Role])) return Invalid("Provide a name, valid email, Iranian mobile number and a Customer or PlatformAdmin global role. Team roles require branch membership.");
        if (await users.FindByEmailAsync(Clean(input.Email)) is not null) return Conflict("The email is already registered.");
        UserInputValidation.TryNormalizeIranianMobile(input.PhoneNumber, out var phone);
        var user = new AppUser { UserName = Clean(input.Email), Email = Clean(input.Email), DisplayName = Clean(input.DisplayName), PhoneNumber = phone, EmailConfirmed = true };
        var password = TemporaryPassword();
        await using var transaction = await db.Database.BeginTransactionAsync(ct);
        var result = await users.CreateAsync(user, password);
        if (!result.Succeeded) return IdentityError(result);
        result = await users.AddToRoleAsync(user, input.Role);
        if (!result.Succeeded) return IdentityError(result);
        await transaction.CommitAsync(ct);
        return Results.Created($"/api/v1/platform/users/{user.Id}", new ManagementCreatedUserDto(user.Id, user.DisplayName, user.Email, user.PhoneNumber, password));
    }

    private static async Task<IResult> UpdateUser(Guid id, ManagementUserInput input, AppDbContext db, UserManager<AppUser> users, CancellationToken ct)
    {
        if (!ValidUser(input)) return Invalid("Provide a name, valid email and Iranian mobile number.");
        var user = await users.FindByIdAsync(id.ToString());
        if (user is null) return Missing();
        var existing = await users.FindByEmailAsync(Clean(input.Email));
        if (existing is not null && existing.Id != id) return Conflict("The email is already registered.");
        UserInputValidation.TryNormalizeIranianMobile(input.PhoneNumber, out var phone);
        user.DisplayName = Clean(input.DisplayName); user.Email = Clean(input.Email); user.UserName = user.Email; user.PhoneNumber = phone;
        var result = await users.UpdateAsync(user);
        if (!result.Succeeded) return IdentityError(result);
        result = await users.UpdateSecurityStampAsync(user);
        return result.Succeeded ? await GetUser(id, db, ct) : IdentityError(result);
    }

    private static async Task<IResult> SetUserRoles(Guid id, ManagementRolesInput input, AppDbContext db, ApiScope scope, UserManager<AppUser> users, CancellationToken ct)
    {
        if (!ValidGlobalRoles(input.Roles)) return Invalid("Only Customer and PlatformAdmin are global grants. Manage Owner, Manager and Staff through branch memberships.");
        var target = input.Roles.Distinct().ToArray();
        if (id == scope.UserId && !target.Contains(AppRoles.PlatformAdmin)) return Conflict("You cannot remove your own platform administrator role.");
        return await Atomic(db, async () =>
        {
            var user = await users.FindByIdAsync(id.ToString());
            if (user is null) return Missing();
            var current = await users.GetRolesAsync(user);
            if (current.Contains(AppRoles.PlatformAdmin) && !target.Contains(AppRoles.PlatformAdmin) &&
                !HasOtherActivePlatformAdmin(await users.GetUsersInRoleAsync(AppRoles.PlatformAdmin), id, DateTimeOffset.UtcNow))
                return Conflict("The last active platform administrator cannot be removed.");
            var add = target.Except(current).ToArray();
            var remove = current.Where(r => r is AppRoles.Customer or AppRoles.PlatformAdmin).Except(target).ToArray();
            var result = add.Length == 0 ? IdentityResult.Success : await users.AddToRolesAsync(user, add);
            if (!result.Succeeded) return IdentityError(result);
            result = remove.Length == 0 ? IdentityResult.Success : await users.RemoveFromRolesAsync(user, remove);
            if (!result.Succeeded) return IdentityError(result);
            await TeamRoleSync.SyncAsync(users, db, user);
            result = await users.UpdateSecurityStampAsync(user);
            return result.Succeeded ? await GetUser(id, db, ct) : IdentityError(result);
        }, ct);
    }

    private static async Task<IResult> SetUserActive(Guid id, ManagementActiveInput input, AppDbContext db, ApiScope scope, UserManager<AppUser> users, CancellationToken ct)
    {
        if (!input.Active && id == scope.UserId) return Conflict("You cannot disable your own account.");
        return await Atomic(db, async () =>
        {
            var user = await users.FindByIdAsync(id.ToString());
            if (user is null) return Missing();
            if (!input.Active && await users.IsInRoleAsync(user, AppRoles.PlatformAdmin) &&
                !HasOtherActivePlatformAdmin(await users.GetUsersInRoleAsync(AppRoles.PlatformAdmin), id, DateTimeOffset.UtcNow))
                return Conflict("The last active platform administrator cannot be disabled.");
            user.LockoutEnabled = true; user.LockoutEnd = input.Active ? null : DateTimeOffset.MaxValue;
            var result = await users.UpdateAsync(user);
            if (!result.Succeeded) return IdentityError(result);
            result = await users.UpdateSecurityStampAsync(user);
            return result.Succeeded ? await GetUser(id, db, ct) : IdentityError(result);
        }, ct);
    }

    private static async Task<IResult> ResetUserPassword(Guid id, UserManager<AppUser> users)
    {
        var user = await users.FindByIdAsync(id.ToString());
        if (user is null) return Missing();
        var password = TemporaryPassword(); var token = await users.GeneratePasswordResetTokenAsync(user);
        var result = await users.ResetPasswordAsync(user, token, password);
        if (!result.Succeeded) return IdentityError(result);
        return Results.Ok(new { userId = user.Id, temporaryPassword = password });
    }
}

public sealed record ManagementActiveInput(bool Active);
public sealed record ManagementBusinessInput(string Name, string Slug = "", string Category = "عمومی", string City = "تهران", string Description = "", bool RequiresApproval = true, string FirstBranchName = "شعبه مرکزی", Guid? OwnerId = null);
public sealed record ManagementBusinessDto(Guid Id, string Name, string Slug, string Category, string City, string Description, bool RequiresApproval, bool Active);
public sealed record ManagementBranchInput(Guid BusinessId, string Name, string Address = "", string TimeZoneId = "Asia/Tehran", int OpenHour = 9, int CloseHour = 18);
public sealed record ManagementBranchDto(Guid Id, Guid BusinessId, string Name, string Address, string TimeZoneId, int OpenHour, int CloseHour, bool Active);
public sealed record ManagementResourceInput(Guid BranchId, string Name, string Kind = "staff", Guid? UserId = null);
public sealed record ManagementResourceDto(Guid Id, Guid BranchId, string Name, string Kind, Guid? UserId, bool Active);
public sealed record ManagementMembershipInput(Guid BranchId, Guid UserId, string Role = AppRoles.Staff);
public sealed record ManagementMembershipDto(Guid Id, Guid BranchId, Guid UserId, string Role, string DisplayName, string? Email);
public sealed record ManagementPersonDto(Guid Id, string DisplayName, string? Email, string? PhoneNumber, bool Active);
public sealed record ManagementServiceInput(Guid BusinessId, string Name, string Description = "", int DurationMinutes = 30, decimal Price = 0, string Currency = "IRR", Guid? CatalogItemId = null, Guid? BranchId = null, Guid[]? ResourceIds = null);
public sealed record ManagementServiceDto(Guid Id, Guid BusinessId, Guid? CatalogItemId, string Name, string Description, int DurationMinutes, decimal Price, string Currency, Guid[] BranchIds, Guid[] ResourceIds, bool Active);
public sealed record ManagementBranchServiceInput(decimal? Price = null);
public sealed record ManagementBranchServiceDto(Guid Id, Guid BranchId, Guid ServiceId, decimal? Price, decimal EffectivePrice, string ServiceName);
public sealed record ManagementResourceServicesInput(Guid[] ServiceIds);
public sealed record ManagementAccountDto(Guid Id, string DisplayName, string? Email);
public sealed record ManagementContextMembership(Guid BranchId, Guid BusinessId, string BranchName, string Role, bool CanManageBusiness, bool CanManageAppointments);
public sealed record ManagementAvailabilityInput(Guid BusinessId, DateOnly EffectiveDate, TimeOnly StartsAt, TimeOnly EndsAt, Guid? BranchId = null, Guid? ResourceId = null, bool IsAvailable = true);
public sealed record ManagementAvailabilityDto(Guid Id, Guid BusinessId, Guid? BranchId, Guid? ResourceId, DateOnly? EffectiveDate, string DayOfWeek, TimeOnly StartsAt, TimeOnly EndsAt, bool IsAvailable);
public sealed record ManagementStatusInput(string Status);
public sealed record ManagementAppointmentDto(Guid Id, Guid BranchId, Guid BusinessId, Guid ServiceId, Guid? ResourceId, Guid CustomerId, DateTimeOffset StartsAt, DateTimeOffset EndsAt, string Status, decimal FinalPrice, string Currency, string CustomerNote, string BranchName, string ServiceName, string? ResourceName, string CustomerName, string? CustomerEmail, string? CustomerPhoneNumber, string BusinessName, string TimeZoneId, string[] ServiceNames, string TrackingCode)
{
    public string[] ServiceNames { get; init; } = ServiceNames.Length == 0 ? [ServiceName] : ServiceNames;
}
public sealed record ManagementReviewDto(Guid Id, Guid BusinessId, Guid? BranchId, Guid CustomerId, Guid? AppointmentId, int Rating, string Comment, string? ManagerReply, string Status, string CustomerName, bool Active, DateTimeOffset CreatedAt);
public sealed record ManagementModerationInput(string Status, string? ManagerReply = null);
public sealed record ManagementReplyInput(string? ManagerReply);
public sealed record ManagementCustomerReviewInput(Guid AppointmentId, int Rating, string Comment = "");
public sealed record ManagementCustomerReviewDto(Guid Id, Guid AppointmentId, Guid BusinessId, Guid BranchId, Guid CustomerId, Guid AuthorId, int Rating, string Comment, string CustomerName, string AuthorName, DateTimeOffset CreatedAt);
public sealed record ManagementCatalogInput(string Name, string Slug, string Category = "عمومی", string Description = "", int SuggestedDurationMinutes = 30, bool IsPublished = true);
public sealed record ManagementCatalogDto(Guid Id, string Name, string Slug, string Category, string Description, int SuggestedDurationMinutes, bool IsPublished, bool Active);
public sealed record ManagementCategoryInput(string Name, string Kind = "Business", int SortOrder = 0, bool IsActive = true);
public sealed record ManagementCategoryDto(Guid Id, string Name, string Kind, int SortOrder, bool Active);
public sealed record ManagementUserInput(string DisplayName, string Email, string PhoneNumber, string Role = AppRoles.Customer);
public sealed record ManagementUserDto(Guid Id, string DisplayName, string? Email, string? PhoneNumber, bool Active, string[] Roles);
public sealed record ManagementCreatedUserDto(Guid Id, string DisplayName, string? Email, string? PhoneNumber, string TemporaryPassword);
public sealed record ManagementRolesInput(string[] Roles);
