namespace BeNobat.Web.Security;

public static class UserInputValidation
{
    public static bool TryNormalizeIranianMobile(string? value, out string normalized)
    {
        normalized = string.Concat((value ?? string.Empty).Trim().Select(ToLatinDigit))
            .Replace(" ", string.Empty).Replace("-", string.Empty);
        if (normalized.StartsWith("+98", StringComparison.Ordinal)) normalized = "0" + normalized[3..];
        else if (normalized.StartsWith("0098", StringComparison.Ordinal)) normalized = "0" + normalized[4..];
        return normalized.Length == 11 && normalized.StartsWith("09", StringComparison.Ordinal) && normalized.All(char.IsAsciiDigit);
    }

    private static char ToLatinDigit(char value) => value switch
    {
        >= '\u06F0' and <= '\u06F9' => (char)('0' + value - '\u06F0'),
        >= '\u0660' and <= '\u0669' => (char)('0' + value - '\u0660'),
        _ => value,
    };
}
