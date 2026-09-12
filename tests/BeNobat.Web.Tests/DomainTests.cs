using BeNobat.Web.Domain;
using BeNobat.Web.Security;
using Xunit;

namespace BeNobat.Web.Tests;

public sealed class DomainTests
{
    [Fact]
    public void New_appointment_starts_pending()
    {
        var appointment = new Appointment();
        Assert.Equal(AppointmentStatus.Pending, appointment.Status);
    }

    [Fact]
    public void Entities_use_time_ordered_identifiers()
    {
        var business = new Business { Name = "Test" };
        Assert.NotEqual(Guid.Empty, business.Id);
        Assert.Equal(7, business.Id.Version);
    }

    [Fact]
    public void Access_roles_are_unique_and_include_customer_and_manager_roles()
    {
        Assert.Equal(AppRoles.All.Length, AppRoles.All.Distinct().Count());
        Assert.Contains(AppRoles.Customer, AppRoles.All);
        Assert.Contains(AppRoles.Owner, AppRoles.All);
        Assert.Contains(AppRoles.Manager, AppRoles.All);
        Assert.Contains(AppRoles.Staff, AppRoles.All);
    }
}
