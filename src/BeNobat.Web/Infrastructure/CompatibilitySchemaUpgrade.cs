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

            -- [fix] عکس پروفایل کاربر (ذخیره در دیتابیس، نه فایل‌سیستم کانتینر).
            ALTER TABLE benobat."AspNetUsers" ADD COLUMN IF NOT EXISTS "AvatarContentType" text NULL;
            ALTER TABLE benobat."AspNetUsers" ADD COLUMN IF NOT EXISTS "AvatarData" bytea NULL;

            -- [feature] اتصال منبع «عضو تیم» به حساب کاربری تا در شعب از لیست کشویی انتخاب شود.
            ALTER TABLE benobat."Resources" ADD COLUMN IF NOT EXISTS "UserId" uuid NULL;
            ALTER TABLE benobat."Resources" DROP CONSTRAINT IF EXISTS "FK_Resources_AspNetUsers_UserId";
            ALTER TABLE benobat."Resources" ADD CONSTRAINT "FK_Resources_AspNetUsers_UserId"
                FOREIGN KEY ("UserId") REFERENCES benobat."AspNetUsers" ("Id") ON DELETE SET NULL;
            CREATE INDEX IF NOT EXISTS "IX_Resources_UserId" ON benobat."Resources" ("UserId");

            -- [feature] امتیاز و نظر مجموعه درباره‌ی سرویس‌گیرنده.
            CREATE TABLE IF NOT EXISTS benobat."CustomerReviews" (
                "Id" uuid PRIMARY KEY, "CreatedAt" timestamptz NOT NULL, "UpdatedAt" timestamptz NOT NULL,
                "DeletedAt" timestamptz NULL, "AppointmentId" uuid NOT NULL, "BusinessId" uuid NOT NULL,
                "CustomerId" uuid NOT NULL, "AuthorId" uuid NOT NULL, "Rating" integer NOT NULL, "Comment" text NOT NULL,
                CONSTRAINT "CK_CustomerReviews_Rating" CHECK ("Rating" BETWEEN 1 AND 5),
                CONSTRAINT "FK_CustomerReviews_Appointments_AppointmentId" FOREIGN KEY ("AppointmentId") REFERENCES benobat."Appointments" ("Id") ON DELETE CASCADE,
                CONSTRAINT "FK_CustomerReviews_Businesses_BusinessId" FOREIGN KEY ("BusinessId") REFERENCES benobat."Businesses" ("Id") ON DELETE RESTRICT,
                CONSTRAINT "FK_CustomerReviews_AspNetUsers_CustomerId" FOREIGN KEY ("CustomerId") REFERENCES benobat."AspNetUsers" ("Id") ON DELETE RESTRICT,
                CONSTRAINT "FK_CustomerReviews_AspNetUsers_AuthorId" FOREIGN KEY ("AuthorId") REFERENCES benobat."AspNetUsers" ("Id") ON DELETE RESTRICT);
            CREATE UNIQUE INDEX IF NOT EXISTS "IX_CustomerReviews_AppointmentId" ON benobat."CustomerReviews" ("AppointmentId");
            CREATE INDEX IF NOT EXISTS "IX_CustomerReviews_BusinessId_CustomerId" ON benobat."CustomerReviews" ("BusinessId", "CustomerId");

            -- مدل صریح خدمت شعبه، ارائه‌دهنده خدمت و رزرو چندخدمتی.
            CREATE TABLE IF NOT EXISTS benobat."BranchServices" (
                "Id" uuid PRIMARY KEY, "CreatedAt" timestamptz NOT NULL, "UpdatedAt" timestamptz NOT NULL, "DeletedAt" timestamptz NULL,
                "BranchId" uuid NOT NULL, "ServiceId" uuid NOT NULL, "Price" numeric(18,2) NULL,
                CONSTRAINT "FK_BranchServices_Branches_BranchId" FOREIGN KEY ("BranchId") REFERENCES benobat."Branches" ("Id") ON DELETE CASCADE,
                CONSTRAINT "FK_BranchServices_Services_ServiceId" FOREIGN KEY ("ServiceId") REFERENCES benobat."Services" ("Id") ON DELETE CASCADE);
            CREATE UNIQUE INDEX IF NOT EXISTS "IX_BranchServices_BranchId_ServiceId" ON benobat."BranchServices" ("BranchId", "ServiceId");
            CREATE INDEX IF NOT EXISTS "IX_BranchServices_ServiceId" ON benobat."BranchServices" ("ServiceId");

            CREATE TABLE IF NOT EXISTS benobat."ServiceResources" (
                "Id" uuid PRIMARY KEY, "CreatedAt" timestamptz NOT NULL, "UpdatedAt" timestamptz NOT NULL, "DeletedAt" timestamptz NULL,
                "ServiceId" uuid NOT NULL, "ResourceId" uuid NOT NULL,
                CONSTRAINT "FK_ServiceResources_Services_ServiceId" FOREIGN KEY ("ServiceId") REFERENCES benobat."Services" ("Id") ON DELETE CASCADE,
                CONSTRAINT "FK_ServiceResources_Resources_ResourceId" FOREIGN KEY ("ResourceId") REFERENCES benobat."Resources" ("Id") ON DELETE CASCADE);
            CREATE UNIQUE INDEX IF NOT EXISTS "IX_ServiceResources_ServiceId_ResourceId" ON benobat."ServiceResources" ("ServiceId", "ResourceId");
            CREATE INDEX IF NOT EXISTS "IX_ServiceResources_ResourceId" ON benobat."ServiceResources" ("ResourceId");

            CREATE TABLE IF NOT EXISTS benobat."AppointmentServices" (
                "Id" uuid PRIMARY KEY, "CreatedAt" timestamptz NOT NULL, "UpdatedAt" timestamptz NOT NULL, "DeletedAt" timestamptz NULL,
                "AppointmentId" uuid NOT NULL, "ServiceId" uuid NOT NULL, "DurationMinutes" integer NOT NULL, "Price" numeric(18,2) NOT NULL,
                CONSTRAINT "FK_AppointmentServices_Appointments_AppointmentId" FOREIGN KEY ("AppointmentId") REFERENCES benobat."Appointments" ("Id") ON DELETE CASCADE,
                CONSTRAINT "FK_AppointmentServices_Services_ServiceId" FOREIGN KEY ("ServiceId") REFERENCES benobat."Services" ("Id") ON DELETE RESTRICT);
            CREATE UNIQUE INDEX IF NOT EXISTS "IX_AppointmentServices_AppointmentId_ServiceId" ON benobat."AppointmentServices" ("AppointmentId", "ServiceId");
            CREATE INDEX IF NOT EXISTS "IX_AppointmentServices_ServiceId" ON benobat."AppointmentServices" ("ServiceId");

            ALTER TABLE benobat."AvailabilityRules" ADD COLUMN IF NOT EXISTS "ResourceId" uuid NULL;
            ALTER TABLE benobat."AvailabilityRules" DROP CONSTRAINT IF EXISTS "FK_AvailabilityRules_Resources_ResourceId";
            ALTER TABLE benobat."AvailabilityRules" ADD CONSTRAINT "FK_AvailabilityRules_Resources_ResourceId"
                FOREIGN KEY ("ResourceId") REFERENCES benobat."Resources" ("Id") ON DELETE CASCADE;
            CREATE INDEX IF NOT EXISTS "IX_AvailabilityRules_ResourceId" ON benobat."AvailabilityRules" ("ResourceId");
            """, cancellationToken);
}
