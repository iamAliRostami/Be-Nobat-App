using Microsoft.AspNetCore.Identity;

namespace BeNobat.Web.Infrastructure;

/// <summary>
/// [fix] پیام‌های خطای پیش‌فرض ASP.NET Identity انگلیسی هستند و مستقیماً در فرم
/// ثبت‌نام نمایش داده می‌شدند (مثلاً «Username 'x' is already taken.»).
/// این کلاس همان پیام‌ها را فارسی می‌کند و چون در سطح Identity ثبت می‌شود،
/// هر جایی که UserManager خطا برگرداند - ثبت‌نام، ساخت کاربر در پنل مدیریت،
/// تغییر ایمیل یا رمز - پیام فارسی خواهد بود.
/// در این برنامه UserName همان Email است، بنابراین خطای تکراری بودن نام کاربری
/// هم با زبان «ایمیل» بیان می‌شود.
/// </summary>
public sealed class PersianIdentityErrorDescriber : IdentityErrorDescriber
{
    private static IdentityError Error(string code, string description) => new() { Code = code, Description = description };

    public override IdentityError DefaultError() =>
        Error(nameof(DefaultError), "خطای ناشناخته‌ای رخ داد؛ لطفاً دوباره تلاش کنید.");

    public override IdentityError ConcurrencyFailure() =>
        Error(nameof(ConcurrencyFailure), "این رکورد هم‌زمان توسط کاربر دیگری تغییر کرده است؛ صفحه را تازه کنید.");

    public override IdentityError PasswordMismatch() =>
        Error(nameof(PasswordMismatch), "رمز عبور فعلی درست نیست.");

    public override IdentityError InvalidToken() =>
        Error(nameof(InvalidToken), "کد تأیید نامعتبر یا منقضی شده است.");

    public override IdentityError LoginAlreadyAssociated() =>
        Error(nameof(LoginAlreadyAssociated), "برای این حساب قبلاً ورودی ثبت شده است.");

    public override IdentityError InvalidUserName(string? userName) =>
        Error(nameof(InvalidUserName), "ایمیل واردشده معتبر نیست.");

    public override IdentityError InvalidEmail(string? email) =>
        Error(nameof(InvalidEmail), "ایمیل واردشده معتبر نیست.");

    public override IdentityError DuplicateUserName(string? userName) =>
        Error(nameof(DuplicateUserName), "این ایمیل قبلاً ثبت شده است؛ وارد شوید یا ایمیل دیگری بزنید.");

    public override IdentityError DuplicateEmail(string? email) =>
        Error(nameof(DuplicateEmail), "این ایمیل قبلاً ثبت شده است؛ وارد شوید یا ایمیل دیگری بزنید.");

    public override IdentityError InvalidRoleName(string? role) =>
        Error(nameof(InvalidRoleName), "نقش انتخاب‌شده معتبر نیست.");

    public override IdentityError DuplicateRoleName(string? role) =>
        Error(nameof(DuplicateRoleName), "این نقش از قبل وجود دارد.");

    public override IdentityError UserAlreadyHasPassword() =>
        Error(nameof(UserAlreadyHasPassword), "برای این حساب رمز عبور تعریف شده است.");

    public override IdentityError UserLockoutNotEnabled() =>
        Error(nameof(UserLockoutNotEnabled), "قفل کردن این حساب فعال نیست.");

    public override IdentityError UserAlreadyInRole(string? role) =>
        Error(nameof(UserAlreadyInRole), "کاربر از قبل این نقش را دارد.");

    public override IdentityError UserNotInRole(string? role) =>
        Error(nameof(UserNotInRole), "کاربر این نقش را ندارد.");

    public override IdentityError PasswordTooShort(int length) =>
        Error(nameof(PasswordTooShort), $"رمز عبور باید حداقل {length} کاراکتر باشد.");

    public override IdentityError PasswordRequiresUniqueChars(int uniqueChars) =>
        Error(nameof(PasswordRequiresUniqueChars), $"رمز عبور باید حداقل {uniqueChars} کاراکتر متفاوت داشته باشد.");

    public override IdentityError PasswordRequiresNonAlphanumeric() =>
        Error(nameof(PasswordRequiresNonAlphanumeric), "رمز عبور باید حداقل یک کاراکتر غیرحرفی-عددی داشته باشد.");

    public override IdentityError PasswordRequiresDigit() =>
        Error(nameof(PasswordRequiresDigit), "رمز عبور باید حداقل یک رقم داشته باشد.");

    public override IdentityError PasswordRequiresLower() =>
        Error(nameof(PasswordRequiresLower), "رمز عبور باید حداقل یک حرف کوچک انگلیسی داشته باشد.");

    public override IdentityError PasswordRequiresUpper() =>
        Error(nameof(PasswordRequiresUpper), "رمز عبور باید حداقل یک حرف بزرگ انگلیسی داشته باشد.");

    public override IdentityError RecoveryCodeRedemptionFailed() =>
        Error(nameof(RecoveryCodeRedemptionFailed), "کد بازیابی معتبر نیست.");
}
