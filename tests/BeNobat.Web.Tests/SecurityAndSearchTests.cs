using BeNobat.Web.Domain;
using BeNobat.Web.Security;
using Xunit;

namespace BeNobat.Web.Tests;

public sealed class SecurityAndSearchTests
{
    [Theory]
    [InlineData("/appointments", "/appointments")]
    [InlineData("/book/abc?branch=1&slot=2", "/book/abc?branch=1&slot=2")]
    [InlineData("https://evil.example", "/")]
    [InlineData("//evil.example", "/")]
    [InlineData("/\\evil.example", "/")]
    [InlineData("javascript:alert(1)", "/")]
    [InlineData("", "/")]
    [InlineData(null, "/")]
    public void SafeRedirect_only_allows_local_paths(string? input, string expected) =>
        Assert.Equal(expected, SafeRedirect.Local(input));

    [Fact]
    public void SafeRedirect_uses_fallback_for_control_characters() =>
        Assert.Equal("/home", SafeRedirect.Local("/a\r\nb", "/home"));

    [Fact]
    public void TextSearch_normalizes_arabic_letters_and_digits() =>
        Assert.Equal("کیف ۱۲", TextSearch.Normalize("  كيف   ١٢ "));

    [Fact]
    public void TextSearch_matches_across_arabic_and_persian_spelling()
    {
        Assert.True(TextSearch.Matches("آرایشگاه کیان", "كيان"));
        Assert.True(TextSearch.Matches("anything", "  "));
        Assert.False(TextSearch.Matches("سالن زیبایی", "دندان"));
    }

    [Fact]
    public void TextSearch_escapes_like_wildcards_and_adds_arabic_variant()
    {
        var patterns = TextSearch.LikePatterns("100%_کی");
        Assert.Contains("%100\\%\\_کی%", patterns);
        Assert.Equal(2, patterns.Length);
        Assert.Empty(TextSearch.LikePatterns("   "));
    }
}
