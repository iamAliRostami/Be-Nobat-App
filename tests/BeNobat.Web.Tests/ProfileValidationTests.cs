using System.Reflection;
using System.Security.Claims;
using BeNobat.Web.Components.Pages;
using BeNobat.Web.Domain;
using BeNobat.Web.Infrastructure;
using BeNobat.Web.Security;
using Microsoft.AspNetCore.Components.Authorization;
using Microsoft.AspNetCore.Components.Forms;
using Microsoft.AspNetCore.Identity;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging.Abstractions;
using Microsoft.Extensions.Options;
using Xunit;

namespace BeNobat.Web.Tests;

public sealed class ProfileValidationTests
{
    private const BindingFlags Instance = BindingFlags.Instance | BindingFlags.NonPublic;

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData("valid\nname")]
    public void Display_name_requires_a_nonempty_printable_value(string? value) =>
        Assert.False(UserInputValidation.TryNormalizeDisplayName(value, out _));

    [Fact]
    public void Display_name_trims_and_enforces_the_boundary()
    {
        Assert.True(UserInputValidation.TryNormalizeDisplayName("  نام کاربر  ", out var normalized));
        Assert.Equal("نام کاربر", normalized);
        Assert.True(UserInputValidation.TryNormalizeDisplayName(new string('x', 100), out _));
        Assert.False(UserInputValidation.TryNormalizeDisplayName(new string('x', 101), out _));
    }

    [Fact]
    public void Image_validation_requires_a_matching_supported_signature_and_size()
    {
        Assert.True(UserAvatar.ValidImage([137, 80, 78, 71, 13, 10, 26, 10], "image/png"));
        Assert.True(UserAvatar.ValidImage([255, 216, 255], "IMAGE/JPEG"));
        Assert.True(UserAvatar.ValidImage("RIFF1234WEBP"u8, "image/webp"));
        Assert.False(UserAvatar.ValidImage("<svg onload=alert(1)>"u8, "image/png"));
        Assert.False(UserAvatar.ValidImage([255, 216, 255], "image/png"));
        Assert.False(UserAvatar.ValidImage([], "image/png"));
        Assert.False(UserAvatar.ValidImage([137, 80], "image/png"));
        Assert.False(UserAvatar.ValidImage(new byte[UserAvatar.MaxBytes + 1], "image/jpeg"));
        Assert.False(UserAvatar.ValidImage([255, 216, 255], "image/svg+xml"));
    }

    [Theory]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData("too-long")]
    public async Task Invalid_profile_name_is_rejected_before_a_user_store_is_accessed(string invalid)
    {
        var component = new MyAccount();
        Set(component, "User", new AppUser());
        var profile = Get(component, "Profile")!;
        profile.GetType().GetProperty("DisplayName")!.SetValue(profile, invalid == "too-long" ? new string('x', 101) : invalid);
        profile.GetType().GetProperty("Phone")!.SetValue(profile, "09121234567");
        await InvokeAsync(component, "SaveProfile");
        Assert.Equal("نام و نام خانوادگی باید بین ۱ تا ۱۰۰ نویسه باشد.", Get(component, "ErrorMessage"));
        Assert.Null(Get(component, "Message"));
    }

    [Fact]
    public async Task Failed_avatar_removal_does_not_report_success_or_erase_the_cached_avatar()
    {
        var cached = AvatarUser();
        var fresh = Clone(cached);
        using var manager = new TestUserManager(fresh, IdentityResult.Failed(new IdentityError { Description = "Save failed" }));
        using var services = Services(manager);
        var component = Component(cached, services, cached.SecurityStamp!);
        await InvokeAsync(component, "RemoveAvatar");
        Assert.Equal(1, manager.UpdateCalls);
        Assert.Null(Get(component, "Message"));
        Assert.Equal("Save failed", Get(component, "ErrorMessage"));
        Assert.Same(cached, Get(component, "User"));
        Assert.NotNull(cached.AvatarData);
        Assert.Equal("image/png", cached.AvatarContentType);
    }

    [Theory]
    [InlineData(true)]
    [InlineData(false)]
    public async Task Stale_or_disabled_sessions_cannot_mutate_a_profile(bool staleStamp)
    {
        var cached = AvatarUser();
        var fresh = Clone(cached);
        if (staleStamp) fresh.SecurityStamp = "changed-security-stamp";
        else { fresh.LockoutEnabled = true; fresh.LockoutEnd = DateTimeOffset.UtcNow.AddDays(1); }
        using var manager = new TestUserManager(fresh, IdentityResult.Success);
        using var services = Services(manager);
        var component = Component(cached, services, cached.SecurityStamp!);
        await InvokeAsync(component, "RemoveAvatar");
        Assert.Equal(0, manager.UpdateCalls);
        Assert.Equal("نشست شما منقضی شده است؛ دوباره وارد حساب شوید.", Get(component, "ErrorMessage"));
        Assert.Null(Get(component, "Message"));
    }

    [Fact]
    public async Task A_browser_claiming_png_for_script_bytes_cannot_replace_a_profile_avatar()
    {
        var cached = AvatarUser();
        using var manager = new TestUserManager(Clone(cached), IdentityResult.Success);
        using var services = Services(manager);
        var component = Component(cached, services, cached.SecurityStamp!);
        await InvokeAsync(component, "UploadAvatar", new InputFileChangeEventArgs([new BrowserFile("<svg onload=alert(1)>"u8.ToArray(), "image/png")]));
        Assert.Equal(0, manager.UpdateCalls);
        Assert.Same(cached, Get(component, "User"));
        Assert.Equal("فایل انتخاب‌شده یک تصویر معتبر PNG، JPEG یا WebP نیست.", Get(component, "ErrorMessage"));
    }

    [Fact]
    public async Task Changing_the_authenticated_account_cannot_apply_a_cached_accounts_profile_edit()
    {
        var cached = AvatarUser();
        var otherAccount = AvatarUser();
        using var manager = new TestUserManager(otherAccount, IdentityResult.Success);
        using var services = Services(manager);
        var component = Component(cached, services, cached.SecurityStamp!);
        typeof(MyAccount).GetProperty("AuthenticationStateProvider", Instance)!.SetValue(component, new Authentication(otherAccount.Id, otherAccount.SecurityStamp!));
        await InvokeAsync(component, "RemoveAvatar");
        Assert.Equal(0, manager.UpdateCalls);
        Assert.Equal("نشست شما منقضی شده است؛ دوباره وارد حساب شوید.", Get(component, "ErrorMessage"));
    }

    private static MyAccount Component(AppUser cached, ServiceProvider services, string stamp)
    {
        var component = new MyAccount();
        Set(component, "User", cached);
        typeof(MyAccount).GetProperty("ScopeFactory", Instance)!.SetValue(component, services.GetRequiredService<IServiceScopeFactory>());
        typeof(MyAccount).GetProperty("AuthenticationStateProvider", Instance)!.SetValue(component, new Authentication(cached.Id, stamp));
        typeof(MyAccount).GetProperty("Logger", Instance)!.SetValue(component, NullLogger<MyAccount>.Instance);
        return component;
    }

    private static ServiceProvider Services(UserManager<AppUser> users) => new ServiceCollection().AddSingleton(users).BuildServiceProvider();
    private static AppUser AvatarUser() => new() { Id = Guid.NewGuid(), SecurityStamp = "valid-security-stamp", AvatarContentType = "image/png", AvatarData = [137, 80, 78, 71, 13, 10, 26, 10] };
    private static AppUser Clone(AppUser source) => new() { Id = source.Id, SecurityStamp = source.SecurityStamp, AvatarContentType = source.AvatarContentType, AvatarData = source.AvatarData };
    private static void Set(MyAccount component, string field, object value) => typeof(MyAccount).GetField(field, Instance)!.SetValue(component, value);
    private static object? Get(MyAccount component, string field) => typeof(MyAccount).GetField(field, Instance)!.GetValue(component);
    private static async Task InvokeAsync(MyAccount component, string method, params object[] args) =>
        await (Task)typeof(MyAccount).GetMethod(method, Instance)!.Invoke(component, args)!;

    private sealed class Authentication(Guid id, string stamp) : AuthenticationStateProvider
    {
        public override Task<AuthenticationState> GetAuthenticationStateAsync() => Task.FromResult(new AuthenticationState(new ClaimsPrincipal(
            new ClaimsIdentity([new Claim(ClaimTypes.NameIdentifier, id.ToString()), new Claim("AspNet.Identity.SecurityStamp", stamp)], "Test"))));
    }

    private sealed class TestUserManager(AppUser fresh, IdentityResult result) : UserManager<AppUser>(new Store(), Microsoft.Extensions.Options.Options.Create(new IdentityOptions()),
        new PasswordHasher<AppUser>(), [], [], new UpperInvariantLookupNormalizer(), new IdentityErrorDescriber(), null!, NullLogger<UserManager<AppUser>>.Instance)
    {
        public int UpdateCalls { get; private set; }
        public override Task<AppUser?> FindByIdAsync(string userId) => Task.FromResult<AppUser?>(fresh);
        public override Task<IdentityResult> UpdateAsync(AppUser user) { UpdateCalls++; return Task.FromResult(result); }
    }

    private sealed class Store : IUserStore<AppUser>
    {
        public void Dispose() { }
        public Task<IdentityResult> CreateAsync(AppUser user, CancellationToken cancellationToken) => throw new NotSupportedException();
        public Task<IdentityResult> DeleteAsync(AppUser user, CancellationToken cancellationToken) => throw new NotSupportedException();
        public Task<AppUser?> FindByIdAsync(string userId, CancellationToken cancellationToken) => throw new NotSupportedException();
        public Task<AppUser?> FindByNameAsync(string normalizedUserName, CancellationToken cancellationToken) => throw new NotSupportedException();
        public Task<string?> GetNormalizedUserNameAsync(AppUser user, CancellationToken cancellationToken) => Task.FromResult(user.NormalizedUserName);
        public Task<string> GetUserIdAsync(AppUser user, CancellationToken cancellationToken) => Task.FromResult(user.Id.ToString());
        public Task<string?> GetUserNameAsync(AppUser user, CancellationToken cancellationToken) => Task.FromResult(user.UserName);
        public Task SetNormalizedUserNameAsync(AppUser user, string? normalizedName, CancellationToken cancellationToken) => throw new NotSupportedException();
        public Task SetUserNameAsync(AppUser user, string? userName, CancellationToken cancellationToken) => throw new NotSupportedException();
        public Task<IdentityResult> UpdateAsync(AppUser user, CancellationToken cancellationToken) => throw new NotSupportedException();
    }

    private sealed class BrowserFile(byte[] bytes, string contentType) : IBrowserFile
    {
        public string Name => "test.png";
        public DateTimeOffset LastModified => DateTimeOffset.UnixEpoch;
        public long Size => bytes.Length;
        public string ContentType => contentType;
        public Stream OpenReadStream(long maxAllowedSize = 512000, CancellationToken cancellationToken = default) => new MemoryStream(bytes, writable: false);
    }
}
