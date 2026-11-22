namespace BeNobat.Web.Security;

public static class AppRoles
{
    public const string PlatformAdmin = "PlatformAdmin";
    public const string Owner = "Owner";
    public const string Manager = "Manager";
    public const string Staff = "Staff";
    public const string Customer = "Customer";

    public static readonly string[] Team = [Owner, Manager, Staff];
    public static readonly string[] All = [PlatformAdmin, Owner, Manager, Staff, Customer];
    // مدیر سامانه باید همه قابلیت‌های مدیریتی نقش‌های پایین‌تر را نیز داشته باشد.
    public static readonly string[] BusinessManagers = [PlatformAdmin, Owner, Manager];
    public static readonly string[] AppointmentManagers = [PlatformAdmin, Owner, Manager, Staff];

    public static bool IsValidRoleString(string? roleString)
    {
        if (string.IsNullOrWhiteSpace(roleString)) return false;
        var parts = roleString.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
        return parts.Length > 0 && parts.All(p => Team.Contains(p));
    }

    public static string NormalizeRoles(string? roleString)
    {
        if (string.IsNullOrWhiteSpace(roleString)) return Staff;
        var parts = roleString.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .Distinct()
            .Where(Team.Contains)
            .OrderBy(r => r switch { Owner => 1, Manager => 2, Staff => 3, _ => 4 })
            .ToList();
        return parts.Count > 0 ? string.Join(",", parts) : Staff;
    }

    public static string[] ParseRoles(string? roleString)
    {
        if (string.IsNullOrWhiteSpace(roleString)) return [];
        return roleString.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .Where(Team.Contains)
            .Distinct()
            .ToArray();
    }

    public static bool HasRole(string? roleString, string role)
    {
        if (string.IsNullOrWhiteSpace(roleString)) return false;
        return roleString.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .Contains(role, StringComparer.OrdinalIgnoreCase);
    }
}

public static class Policies
{
    public const string ManagePlatform = nameof(ManagePlatform);
    public const string ManageBusiness = nameof(ManageBusiness);
    public const string ManageAppointments = nameof(ManageAppointments);
    public const string ViewOwnAppointments = nameof(ViewOwnAppointments);
}
