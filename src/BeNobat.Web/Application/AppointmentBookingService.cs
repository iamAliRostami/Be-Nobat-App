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

public enum ConfirmFailure { NotFound, NotPending, SlotUnavailable, CustomerOverlap, InvalidTime }

/// <summary>یک زمان پیشنهادی برای تأییدکننده؛ OverrunMinutes &gt; 0 یعنی خدمت کمی از فاصله‌ی خالی بلندتر است.</summary>
public sealed record RescheduleOption(DateTimeOffset StartsAt, int OverrunMinutes);

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

    /// <summary>
    /// زمان‌هایی که خدمت کمی (تا ۱۵ دقیقه) بلندتر از فاصله‌ی خالی است. مقدار هر شروع، دقیقه‌ی اضافه است.
    /// این زمان‌ها فقط با تأیید شعبه ثبت می‌شوند.
    /// </summary>
    public IReadOnlyDictionary<Guid, Dictionary<DateTimeOffset, int>> FlexibleByResource { get; init; } = new Dictionary<Guid, Dictionary<DateTimeOffset, int>>();
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
        // اول کارمندی که کاملاً جا دارد؛ در غیر این صورت کارمندی که با تحمل زمانی (حداکثر ۱۵ دقیقه) جا دارد.
        var resource = definition.Resources.FirstOrDefault(x => definition.SlotsByResource.TryGetValue(x.Id, out var slots) && slots.Contains(start));
        var overrun = 0;
        if (resource is null)
        {
            resource = definition.Resources.FirstOrDefault(x => definition.FlexibleByResource.TryGetValue(x.Id, out var flexible) && flexible.ContainsKey(start));
            if (resource is null) return Failed(BookingFailure.SlotUnavailable);
            overrun = definition.FlexibleByResource[resource.Id][start];
        }
        // EndsAt پایان بازه‌ی رزروشده است؛ دقیقه‌ی اضافه در فاصله‌ی بین نوبت‌ها جذب می‌شود.
        var end = start.AddMinutes(definition.DurationMinutes - overrun);
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
            OverrunMinutes = overrun,
            // نوبتی که از فاصله‌ی خالی بلندتر است همیشه باید شعبه تأیید کند تا زمان نهایی را هماهنگ کند.
            Status = definition.Business!.RequiresApproval || overrun > 0 ? AppointmentStatus.Pending : AppointmentStatus.Confirmed,
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
        var (strict, flexible) = await SlotMapsAsync(db, definition.Business!.Id, branch,
            definition.Resources.Select(x => x.Id).ToList(), date, definition.DurationMinutes, now, null, cancellationToken);
        return definition with { SlotsByResource = strict, FlexibleByResource = flexible };
    }

    /// <summary>زمان‌های کاملاً جاشونده و زمان‌های جاشونده با تحمل زمانی برای هر کارمند در یک روز.</summary>
    private static async Task<(Dictionary<Guid, List<DateTimeOffset>> Strict, Dictionary<Guid, Dictionary<DateTimeOffset, int>> Flexible)> SlotMapsAsync(
        AppDbContext db, Guid businessId, Branch branch, IReadOnlyCollection<Guid> resourceIds, DateOnly date, int durationMinutes,
        DateTimeOffset now, Guid? excludeAppointmentId, CancellationToken cancellationToken)
    {
        var ids = resourceIds.ToList();
        // All weekly days are needed to distinguish an offday from an unconfigured schedule.
        var rules = await db.AvailabilityRules.AsNoTracking().Where(x => x.BusinessId == businessId
            && (x.BranchId == null || x.BranchId == branch.Id)
            && (x.ResourceId == null || ids.Contains(x.ResourceId.Value)))
            .ToListAsync(cancellationToken);
        var (dayStart, dayEnd) = BranchClock.DayWindow(date, branch.TimeZoneId);
        var busy = await db.Appointments.AsNoTracking().Where(x => x.ResourceId != null && ids.Contains(x.ResourceId.Value)
            && x.Status != AppointmentStatus.Cancelled && x.StartsAt < dayEnd && x.EndsAt > dayStart
            && (excludeAppointmentId == null || x.Id != excludeAppointmentId))
            .Select(x => new { x.ResourceId, x.StartsAt, x.EndsAt }).ToListAsync(cancellationToken);
        var strictMap = new Dictionary<Guid, List<DateTimeOffset>>();
        var flexibleMap = new Dictionary<Guid, Dictionary<DateTimeOffset, int>>();
        var duration = TimeSpan.FromMinutes(durationMinutes);
        foreach (var id in ids)
        {
            var intervals = AvailabilityResolver.Resolve(rules, date, branch.Id, id, branch.OpenHour, branch.CloseHour);
            var resourceBusy = busy.Where(x => x.ResourceId == id).Select(x => (x.StartsAt, x.EndsAt)).ToList();
            var strict = AvailabilityResolver.Slots(intervals, date, branch.TimeZoneId, duration, BookingPolicy.SlotStepMinutes, resourceBusy, now);
            strictMap[id] = strict;
            flexibleMap[id] = AvailabilityResolver.FlexibleSlots(intervals, date, branch.TimeZoneId, duration,
                BookingPolicy.SlotStepMinutes, resourceBusy, now, strict);
        }
        return (strictMap, flexibleMap);
    }

    /// <summary>
    /// زمان‌های پیشنهادی برای تأییدکننده‌ی یک نوبت «در انتظار»: همان کارمند و همان مدت خدمات، با نادیده گرفتن خودِ نوبت.
    /// دسترسی کاربر باید پیش از فراخوانی بررسی شده باشد.
    /// </summary>
    public async Task<IReadOnlyList<RescheduleOption>> GetRescheduleOptionsAsync(Guid appointmentId, DateOnly date,
        CancellationToken cancellationToken = default)
    {
        await using var db = await dbFactory.CreateDbContextAsync(cancellationToken);
        var appointment = await db.Appointments.AsNoTracking().Include(x => x.Branch).Include(x => x.Services)
            .FirstOrDefaultAsync(x => x.Id == appointmentId, cancellationToken);
        if (appointment is null || appointment.ResourceId is not Guid resourceId || appointment.Status != AppointmentStatus.Pending) return [];
        var now = DateTimeOffset.UtcNow;
        if (!BookingPolicy.IsWithinHorizon(date, BranchClock.LocalDate(now, appointment.Branch.TimeZoneId))) return [];
        var (strict, flexible) = await SlotMapsAsync(db, appointment.Branch.BusinessId, appointment.Branch, [resourceId], date,
            FullDurationMinutes(appointment), now, appointmentId, cancellationToken);
        var options = strict[resourceId].Select(x => new RescheduleOption(x, 0))
            .Concat(flexible[resourceId].Select(x => new RescheduleOption(x.Key, x.Value)))
            .OrderBy(x => x.StartsAt).ToList();
        return options;
    }

    /// <summary>
    /// تأیید نوبت «در انتظار». اگر <paramref name="newStart"/> داده شود، نوبت پیش از تأیید به همان زمان منتقل می‌شود
    /// (فقط به زمان‌هایی که <see cref="GetRescheduleOptionsAsync"/> پیشنهاد می‌دهد). دسترسی کاربر باید پیش از فراخوانی بررسی شده باشد.
    /// </summary>
    public async Task<ConfirmFailure?> ConfirmAsync(Guid appointmentId, DateTimeOffset? newStart, CancellationToken cancellationToken = default)
    {
        await using var db = await dbFactory.CreateDbContextAsync(cancellationToken);
        await using var transaction = await db.Database.BeginTransactionAsync(cancellationToken);
        var head = await db.Appointments.AsNoTracking().Where(x => x.Id == appointmentId)
            .Select(x => new { x.CustomerId, x.ResourceId }).FirstOrDefaultAsync(cancellationToken);
        if (head is null) return ConfirmFailure.NotFound;
        // همان ترتیب قفل رزرو: اول مشتری، بعد کارمند.
        await LockAsync(db, head.CustomerId, customer: true, cancellationToken);
        if (head.ResourceId is Guid lockedResource) await LockAsync(db, lockedResource, customer: false, cancellationToken);

        var appointment = await db.Appointments.Include(x => x.Branch).Include(x => x.Services)
            .FirstOrDefaultAsync(x => x.Id == appointmentId, cancellationToken);
        if (appointment is null) return ConfirmFailure.NotFound;
        var now = DateTimeOffset.UtcNow;
        if (!BookingPolicy.CanTransition(appointment.Status, AppointmentStatus.Confirmed, appointment.StartsAt, now)) return ConfirmFailure.NotPending;

        if (newStart is DateTimeOffset requested && requested.ToUniversalTime() != appointment.StartsAt)
        {
            var start = requested.ToUniversalTime();
            if (appointment.ResourceId is not Guid resourceId) return ConfirmFailure.InvalidTime;
            var duration = FullDurationMinutes(appointment);
            var date = BranchClock.LocalDate(start, appointment.Branch.TimeZoneId);
            if (!BookingPolicy.IsWithinHorizon(date, BranchClock.LocalDate(now, appointment.Branch.TimeZoneId))) return ConfirmFailure.InvalidTime;
            var (strict, flexible) = await SlotMapsAsync(db, appointment.Branch.BusinessId, appointment.Branch, [resourceId], date,
                duration, now, appointmentId, cancellationToken);
            var overrun = 0;
            if (!strict[resourceId].Contains(start))
            {
                if (!flexible[resourceId].TryGetValue(start, out overrun)) return ConfirmFailure.SlotUnavailable;
            }
            var end = start.AddMinutes(duration - overrun);
            if (await db.Appointments.AnyAsync(a => a.Id != appointmentId && a.CustomerId == appointment.CustomerId
                && (a.Status == AppointmentStatus.Pending || a.Status == AppointmentStatus.Confirmed)
                && a.StartsAt < end && a.EndsAt > start, cancellationToken)) return ConfirmFailure.CustomerOverlap;
            appointment.RequestedStartsAt ??= appointment.StartsAt;
            appointment.StartsAt = start;
            appointment.EndsAt = end;
            appointment.OverrunMinutes = overrun;
        }

        appointment.Status = AppointmentStatus.Confirmed;
        appointment.UpdatedAt = now;
        try
        {
            await db.SaveChangesAsync(cancellationToken);
            await transaction.CommitAsync(cancellationToken);
        }
        catch (DbUpdateException exception) when (exception.InnerException is PostgresException { SqlState: PostgresErrorCodes.ExclusionViolation })
        {
            await transaction.RollbackAsync(cancellationToken);
            return ConfirmFailure.SlotUnavailable;
        }
        return null;
    }

    /// <summary>مدت واقعی خدمات نوبت (بازه‌ی رزروشده + دقیقه‌ی تحمل‌شده).</summary>
    private static int FullDurationMinutes(Appointment appointment)
    {
        var fromServices = appointment.Services.Sum(x => x.DurationMinutes);
        return fromServices > 0 ? fromServices : (int)(appointment.EndsAt - appointment.StartsAt).TotalMinutes + appointment.OverrunMinutes;
    }

    private static decimal PriceOf(BookingAvailability definition, Service service) =>
        definition.BranchServices.First(x => x.ServiceId == service.Id).Price ?? service.Price;

    private static AppointmentBookingResult Failed(BookingFailure failure) => new(null, null, failure);
}
