using System.ComponentModel.DataAnnotations;
using System.Security.Claims;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using BeNobat.Web.Security;
using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;
using Npgsql;

namespace BeNobat.Web.Application;

public enum CredentialChangeFailure { SessionExpired, InvalidInput, DuplicateEmail, PasswordMismatch, WeakPassword, SaveFailed }

public sealed record CredentialChangeResult(AppUser? User, CredentialChangeFailure? Failure, bool Changed = true)
{
    public bool Success => User is not null && Failure is null;
}

/// <summary>Credential changes recheck the persisted cookie session and commit all Identity writes together.</summary>
public static class AccountCredentialChanges
{
    public static async Task<CredentialChangeResult> ChangeEmailAsync(AppDbContext db, UserManager<AppUser> users,
        ClaimsPrincipal principal, string? newEmail, CancellationToken cancellationToken = default,
        Func<AppUser, CancellationToken, Task>? beforeCommit = null)
    {
        var email = newEmail?.Trim() ?? "";
        if (email.Length is 0 or > 256 || email.Any(char.IsControl) || !new EmailAddressAttribute().IsValid(email))
            return Failed(CredentialChangeFailure.InvalidInput);
        await using var transaction = await db.Database.BeginTransactionAsync(cancellationToken);
        var user = await LockedSessionUserAsync(db, principal, cancellationToken);
        if (user is null) return Failed(CredentialChangeFailure.SessionExpired);
        if (string.Equals(email, user.Email, StringComparison.OrdinalIgnoreCase))
        {
            if (beforeCommit is not null) await beforeCommit(user, cancellationToken);
            await transaction.CommitAsync(cancellationToken);
            return new(user, null, Changed: false);
        }
        var existing = await users.FindByEmailAsync(email);
        if (existing is not null && existing.Id != user.Id) return Failed(CredentialChangeFailure.DuplicateEmail);

        user.Email = email;
        user.UserName = email;
        user.EmailConfirmed = false;
        try
        {
            var updated = await users.UpdateAsync(user);
            if (!updated.Succeeded) return Failed(EmailFailure(updated));
            // Stamp rotation can fail too. The preceding email/username update must roll back with it.
            var stamped = await users.UpdateSecurityStampAsync(user);
            if (!stamped.Succeeded) return Failed(CredentialChangeFailure.SaveFailed);
            if (beforeCommit is not null) await beforeCommit(user, cancellationToken);
            await transaction.CommitAsync(cancellationToken);
            return new(user, null);
        }
        catch (DbUpdateException exception) when (exception.InnerException is PostgresException { SqlState: PostgresErrorCodes.UniqueViolation })
        {
            await transaction.RollbackAsync(cancellationToken);
            return Failed(CredentialChangeFailure.DuplicateEmail);
        }
    }

    public static async Task<CredentialChangeResult> ChangePasswordAsync(AppDbContext db, UserManager<AppUser> users,
        ClaimsPrincipal principal, string? currentPassword, string? newPassword, CancellationToken cancellationToken = default,
        Func<AppUser, CancellationToken, Task>? beforeCommit = null)
    {
        if (string.IsNullOrEmpty(currentPassword) || currentPassword.Length > 256 || string.IsNullOrEmpty(newPassword) || newPassword.Length > 256)
            return Failed(CredentialChangeFailure.InvalidInput);
        await using var transaction = await db.Database.BeginTransactionAsync(cancellationToken);
        var user = await LockedSessionUserAsync(db, principal, cancellationToken);
        if (user is null) return Failed(CredentialChangeFailure.SessionExpired);
        var result = await users.ChangePasswordAsync(user, currentPassword, newPassword);
        if (!result.Succeeded)
        {
            if (result.Errors.Any(x => x.Code == nameof(IdentityErrorDescriber.PasswordMismatch))) return Failed(CredentialChangeFailure.PasswordMismatch);
            if (result.Errors.Any(x => x.Code.StartsWith("Password", StringComparison.Ordinal))) return Failed(CredentialChangeFailure.WeakPassword);
            return Failed(CredentialChangeFailure.SaveFailed);
        }
        if (beforeCommit is not null) await beforeCommit(user, cancellationToken);
        await transaction.CommitAsync(cancellationToken);
        return new(user, null);
    }

    private static async Task<AppUser?> LockedSessionUserAsync(AppDbContext db, ClaimsPrincipal principal, CancellationToken cancellationToken)
    {
        if (UserContext.IdOf(principal) is not Guid id) return null;
        // Serialize against password resets, disable/enable and other credential changes before checking the session.
        await db.Database.ExecuteSqlAsync($"SELECT 1 FROM benobat.\"AspNetUsers\" WHERE \"Id\" = {id} FOR UPDATE", cancellationToken);
        var fresh = await UserContext.CurrentSessionUsers(db, principal).FirstOrDefaultAsync(cancellationToken);
        if (fresh is null) return null;
        // Cookie validation may already have tracked an older user instance in this HTTP scope.
        foreach (var entry in db.ChangeTracker.Entries<AppUser>().Where(x => x.Entity.Id == id).ToList())
            entry.State = EntityState.Detached;
        db.Attach(fresh);
        return fresh;
    }

    private static CredentialChangeFailure EmailFailure(IdentityResult result) =>
        result.Errors.Any(x => x.Code is nameof(IdentityErrorDescriber.DuplicateEmail) or nameof(IdentityErrorDescriber.DuplicateUserName))
            ? CredentialChangeFailure.DuplicateEmail : CredentialChangeFailure.InvalidInput;

    private static CredentialChangeResult Failed(CredentialChangeFailure failure) => new(null, failure);
}
