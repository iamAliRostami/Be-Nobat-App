using System.Data;
using System.Reflection;
using System.Security.Claims;
using System.Text.RegularExpressions;
using BeNobat.Web.Components.Pages;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using BeNobat.Web.Security;
using Microsoft.AspNetCore.Components.Authorization;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace BeNobat.Web.Tests;

public sealed class AdminReviewsTests
{
    private const BindingFlags PrivateInstance = BindingFlags.Instance | BindingFlags.NonPublic;
    private const BindingFlags PrivateStatic = BindingFlags.Static | BindingFlags.NonPublic;

    [Theory]
    [InlineData(AppRoles.Manager, true)]
    [InlineData(AppRoles.PlatformAdmin, false)]
    public async Task Editing_lookup_keeps_the_actual_authorization_query_and_targets_only_the_requested_id(
        string role, bool requiresMembership)
    {
        using var db = new AppDbContext(new DbContextOptionsBuilder<AppDbContext>()
            .UseNpgsql("Host=localhost;Database=unused").Options);
        var userId = Guid.NewGuid();
        var evaluationId = Guid.NewGuid();
        var access = new AdminAccessScope(db, new FixedAuthentication(userId, role));
        var query = EvaluationById(await access.CustomerReviewsAsync(), evaluationId);

        // Translate the production scoped query, without opening a connection. An ID
        // lookup for editing must narrow the authorization query, rather than replace it.
        var sql = query.ToQueryString();

        Assert.Contains(evaluationId.ToString(), sql);
        Assert.Contains("\"Id\" =", sql);
        Assert.Contains("\"DeletedAt\" IS NULL", sql);
        if (requiresMembership)
        {
            Assert.Contains("benobat.\"BranchMemberships\"", sql);
            Assert.Contains("EXISTS", sql);
            Assert.Contains(userId.ToString(), sql);
            Assert.Contains("'Owner'", sql);
            Assert.Contains("'Manager'", sql);
            Assert.Contains("\"BranchId\"", sql);
        }
        else
        {
            Assert.DoesNotContain("\"BranchMemberships\"", sql);
            Assert.DoesNotContain("EXISTS", sql);
        }
        Assert.Equal(ConnectionState.Closed, db.Database.GetDbConnection().State);
    }

    [Fact]
    public void A_guessed_evaluation_id_cannot_restore_an_item_outside_the_authorized_source()
    {
        var allowed = Evaluation(AppointmentFor());
        var inaccessible = Evaluation(AppointmentFor());
        var authorized = new[] { allowed, inaccessible }.AsQueryable().Where(x => x.Id != inaccessible.Id);

        Assert.Empty(EvaluationById(authorized, inaccessible.Id));
        Assert.Equal(allowed.Id, Assert.Single(EvaluationById(authorized, allowed.Id)).Id);
    }

    [Fact]
    public void Historical_evaluation_lookup_keeps_authorized_ids_and_own_soft_delete_filter_but_loads_archived_names()
    {
        using var db = new AppDbContext(new DbContextOptionsBuilder<AppDbContext>()
            .UseNpgsql("Host=localhost;Database=unused").Options);
        var firstId = Guid.NewGuid();
        var secondId = Guid.NewGuid();
        var query = HistoricalEvaluations(db, [firstId, secondId]);

        var sql = query.ToQueryString();

        Assert.Contains(firstId.ToString(), sql);
        Assert.Contains(secondId.ToString(), sql);
        Assert.Contains("\"Id\" = ANY", sql);
        Assert.Contains("benobat.\"Appointments\"", sql);
        Assert.Contains("benobat.\"Services\"", sql);
        Assert.Contains("benobat.\"Branches\"", sql);
        Assert.Contains("benobat.\"Businesses\"", sql);
        Assert.Contains("\"CreatedAt\" DESC", sql);
        // The evaluation itself must be live. Soft-deleted related records only
        // supply historical labels and must not suppress an authorized evaluation.
        Assert.Single(Regex.Matches(sql, "\"DeletedAt\" IS NULL"));
        Assert.Equal(ConnectionState.Closed, db.Database.GetDbConnection().State);
    }

    [Fact]
    public void Historical_evaluation_lookup_with_no_authorized_ids_translates_without_a_connection()
    {
        using var db = new AppDbContext(new DbContextOptionsBuilder<AppDbContext>()
            .UseNpgsql("Host=localhost;Database=unused").Options);

        var sql = HistoricalEvaluations(db, []).ToQueryString();

        Assert.NotEmpty(sql);
        Assert.Equal(ConnectionState.Closed, db.Database.GetDbConnection().State);
    }

    [Fact]
    public void Editing_changes_only_rating_comment_and_update_time_and_preserves_authorship_and_associations()
    {
        var item = Evaluation(AppointmentFor());
        item.Rating = 2;
        item.Comment = "یادداشت قبلی";
        item.UpdatedAt = new DateTimeOffset(2020, 1, 1, 0, 0, 0, TimeSpan.Zero);
        var id = item.Id;
        var authorId = item.AuthorId;
        var customerId = item.CustomerId;
        var businessId = item.BusinessId;
        var appointmentId = item.AppointmentId;
        var createdAt = item.CreatedAt;
        var author = item.Author;
        var appointment = item.Appointment;
        var before = DateTimeOffset.UtcNow;

        Assert.True(ApplyEdit(item, 4, "  همکاری مناسب  "));

        Assert.Equal(4, item.Rating);
        Assert.Equal("همکاری مناسب", item.Comment);
        Assert.InRange(item.UpdatedAt, before, DateTimeOffset.UtcNow);
        Assert.Equal(id, item.Id);
        Assert.Equal(authorId, item.AuthorId);
        Assert.Equal(customerId, item.CustomerId);
        Assert.Equal(businessId, item.BusinessId);
        Assert.Equal(appointmentId, item.AppointmentId);
        Assert.Equal(createdAt, item.CreatedAt);
        Assert.Same(author, item.Author);
        Assert.Same(appointment, item.Appointment);
    }

    [Theory]
    [InlineData(0, 10)]
    [InlineData(6, 10)]
    [InlineData(-1, 10)]
    [InlineData(3, 2001)]
    public void Invalid_rating_or_overlong_comment_does_not_partially_change_the_existing_evaluation(
        int rating, int commentLength)
    {
        var item = Evaluation(AppointmentFor());
        item.Rating = 5;
        item.Comment = "یادداشت قبلی";
        var updatedAt = item.UpdatedAt;
        var comment = "  " + new string('ا', commentLength) + "  ";

        Assert.False(ApplyEdit(item, rating, comment));

        Assert.Equal(5, item.Rating);
        Assert.Equal("یادداشت قبلی", item.Comment);
        Assert.Equal(updatedAt, item.UpdatedAt);
    }

    [Fact]
    public void Optional_empty_comment_and_exactly_two_thousand_trimmed_characters_are_valid()
    {
        var item = Evaluation(AppointmentFor());
        Assert.True(ApplyEdit(item, 1, null));
        Assert.Equal("", item.Comment);
        Assert.True(ApplyEdit(item, 5, "  " + new string('ا', 2000) + "  "));
        Assert.Equal(new string('ا', 2000), item.Comment);
    }

    [Theory]
    [InlineData("all", true, true)]
    [InlineData("unreviewed", true, false)]
    [InlineData("reviewed", false, true)]
    public void Evaluation_state_filters_show_only_the_requested_sections(
        string state, bool showUnreviewed, bool showReviewed)
    {
        var component = new AdminReviews();
        Set(component, "EvaluationFilter", state);
        var appointment = AppointmentFor();
        var item = Evaluation(appointment);

        Assert.Equal(showUnreviewed ? 1 : 0, FilterUnreviewed(component, [appointment]).Count());
        Assert.Equal(showReviewed ? 1 : 0, FilterReviewed(component, [item]).Count());
    }

    [Fact]
    public void Exact_business_branch_customer_and_rating_filters_combine_without_widening_the_source()
    {
        var component = new AdminReviews();
        var customer = Customer();
        var branch = BranchFor();
        var matching = AppointmentFor(branch, customer);
        var sameLabelsOtherCustomer = AppointmentFor(branch);
        sameLabelsOtherCustomer.Customer.DisplayName = customer.DisplayName;
        var otherBranch = new Branch { Name = branch.Name, BusinessId = branch.BusinessId, Business = branch.Business };
        var sameBusinessOtherBranch = AppointmentFor(otherBranch, customer);
        var otherBusiness = new Business { Name = branch.Business.Name };
        var unrelatedBranch = new Branch { Name = branch.Name, BusinessId = otherBusiness.Id, Business = otherBusiness };
        var unrelatedAppointment = AppointmentFor(unrelatedBranch, customer);
        var inaccessible = AppointmentFor(branch, customer);
        var authorizedAppointments = new[]
        {
            matching, sameLabelsOtherCustomer, sameBusinessOtherBranch, unrelatedAppointment, inaccessible,
        }.Where(x => x.Id != inaccessible.Id);
        Set(component, "EvaluationFilter", "all");
        Set(component, "BusinessFilter", branch.BusinessId);
        Set(component, "BranchFilter", branch.Id);
        Set(component, "CustomerFilter", customer.Id);
        Set(component, "RatingFilter", 4);

        Assert.Equal(matching.Id, Assert.Single(FilterUnreviewed(component, authorizedAppointments)).Id);

        var matchingReview = Evaluation(matching, 4);
        var differentRating = Evaluation(AppointmentFor(branch, customer), 5);
        var inaccessibleReview = Evaluation(inaccessible, 4);
        var authorizedReviews = new[]
        {
            matchingReview, Evaluation(sameLabelsOtherCustomer, 4), Evaluation(sameBusinessOtherBranch, 4),
            Evaluation(unrelatedAppointment, 4), differentRating, inaccessibleReview,
        }.Where(x => x.Id != inaccessibleReview.Id);

        Assert.Equal(matchingReview.Id, Assert.Single(FilterReviewed(component, authorizedReviews)).Id);
    }

    [Theory]
    [InlineData("phone")]
    [InlineData("tracking")]
    [InlineData("persian")]
    public void Outgoing_search_matches_phone_tracking_code_and_normalized_persian_names(string searchKind)
    {
        var component = new AdminReviews();
        var appointment = AppointmentFor();
        appointment.Customer.DisplayName = "علی کریمی";
        appointment.Customer.PhoneNumber = "09123456789";
        var other = AppointmentFor();
        other.Customer.DisplayName = "مراجعه‌کننده دیگر";
        var search = searchKind switch
        {
            "phone" => "09123456789",
            "tracking" => appointment.TrackingCode,
            _ => "علي كريمي",
        };
        Set(component, "Search", search);
        Set(component, "EvaluationFilter", "all");

        Assert.Equal(appointment.Id, Assert.Single(FilterUnreviewed(component, [appointment, other])).Id);
        var item = Evaluation(appointment);
        Assert.Equal(item.Id, Assert.Single(FilterReviewed(component, [item, Evaluation(other)])).Id);
    }

    [Fact]
    public void Query_parameters_restore_the_outgoing_tab_exact_customer_and_reviewed_state()
    {
        var component = new AdminReviews();
        var customer = Customer();
        SetProperty(component, "TabParam", "outgoing");
        SetProperty(component, "CustomerParam", customer.Id);
        SetProperty(component, "EvaluationStatusParam", "reviewed");

        typeof(AdminReviews).GetMethod("OnParametersSet", PrivateInstance)!.Invoke(component, null);

        Assert.Equal("outgoing", Get(component, "Tab"));
        Assert.Equal("reviewed", Get(component, "EvaluationFilter"));
        Assert.Equal(customer.Id, Get(component, "CustomerFilter"));
        var matching = Evaluation(AppointmentFor(customer: customer));
        var otherCustomer = Evaluation(AppointmentFor());
        otherCustomer.Customer.DisplayName = customer.DisplayName;
        Assert.Equal(matching.Id, Assert.Single(FilterReviewed(component, [matching, otherCustomer])).Id);
        Assert.Empty(FilterUnreviewed(component, [AppointmentFor(customer: customer)]));
    }

    [Fact]
    public async Task Navigation_disposal_cancels_initialization_before_a_database_context_is_created()
    {
        var factory = new BlockingContextFactory();
        var component = new AdminReviews();
        typeof(AdminReviews).GetProperty("DbFactory", PrivateInstance)!.SetValue(component, factory);
        var initialization = (Task)typeof(AdminReviews).GetMethod("OnInitializedAsync", PrivateInstance)!
            .Invoke(component, null)!;
        await factory.Started.Task.WaitAsync(TimeSpan.FromSeconds(3), TestContext.Current.CancellationToken);
        Assert.False(initialization.IsCompleted);

        component.Dispose();

        await initialization.WaitAsync(TimeSpan.FromSeconds(3), TestContext.Current.CancellationToken);
        Assert.True(Assert.Single(factory.Tokens).IsCancellationRequested);
        Assert.Null(Get(component, "LoadCancellation"));
        // The factory never returns a context; cancelled initialization cannot access a database.
        component.Dispose();
    }

    [Fact]
    public async Task A_new_load_cancels_the_previous_request_and_navigation_cancels_the_latest()
    {
        var factory = new BlockingContextFactory();
        var component = new AdminReviews();
        typeof(AdminReviews).GetProperty("DbFactory", PrivateInstance)!.SetValue(component, factory);
        var load = typeof(AdminReviews).GetMethod("Load", PrivateInstance)!;

        var first = (Task)load.Invoke(component, null)!;
        var latest = (Task)load.Invoke(component, null)!;

        Assert.Equal(2, factory.Tokens.Count);
        Assert.True(factory.Tokens[0].IsCancellationRequested);
        Assert.False(factory.Tokens[1].IsCancellationRequested);
        await first.WaitAsync(TimeSpan.FromSeconds(3), TestContext.Current.CancellationToken);
        Assert.False(latest.IsCompleted);
        component.Dispose();
        await latest.WaitAsync(TimeSpan.FromSeconds(3), TestContext.Current.CancellationToken);
        Assert.True(factory.Tokens[1].IsCancellationRequested);
        Assert.Null(Get(component, "LoadCancellation"));
    }

    [Fact]
    public async Task Duplicate_evaluation_saves_are_blocked_and_disposal_cancels_the_write_and_releases_its_guard()
    {
        var factory = new BlockingContextFactory();
        var component = new AdminReviews();
        typeof(AdminReviews).GetProperty("DbFactory", PrivateInstance)!.SetValue(component, factory);
        var item = Evaluation(AppointmentFor(), 4);
        item.Comment = "یادداشت موجود";
        var updatedAt = item.UpdatedAt;
        typeof(AdminReviews).GetMethod("EditCustomerReview", PrivateInstance)!.Invoke(component, new object[] { item });
        Assert.Same(item, Get(component, "EditingEvaluation"));
        var form = Get(component, "EvaluationForm")!;
        Assert.Equal(item.Rating, form.GetType().GetProperty("Rating")!.GetValue(form));
        Assert.Equal(item.Comment, form.GetType().GetProperty("Comment")!.GetValue(form));
        var save = typeof(AdminReviews).GetMethod("SaveEvaluation", PrivateInstance)!;

        var first = (Task)save.Invoke(component, null)!;
        await factory.Started.Task.WaitAsync(TimeSpan.FromSeconds(3), TestContext.Current.CancellationToken);
        var duplicate = (Task)save.Invoke(component, null)!;
        await duplicate.WaitAsync(TimeSpan.FromSeconds(3), TestContext.Current.CancellationToken);

        Assert.False(first.IsCompleted);
        Assert.Single(factory.Tokens);
        var busy = (HashSet<Guid>)Get(component, "BusyIds")!;
        Assert.Contains(item.Id, busy);
        component.Dispose();
        await first.WaitAsync(TimeSpan.FromSeconds(3), TestContext.Current.CancellationToken);
        Assert.True(factory.Tokens[0].IsCancellationRequested);
        Assert.Empty(busy);
        Assert.Equal(4, item.Rating);
        Assert.Equal("یادداشت موجود", item.Comment);
        Assert.Equal(updatedAt, item.UpdatedAt);
    }

    private static IQueryable<CustomerReview> EvaluationById(IQueryable<CustomerReview> query, Guid id) =>
        (IQueryable<CustomerReview>)typeof(AdminReviews).GetMethod("EvaluationById", PrivateStatic)!
            .Invoke(null, new object[] { query, id })!;

    private static IQueryable<CustomerReview> HistoricalEvaluations(AppDbContext db, List<Guid> authorizedIds) =>
        (IQueryable<CustomerReview>)typeof(AdminReviews).GetMethod("HistoricalEvaluations", PrivateStatic)!
            .Invoke(null, new object[] { db, authorizedIds })!;

    private static bool ApplyEdit(CustomerReview item, int rating, string? comment) =>
        (bool)typeof(AdminReviews).GetMethod("ApplyEvaluationEdit", PrivateStatic)!
            .Invoke(null, new object?[] { item, rating, comment })!;

    private static IEnumerable<Appointment> FilterUnreviewed(AdminReviews component, IEnumerable<Appointment> source) =>
        (IEnumerable<Appointment>)typeof(AdminReviews).GetMethod("FilterUnreviewed", PrivateInstance)!
            .Invoke(component, new object[] { source })!;

    private static IEnumerable<CustomerReview> FilterReviewed(AdminReviews component, IEnumerable<CustomerReview> source) =>
        (IEnumerable<CustomerReview>)typeof(AdminReviews).GetMethod("FilterReviewed", PrivateInstance)!
            .Invoke(component, new object[] { source })!;

    private static void Set(object component, string field, object value) =>
        component.GetType().GetField(field, PrivateInstance)!.SetValue(component, value);

    private static object? Get(object component, string field) =>
        component.GetType().GetField(field, PrivateInstance)!.GetValue(component);

    private static void SetProperty(object component, string property, object value) =>
        component.GetType().GetProperty(property)!.SetValue(component, value);

    private static AppUser Customer() => new()
    {
        Id = Guid.NewGuid(), DisplayName = "مشتری", Email = "customer@example.test",
    };

    private static Branch BranchFor()
    {
        var business = new Business { Name = "مجموعه" };
        return new Branch { Name = "شعبه", BusinessId = business.Id, Business = business };
    }

    private static Appointment AppointmentFor(Branch? branch = null, AppUser? customer = null)
    {
        branch ??= BranchFor();
        customer ??= Customer();
        var service = new Service { Name = "خدمت", BusinessId = branch.BusinessId, Business = branch.Business };
        var start = new DateTimeOffset(2025, 1, 1, 8, 0, 0, TimeSpan.Zero);
        return new Appointment
        {
            Id = Guid.NewGuid(), BranchId = branch.Id, Branch = branch, CustomerId = customer.Id,
            Customer = customer, ServiceId = service.Id, Service = service, StartsAt = start,
            EndsAt = start.AddMinutes(30), Status = AppointmentStatus.Completed,
        };
    }

    private static CustomerReview Evaluation(Appointment appointment, int rating = 3)
    {
        var author = new AppUser { Id = Guid.NewGuid(), DisplayName = "مدیر", Email = "manager@example.test" };
        return new CustomerReview
        {
            AppointmentId = appointment.Id, Appointment = appointment,
            BusinessId = appointment.Branch.BusinessId, Business = appointment.Branch.Business,
            CustomerId = appointment.CustomerId, Customer = appointment.Customer,
            AuthorId = author.Id, Author = author, Rating = rating,
        };
    }

    private sealed class FixedAuthentication(Guid userId, string role) : AuthenticationStateProvider
    {
        public override Task<AuthenticationState> GetAuthenticationStateAsync() => Task.FromResult(new AuthenticationState(
            new ClaimsPrincipal(new ClaimsIdentity(new[]
            {
                new Claim(ClaimTypes.NameIdentifier, userId.ToString()), new Claim(ClaimTypes.Role, role),
            }, "Test"))));
    }

    private sealed class BlockingContextFactory : IDbContextFactory<AppDbContext>
    {
        private readonly TaskCompletionSource<AppDbContext> context = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public TaskCompletionSource<bool> Started { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public List<CancellationToken> Tokens { get; } = [];
        public AppDbContext CreateDbContext() => throw new InvalidOperationException("Use the asynchronous factory.");
        public Task<AppDbContext> CreateDbContextAsync(CancellationToken cancellationToken = default)
        {
            Tokens.Add(cancellationToken);
            Started.TrySetResult(true);
            return context.Task.WaitAsync(cancellationToken);
        }
    }
}
