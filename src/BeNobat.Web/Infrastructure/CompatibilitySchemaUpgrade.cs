using Microsoft.EntityFrameworkCore;

namespace BeNobat.Web.Infrastructure;

/// <summary>
/// Keeps databases created by the early EnsureCreated-based preview usable until the
/// project moves to reviewed EF migrations. Statements are deliberately idempotent.
/// </summary>
public static class CompatibilitySchemaUpgrade
{
    public static Task ApplyAsync(AppDbContext db, CancellationToken cancellationToken = default) =>
        db.Database.ExecuteSqlRawAsync("""
            CREATE TABLE IF NOT EXISTS benobat."BranchMemberships" (
                "Id" uuid PRIMARY KEY, "CreatedAt" timestamptz NOT NULL, "UpdatedAt" timestamptz NOT NULL,
                "DeletedAt" timestamptz NULL, "BranchId" uuid NOT NULL, "UserId" uuid NOT NULL, "Role" text NOT NULL,
                CONSTRAINT "FK_BranchMemberships_Branches_BranchId" FOREIGN KEY ("BranchId") REFERENCES benobat."Branches" ("Id") ON DELETE CASCADE,
                CONSTRAINT "FK_BranchMemberships_AspNetUsers_UserId" FOREIGN KEY ("UserId") REFERENCES benobat."AspNetUsers" ("Id") ON DELETE CASCADE);
            CREATE UNIQUE INDEX IF NOT EXISTS "IX_BranchMemberships_BranchId_UserId" ON benobat."BranchMemberships" ("BranchId", "UserId");
            CREATE TABLE IF NOT EXISTS benobat."AvailabilityRules" (
                "Id" uuid PRIMARY KEY, "CreatedAt" timestamptz NOT NULL, "UpdatedAt" timestamptz NOT NULL,
                "DeletedAt" timestamptz NULL, "BusinessId" uuid NOT NULL, "BranchId" uuid NULL,
                "DayOfWeek" integer NOT NULL, "StartsAt" time NOT NULL, "EndsAt" time NOT NULL, "IsAvailable" boolean NOT NULL,
                CONSTRAINT "FK_AvailabilityRules_Businesses_BusinessId" FOREIGN KEY ("BusinessId") REFERENCES benobat."Businesses" ("Id") ON DELETE CASCADE,
                CONSTRAINT "FK_AvailabilityRules_Branches_BranchId" FOREIGN KEY ("BranchId") REFERENCES benobat."Branches" ("Id") ON DELETE CASCADE);
            CREATE INDEX IF NOT EXISTS "IX_AvailabilityRules_BusinessId_BranchId_DayOfWeek_StartsAt" ON benobat."AvailabilityRules" ("BusinessId", "BranchId", "DayOfWeek", "StartsAt");
            CREATE TABLE IF NOT EXISTS benobat."Reviews" (
                "Id" uuid PRIMARY KEY, "CreatedAt" timestamptz NOT NULL, "UpdatedAt" timestamptz NOT NULL,
                "DeletedAt" timestamptz NULL, "BusinessId" uuid NOT NULL, "BranchId" uuid NULL, "CustomerId" uuid NOT NULL,
                "AppointmentId" uuid NULL, "Rating" integer NOT NULL, "Comment" text NOT NULL, "ManagerReply" text NULL, "Status" text NOT NULL,
                CONSTRAINT "CK_Reviews_Rating" CHECK ("Rating" BETWEEN 1 AND 5),
                CONSTRAINT "FK_Reviews_Businesses_BusinessId" FOREIGN KEY ("BusinessId") REFERENCES benobat."Businesses" ("Id") ON DELETE RESTRICT,
                CONSTRAINT "FK_Reviews_Branches_BranchId" FOREIGN KEY ("BranchId") REFERENCES benobat."Branches" ("Id") ON DELETE SET NULL,
                CONSTRAINT "FK_Reviews_AspNetUsers_CustomerId" FOREIGN KEY ("CustomerId") REFERENCES benobat."AspNetUsers" ("Id") ON DELETE RESTRICT,
                CONSTRAINT "FK_Reviews_Appointments_AppointmentId" FOREIGN KEY ("AppointmentId") REFERENCES benobat."Appointments" ("Id") ON DELETE SET NULL);
            CREATE INDEX IF NOT EXISTS "IX_Reviews_BusinessId_Status" ON benobat."Reviews" ("BusinessId", "Status");
            CREATE UNIQUE INDEX IF NOT EXISTS "IX_Reviews_AppointmentId" ON benobat."Reviews" ("AppointmentId") WHERE "AppointmentId" IS NOT NULL;
            """, cancellationToken);
}
