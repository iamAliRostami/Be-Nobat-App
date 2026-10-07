using System.Reflection;
using BeNobat.Web.Components.Pages;
using BeNobat.Web.Domain;
using Xunit;

namespace BeNobat.Web.Tests;

public sealed class BookingProviderSelectionTests
{
    private const BindingFlags PrivateInstance = BindingFlags.Instance | BindingFlags.NonPublic;

    [Fact]
    public void Provider_link_preselection_is_applied_after_the_user_selects_a_service()
    {
        var requested = new Resource { Name = "کارمند انتخاب‌شده" };
        var other = new Resource { Name = "کارمند دیگر" };
        var component = ComponentFor(requested.Id.ToString(), requested, other);

        Invoke(component, "ToggleService", Guid.NewGuid());
        Invoke(component, "ApplyEligibleProviderSelection");

        Assert.Equal(requested.Id.ToString(), Choice(component));
    }

    [Fact]
    public void Later_service_changes_keep_an_eligible_user_choice_and_do_not_reapply_the_link_provider()
    {
        var requested = new Resource { Name = "کارمند لینک" };
        var chosen = new Resource { Name = "انتخاب کاربر" };
        var component = ComponentFor(requested.Id.ToString(), requested, chosen);
        Invoke(component, "ApplyEligibleProviderSelection");
        Set(component, "ProviderChoice", chosen.Id.ToString());

        Invoke(component, "ToggleService", Guid.NewGuid());
        Invoke(component, "ApplyEligibleProviderSelection");

        Assert.Equal(chosen.Id.ToString(), Choice(component));
    }

    [Fact]
    public void A_provider_that_cannot_perform_the_changed_services_is_cleared()
    {
        var requested = new Resource { Name = "کارمند لینک" };
        var other = new Resource { Name = "کارمند دیگر" };
        var another = new Resource { Name = "کارمند سوم" };
        var component = ComponentFor(requested.Id.ToString(), requested, other);
        Invoke(component, "ApplyEligibleProviderSelection");
        Set(component, "EligibleResources", new List<Resource> { other, another });

        Invoke(component, "ApplyEligibleProviderSelection");

        Assert.Equal("", Choice(component));
    }

    [Fact]
    public void An_ineligible_link_provider_does_not_prevent_selection_of_the_only_qualified_provider()
    {
        var eligible = new Resource { Name = "کارمند واجد شرایط" };
        var component = ComponentFor(Guid.NewGuid().ToString(), eligible);

        Invoke(component, "ApplyEligibleProviderSelection");

        Assert.Equal(eligible.Id.ToString(), Choice(component));
    }

    [Fact]
    public void Any_provider_choice_is_replaced_when_only_one_provider_remains_qualified()
    {
        var first = new Resource { Name = "کارمند اول" };
        var second = new Resource { Name = "کارمند دوم" };
        var component = ComponentFor("any", first, second);
        Invoke(component, "ApplyEligibleProviderSelection");
        Assert.Equal("any", Choice(component));
        Set(component, "EligibleResources", new List<Resource> { second });

        Invoke(component, "ApplyEligibleProviderSelection");

        Assert.Equal(second.Id.ToString(), Choice(component));
    }

    private static Booking ComponentFor(string queryProvider, params Resource[] resources)
    {
        var component = new Booking();
        typeof(Booking).GetProperty(nameof(Booking.QueryProvider))!.SetValue(component, queryProvider);
        Set(component, "EligibleResources", resources.ToList());
        return component;
    }

    private static void Invoke(Booking component, string method, params object[] arguments) =>
        typeof(Booking).GetMethod(method, PrivateInstance)!.Invoke(component, arguments);

    private static void Set(Booking component, string field, object value) =>
        typeof(Booking).GetField(field, PrivateInstance)!.SetValue(component, value);

    private static string Choice(Booking component) =>
        (string)typeof(Booking).GetField("ProviderChoice", PrivateInstance)!.GetValue(component)!;
}
