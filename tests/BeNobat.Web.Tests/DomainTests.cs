using BeNobat.Web.Domain;
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
}
