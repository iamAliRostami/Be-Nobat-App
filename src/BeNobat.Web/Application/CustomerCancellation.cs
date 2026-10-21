using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using Microsoft.EntityFrameworkCore;

namespace BeNobat.Web.Application;

public static class CustomerCancellation
{
    // Kept in SQL so status, ownership, session and the current cancellation rules are checked
    // by the same atomic UPDATE. Includes archived links for already booked appointments.
    public static IQueryable<Appointment> Eligible(AppDbContext db, IQueryable<Appointment> appointments, DateTimeOffset now) =>
        appointments.Where(a => (a.Status == AppointmentStatus.Pending || a.Status == AppointmentStatus.Confirmed) && a.StartsAt > now &&
            a.StartsAt >= now.AddMinutes(
                a.Services.Select(s => (int?)(
                    db.BranchServices.IgnoreQueryFilters().Where(bs => bs.BranchId == a.BranchId && bs.ServiceId == s.ServiceId)
                        .Select(bs => bs.CancellationNoticeMinutes).FirstOrDefault()
                    ?? s.Service.CancellationNoticeMinutes ?? a.Branch.CancellationNoticeMinutes ?? a.Branch.Business.CancellationNoticeMinutes)).Max()
                ?? (db.BranchServices.IgnoreQueryFilters().Where(bs => bs.BranchId == a.BranchId && bs.ServiceId == a.ServiceId)
                        .Select(bs => bs.CancellationNoticeMinutes).FirstOrDefault()
                    ?? a.Service.CancellationNoticeMinutes ?? a.Branch.CancellationNoticeMinutes ?? a.Branch.Business.CancellationNoticeMinutes)));
}
