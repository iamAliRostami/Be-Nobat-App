namespace BeNobat.Web.Security;

public static class AppRoles
{
    public const string PlatformAdmin = "PlatformAdmin";
    public const string Owner = "Owner";
    public const string Manager = "Manager";
    public const string Staff = "Staff";
    public const string Customer = "Customer";

    public static readonly string[] All = [PlatformAdmin, Owner, Manager, Staff, Customer];
    // مدیر سامانه باید همه قابلیت‌های مدیریتی نقش‌های پایین‌تر را نیز داشته باشد.
    public static readonly string[] BusinessManagers = [PlatformAdmin, Owner, Manager];
    public static readonly string[] AppointmentManagers = [PlatformAdmin, Owner, Manager, Staff];
}

public static class Policies
{
    public const string ManagePlatform = nameof(ManagePlatform);
    public const string ManageBusiness = nameof(ManageBusiness);
    public const string ManageAppointments = nameof(ManageAppointments);
    public const string ViewOwnAppointments = nameof(ViewOwnAppointments);
}
