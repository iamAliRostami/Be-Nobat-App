using System.ComponentModel.DataAnnotations;
using BeNobat.Web.Domain;
using BeNobat.Web.Application;
using BeNobat.Web.Infrastructure;
using BeNobat.Web.Security;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Identity;

namespace BeNobat.Web.Api;

public static class AuthenticationEndpoints
{
    public static RouteGroupBuilder MapMobileAuthentication(this RouteGroupBuilder root)
    {
        var auth = root.MapGroup("/auth");
        auth.MapPost("/login", async (LoginInput input, UserManager<AppUser> users, SignInManager<AppUser> signIn, MobileTokenService tokens) =>
        {
            if (!Email(input.Email) || string.IsNullOrEmpty(input.Password) || input.Password.Length > 256) return ApiResults.Error("invalid_input", "A valid email and password are required.");
            var user = await users.FindByEmailAsync(input.Email!.Trim());
            if (user is null) return ApiResults.Error("invalid_credentials", "Email or password is incorrect.", 401);
            if (user.LockoutEnabled && user.LockoutEnd > DateTimeOffset.UtcNow) return ApiResults.Error("account_locked", "This account is temporarily unavailable.", 401);
            var result = await signIn.CheckPasswordSignInAsync(user, input.Password, lockoutOnFailure: true);
            if (!result.Succeeded) return ApiResults.Error(result.IsLockedOut ? "account_locked" : "invalid_credentials", "Email or password is incorrect.", 401);
            return Results.Ok(await tokens.IssueAsync(user));
        }).AllowAnonymous().RequireRateLimiting("mobile-auth");
        auth.MapPost("/register", async (RegisterInput input, UserManager<AppUser> users, MobileTokenService tokens) =>
        {
            if (!Email(input.Email) || !UserInputValidation.TryNormalizeDisplayName(input.DisplayName, out var displayName) ||
                string.IsNullOrEmpty(input.Password) || input.Password.Length > 256 || !UserInputValidation.TryNormalizeIranianMobile(input.PhoneNumber, out var phone))
                return ApiResults.Error("invalid_input", "A name, valid email, Iranian mobile number and password are required.");
            var email = input.Email!.Trim();
            var user = new AppUser { Id = Guid.CreateVersion7(), UserName = email, Email = email, DisplayName = displayName, PhoneNumber = phone, LockoutEnabled = true };
            var result = await users.CreateAsync(user, input.Password);
            if (!result.Succeeded) return IdentityError(result);
            var role = await users.AddToRoleAsync(user, AppRoles.Customer);
            if (!role.Succeeded) { await users.DeleteAsync(user); return IdentityError(role); }
            return Results.Created("/api/v1/me", await tokens.IssueAsync(user));
        }).AllowAnonymous().RequireRateLimiting("mobile-auth");
        auth.MapPost("/logout", async (HttpContext http, UserManager<AppUser> users) =>
        {
            var user = await users.GetUserAsync(http.User);
            if (user is not null && http.User.FindFirst(MobileAuthentication.SessionClaim)?.Value is { } session)
                await users.RemoveAuthenticationTokenAsync(user, MobileAuthentication.Scheme, session);
            return Results.NoContent();
        }).RequireAuthorization(new AuthorizeAttribute { AuthenticationSchemes = MobileAuthentication.Scheme });

        var me = root.MapGroup("/me").RequireAuthorization(new AuthorizeAttribute { AuthenticationSchemes = MobileAuthentication.Scheme });
        me.MapGet("", async (HttpContext http, UserManager<AppUser> users, MobileTokenService tokens) =>
            Results.Ok(await tokens.UserAsync((await users.GetUserAsync(http.User))!)));
        me.MapPatch("", async (ProfileInput input, HttpContext http, UserManager<AppUser> users, MobileTokenService tokens) =>
        {
            if (!UserInputValidation.TryNormalizeDisplayName(input.DisplayName, out var displayName) || !UserInputValidation.TryNormalizeIranianMobile(input.PhoneNumber, out var phone))
                return ApiResults.Error("invalid_input", "A name and valid Iranian mobile number are required.");
            var user = (await users.GetUserAsync(http.User))!;
            user.DisplayName = displayName; user.PhoneNumber = phone;
            var result = await users.UpdateAsync(user);
            return result.Succeeded ? Results.Ok(await tokens.UserAsync(user)) : IdentityError(result);
        });
        me.MapPost("/password", async (PasswordInput input, HttpContext http, AppDbContext db, UserManager<AppUser> users, MobileTokenService tokens, CancellationToken ct) =>
        {
            if (string.IsNullOrEmpty(input.CurrentPassword) || input.CurrentPassword.Length > 256 || string.IsNullOrEmpty(input.NewPassword) || input.NewPassword.Length > 256) return ApiResults.Error("invalid_input", "Both passwords are required.");
            MobileSession? session = null;
            var result = await AccountCredentialChanges.ChangePasswordAsync(db, users, http.User, input.CurrentPassword, input.NewPassword, ct,
                async (user, _) => { session = await tokens.IssueAsync(user); });
            return result.Success ? Results.Ok(session) : CredentialError(result.Failure!.Value);
        });
        me.MapPost("/email", async (EmailInput input, HttpContext http, AppDbContext db, UserManager<AppUser> users, MobileTokenService tokens, CancellationToken ct) =>
        {
            if (!Email(input.Email) || string.IsNullOrEmpty(input.CurrentPassword) || input.CurrentPassword.Length > 256) return ApiResults.Error("invalid_input", "A valid email and current password are required.");
            var user = (await users.GetUserAsync(http.User))!;
            if (!await users.CheckPasswordAsync(user, input.CurrentPassword)) return ApiResults.Error("password_mismatch", "Current password is incorrect.");
            MobileSession? session = null;
            var result = await AccountCredentialChanges.ChangeEmailAsync(db, users, http.User, input.Email, ct,
                async (freshUser, _) => { session = await tokens.IssueAsync(freshUser); });
            return result.Success ? Results.Ok(session) : CredentialError(result.Failure!.Value);
        });
        me.MapPost("/avatar", async (AvatarInput input, HttpContext http, UserManager<AppUser> users, MobileTokenService tokens) =>
        {
            if (input.DataBase64 is null || input.DataBase64.Length > (UserAvatar.MaxBytes * 4 / 3 + 8) || !UserAvatar.AllowedContentTypes.Contains(input.ContentType)) return ApiResults.Error("invalid_avatar", "Use a PNG, JPEG or WebP image under 2 MB.");
            byte[] bytes;
            try { bytes = Convert.FromBase64String(input.DataBase64); } catch (FormatException) { return ApiResults.Error("invalid_avatar", "The image is invalid."); }
            if (bytes.Length is 0 || bytes.Length > UserAvatar.MaxBytes || !ValidImage(bytes, input.ContentType!)) return ApiResults.Error("invalid_avatar", "The image is invalid or too large.");
            var user = (await users.GetUserAsync(http.User))!; user.AvatarContentType = input.ContentType; user.AvatarData = bytes;
            var result = await users.UpdateAsync(user);
            return result.Succeeded ? Results.Ok(await tokens.UserAsync(user)) : IdentityError(result);
        });
        me.MapDelete("/avatar", async (HttpContext http, UserManager<AppUser> users, MobileTokenService tokens) =>
        {
            var user = (await users.GetUserAsync(http.User))!; user.AvatarContentType = null; user.AvatarData = null;
            var result = await users.UpdateAsync(user);
            return result.Succeeded ? Results.Ok(await tokens.UserAsync(user)) : IdentityError(result);
        });
        return root;
    }
    private static bool Email(string? value) => value is { Length: > 0 and <= 256 } && new EmailAddressAttribute().IsValid(value.Trim());
    private static IResult IdentityError(IdentityResult result) => ApiResults.Error("invalid_input", "The account details could not be saved.", fields: result.Errors.Select(e => new { code = e.Code, message = e.Description }).ToArray());
    private static IResult CredentialError(CredentialChangeFailure failure) => failure switch
    {
        CredentialChangeFailure.SessionExpired => ApiResults.Error("unauthorized", "This session has expired. Sign in again.", 401),
        CredentialChangeFailure.DuplicateEmail => ApiResults.Error("duplicate_email", "This email is already registered.", 409),
        CredentialChangeFailure.PasswordMismatch => ApiResults.Error("password_mismatch", "Current password is incorrect."),
        CredentialChangeFailure.WeakPassword => ApiResults.Error("weak_password", "Use at least six characters, a lowercase letter and a digit."),
        CredentialChangeFailure.SaveFailed => ApiResults.Error("conflict", "The account changed. Refresh and try again.", 409),
        _ => ApiResults.Error("invalid_input", "The account details are invalid."),
    };
    public static bool ValidImage(byte[] bytes, string contentType) => UserAvatar.ValidImage(bytes, contentType);
}
public sealed record LoginInput(string? Email, string? Password);
public sealed record RegisterInput(string? DisplayName, string? Email, string? PhoneNumber, string? Password);
public sealed record ProfileInput(string? DisplayName, string? PhoneNumber);
public sealed record PasswordInput(string? CurrentPassword, string? NewPassword);
public sealed record EmailInput(string? Email, string? CurrentPassword);
public sealed record AvatarInput(string? ContentType, string? DataBase64);
