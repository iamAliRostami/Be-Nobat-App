using BeNobat.Web.Domain;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Identity.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore;

namespace BeNobat.Web.Infrastructure;

public sealed class AppDbContext(DbContextOptions<AppDbContext> options)
    : IdentityDbContext<AppUser, IdentityRole<Guid>, Guid>(options)
{
    public DbSet<Business> Businesses => Set<Business>();
    public DbSet<Branch> Branches => Set<Branch>();
    public DbSet<Service> Services => Set<Service>();
    public DbSet<Resource> Resources => Set<Resource>();
    public DbSet<Appointment> Appointments => Set<Appointment>();
    public DbSet<BranchMembership> BranchMemberships => Set<BranchMembership>();
    public DbSet<AvailabilityRule> AvailabilityRules => Set<AvailabilityRule>();
    public DbSet<Review> Reviews => Set<Review>();
    public DbSet<CustomerReview> CustomerReviews => Set<CustomerReview>();
    public DbSet<BranchService> BranchServices => Set<BranchService>();
    public DbSet<ServiceResource> ServiceResources => Set<ServiceResource>();
    public DbSet<AppointmentService> AppointmentServices => Set<AppointmentService>();

    protected override void OnModelCreating(ModelBuilder builder)
    {
        base.OnModelCreating(builder);
        builder.HasDefaultSchema("benobat");
        builder.Entity<Business>().HasIndex(x => x.Slug).IsUnique();
        builder.Entity<Branch>().HasOne(x => x.Business).WithMany(x => x.Branches)
            .HasForeignKey(x => x.BusinessId).OnDelete(DeleteBehavior.Restrict);
        builder.Entity<Service>().HasOne(x => x.Business).WithMany(x => x.Services)
            .HasForeignKey(x => x.BusinessId).OnDelete(DeleteBehavior.Restrict);
        builder.Entity<Resource>().HasOne(x => x.Branch).WithMany(x => x.Resources)
            .HasForeignKey(x => x.BranchId).OnDelete(DeleteBehavior.Restrict);
        // [feature] پیوند اختیاری منبع «عضو تیم» به حساب کاربری.
        builder.Entity<Resource>().HasOne(x => x.User).WithMany()
            .HasForeignKey(x => x.UserId).OnDelete(DeleteBehavior.SetNull);
        builder.Entity<Service>().Property(x => x.Price).HasPrecision(18, 2);
        builder.Entity<BranchService>().Property(x => x.Price).HasPrecision(18, 2);
        builder.Entity<BranchService>().HasIndex(x => new { x.BranchId, x.ServiceId }).IsUnique();
        builder.Entity<BranchService>().HasOne(x => x.Branch).WithMany(x => x.BranchServices).HasForeignKey(x => x.BranchId).OnDelete(DeleteBehavior.Cascade);
        builder.Entity<BranchService>().HasOne(x => x.Service).WithMany(x => x.BranchServices).HasForeignKey(x => x.ServiceId).OnDelete(DeleteBehavior.Cascade);
        builder.Entity<ServiceResource>().HasIndex(x => new { x.ServiceId, x.ResourceId }).IsUnique();
        builder.Entity<ServiceResource>().HasOne(x => x.Service).WithMany(x => x.ServiceResources).HasForeignKey(x => x.ServiceId).OnDelete(DeleteBehavior.Cascade);
        builder.Entity<ServiceResource>().HasOne(x => x.Resource).WithMany(x => x.ServiceResources).HasForeignKey(x => x.ResourceId).OnDelete(DeleteBehavior.Cascade);
        builder.Entity<Appointment>().Property(x => x.FinalPrice).HasPrecision(18, 2);
        builder.Entity<Appointment>().Property(x => x.Status).HasConversion<string>();
        builder.Entity<Appointment>().HasOne(x => x.Branch).WithMany()
            .HasForeignKey(x => x.BranchId).OnDelete(DeleteBehavior.Restrict);
        builder.Entity<Appointment>().HasOne(x => x.Service).WithMany()
            .HasForeignKey(x => x.ServiceId).OnDelete(DeleteBehavior.Restrict);
        builder.Entity<Appointment>().HasOne(x => x.Resource).WithMany()
            .HasForeignKey(x => x.ResourceId).OnDelete(DeleteBehavior.SetNull);
        builder.Entity<Appointment>().HasOne(x => x.Customer).WithMany()
            .HasForeignKey(x => x.CustomerId).OnDelete(DeleteBehavior.Restrict);
        builder.Entity<Appointment>().HasIndex(x => new { x.BranchId, x.StartsAt });
        builder.Entity<AppointmentService>().Property(x => x.Price).HasPrecision(18, 2);
        builder.Entity<AppointmentService>().HasIndex(x => new { x.AppointmentId, x.ServiceId }).IsUnique();
        builder.Entity<AppointmentService>().HasOne(x => x.Appointment).WithMany(x => x.Services).HasForeignKey(x => x.AppointmentId).OnDelete(DeleteBehavior.Cascade);
        builder.Entity<AppointmentService>().HasOne(x => x.Service).WithMany().HasForeignKey(x => x.ServiceId).OnDelete(DeleteBehavior.Restrict);
        builder.Entity<BranchMembership>().HasIndex(x => new { x.BranchId, x.UserId }).IsUnique();
        builder.Entity<BranchMembership>().HasOne(x => x.Branch).WithMany(x => x.Memberships)
            .HasForeignKey(x => x.BranchId).OnDelete(DeleteBehavior.Cascade);
        builder.Entity<BranchMembership>().HasOne(x => x.User).WithMany(x => x.BranchMemberships)
            .HasForeignKey(x => x.UserId).OnDelete(DeleteBehavior.Cascade);
        builder.Entity<AvailabilityRule>().HasOne(x => x.Business).WithMany(x => x.AvailabilityRules)
            .HasForeignKey(x => x.BusinessId).OnDelete(DeleteBehavior.Cascade);
        builder.Entity<AvailabilityRule>().HasOne(x => x.Branch).WithMany(x => x.AvailabilityRules)
            .HasForeignKey(x => x.BranchId).OnDelete(DeleteBehavior.Cascade);
        builder.Entity<AvailabilityRule>().HasOne(x => x.Resource).WithMany(x => x.AvailabilityRules)
            .HasForeignKey(x => x.ResourceId).OnDelete(DeleteBehavior.Cascade);
        builder.Entity<AvailabilityRule>().HasIndex(x => new { x.BusinessId, x.BranchId, x.DayOfWeek, x.StartsAt });
        builder.Entity<Review>().Property(x => x.Status).HasConversion<string>();
        builder.Entity<Review>().HasIndex(x => new { x.BusinessId, x.Status });
        builder.Entity<Review>().HasIndex(x => x.AppointmentId).IsUnique();
        builder.Entity<Review>().HasOne(x => x.Business).WithMany(x => x.Reviews)
            .HasForeignKey(x => x.BusinessId).OnDelete(DeleteBehavior.Restrict);
        builder.Entity<Review>().HasOne(x => x.Branch).WithMany(x => x.Reviews)
            .HasForeignKey(x => x.BranchId).OnDelete(DeleteBehavior.SetNull);
        builder.Entity<Review>().HasOne(x => x.Customer).WithMany(x => x.Reviews)
            .HasForeignKey(x => x.CustomerId).OnDelete(DeleteBehavior.Restrict);
        builder.Entity<Review>().HasOne(x => x.Appointment).WithMany()
            .HasForeignKey(x => x.AppointmentId).OnDelete(DeleteBehavior.SetNull);
        builder.Entity<Review>().ToTable(t => t.HasCheckConstraint("CK_Reviews_Rating", "\"Rating\" BETWEEN 1 AND 5"));

        // [feature] امتیازدهی مجموعه به سرویس‌گیرنده.
        builder.Entity<CustomerReview>().HasIndex(x => x.AppointmentId).IsUnique();
        builder.Entity<CustomerReview>().HasIndex(x => new { x.BusinessId, x.CustomerId });
        builder.Entity<CustomerReview>().HasOne(x => x.Appointment).WithMany()
            .HasForeignKey(x => x.AppointmentId).OnDelete(DeleteBehavior.Cascade);
        builder.Entity<CustomerReview>().HasOne(x => x.Business).WithMany()
            .HasForeignKey(x => x.BusinessId).OnDelete(DeleteBehavior.Restrict);
        builder.Entity<CustomerReview>().HasOne(x => x.Customer).WithMany()
            .HasForeignKey(x => x.CustomerId).OnDelete(DeleteBehavior.Restrict);
        builder.Entity<CustomerReview>().HasOne(x => x.Author).WithMany()
            .HasForeignKey(x => x.AuthorId).OnDelete(DeleteBehavior.Restrict);
        builder.Entity<CustomerReview>()
            .ToTable(t => t.HasCheckConstraint("CK_CustomerReviews_Rating", "\"Rating\" BETWEEN 1 AND 5"));

        foreach (var entityType in builder.Model.GetEntityTypes()
                     .Where(x => typeof(Entity).IsAssignableFrom(x.ClrType)))
        {
            var parameter = System.Linq.Expressions.Expression.Parameter(entityType.ClrType, "entity");
            var deletedAt = System.Linq.Expressions.Expression.Property(parameter, nameof(Entity.DeletedAt));
            var filter = System.Linq.Expressions.Expression.Lambda(
                System.Linq.Expressions.Expression.Equal(deletedAt, System.Linq.Expressions.Expression.Constant(null)),
                parameter);
            builder.Entity(entityType.ClrType).HasQueryFilter(filter);
        }
    }
}
