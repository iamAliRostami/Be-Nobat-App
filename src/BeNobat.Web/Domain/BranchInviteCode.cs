namespace BeNobat.Web.Domain;

public static class BranchInviteCode
{
    private const string Chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    public static string Generate()
    {
        var chars = new char[6];
        for (int i = 0; i < chars.Length; i++)
        {
            chars[i] = Chars[Random.Shared.Next(Chars.Length)];
        }
        return $"BN-{new string(chars)}";
    }
}
