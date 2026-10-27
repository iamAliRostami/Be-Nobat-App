using System;
using System.Text;
using QRCoder;

namespace BeNobat.Web.Infrastructure;

public static class QrCodeHelper
{
    public static string GenerateSvgDataUri(string content)
    {
        if (string.IsNullOrWhiteSpace(content))
        {
            return string.Empty;
        }

        using var generator = new QRCodeGenerator();
        using var data = generator.CreateQrCode(content, QRCodeGenerator.ECCLevel.M);
        var svgCode = new SvgQRCode(data);
        var svg = svgCode.GetGraphic(20);
        var base64 = Convert.ToBase64String(Encoding.UTF8.GetBytes(svg));
        return $"data:image/svg+xml;base64,{base64}";
    }
}
