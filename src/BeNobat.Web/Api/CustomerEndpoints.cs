using BeNobat.Web.Application;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using BeNobat.Web.Security;
using Microsoft.AspNetCore.Authorization;
using Microsoft.EntityFrameworkCore;

namespace BeNobat.Web.Api;

public static class CustomerEndpoints
{
    public static RouteGroupBuilder MapCustomerEndpoints(this RouteGroupBuilder root)
    {
        root.MapGet("/slots", async (Guid businessId, Guid branchId, string? serviceIds, Guid? resourceId, DateOnly date, AppointmentBookingService booking, CancellationToken ct) =>
        {
            var ids = ApiResults.ServiceIds(serviceIds);
            if (ids is null) return ApiResults.Error("invalid_input", "Select at least one service.");
            var result = await booking.GetAvailabilityAsync(businessId, branchId, ids, resourceId, date, ct);
            if (!result.Success) return Failure(result.Failure!.Value);
            var providers = result.Resources.ToDictionary(r => r.Id, r => r.Name);
            var slots = result.SlotsByResource.SelectMany(pair => pair.Value.Select(start => new SlotDto(pair.Key, providers.GetValueOrDefault(pair.Key) ?? "", start, start.AddMinutes(result.DurationMinutes)))).OrderBy(s => s.StartsAt).ThenBy(s => s.ResourceName).ToArray();
            return Results.Ok(new { date, timeZoneId = result.Branch!.TimeZoneId, totalDurationMinutes = result.DurationMinutes, totalPrice = result.TotalPrice, result.Currency, slots });
        }).AllowAnonymous();
        var customer = root.MapGroup("").RequireAuthorization(new AuthorizeAttribute { AuthenticationSchemes = MobileAuthentication.Scheme });
        customer.MapGet("/favorites", async (ApiScope scope, AppDbContext db, int? page, int? pageSize, CancellationToken ct) =>
        {
            var (p, size) = ApiResults.Pagination(page, pageSize);
            var query = db.Businesses.AsNoTracking().Where(b => db.FavoriteBusinesses.Any(f => f.BusinessId == b.Id && f.UserId == scope.UserId));
            return Results.Ok(new ApiPage<BusinessDto>(await query.OrderBy(b => b.Name).Skip((p - 1) * size).Take(size).Select(PublicEndpoints.BusinessProjection).ToListAsync(ct), p, size, await query.CountAsync(ct)));
        });
        customer.MapPut("/favorites/{businessId:guid}", async (Guid businessId, ApiScope scope, AppDbContext db, CancellationToken ct) =>
        {
            if (!await db.Businesses.AnyAsync(b => b.Id == businessId, ct)) return ApiResults.Error("not_found", "Business was not found.", 404);
            var favorite = await db.FavoriteBusinesses.IgnoreQueryFilters().FirstOrDefaultAsync(f => f.BusinessId == businessId && f.UserId == scope.UserId, ct);
            if (favorite is null) db.FavoriteBusinesses.Add(new FavoriteBusiness { BusinessId = businessId, UserId = scope.UserId });
            else { favorite.DeletedAt = null; favorite.UpdatedAt = DateTimeOffset.UtcNow; }
            await db.SaveChangesAsync(ct); return Results.NoContent();
        });
        customer.MapDelete("/favorites/{businessId:guid}", async (Guid businessId, ApiScope scope, AppDbContext db, CancellationToken ct) =>
        {
            await db.FavoriteBusinesses.Where(f => f.BusinessId == businessId && f.UserId == scope.UserId).ExecuteDeleteAsync(ct);
            return Results.NoContent();
        });
        customer.MapPost("/appointments", async (BookingInput input, ApiScope scope, AppointmentBookingService booking, AppDbContext db, CancellationToken ct) =>
        {
            var result = await booking.BookAsync(scope.UserId, new AppointmentBookingRequest(input.BusinessId, input.BranchId, input.ServiceIds ?? [], input.ResourceId, input.StartsAt,
                input.PhoneNumber, input.CustomerNote, input.TermsAccepted, input.ExpectedPrice, input.ExpectedDurationMinutes), ct);
            if (!result.Success) return Failure(result.Failure!.Value);
            var dto = await AppointmentDetail(db, result.Appointment!.Id, scope.UserId, ct);
            return Results.Created($"/api/v1/appointments/{result.Appointment.Id}", dto);
        });
        customer.MapGet("/appointments", async (ApiScope scope, AppDbContext db, int? page, int? pageSize, string? status, CancellationToken ct) =>
        {
            var query = History(db).Where(a => a.CustomerId == scope.UserId);
            if (!string.IsNullOrEmpty(status))
            {
                if (!Enum.TryParse<AppointmentStatus>(status, true, out var value) || !Enum.IsDefined(value) || int.TryParse(status, out _)) return ApiResults.Error("invalid_input", "Appointment status is invalid.");
                query = query.Where(a => a.Status == value);
            }
            var (p, size) = ApiResults.Pagination(page, pageSize);
            var total = await query.CountAsync(ct);
            var appointments = await query.OrderByDescending(a => a.StartsAt).ThenBy(a => a.Id).Skip((p - 1) * size).Take(size).ToListAsync(ct);
            var ids = appointments.Select(a => a.Id).ToArray();
            var reviews = await db.Reviews.IgnoreQueryFilters().AsNoTracking().Where(r => r.CustomerId == scope.UserId && r.AppointmentId != null && ids.Contains(r.AppointmentId.Value)).ToDictionaryAsync(r => r.AppointmentId!.Value, r => r.Id, ct);
            return Results.Ok(new ApiPage<AppointmentDto>(appointments.Select(a => ToDto(a, reviews.GetValueOrDefault(a.Id) is var review && review != Guid.Empty ? review : null)).ToArray(), p, size, total));
        });
        customer.MapGet("/appointments/{appointmentId:guid}", async (Guid appointmentId, ApiScope scope, AppDbContext db, CancellationToken ct) =>
        {
            var dto = await AppointmentDetail(db, appointmentId, scope.UserId, ct);
            return dto is null ? ApiResults.Error("not_found", "Appointment was not found.", 404) : Results.Ok(dto);
        });
        customer.MapPost("/appointments/{appointmentId:guid}/cancel", async (Guid appointmentId, ApiScope scope, AppDbContext db, CancellationToken ct) =>
        {
            var now = DateTimeOffset.UtcNow;
            var count = await db.Appointments.IgnoreQueryFilters().Where(a => a.DeletedAt == null && a.Id == appointmentId && a.CustomerId == scope.UserId &&
                (a.Status == AppointmentStatus.Pending || a.Status == AppointmentStatus.Confirmed) && a.StartsAt >= now.Add(BookingPolicy.CustomerCancellationNotice))
                .ExecuteUpdateAsync(update => update.SetProperty(a => a.Status, AppointmentStatus.Cancelled).SetProperty(a => a.UpdatedAt, now), ct);
            if (count == 0)
            {
                var exists = await db.Appointments.IgnoreQueryFilters().AnyAsync(a => a.DeletedAt == null && a.Id == appointmentId && a.CustomerId == scope.UserId, ct);
                return ApiResults.Error(exists ? "cancellation_unavailable" : "not_found", exists ? "This appointment cannot be cancelled within two hours of its start or after a final status." : "Appointment was not found.", exists ? 409 : 404);
            }
            return Results.Ok(await AppointmentDetail(db, appointmentId, scope.UserId, ct));
        });
        customer.MapPost("/appointments/{appointmentId:guid}/review", async (Guid appointmentId, ReviewInput input, ApiScope scope, AppDbContext db, CancellationToken ct) =>
        {
            if (input.Rating is < 1 or > 5 || (input.Comment?.Length ?? 0) > 2000) return ApiResults.Error("invalid_input", "Rating must be between 1 and 5 and the comment under 2000 characters.");
            var appointment = await db.Appointments.Include(a => a.Branch).ThenInclude(b => b.Business).FirstOrDefaultAsync(a => a.Id == appointmentId && a.CustomerId == scope.UserId, ct);
            if (appointment is null || appointment.Branch.DeletedAt != null || appointment.Branch.Business.DeletedAt != null) return ApiResults.Error("not_found", "Appointment was not found.", 404);
            if (!BookingPolicy.CanCustomerReview(appointment, DateTimeOffset.UtcNow)) return ApiResults.Error("review_unavailable", "A review is available after the completed appointment.", 409);
            if (await db.Reviews.IgnoreQueryFilters().AnyAsync(r => r.AppointmentId == appointmentId, ct)) return ApiResults.Error("review_exists", "This appointment already has a review.", 409);
            var review = new Review { AppointmentId = appointmentId, BusinessId = appointment.Branch.BusinessId, BranchId = appointment.BranchId, CustomerId = scope.UserId, Rating = input.Rating, Comment = input.Comment?.Trim() ?? "", Status = ReviewStatus.Pending };
            db.Reviews.Add(review); await db.SaveChangesAsync(ct);
            return Results.Created($"/api/v1/appointments/{appointmentId}", new { review.Id, review.Rating, review.Comment, status = review.Status.ToString(), review.CreatedAt });
        });
        return root;
    }
    public static IResult Failure(BookingFailure failure)
    {
        var code = failure switch
        {
            BookingFailure.InvalidRequest => "invalid_input", BookingFailure.TermsNotAccepted => "terms_required", BookingFailure.InvalidPhone => "invalid_phone",
            BookingFailure.CustomerNotFound => "unauthorized", BookingFailure.BusinessUnavailable => "business_unavailable", BookingFailure.BranchUnavailable => "branch_unavailable",
            BookingFailure.ServiceUnavailable => "service_unavailable", BookingFailure.NoEligibleProvider => "no_provider", BookingFailure.OutsideHorizon => "outside_horizon",
            BookingFailure.TooSoon => "too_soon", BookingFailure.TermsChanged => "terms_changed", BookingFailure.SlotUnavailable => "slot_unavailable",
            BookingFailure.CustomerOverlap => "customer_overlap", BookingFailure.ActiveLimitReached => "active_limit", _ => "invalid_input",
        };
        var status = failure is BookingFailure.CustomerNotFound ? 401 : failure is BookingFailure.InvalidRequest or BookingFailure.InvalidPhone or BookingFailure.TermsNotAccepted or BookingFailure.OutsideHorizon or BookingFailure.TooSoon ? 400 : 409;
        return ApiResults.Error(code, "The booking could not be completed. Refresh availability and check the selected details.", status);
    }
    private static IQueryable<Appointment> History(AppDbContext db) => db.Appointments.IgnoreQueryFilters().AsNoTracking().Where(a => a.DeletedAt == null)
        .Include(a => a.Branch).ThenInclude(b => b.Business).Include(a => a.Service).Include(a => a.Resource).Include(a => a.Services).ThenInclude(s => s.Service);
    private static async Task<AppointmentDto?> AppointmentDetail(AppDbContext db, Guid id, Guid customer, CancellationToken ct)
    {
        var appointment = await History(db).FirstOrDefaultAsync(a => a.Id == id && a.CustomerId == customer, ct);
        if (appointment is null) return null;
        var review = await db.Reviews.IgnoreQueryFilters().AsNoTracking().Where(r => r.CustomerId == customer && r.AppointmentId == id).Select(r => (Guid?)r.Id).FirstOrDefaultAsync(ct);
        return ToDto(appointment, review);
    }
    private static AppointmentDto ToDto(Appointment a, Guid? review) => new(a.Id, a.Branch.BusinessId, a.Branch.Business.Name, a.BranchId, a.Branch.Name, a.Branch.TimeZoneId,
        a.Services.Count > 0 ? a.Services.Select(s => s.Service.Name).ToArray() : [a.Service.Name], a.ResourceId, a.Resource?.Name, a.StartsAt, a.EndsAt, a.Status.ToString(), a.FinalPrice,
        a.Currency, a.CustomerNote, a.TrackingCode, BookingPolicy.CanCustomerCancel(a, DateTimeOffset.UtcNow), review == null && a.Branch.DeletedAt == null && a.Branch.Business.DeletedAt == null && BookingPolicy.CanCustomerReview(a, DateTimeOffset.UtcNow), review);
}
public sealed record SlotDto(Guid ResourceId, string ResourceName, DateTimeOffset StartsAt, DateTimeOffset EndsAt);
public sealed record BookingInput(Guid BusinessId, Guid BranchId, Guid[]? ServiceIds, Guid? ResourceId, DateTimeOffset StartsAt, string? PhoneNumber, string? CustomerNote, bool TermsAccepted, decimal? ExpectedPrice = null, int? ExpectedDurationMinutes = null);
public sealed record ReviewInput(int Rating, string? Comment);
