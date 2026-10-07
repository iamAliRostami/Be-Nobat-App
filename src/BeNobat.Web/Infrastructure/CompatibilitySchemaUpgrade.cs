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

            -- کاتالوگ سراسری خدمات؛ مشخصات تجاری هر ارائه همچنان در Services نگهداری می‌شود.
            CREATE TABLE IF NOT EXISTS benobat."ServiceCatalogItems" (
                "Id" uuid PRIMARY KEY, "CreatedAt" timestamptz NOT NULL, "UpdatedAt" timestamptz NOT NULL,
                "DeletedAt" timestamptz NULL, "Name" text NOT NULL, "Slug" text NOT NULL,
                "Category" text NOT NULL, "Description" text NOT NULL, "SuggestedDurationMinutes" integer NOT NULL,
                "IsPublished" boolean NOT NULL);
            CREATE UNIQUE INDEX IF NOT EXISTS "IX_ServiceCatalogItems_Slug" ON benobat."ServiceCatalogItems" ("Slug");
            CREATE TABLE IF NOT EXISTS benobat."CategoryDefinitions" (
                "Id" uuid PRIMARY KEY, "CreatedAt" timestamptz NOT NULL, "UpdatedAt" timestamptz NOT NULL,
                "DeletedAt" timestamptz NULL, "Name" text NOT NULL, "Kind" text NOT NULL,
                "SortOrder" integer NOT NULL, "IsActive" boolean NOT NULL);
            CREATE UNIQUE INDEX IF NOT EXISTS "IX_CategoryDefinitions_Kind_Name" ON benobat."CategoryDefinitions" ("Kind", "Name");
            CREATE TABLE IF NOT EXISTS benobat."FavoriteBusinesses" (
                "Id" uuid PRIMARY KEY, "CreatedAt" timestamptz NOT NULL, "UpdatedAt" timestamptz NOT NULL,
                "DeletedAt" timestamptz NULL, "UserId" uuid NOT NULL, "BusinessId" uuid NOT NULL,
                CONSTRAINT "FK_FavoriteBusinesses_AspNetUsers_UserId" FOREIGN KEY ("UserId") REFERENCES benobat."AspNetUsers" ("Id") ON DELETE CASCADE,
                CONSTRAINT "FK_FavoriteBusinesses_Businesses_BusinessId" FOREIGN KEY ("BusinessId") REFERENCES benobat."Businesses" ("Id") ON DELETE CASCADE);
            CREATE UNIQUE INDEX IF NOT EXISTS "IX_FavoriteBusinesses_UserId_BusinessId" ON benobat."FavoriteBusinesses" ("UserId", "BusinessId");
            CREATE INDEX IF NOT EXISTS "IX_FavoriteBusinesses_BusinessId" ON benobat."FavoriteBusinesses" ("BusinessId");
            ALTER TABLE benobat."Services" ADD COLUMN IF NOT EXISTS "CatalogItemId" uuid NULL;
            DO $$ BEGIN
                ALTER TABLE benobat."Services" ADD CONSTRAINT "FK_Services_ServiceCatalogItems_CatalogItemId"
                    FOREIGN KEY ("CatalogItemId") REFERENCES benobat."ServiceCatalogItems" ("Id") ON DELETE SET NULL;
            EXCEPTION WHEN duplicate_object THEN NULL; END $$;
            CREATE UNIQUE INDEX IF NOT EXISTS "IX_Services_BusinessId_CatalogItemId" ON benobat."Services" ("BusinessId", "CatalogItemId") WHERE "CatalogItemId" IS NOT NULL;

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
            ALTER TABLE benobat."AvailabilityRules" ADD COLUMN IF NOT EXISTS "EffectiveDate" date NULL;
            ALTER TABLE benobat."AvailabilityRules" DROP CONSTRAINT IF EXISTS "FK_AvailabilityRules_Resources_ResourceId";
            ALTER TABLE benobat."AvailabilityRules" ADD CONSTRAINT "FK_AvailabilityRules_Resources_ResourceId"
                FOREIGN KEY ("ResourceId") REFERENCES benobat."Resources" ("Id") ON DELETE CASCADE;
            CREATE INDEX IF NOT EXISTS "IX_AvailabilityRules_ResourceId" ON benobat."AvailabilityRules" ("ResourceId");
            CREATE INDEX IF NOT EXISTS "IX_AvailabilityRules_SchedulingScope" ON benobat."AvailabilityRules"
                ("BusinessId", "BranchId", "ResourceId", "EffectiveDate", "DayOfWeek", "StartsAt");

            -- Guard business invariants even when data is written outside the web UI.
            DO $$ BEGIN
                ALTER TABLE benobat."Services" ADD CONSTRAINT "CK_Services_Duration" CHECK ("DurationMinutes" > 0) NOT VALID;
            EXCEPTION WHEN duplicate_object THEN NULL; END $$;
            DO $$ BEGIN
                ALTER TABLE benobat."Services" ADD CONSTRAINT "CK_Services_Price" CHECK ("Price" >= 0) NOT VALID;
            EXCEPTION WHEN duplicate_object THEN NULL; END $$;
            DO $$ BEGIN
                ALTER TABLE benobat."Appointments" ADD CONSTRAINT "CK_Appointments_TimeRange" CHECK ("EndsAt" > "StartsAt") NOT VALID;
            EXCEPTION WHEN duplicate_object THEN NULL; END $$;
            DO $$ BEGIN
                ALTER TABLE benobat."Appointments" ADD CONSTRAINT "CK_Appointments_FinalPrice" CHECK ("FinalPrice" >= 0) NOT VALID;
            EXCEPTION WHEN duplicate_object THEN NULL; END $$;

            -- یادداشت مشتری روی نوبت و تنظیم «نیاز به تأیید» برای هر کسب‌وکار.
            ALTER TABLE benobat."Appointments" ADD COLUMN IF NOT EXISTS "CustomerNote" text NOT NULL DEFAULT '';
            ALTER TABLE benobat."Businesses" ADD COLUMN IF NOT EXISTS "RequiresApproval" boolean NOT NULL DEFAULT TRUE;
            CREATE INDEX IF NOT EXISTS "IX_Appointments_ResourceId_StartsAt" ON benobat."Appointments" ("ResourceId", "StartsAt");
            CREATE INDEX IF NOT EXISTS "IX_Appointments_CustomerId_StartsAt" ON benobat."Appointments" ("CustomerId", "StartsAt");

            -- لایه‌ی دفاعی دوم در برابر double-booking: حتی اگر قفل برنامه دور زده شود، دیتابیس دو نوبت
            -- هم‌پوشان فعال را برای یک ارائه‌دهنده نمی‌پذیرد. اگر داده‌ی موجود تداخل داشته باشد یا
            -- اکستنشن در دسترس نباشد فقط یک NOTICE ثبت می‌شود و برنامه بالا می‌آید.
            DO $$ BEGIN
                CREATE EXTENSION IF NOT EXISTS btree_gist;
                ALTER TABLE benobat."Appointments" ADD CONSTRAINT "EX_Appointments_NoResourceOverlap"
                    EXCLUDE USING gist ("ResourceId" WITH =, tstzrange("StartsAt", "EndsAt", '[)') WITH &&)
                    WHERE ("ResourceId" IS NOT NULL AND "DeletedAt" IS NULL AND "Status" <> 'Cancelled');
            EXCEPTION
                WHEN duplicate_object THEN NULL;
                WHEN OTHERS THEN RAISE NOTICE 'EX_Appointments_NoResourceOverlap skipped: %', SQLERRM;
            END $$;
            """, cancellationToken);
}
