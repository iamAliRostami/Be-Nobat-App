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
    public void Platform_administrator_is_a_distinct_supported_role()
    {
        Assert.Contains(AppRoles.PlatformAdmin, AppRoles.All);
        Assert.DoesNotContain(AppRoles.Customer, new[] { AppRoles.Owner, AppRoles.Manager, AppRoles.Staff });
    }

    [Fact]
    public void Branch_can_track_booking_resources()
    {
        var branch = new Branch { Name = "مرکزی" };
        branch.Resources.Add(new Resource { Name = "اتاق یک", BranchId = branch.Id });
        Assert.Single(branch.Resources);
    }

    [Fact]
    public void Availability_can_apply_to_a_whole_business_or_one_branch()
    {
        var businessRule = new AvailabilityRule { BusinessId = Guid.NewGuid(), BranchId = null };
        var branchRule = new AvailabilityRule { BusinessId = businessRule.BusinessId, BranchId = Guid.NewGuid() };
        Assert.Null(businessRule.BranchId);
        Assert.NotNull(branchRule.BranchId);
    }

    [Theory]
    [InlineData(1)]
    [InlineData(5)]
    public void Review_accepts_the_supported_rating_boundaries(int rating)
    {
        var review = new Review { Rating = rating };
        Assert.InRange(review.Rating, 1, 5);
    }

    [Fact]
    public void Membership_connects_a_user_to_a_branch()
    {
        var membership = new BranchMembership { UserId = Guid.NewGuid(), BranchId = Guid.NewGuid() };
        Assert.NotEqual(Guid.Empty, membership.UserId);
        Assert.NotEqual(Guid.Empty, membership.BranchId);
    }
}
