using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using BeNobat.Web.Security;
using System.Security.Cryptography;
using Microsoft.EntityFrameworkCore;
using Npgsql;

namespace BeNobat.Web.Application;

public enum BookingFailure
{
    InvalidRequest, TermsNotAccepted, InvalidPhone, CustomerNotFound, BusinessUnavailable,
    BranchUnavailable, ServiceUnavailable, NoEligibleProvider, OutsideHorizon, TooSoon,
    TermsChanged, SlotUnavailable, CustomerOverlap, ActiveLimitReached,
}

public sealed record AppointmentBookingRequest(
    Guid BusinessId, Guid BranchId, IReadOnlyCollection<Guid> ServiceIds, Guid? ResourceId,
    DateTimeOffset StartsAt, string? PhoneNumber, string? CustomerNote, bool TermsAccepted,
    decimal? ExpectedPrice = null, int? ExpectedDurationMinutes = null, string? ExpectedSecurityStamp = null);

public sealed record AppointmentBookingResult(Appointment? Appointment, Resource? Resource, BookingFailure? Failure)
{
    public bool Success => Failure is null && Appointment is not null;
}

public sealed record BookingAvailability
{
    public Business? Business { get; init; }
    public Branch? Branch { get; init; }
    public IReadOnlyList<Service> Services { get; init; } = [];
    public IReadOnlyList<BranchService> BranchServices { get; init; } = [];
    public IReadOnlyList<Resource> Resources { get; init; } = [];
    public int DurationMinutes { get; init; }
    public decimal TotalPrice { get; init; }
    public string Currency { get; init; } = "IRR";
    public IReadOnlyDictionary<Guid, List<DateTimeOffset>> SlotsByResource { get; init; } = new Dictionary<Guid, List<DateTimeOffset>>();
    public BookingFailure? Failure { get; init; }
    public bool Success => Failure is null;
}

/// <summary>The authoritative booking rules used by both the interactive web flow and the mobile API.</summary>
public sealed class AppointmentBookingService(IDbContextFactory<AppDbContext> dbFactory)
{
    public async Task<BookingAvailability> GetAvailabilityAsync(Guid businessId, Guid branchId,
        IReadOnlyCollection<Guid> serviceIds, Guid? resourceId, DateOnly date, CancellationToken cancellationToken = default)
    {
        await using var db = await dbFactory.CreateDbContextAsync(cancellationToken);
        var definition = await LoadDefinitionAsync(db, businessId, branchId, serviceIds, resourceId, cancellationToken);
        if (!definition.Success) return definition;
        return await AddSlotsAsync(db, definition, date, DateTimeOffset.UtcNow, cancellationToken);
    }

    public async Task<AppointmentBookingResult> BookAsync(Guid customerId, AppointmentBookingRequest request,
        CancellationToken cancellationToken = default)
    {
        if (!request.TermsAccepted) return Failed(BookingFailure.TermsNotAccepted);
        if (!UserInputValidation.TryNormalizeIranianMobile(request.PhoneNumber ?? "", out var phone)) return Failed(BookingFailure.InvalidPhone);
        if (customerId == Guid.Empty || request.StartsAt == default || (request.CustomerNote?.Length ?? 0) > 500)
            return Failed(BookingFailure.InvalidRequest);

        await using var db = await dbFactory.CreateDbContextAsync(cancellationToken);
        await using var transaction = await db.Database.BeginTransactionAsync(cancellationToken);
        // Every booking locks its customer before its providers. This also serializes overlap/cap checks
        // when the customer's concurrent requests target different providers or different branches.
        await LockAsync(db, customerId, customer: true, cancellationToken);
        var accountNow = DateTimeOffset.UtcNow;
        if (!await db.Users.AnyAsync(x => x.Id == customerId
            && (!x.LockoutEnabled || x.LockoutEnd == null || x.LockoutEnd <= accountNow)
            && (request.ExpectedSecurityStamp == null || x.SecurityStamp == request.ExpectedSecurityStamp), cancellationToken))
            return Failed(BookingFailure.CustomerNotFound);
        var definition = await LoadDefinitionAsync(db, request.BusinessId, request.BranchId, request.ServiceIds, request.ResourceId, cancellationToken);
        if (!definition.Success) return Failed(definition.Failure!.Value);
        var lockedResources = definition.Resources.Select(x => x.Id).OrderBy(x => x).ToList();
        foreach (var id in lockedResources) await LockAsync(db, id, customer: false, cancellationToken);

        // Reload after waiting for locks; never trust a browser's cached hierarchy, price or duration.
        definition = await LoadDefinitionAsync(db, request.BusinessId, request.BranchId, request.ServiceIds, request.ResourceId, cancellationToken);
        if (!definition.Success) return Failed(definition.Failure!.Value);
        definition = definition with { Resources = definition.Resources.Where(x => lockedResources.Contains(x.Id)).ToList() };
        if (definition.Resources.Count == 0) return Failed(BookingFailure.NoEligibleProvider);
        if ((request.ExpectedPrice is decimal expectedPrice && expectedPrice != definition.TotalPrice) ||
            (request.ExpectedDurationMinutes is int expectedDuration && expectedDuration != definition.DurationMinutes))
            return Failed(BookingFailure.TermsChanged);

        var now = DateTimeOffset.UtcNow;
        var start = request.StartsAt.ToUniversalTime();
        if (!BookingPolicy.IsBookable(start, now)) return Failed(BookingFailure.TooSoon);
        definition = await AddSlotsAsync(db, definition, BranchClock.LocalDate(start, definition.Branch!.TimeZoneId), now, cancellationToken);
        if (!definition.Success) return Failed(definition.Failure!.Value);
        var resource = definition.Resources.FirstOrDefault(x => definition.SlotsByResource.TryGetValue(x.Id, out var slots) && slots.Contains(start));
        if (resource is null) return Failed(BookingFailure.SlotUnavailable);
        var end = start.AddMinutes(definition.DurationMinutes);
        if (await db.Appointments.AnyAsync(a => a.CustomerId == customerId
            && (a.Status == AppointmentStatus.Pending || a.Status == AppointmentStatus.Confirmed)
            && a.StartsAt < end && a.EndsAt > start, cancellationToken)) return Failed(BookingFailure.CustomerOverlap);
        var activeCount = await db.Appointments.CountAsync(a => a.CustomerId == customerId
            && (a.Status == AppointmentStatus.Pending || a.Status == AppointmentStatus.Confirmed) && a.EndsAt >= now, cancellationToken);
        if (activeCount >= BookingPolicy.MaxActiveAppointmentsPerCustomer) return Failed(BookingFailure.ActiveLimitReached);

        var appointment = new Appointment
        {
            BranchId = definition.Branch!.Id,
            ServiceId = definition.Services[0].Id,
            ResourceId = resource.Id,
            CustomerId = customerId,
            StartsAt = start,
            EndsAt = end,
            FinalPrice = definition.TotalPrice,
            Currency = definition.Currency,
            CustomerNote = request.CustomerNote?.Trim() ?? "",
            Status = definition.Business!.RequiresApproval ? AppointmentStatus.Pending : AppointmentStatus.Confirmed,
        };
        foreach (var service in definition.Services)
            appointment.Services.Add(new AppointmentService
            {
                ServiceId = service.Id,
                DurationMinutes = service.DurationMinutes,
                Price = PriceOf(definition, service),
            });
        db.Appointments.Add(appointment);
        var profileConcurrencyStamp = Guid.NewGuid().ToString("N");
        await db.Users.Where(x => x.Id == customerId && x.PhoneNumber != phone).ExecuteUpdateAsync(setters => setters
            .SetProperty(x => x.PhoneNumber, phone).SetProperty(x => x.PhoneNumberConfirmed, false)
            .SetProperty(x => x.ConcurrencyStamp, profileConcurrencyStamp), cancellationToken);
        try
        {
            await db.SaveChangesAsync(cancellationToken);
            await transaction.CommitAsync(cancellationToken);
        }
        catch (DbUpdateException exception) when (exception.InnerException is PostgresException { SqlState: PostgresErrorCodes.ExclusionViolation })
        {
            await transaction.RollbackAsync(cancellationToken);
            return Failed(BookingFailure.SlotUnavailable);
        }
        return new AppointmentBookingResult(appointment, resource, null);
    }

    private static async Task LockAsync(AppDbContext db, Guid id, bool customer, CancellationToken cancellationToken)
    {
        // PostgreSQL's two-int and one-bigint lock spaces are disjoint. Hash collisions merely
        // serialize unrelated customers; provider locks remain compatible with the former web flow.
        if (customer)
        {
            var key = BitConverter.ToInt32(SHA256.HashData(id.ToByteArray()), 0);
            await db.Database.ExecuteSqlAsync($"SELECT pg_advisory_xact_lock(112966, {key})", cancellationToken);
        }
        else
        {
            var key = BitConverter.ToInt64(id.ToByteArray(), 0);
            await db.Database.ExecuteSqlAsync($"SELECT pg_advisory_xact_lock({key})", cancellationToken);
        }
    }

    private static async Task<BookingAvailability> LoadDefinitionAsync(AppDbContext db, Guid businessId, Guid branchId,
        IReadOnlyCollection<Guid> serviceIds, Guid? resourceId, CancellationToken cancellationToken)
    {
        if (businessId == Guid.Empty || branchId == Guid.Empty || serviceIds is null || serviceIds.Count is < 1 or > 50 || resourceId == Guid.Empty)
            return new() { Failure = BookingFailure.InvalidRequest };
        var business = await db.Businesses.AsNoTracking().FirstOrDefaultAsync(x => x.Id == businessId, cancellationToken);
        if (business is null) return new() { Failure = BookingFailure.BusinessUnavailable };
        var branch = await db.Branches.AsNoTracking().FirstOrDefaultAsync(x => x.Id == branchId && x.BusinessId == businessId, cancellationToken);
        if (branch is null) return new() { Failure = BookingFailure.BranchUnavailable };
        var ids = serviceIds.Distinct().ToList();
        var services = await db.Services.AsNoTracking().Where(x => x.BusinessId == businessId && ids.Contains(x.Id)).OrderBy(x => x.Id).ToListAsync(cancellationToken);
        var branchServices = await db.BranchServices.AsNoTracking().Where(x => x.BranchId == branchId && ids.Contains(x.ServiceId)).ToListAsync(cancellationToken);
        if (services.Count != ids.Count || branchServices.Select(x => x.ServiceId).Distinct().Count() != ids.Count)
            return new() { Failure = BookingFailure.ServiceUnavailable };
        var duration = services.Sum(x => (long)x.DurationMinutes);
        if (duration is <= 0 or > 1440 || services.Any(x => x.DurationMinutes <= 0 || x.Price < 0) || branchServices.Any(x => x.Price < 0))
            return new() { Failure = BookingFailure.ServiceUnavailable };
        var currencies = services.Select(x => x.Currency).Distinct(StringComparer.OrdinalIgnoreCase).ToList();
        if (currencies.Count != 1) return new() { Failure = BookingFailure.ServiceUnavailable };
        var resources = await db.Resources.AsNoTracking().Where(x => x.BranchId == branchId && x.Kind == "staff"
            && (resourceId == null || x.Id == resourceId)).OrderBy(x => x.Name).ThenBy(x => x.Id).ToListAsync(cancellationToken);
        var assignments = await db.ServiceResources.AsNoTracking().Where(x => ids.Contains(x.ServiceId))
            .Select(x => new { x.ServiceId, x.ResourceId }).ToListAsync(cancellationToken);
        resources = resources.Where(r => ids.All(s => assignments.Any(x => x.ResourceId == r.Id && x.ServiceId == s))).ToList();
        if (resources.Count == 0) return new() { Failure = BookingFailure.NoEligibleProvider };
        return new BookingAvailability
        {
            Business = business, Branch = branch, Services = services, BranchServices = branchServices, Resources = resources,
            DurationMinutes = (int)duration, Currency = currencies[0],
            TotalPrice = services.Sum(x => branchServices.First(y => y.ServiceId == x.Id).Price ?? x.Price),
        };
    }

    private static async Task<BookingAvailability> AddSlotsAsync(AppDbContext db, BookingAvailability definition,
        DateOnly date, DateTimeOffset now, CancellationToken cancellationToken)
    {
        var branch = definition.Branch!;
        if (!BookingPolicy.IsWithinHorizon(date, BranchClock.LocalDate(now, branch.TimeZoneId)))
            return definition with { Failure = BookingFailure.OutsideHorizon };
        var ids = definition.Resources.Select(x => x.Id).ToList();
        // All weekly days are needed to distinguish an offday from an unconfigured schedule.
        var rules = await db.AvailabilityRules.AsNoTracking().Where(x => x.BusinessId == definition.Business!.Id
            && (x.BranchId == null || x.BranchId == branch.Id)
            && (x.ResourceId == null || ids.Contains(x.ResourceId.Value)))
            .ToListAsync(cancellationToken);
        var (dayStart, dayEnd) = BranchClock.DayWindow(date, branch.TimeZoneId);
        var busy = await db.Appointments.AsNoTracking().Where(x => x.ResourceId != null && ids.Contains(x.ResourceId.Value)
            && x.Status != AppointmentStatus.Cancelled && x.StartsAt < dayEnd && x.EndsAt > dayStart)
            .Select(x => new { x.ResourceId, x.StartsAt, x.EndsAt }).ToListAsync(cancellationToken);
        var map = new Dictionary<Guid, List<DateTimeOffset>>();
        foreach (var resource in definition.Resources)
        {
            var intervals = AvailabilityResolver.Resolve(rules, date, branch.Id, resource.Id, branch.OpenHour, branch.CloseHour);
            map[resource.Id] = AvailabilityResolver.Slots(intervals, date, branch.TimeZoneId, TimeSpan.FromMinutes(definition.DurationMinutes),
                BookingPolicy.SlotStepMinutes, busy.Where(x => x.ResourceId == resource.Id).Select(x => (x.StartsAt, x.EndsAt)), now);
        }
        return definition with { SlotsByResource = map };
    }

    private static decimal PriceOf(BookingAvailability definition, Service service) =>
        definition.BranchServices.First(x => x.ServiceId == service.Id).Price ?? service.Price;

    private static AppointmentBookingResult Failed(BookingFailure failure) => new(null, null, failure);
}
