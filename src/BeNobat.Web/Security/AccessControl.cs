namespace BeNobat.Web.Security;

public static class AppRoles
{
    public const string PlatformAdmin = "PlatformAdmin";
    public const string Owner = "Owner";
    public const string Manager = "Manager";
    public const string Staff = "Staff";
    public const string Customer = "Customer";

    public static readonly string[] All = [PlatformAdmin, Owner, Manager, Staff, Customer];
}

public static class Policies
{
    public const string ManageBusiness = nameof(ManageBusiness);
    public const string ManageAppointments = nameof(ManageAppointments);
    public const string ViewOwnAppointments = nameof(ViewOwnAppointments);
}

