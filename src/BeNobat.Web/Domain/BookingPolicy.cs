namespace BeNobat.Web.Domain;

public static class BookingPolicy
{
    public static readonly TimeSpan MinimumBookingLeadTime = TimeSpan.FromMinutes(30);
    public static readonly TimeSpan CustomerCancellationNotice = TimeSpan.FromHours(2);

    /// <summary>فاصله‌ی زمان‌های شروع پیشنهادی در تقویم رزرو.</summary>
    public const int SlotStepMinutes = 30;

    /// <summary>دورترین روزی که می‌شود نوبت گرفت (برحسب روز از امروز).</summary>
    public const int MaxAdvanceDays = 90;

    /// <summary>سقف نوبت‌های فعال (در انتظار/تأییدشده‌ی آینده) برای هر مشتری؛ مانع رزرو انبوه و بی‌مصرف.</summary>
    public const int MaxActiveAppointmentsPerCustomer = 10;

    public static bool IsWithinHorizon(DateOnly date, DateOnly today) => date >= today && date <= today.AddDays(MaxAdvanceDays);

    /// <summary>
    /// ماشین وضعیت نوبت: در انتظار ← تأیید/لغو؛ تأییدشده ← لغو؛ «تکمیل» و «عدم حضور» فقط بعد از
    /// شروع نوبت؛ وضعیت‌های پایانی (تکمیل، لغو، عدم حضور) دیگر تغییر نمی‌کنند.
    /// </summary>
    public static bool CanTransition(AppointmentStatus from, AppointmentStatus to, DateTimeOffset startsAt, DateTimeOffset now)
    {
        if (from == to) return false;
        var started = startsAt <= now;
        return from switch
        {
            AppointmentStatus.Pending => to switch
            {
                AppointmentStatus.Confirmed => true,
                AppointmentStatus.Cancelled => true,
                AppointmentStatus.Completed => started,
                AppointmentStatus.NoShow => started,
                _ => false,
            },
            AppointmentStatus.Confirmed => to switch
            {
                AppointmentStatus.Cancelled => true,
                AppointmentStatus.Completed => started,
                AppointmentStatus.NoShow => started,
                _ => false,
            },
            _ => false,
        };
    }

    public static bool IsActiveFuture(Appointment appointment, DateTimeOffset now) =>
        (appointment.Status is AppointmentStatus.Pending or AppointmentStatus.Confirmed) && appointment.EndsAt >= now;

    public static int TotalDuration(IEnumerable<Service> services) => services.Sum(x => x.DurationMinutes);
    public static decimal TotalPrice(IEnumerable<Service> services) => services.Sum(x => x.Price);

    public static bool IsBookable(DateTimeOffset startsAt, DateTimeOffset now) => startsAt >= now.Add(MinimumBookingLeadTime);

    public static bool CanCustomerCancel(Appointment appointment, DateTimeOffset now) =>
        (appointment.Status is AppointmentStatus.Pending or AppointmentStatus.Confirmed) &&
        appointment.StartsAt >= now.Add(CustomerCancellationNotice);

    public static bool CanCustomerReview(Appointment appointment, DateTimeOffset now) =>
        appointment.Status == AppointmentStatus.Completed ||
        (appointment.Status == AppointmentStatus.Confirmed && appointment.EndsAt < now);

    public static (DateTimeOffset Start, DateTimeOffset End) UtcDayWindow(DateOnly date, TimeZoneInfo zone)
    {
        return BranchClock.DayWindow(date, zone);
    }

    public static bool Overlaps(DateTimeOffset startsAt, DateTimeOffset endsAt,
        DateTimeOffset otherStartsAt, DateTimeOffset otherEndsAt) =>
        startsAt < otherEndsAt && otherStartsAt < endsAt;
}
