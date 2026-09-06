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

    protected override void OnModelCreating(ModelBuilder builder)
    {
        base.OnModelCreating(builder);
        builder.HasDefaultSchema("benobat");
        builder.Entity<Business>().HasIndex(x => x.Slug).IsUnique();
        builder.Entity<Branch>().HasOne(x => x.Business).WithMany(x => x.Branches)
            .HasForeignKey(x => x.BusinessId).OnDelete(DeleteBehavior.Restrict);
        builder.Entity<Service>().Property(x => x.Price).HasPrecision(18, 2);
        builder.Entity<Appointment>().Property(x => x.FinalPrice).HasPrecision(18, 2);
        builder.Entity<Appointment>().Property(x => x.Status).HasConversion<string>();

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
