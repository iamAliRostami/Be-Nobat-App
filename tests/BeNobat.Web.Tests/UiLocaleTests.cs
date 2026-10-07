using BeNobat.Web.Infrastructure;
using Microsoft.AspNetCore.Http;
using Xunit;

namespace BeNobat.Web.Tests;

public sealed class UiLocaleTests
{
    [Theory]
    [InlineData("en", "en", "ltr")]
    [InlineData("ar", "ar", "rtl")]
    [InlineData("fa", "fa", "rtl")]
    [InlineData("unsupported", "fa", "rtl")]
    public void Language_and_direction_follow_a_validated_preference(string cookie, string expected, string direction)
    {
        var http = new DefaultHttpContext();
        http.Request.Headers.Cookie = $"benobat-language={cookie}";
        var locale = new UiLocale(new HttpContextAccessor { HttpContext = http });
        Assert.Equal(expected, locale.Language);
        Assert.Equal(direction, locale.Direction);
    }

    [Fact]
    public void Circuit_keeps_its_language_when_the_request_context_is_released()
    {
        var http = new DefaultHttpContext();
        http.Request.Headers.Cookie = "benobat-language=en";
        var accessor = new HttpContextAccessor { HttpContext = http };
        var locale = new UiLocale(accessor);
        accessor.HttpContext = null;
        Assert.Equal("en", locale.Language);
        Assert.Equal("fa", new UiLocale(accessor).Language);
    }
}
