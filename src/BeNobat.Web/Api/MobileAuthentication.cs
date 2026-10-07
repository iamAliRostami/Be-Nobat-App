using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;
using System.Text.Encodings.Web;
using System.Text.Json;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.DataProtection;
using Microsoft.AspNetCore.Identity;
using Microsoft.Extensions.Options;

namespace BeNobat.Web.Api;

public static class MobileAuthentication
{
    public const string Scheme = "MobileBearer";
    public const string SessionClaim = "benobat:mobile-session";
    public static readonly TimeSpan Lifetime = TimeSpan.FromDays(7);
}

public sealed record MobileSession(string AccessToken, string TokenType, DateTimeOffset ExpiresAt, UserDto User);
internal sealed record MobileTicket(Guid UserId, string SessionId, string SecurityStamp, DateTimeOffset ExpiresAt);

/// <summary>Opaque protected credentials backed by Identity's token store. Logout and security-stamp changes revoke them.</summary>
public sealed class MobileTokenService(IDataProtectionProvider protection, UserManager<AppUser> users)
{
    private readonly IDataProtector protector = protection.CreateProtector("BeNobat.MobileBearer.v1");
    internal MobileTicket? Read(string token)
    {
        try { return JsonSerializer.Deserialize<MobileTicket>(protector.Unprotect(token)); }
        catch (Exception ex) when (ex is CryptographicException or JsonException or ArgumentException) { return null; }
    }
    public async Task<MobileSession> IssueAsync(AppUser user)
    {
        var expiresAt = DateTimeOffset.UtcNow.Add(MobileAuthentication.Lifetime);
        var ticket = new MobileTicket(user.Id, Convert.ToHexString(RandomNumberGenerator.GetBytes(24)), await users.GetSecurityStampAsync(user), expiresAt);
        var token = protector.Protect(JsonSerializer.Serialize(ticket));
        var result = await users.SetAuthenticationTokenAsync(user, MobileAuthentication.Scheme, ticket.SessionId, Hash(token));
        if (!result.Succeeded) throw new InvalidOperationException("The mobile session could not be persisted.");
        return new MobileSession(token, "Bearer", expiresAt, await UserAsync(user));
    }
    public async Task<UserDto> UserAsync(AppUser user) => new(user.Id, user.DisplayName, user.Email, user.PhoneNumber,
        UserAvatar.Has(user) ? UserAvatar.Url(user) : null, (await users.GetRolesAsync(user)).ToArray());
    internal static string Hash(string token) => Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(token)));
}

public sealed class MobileBearerHandler(IOptionsMonitor<AuthenticationSchemeOptions> options, ILoggerFactory logger,
    UrlEncoder encoder, UserManager<AppUser> users, IUserClaimsPrincipalFactory<AppUser> principals, MobileTokenService tokens)
    : AuthenticationHandler<AuthenticationSchemeOptions>(options, logger, encoder)
{
    protected override async Task<AuthenticateResult> HandleAuthenticateAsync()
    {
        var header = Request.Headers.Authorization.ToString();
        if (!header.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase)) return AuthenticateResult.NoResult();
        var token = header[7..].Trim();
        if (token.Length is 0 or > 8192) return AuthenticateResult.Fail("Invalid credential.");
        var ticket = tokens.Read(token);
        if (ticket is null || ticket.ExpiresAt <= DateTimeOffset.UtcNow) return AuthenticateResult.Fail("Expired credential.");
        var user = await users.FindByIdAsync(ticket.UserId.ToString());
        if (user is null || (user.LockoutEnabled && user.LockoutEnd is { } until && until > DateTimeOffset.UtcNow) ||
            !string.Equals(user.SecurityStamp, ticket.SecurityStamp, StringComparison.Ordinal)) return AuthenticateResult.Fail("Revoked credential.");
        var stored = await users.GetAuthenticationTokenAsync(user, MobileAuthentication.Scheme, ticket.SessionId);
        if (stored is null || !CryptographicOperations.FixedTimeEquals(Encoding.ASCII.GetBytes(stored), Encoding.ASCII.GetBytes(MobileTokenService.Hash(token))))
            return AuthenticateResult.Fail("Revoked credential.");
        // Fresh Identity roles on every request prevent stale administrative claims from granting access.
        var principal = await principals.CreateAsync(user);
        ((ClaimsIdentity)principal.Identity!).AddClaim(new Claim(MobileAuthentication.SessionClaim, ticket.SessionId));
        return AuthenticateResult.Success(new AuthenticationTicket(principal, Scheme.Name));
    }
    protected override Task HandleChallengeAsync(AuthenticationProperties properties)
    {
        Response.StatusCode = StatusCodes.Status401Unauthorized;
        Response.Headers.WWWAuthenticate = "Bearer";
        return Response.WriteAsJsonAsync(new ApiError("unauthorized", "Sign in to continue."));
    }
    protected override Task HandleForbiddenAsync(AuthenticationProperties properties)
    {
        Response.StatusCode = StatusCodes.Status403Forbidden;
        return Response.WriteAsJsonAsync(new ApiError("forbidden", "You do not have access to this operation."));
    }
}
