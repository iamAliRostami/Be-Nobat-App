using System.Text;
using BeNobat.Web.Domain;

namespace BeNobat.Web.Infrastructure;

public static class DemoMediaAssets
{
    public static (byte[] Avatar, byte[] Header) GetBusinessMedia(string slug, string name, string category)
    {
        var avatarSvg = GenerateAvatarSvg(name, category);
        var headerSvg = GenerateHeaderSvg(name, category);
        return (Encoding.UTF8.GetBytes(avatarSvg), Encoding.UTF8.GetBytes(headerSvg));
    }

    public static (byte[] Avatar, byte[] Header) GetBranchMedia(string slug, string branchName, string category)
    {
        var avatarSvg = GenerateAvatarSvg(branchName, category, isBranch: true);
        var headerSvg = GenerateHeaderSvg(branchName, category, isBranch: true);
        return (Encoding.UTF8.GetBytes(avatarSvg), Encoding.UTF8.GetBytes(headerSvg));
    }

    public static List<BusinessGalleryItem> GetBusinessGallery(Guid businessId, string category)
    {
        var photos = GetGallerySvgDefinitions(category);
        var list = new List<BusinessGalleryItem>();
        for (var i = 0; i < photos.Count; i++)
        {
            var p = photos[i];
            list.Add(new BusinessGalleryItem
            {
                Id = Guid.NewGuid(),
                BusinessId = businessId,
                Caption = p.Caption,
                ImageData = Encoding.UTF8.GetBytes(p.Svg),
                ContentType = "image/svg+xml",
                SortOrder = i,
                CreatedAt = DateTimeOffset.UtcNow,
                UpdatedAt = DateTimeOffset.UtcNow
            });
        }
        return list;
    }

    public static List<BranchGalleryItem> GetBranchGallery(Guid branchId, string branchName, string category)
    {
        var photos = GetGallerySvgDefinitions(category);
        var list = new List<BranchGalleryItem>();
        for (var i = 0; i < photos.Count; i++)
        {
            var p = photos[i];
            list.Add(new BranchGalleryItem
            {
                Id = Guid.NewGuid(),
                BranchId = branchId,
                Caption = $"{branchName} - {p.Caption}",
                ImageData = Encoding.UTF8.GetBytes(p.Svg),
                ContentType = "image/svg+xml",
                SortOrder = i,
                CreatedAt = DateTimeOffset.UtcNow,
                UpdatedAt = DateTimeOffset.UtcNow
            });
        }
        return list;
    }

    private static (string C1, string C2, string C3, string Accent, string IconSvg) GetCategoryVisuals(string category)
    {
        if (category.Contains("زیبایی") || category.Contains("آرایش"))
        {
            return ("#831843", "#be185d", "#ec4899", "#fbcfe8",
                """
                <g fill="none" stroke="currentColor" stroke-width="5" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M100 35 C80 65 60 90 60 120 C60 145 78 165 100 165 C122 165 140 145 140 120 C140 90 120 65 100 35 Z" fill="rgba(255,255,255,0.2)"/>
                  <circle cx="100" cy="115" r="14" fill="#ffffff"/>
                  <path d="M70 125 C80 145 95 150 100 150"/>
                  <path d="M130 125 C120 145 105 150 100 150"/>
                  <path d="M100 35 L100 165" stroke="rgba(255,255,255,0.4)" stroke-width="2"/>
                </g>
                """);
        }
        if (category.Contains("دندان"))
        {
            return ("#0f172a", "#0e7490", "#06b6d4", "#cffafe",
                """
                <g fill="none" stroke="currentColor" stroke-width="5" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M70 60 C60 80 55 105 60 140 C65 160 80 160 85 130 C90 105 110 105 115 130 C120 160 135 160 140 140 C145 105 140 80 130 60 C115 50 85 50 70 60 Z" fill="rgba(255,255,255,0.2)"/>
                  <path d="M85 85 Q100 95 115 85" stroke="#ffffff" stroke-width="6"/>
                  <circle cx="130" cy="55" r="6" fill="#fef08a"/>
                </g>
                """);
        }
        if (category.Contains("فیزیوتراپی") || category.Contains("توان"))
        {
            return ("#064e3b", "#047857", "#10b981", "#a7f3d0",
                """
                <g fill="none" stroke="currentColor" stroke-width="5" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="100" cy="65" r="18" fill="rgba(255,255,255,0.2)"/>
                  <path d="M100 85 L100 130 M70 100 L130 100 M80 160 L100 130 L120 160" stroke-width="6"/>
                  <path d="M60 70 Q100 40 140 70" stroke="rgba(255,255,255,0.4)" stroke-width="3"/>
                </g>
                """);
        }
        if (category.Contains("مشاوره") || category.Contains("روان"))
        {
            return ("#312e81", "#4338ca", "#818cf8", "#e0e7ff",
                """
                <g fill="none" stroke="currentColor" stroke-width="5" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M65 110 C55 90 60 65 80 55 C100 45 120 50 130 70 C140 90 135 115 120 125 L125 145 L105 135 C75 140 65 125 65 110 Z" fill="rgba(255,255,255,0.2)"/>
                  <circle cx="90" cy="85" r="5" fill="#ffffff"/>
                  <circle cx="110" cy="85" r="5" fill="#ffffff"/>
                </g>
                """);
        }
        if (category.Contains("پیرایش") || category.Contains("داماد"))
        {
            return ("#451a03", "#78350f", "#d97706", "#fef3c7",
                """
                <g fill="none" stroke="currentColor" stroke-width="5" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="75" cy="65" r="16"/>
                  <circle cx="125" cy="65" r="16"/>
                  <path d="M85 75 L130 150 M115 75 L70 150"/>
                  <circle cx="100" cy="112" r="6" fill="#ffffff"/>
                </g>
                """);
        }
        if (category.Contains("ماساژ") || category.Contains("اسپا") || category.Contains("تندرستی"))
        {
            return ("#134e4a", "#0d9488", "#2dd4bf", "#ccfbf1",
                """
                <g fill="none" stroke="currentColor" stroke-width="5" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="100" cy="60" r="16" fill="rgba(255,255,255,0.2)"/>
                  <path d="M60 110 Q100 80 140 110 Q100 140 60 110 Z" fill="rgba(255,255,255,0.2)"/>
                  <path d="M50 145 Q100 120 150 145"/>
                </g>
                """);
        }
        if (category.Contains("پزشکی") || category.Contains("سلامت") || category.Contains("پوست") || category.Contains("لیزر"))
        {
            return ("#1e3a8a", "#2563eb", "#60a5fa", "#dbeafe",
                """
                <g fill="none" stroke="currentColor" stroke-width="5" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="75" y="45" width="50" height="110" rx="10" fill="rgba(255,255,255,0.25)"/>
                  <rect x="45" y="75" width="110" height="50" rx="10" fill="rgba(255,255,255,0.25)"/>
                  <circle cx="100" cy="100" r="12" fill="#ffffff"/>
                </g>
                """);
        }
        if (category.Contains("خودرو") || category.Contains("تعمیر"))
        {
            return ("#1c1917", "#44403c", "#f97316", "#ffedd5",
                """
                <g fill="none" stroke="currentColor" stroke-width="5" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="50" y="90" width="100" height="50" rx="14" fill="rgba(255,255,255,0.2)"/>
                  <circle cx="75" cy="140" r="15" fill="#f97316"/>
                  <circle cx="125" cy="140" r="15" fill="#f97316"/>
                  <path d="M70 90 L85 60 L115 60 L130 90 Z"/>
                </g>
                """);
        }
        if (category.Contains("ورزش") || category.Contains("بدنسازی") || category.Contains("تناسب"))
        {
            return ("#7c2d12", "#c2410c", "#fb923c", "#ffedd5",
                """
                <g fill="none" stroke="currentColor" stroke-width="6" stroke-linecap="round" stroke-linejoin="round">
                  <line x1="50" y1="100" x2="150" y2="100"/>
                  <rect x="40" y="75" width="18" height="50" rx="4" fill="#fb923c"/>
                  <rect x="142" y="75" width="18" height="50" rx="4" fill="#fb923c"/>
                  <rect x="25" y="85" width="12" height="30" rx="3" fill="#ffffff"/>
                  <rect x="163" y="85" width="12" height="30" rx="3" fill="#ffffff"/>
                </g>
                """);
        }
        if (category.Contains("پت") || category.Contains("حیوانات") || category.Contains("دامپزشک"))
        {
            return ("#4c1d95", "#7c3aed", "#a78bfa", "#ede9fe",
                """
                <g fill="none" stroke="currentColor" stroke-width="5" stroke-linecap="round" stroke-linejoin="round">
                  <ellipse cx="100" cy="120" rx="26" ry="20" fill="rgba(255,255,255,0.25)"/>
                  <circle cx="68" cy="85" r="10" fill="#ffffff"/>
                  <circle cx="90" cy="65" r="10" fill="#ffffff"/>
                  <circle cx="110" cy="65" r="10" fill="#ffffff"/>
                  <circle cx="132" cy="85" r="10" fill="#ffffff"/>
                </g>
                """);
        }
        if (category.Contains("عکاسی") || category.Contains("آتلیه"))
        {
            return ("#881337", "#be123c", "#f43f5e", "#ffe4e6",
                """
                <g fill="none" stroke="currentColor" stroke-width="5" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="50" y="65" width="100" height="75" rx="14" fill="rgba(255,255,255,0.2)"/>
                  <circle cx="100" cy="102" r="22" stroke-width="6"/>
                  <circle cx="100" cy="102" r="8" fill="#ffffff"/>
                  <rect x="65" y="52" width="25" height="12" rx="3" fill="#f43f5e"/>
                </g>
                """);
        }
        // Default modern emerald
        return ("#0f172a", "#0f766e", "#14b8a6", "#ccfbf1",
            """
            <g fill="none" stroke="currentColor" stroke-width="5" stroke-linecap="round" stroke-linejoin="round">
              <polygon points="100,45 145,85 130,145 70,145 55,85" fill="rgba(255,255,255,0.2)"/>
              <circle cx="100" cy="100" r="16" fill="#ffffff"/>
            </g>
            """);
    }

    private static string GenerateAvatarSvg(string name, string category, bool isBranch = false)
    {
        var v = GetCategoryVisuals(category);
        var initial = string.IsNullOrWhiteSpace(name) ? (isBranch ? "ش" : "ب") : name.Trim()[..1];

        return $"""
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" width="200" height="200">
          <defs>
            <linearGradient id="gradAvatar" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" stop-color="{v.C2}" />
              <stop offset="100%" stop-color="{v.C1}" />
            </linearGradient>
            <filter id="shadow" x="-10%" y="-10%" width="130%" height="130%">
              <feDropShadow dx="0" dy="8" stdDeviation="10" flood-color="#000000" flood-opacity="0.35"/>
            </filter>
          </defs>
          <rect width="200" height="200" rx="44" fill="url(#gradAvatar)" />
          <circle cx="100" cy="100" r="82" fill="none" stroke="rgba(255,255,255,0.15)" stroke-width="3" stroke-dasharray="6,6"/>
          <g color="#ffffff" filter="url(#shadow)">
            {v.IconSvg}
          </g>
          <rect x="24" y="142" width="46" height="34" rx="10" fill="{v.Accent}" />
          <text x="47" y="165" font-family="Vazirmatn, Tahoma, sans-serif" font-size="20" font-weight="900" fill="{v.C1}" text-anchor="middle" dominant-baseline="central">{initial}</text>
        </svg>
        """;
    }

    private static string GenerateHeaderSvg(string name, string category, bool isBranch = false)
    {
        var v = GetCategoryVisuals(category);
        var subtext = isBranch ? "شعبه منتخب و استاندارد" : $"مرکز تخصصی {category}";

        return $"""
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 400" width="1200" height="400" preserveAspectRatio="none">
          <defs>
            <linearGradient id="headerGrad" x1="0%" y1="30%" x2="100%" y2="80%">
              <stop offset="0%" stop-color="{v.C1}" />
              <stop offset="60%" stop-color="{v.C2}" />
              <stop offset="100%" stop-color="{v.C1}" />
            </linearGradient>
            <radialGradient id="glow" cx="80%" cy="30%" r="50%">
              <stop offset="0%" stop-color="{v.C3}" stop-opacity="0.5"/>
              <stop offset="100%" stop-color="{v.C1}" stop-opacity="0"/>
            </radialGradient>
            <radialGradient id="glow2" cx="20%" cy="80%" r="40%">
              <stop offset="0%" stop-color="{v.Accent}" stop-opacity="0.25"/>
              <stop offset="100%" stop-color="{v.C1}" stop-opacity="0"/>
            </radialGradient>
          </defs>
          <rect width="1200" height="400" fill="url(#headerGrad)"/>
          <rect width="1200" height="400" fill="url(#glow)"/>
          <rect width="1200" height="400" fill="url(#glow2)"/>

          <!-- Decorative layered waves -->
          <path d="M0,320 C300,280 500,380 800,320 C1000,280 1100,340 1200,300 L1200,400 L0,400 Z" fill="rgba(0,0,0,0.3)"/>
          <path d="M0,350 C350,300 700,380 1200,330 L1200,400 L0,400 Z" fill="rgba(0,0,0,0.2)"/>

          <!-- Abstract geometric circles -->
          <circle cx="1050" cy="120" r="160" fill="none" stroke="rgba(255,255,255,0.08)" stroke-width="40"/>
          <circle cx="1050" cy="120" r="90" fill="none" stroke="rgba(255,255,255,0.12)" stroke-width="2"/>
          <circle cx="150" cy="80" r="50" fill="none" stroke="rgba(255,255,255,0.06)" stroke-width="12"/>

          <!-- Stylized banner emblem -->
          <g transform="translate(980, 50) scale(0.7)" color="#ffffff" opacity="0.35">
            {v.IconSvg}
          </g>

          <text x="60" y="320" font-family="Vazirmatn, Tahoma, sans-serif" font-size="28" font-weight="700" fill="{v.Accent}" opacity="0.85">{subtext}</text>
        </svg>
        """;
    }

    private static List<(string Svg, string Caption)> GetGallerySvgDefinitions(string category)
    {
        var v = GetCategoryVisuals(category);
        return new List<(string Svg, string Caption)>
        {
            (
                $"""
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 600" width="600" height="600">
                  <rect width="600" height="600" fill="{v.C1}"/>
                  <circle cx="300" cy="300" r="220" fill="{v.C2}" opacity="0.5"/>
                  <rect x="120" y="380" width="360" height="120" rx="16" fill="rgba(255,255,255,0.15)"/>
                  <circle cx="300" cy="240" r="70" fill="rgba(255,255,255,0.3)"/>
                  <text x="300" y="550" font-family="Vazirmatn, sans-serif" font-size="24" fill="#ffffff" text-anchor="middle" font-weight="bold">محیط پذیرش و فضای انتظار</text>
                </svg>
                """,
                "محیط اختصاصی و فضای انتظار"
            ),
            (
                $"""
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 600" width="600" height="600">
                  <rect width="600" height="600" fill="{v.C2}"/>
                  <polygon points="300,100 500,450 100,450" fill="{v.C1}" opacity="0.4"/>
                  <circle cx="300" cy="300" r="90" fill="{v.Accent}" opacity="0.3"/>
                  <text x="300" y="550" font-family="Vazirmatn, sans-serif" font-size="24" fill="#ffffff" text-anchor="middle" font-weight="bold">تجهیزات و ابزار مدرن</text>
                </svg>
                """,
                "تجهیزات مدرن و بهداشتی"
            ),
            (
                $"""
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 600" width="600" height="600">
                  <defs>
                    <radialGradient id="galGlow" cx="50%" cy="50%" r="50%">
                      <stop offset="0%" stop-color="{v.C3}"/>
                      <stop offset="100%" stop-color="{v.C1}"/>
                    </radialGradient>
                  </defs>
                  <rect width="600" height="600" fill="url(#galGlow)"/>
                  <rect x="150" y="150" width="300" height="300" rx="36" fill="none" stroke="#ffffff" stroke-width="8" opacity="0.4"/>
                  <text x="300" y="550" font-family="Vazirmatn, sans-serif" font-size="24" fill="#ffffff" text-anchor="middle" font-weight="bold">فضای آرامش‌بخش و استاندارد</text>
                </svg>
                """,
                "فضای ارائه خدمات تخصصی"
            )
        };
    }
}
