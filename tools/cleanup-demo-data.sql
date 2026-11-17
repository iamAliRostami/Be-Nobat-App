-- ==============================================================================
-- اسکریپت پاکسازی اطلاعات دمو برای انتشار عمومی بنوبت (BeNobat Production Cleanup)
-- این اسکریپت تمام داده‌های تستی را حذف کرده و فقط حساب مدیر و داده‌های پایه را نگه می‌دارد.
-- ==============================================================================

BEGIN;

-- ۱. حذف نوبت‌ها و خدمات نوبت
DELETE FROM benobat."AppointmentServices";
DELETE FROM benobat."Appointments";

-- ۲. حذف قوانین تقویم و ساعات کاری
DELETE FROM benobat."AvailabilityRules";

-- ۳. حذف تخصیص منابع، پرسنل و خدمات
DELETE FROM benobat."ServiceResources";
DELETE FROM benobat."BranchServices";
DELETE FROM benobat."BranchMemberships";
DELETE FROM benobat."ResourceGalleryItems";
DELETE FROM benobat."Resources";
DELETE FROM benobat."Services";

-- ۴. حذف تصاویر گالری، شعب و کسب‌وکارها
DELETE FROM benobat."BranchGalleryItems";
DELETE FROM benobat."Branches";
DELETE FROM benobat."BusinessGalleryItems";
DELETE FROM benobat."FavoriteBusinesses";
DELETE FROM benobat."CustomerReviews";
DELETE FROM benobat."Reviews";
DELETE FROM benobat."BusinessRequests";
DELETE FROM benobat."Businesses";

-- ۵. حذف پیام‌های تماس، اعلان‌ها و درخواست‌های ریست پسورد تستی
DELETE FROM benobat."ContactMessages";
DELETE FROM benobat."AdminMessageReads";
DELETE FROM benobat."AdminBroadcastMessages";
DELETE FROM benobat."PasswordResetRequests";

-- ۶. حذف کلیه کاربران غیر ادمین (فقط حساب دارای نقش PlatformAdmin حفظ می‌شود)
DELETE FROM benobat."AspNetUserRoles"
WHERE "UserId" NOT IN (
    SELECT ur."UserId"
    FROM benobat."AspNetUserRoles" ur
    INNER JOIN benobat."AspNetRoles" r ON ur."RoleId" = r."Id"
    WHERE r."Name" = 'PlatformAdmin'
);

DELETE FROM benobat."AspNetUserTokens"
WHERE "UserId" NOT IN (
    SELECT ur."UserId"
    FROM benobat."AspNetUserRoles" ur
    INNER JOIN benobat."AspNetRoles" r ON ur."RoleId" = r."Id"
    WHERE r."Name" = 'PlatformAdmin'
);

DELETE FROM benobat."AspNetUserClaims"
WHERE "UserId" NOT IN (
    SELECT ur."UserId"
    FROM benobat."AspNetUserRoles" ur
    INNER JOIN benobat."AspNetRoles" r ON ur."RoleId" = r."Id"
    WHERE r."Name" = 'PlatformAdmin'
);

DELETE FROM benobat."AspNetUserLogins"
WHERE "UserId" NOT IN (
    SELECT ur."UserId"
    FROM benobat."AspNetUserRoles" ur
    INNER JOIN benobat."AspNetRoles" r ON ur."RoleId" = r."Id"
    WHERE r."Name" = 'PlatformAdmin'
);

DELETE FROM benobat."AspNetUsers"
WHERE "Id" NOT IN (
    SELECT ur."UserId"
    FROM benobat."AspNetUserRoles" ur
    INNER JOIN benobat."AspNetRoles" r ON ur."RoleId" = r."Id"
    WHERE r."Name" = 'PlatformAdmin'
);

COMMIT;
