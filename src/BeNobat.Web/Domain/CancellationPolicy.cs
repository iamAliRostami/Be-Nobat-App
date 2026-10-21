namespace BeNobat.Web.Domain;

public static class CancellationPolicy
{
    public const int DefaultMinutes = 120;
    public const int MaximumMinutes = 129600; // booking horizon: 90 days
    public static bool IsValid(int? minutes) => minutes is null or >= 0 and <= MaximumMinutes;
    public static int Resolve(int business, int? branch, int? service, int? branchService) =>
        branchService ?? service ?? branch ?? business;

    public static int NoticeMinutes(Appointment appointment)
    {
        var branch = appointment.Branch;
        int ForService(Service? service, Guid id) => Resolve(branch?.Business?.CancellationNoticeMinutes ?? DefaultMinutes,
            branch?.CancellationNoticeMinutes, service?.CancellationNoticeMinutes,
            branch?.BranchServices.FirstOrDefault(x => x.ServiceId == id)?.CancellationNoticeMinutes);
        return appointment.Services.Count == 0 ? ForService(appointment.Service, appointment.ServiceId)
            : appointment.Services.Max(s => ForService(s.Service, s.ServiceId));
    }
    public static DateTimeOffset Deadline(Appointment appointment) => appointment.StartsAt.AddMinutes(-NoticeMinutes(appointment));
}
