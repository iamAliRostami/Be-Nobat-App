namespace BeNobat.Web.Infrastructure;

using BeNobat.Web.Domain;

public sealed record SocialChannel(
    string Key,
    string NameFa,
    string NameEn,
    string Handle,
    string Url,
    string BrandColor,
    string SvgPath,
    string ViewBox = "0 0 24 24"
);

public static class SocialHelper
{
    public static string ResolveUrl(string key, string? raw)
    {
        if (string.IsNullOrWhiteSpace(raw)) return string.Empty;
        var val = raw.Trim();
        if (val.StartsWith("http://", StringComparison.OrdinalIgnoreCase) ||
            val.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
        {
            return val;
        }

        var clean = val.TrimStart('@');
        return key.ToLowerInvariant() switch
        {
            "telegram" => $"https://t.me/{clean}",
            "instagram" => $"https://instagram.com/{clean}",
            "eitaa" => $"https://eitaa.com/{clean}",
            "bale" => $"https://ble.ir/{clean}",
            "rubika" => $"https://rubika.ir/{clean}",
            "twitter" or "x" => $"https://x.com/{clean}",
            "whatsapp" => clean.StartsWith('+') || clean.All(char.IsDigit)
                ? $"https://wa.me/{(clean.StartsWith("09") ? "98" + clean.Substring(1) : clean)}"
                : $"https://wa.me/{clean}",
            "linkedin" => clean.Contains('/') ? $"https://linkedin.com/{clean}" : $"https://linkedin.com/company/{clean}",
            "aparat" => $"https://aparat.com/{clean}",
            "website" => val.StartsWith("http", StringComparison.OrdinalIgnoreCase) ? val : $"https://{val}",
            _ => val
        };
    }

    public static string FormatHandle(string? raw)
    {
        if (string.IsNullOrWhiteSpace(raw)) return string.Empty;
        var val = raw.Trim();
        if (val.StartsWith("http://", StringComparison.OrdinalIgnoreCase) ||
            val.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
        {
            try
            {
                var uri = new Uri(val);
                var path = uri.AbsolutePath.Trim('/');
                if (!string.IsNullOrEmpty(path)) return "@" + path;
                return uri.Host;
            }
            catch
            {
                return val;
            }
        }
        return val.StartsWith('@') ? val : "@" + val;
    }

    public static List<SocialChannel> GetConfiguredChannels(PlatformPricingSettings? s)
    {
        var list = new List<SocialChannel>();
        if (s == null) return list;

        // Eitaa (ایتا)
        if (!string.IsNullOrWhiteSpace(s.Eitaa))
        {
            list.Add(new SocialChannel(
                "eitaa",
                "ایتا",
                "Eitaa",
                FormatHandle(s.Eitaa),
                ResolveUrl("eitaa", s.Eitaa),
                "#e67e22",
                "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 14.5h-2v-5h2v5zm0-7h-2V7h2v2.5z"
            ));
        }

        // Telegram (تلگرام)
        if (!string.IsNullOrWhiteSpace(s.Telegram))
        {
            list.Add(new SocialChannel(
                "telegram",
                "تلگرام",
                "Telegram",
                FormatHandle(s.Telegram),
                ResolveUrl("telegram", s.Telegram),
                "#229ed9",
                "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm4.64 6.8c-.15 1.58-.8 5.42-1.13 7.19-.14.75-.42 1-.68 1.03-.58.05-1.02-.38-1.58-.75-.88-.58-1.38-.94-2.23-1.5-.99-.65-.35-1.01.22-1.59.15-.15 2.71-2.48 2.76-2.69a.2.2 0 00-.05-.18c-.06-.05-.14-.03-.21-.02-.09.02-1.49.95-4.22 2.79-.4.27-.76.41-1.08.4-.36-.01-1.04-.2-1.55-.37-.63-.2-1.12-.31-1.08-.66.02-.18.27-.36.75-.55 2.92-1.27 4.86-2.11 5.83-2.51 2.78-1.16 3.35-1.36 3.73-1.36.08 0 .27.02.39.12.1.08.13.19.14.27-.01.06.01.24 0 .38z"
            ));
        }

        // Instagram (اینستاگرام)
        if (!string.IsNullOrWhiteSpace(s.Instagram))
        {
            list.Add(new SocialChannel(
                "instagram",
                "اینستاگرام",
                "Instagram",
                FormatHandle(s.Instagram),
                ResolveUrl("instagram", s.Instagram),
                "#e1306c",
                "M12 2.163c3.204 0 3.584.012 4.85.07 3.252.148 4.771 1.691 4.919 4.919.058 1.265.069 1.645.069 4.849 0 3.205-.012 3.584-.069 4.849-.149 3.225-1.664 4.771-4.919 4.919-1.266.058-1.644.07-4.85.07-3.204 0-3.584-.012-4.849-.07-3.26-.149-4.771-1.699-4.919-4.92-.058-1.265-.07-1.644-.07-4.849 0-3.204.013-3.583.07-4.849.149-3.227 1.664-4.771 4.919-4.919 1.266-.057 1.645-.069 4.849-.069zm0-2.163c-3.259 0-3.667.014-4.947.072-4.358.2-6.78 2.618-6.98 6.98-.059 1.281-.073 1.689-.073 4.948 0 3.259.014 3.668.072 4.948.2 4.358 2.618 6.78 6.98 6.98 1.281.058 1.689.072 4.948.072 3.259 0 3.668-.014 4.948-.072 4.354-.2 6.782-2.618 6.979-6.98.059-1.28.073-1.689.073-4.948 0-3.259-.014-3.667-.072-4.947-.196-4.354-2.617-6.78-6.979-6.98-1.281-.059-1.69-.073-4.949-.073zm0 5.838c-3.403 0-6.162 2.759-6.162 6.162s2.759 6.163 6.162 6.163 6.162-2.759 6.162-6.163c0-3.403-2.759-6.162-6.162-6.162zm0 10.162c-2.209 0-4-1.79-4-4 0-2.209 1.791-4 4-4s4 1.791 4 4c0 2.21-1.791 4-4 4zm6.406-11.845c-.796 0-1.441.645-1.441 1.44s.645 1.44 1.441 1.44c.795 0 1.439-.645 1.439-1.44s-.644-1.44-1.439-1.44z"
            ));
        }

        // Bale (بله)
        if (!string.IsNullOrWhiteSpace(s.Bale))
        {
            list.Add(new SocialChannel(
                "bale",
                "بله",
                "Bale",
                FormatHandle(s.Bale),
                ResolveUrl("bale", s.Bale),
                "#00a859",
                "M12 2C6.48 2 2 6.48 2 12c0 2.12.67 4.09 1.81 5.71L2.6 21.4a.75.75 0 00.93.93l3.69-1.21A9.95 9.95 0 0012 22c5.52 0 10-4.48 10-10S17.52 2 12 2zm1 14.5h-2v-2h2v2zm0-4h-2V7h2v5.5z"
            ));
        }

        // Rubika (روبیکا)
        if (!string.IsNullOrWhiteSpace(s.Rubika))
        {
            list.Add(new SocialChannel(
                "rubika",
                "روبیکا",
                "Rubika",
                FormatHandle(s.Rubika),
                ResolveUrl("rubika", s.Rubika),
                "#9b51e0",
                "M12 2.5l8 4.6v9.8l-8 4.6-8-4.6V7.1l8-4.6zm0 2.3L6 8.3v7.4l6 3.5 6-3.5V8.3l-6-3.5zm0 3.2a3 3 0 110 6 3 3 0 010-6z"
            ));
        }

        // Twitter / X (ایکس)
        if (!string.IsNullOrWhiteSpace(s.Twitter))
        {
            list.Add(new SocialChannel(
                "twitter",
                "ایکس (توییتر)",
                "X (Twitter)",
                FormatHandle(s.Twitter),
                ResolveUrl("twitter", s.Twitter),
                "#000000",
                "M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z"
            ));
        }

        // WhatsApp (واتساپ)
        if (!string.IsNullOrWhiteSpace(s.WhatsApp))
        {
            list.Add(new SocialChannel(
                "whatsapp",
                "واتساپ",
                "WhatsApp",
                FormatHandle(s.WhatsApp),
                ResolveUrl("whatsapp", s.WhatsApp),
                "#25d366",
                "M12.04 2c-5.46 0-9.91 4.45-9.91 9.91 0 1.75.46 3.45 1.32 4.95L2.05 22l5.25-1.38c1.45.79 3.08 1.21 4.74 1.21 5.46 0 9.91-4.45 9.91-9.91 0-2.65-1.03-5.14-2.9-7.01A9.816 9.816 0 0012.04 2m.01 1.67c2.2 0 4.26.86 5.82 2.41a8.165 8.165 0 012.41 5.83c0 4.54-3.7 8.24-8.24 8.24-1.48 0-2.93-.4-4.2-1.15l-.3-.18-3.12.82.83-3.04-.2-.31a8.17 8.17 0 01-1.25-4.38c0-4.54 3.7-8.24 8.24-8.24m4.52 11.53c-.25-.13-1.47-.72-1.7-.81-.23-.08-.39-.13-.56.13-.17.25-.64.81-.79.97-.14.17-.29.19-.54.06-.25-.13-1.06-.39-2.02-1.25-.75-.67-1.26-1.5-1.41-1.75-.15-.25-.02-.39.11-.51.11-.11.25-.29.38-.44.13-.14.17-.25.25-.42.08-.17.04-.31-.02-.44-.06-.13-.56-1.34-.76-1.84-.2-.49-.4-.42-.56-.43h-.48c-.17 0-.44.06-.67.31-.23.25-.88.86-.88 2.1 0 1.24.9 2.44 1.03 2.61.13.17 1.77 2.7 4.29 3.78.6.26 1.07.41 1.44.53.6.19 1.15.16 1.58.1.48-.07 1.47-.6 1.68-1.18.21-.58.21-1.07.14-1.18-.06-.12-.22-.19-.47-.32z"
            ));
        }

        // LinkedIn (لینکدین)
        if (!string.IsNullOrWhiteSpace(s.LinkedIn))
        {
            list.Add(new SocialChannel(
                "linkedin",
                "لینکدین",
                "LinkedIn",
                FormatHandle(s.LinkedIn),
                ResolveUrl("linkedin", s.LinkedIn),
                "#0a66c2",
                "M19 3a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h14m-.5 15.5v-5.3a3.26 3.26 0 0 0-3.26-3.26c-.85 0-1.84.52-2.28 1.3v-1.11h-2.79v8.37h2.79v-4.93c0-.77.62-1.4 1.39-1.4a1.4 1.4 0 0 1 1.4 1.4v4.93h2.75M6.88 8.56a1.68 1.68 0 0 0 1.68-1.68c0-.93-.75-1.69-1.68-1.69a1.69 1.69 0 0 0-1.69 1.69c0 .93.76 1.68 1.69 1.68m1.39 9.94v-8.37H5.5v8.37h2.77z"
            ));
        }

        // Aparat (آپارات)
        if (!string.IsNullOrWhiteSpace(s.Aparat))
        {
            list.Add(new SocialChannel(
                "aparat",
                "آپارات",
                "Aparat",
                FormatHandle(s.Aparat),
                ResolveUrl("aparat", s.Aparat),
                "#ed145b",
                "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1.5 14.5v-9l7 4.5-7 4.5z"
            ));
        }

        return list;
    }

    public static List<SocialChannel> GetConfiguredChannels(ISocialMediaHolder? holder)
    {
        if (holder == null) return [];
        return GetConfiguredChannels(
            telegram: holder.Telegram,
            instagram: holder.Instagram,
            whatsApp: holder.WhatsApp,
            eitaa: holder.Eitaa,
            bale: holder.Bale,
            rubika: holder.Rubika,
            twitter: holder.Twitter,
            linkedin: holder.LinkedIn,
            aparat: holder.Aparat,
            website: holder.Website
        );
    }

    public static List<SocialChannel> GetConfiguredChannels(
        string? telegram = null,
        string? instagram = null,
        string? whatsApp = null,
        string? eitaa = null,
        string? bale = null,
        string? rubika = null,
        string? twitter = null,
        string? linkedin = null,
        string? aparat = null,
        string? website = null)
    {
        var list = new List<SocialChannel>();

        // Instagram
        if (!string.IsNullOrWhiteSpace(instagram))
        {
            list.Add(new SocialChannel(
                "instagram",
                "اینستاگرام",
                "Instagram",
                FormatHandle(instagram),
                ResolveUrl("instagram", instagram),
                "#e1306c",
                "M12 2.163c3.204 0 3.584.012 4.85.07 3.252.148 4.771 1.691 4.919 4.919.058 1.265.069 1.645.069 4.849 0 3.205-.012 3.584-.069 4.849-.149 3.225-1.664 4.771-4.919 4.919-1.266.058-1.644.07-4.85.07-3.204 0-3.584-.012-4.849-.07-3.26-.149-4.771-1.699-4.919-4.92-.058-1.265-.07-1.644-.07-4.849 0-3.204.013-3.583.07-4.849.149-3.227 1.664-4.771 4.919-4.919 1.266-.057 1.645-.069 4.849-.069zm0-2.163c-3.259 0-3.667.014-4.947.072-4.358.2-6.78 2.618-6.98 6.98-.059 1.281-.073 1.689-.073 4.948 0 3.259.014 3.668.072 4.948.2 4.358 2.618 6.78 6.98 6.98 1.281.058 1.689.072 4.948.072 3.259 0 3.668-.014 4.948-.072 4.354-.2 6.782-2.618 6.979-6.98.059-1.28.073-1.689.073-4.948 0-3.259-.014-3.667-.072-4.947-.196-4.354-2.617-6.78-6.979-6.98-1.281-.059-1.69-.073-4.949-.073zm0 5.838c-3.403 0-6.162 2.759-6.162 6.162s2.759 6.163 6.162 6.163 6.162-2.759 6.162-6.163c0-3.403-2.759-6.162-6.162-6.162zm0 10.162c-2.209 0-4-1.79-4-4 0-2.209 1.791-4 4-4s4 1.791 4 4c0 2.21-1.791 4-4 4zm6.406-11.845c-.796 0-1.441.645-1.441 1.44s.645 1.44 1.441 1.44c.795 0 1.439-.645 1.439-1.44s-.644-1.44-1.439-1.44z"
            ));
        }

        // Telegram
        if (!string.IsNullOrWhiteSpace(telegram))
        {
            list.Add(new SocialChannel(
                "telegram",
                "تلگرام",
                "Telegram",
                FormatHandle(telegram),
                ResolveUrl("telegram", telegram),
                "#229ed9",
                "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm4.64 6.8c-.15 1.58-.8 5.42-1.13 7.19-.14.75-.42 1-.68 1.03-.58.05-1.02-.38-1.58-.75-.88-.58-1.38-.94-2.23-1.5-.99-.65-.35-1.01.22-1.59.15-.15 2.71-2.48 2.76-2.69a.2.2 0 00-.05-.18c-.06-.05-.14-.03-.21-.02-.09.02-1.49.95-4.22 2.79-.4.27-.76.41-1.08.4-.36-.01-1.04-.2-1.55-.37-.63-.2-1.12-.31-1.08-.66.02-.18.27-.36.75-.55 2.92-1.27 4.86-2.11 5.83-2.51 2.78-1.16 3.35-1.36 3.73-1.36.08 0 .27.02.39.12.1.08.13.19.14.27-.01.06.01.24 0 .38z"
            ));
        }

        // WhatsApp
        if (!string.IsNullOrWhiteSpace(whatsApp))
        {
            list.Add(new SocialChannel(
                "whatsapp",
                "واتساپ",
                "WhatsApp",
                FormatHandle(whatsApp),
                ResolveUrl("whatsapp", whatsApp),
                "#25d366",
                "M12.04 2c-5.46 0-9.91 4.45-9.91 9.91 0 1.75.46 3.45 1.32 4.95L2.05 22l5.25-1.38c1.45.79 3.08 1.21 4.74 1.21 5.46 0 9.91-4.45 9.91-9.91 0-2.65-1.03-5.14-2.9-7.01A9.816 9.816 0 0012.04 2m.01 1.67c2.2 0 4.26.86 5.82 2.41a8.165 8.165 0 012.41 5.83c0 4.54-3.7 8.24-8.24 8.24-1.48 0-2.93-.4-4.2-1.15l-.3-.18-3.12.82.83-3.04-.2-.31a8.17 8.17 0 01-1.25-4.38c0-4.54 3.7-8.24 8.24-8.24m4.52 11.53c-.25-.13-1.47-.72-1.7-.81-.23-.08-.39-.13-.56.13-.17.25-.64.81-.79.97-.14.17-.29.19-.54.06-.25-.13-1.06-.39-2.02-1.25-.75-.67-1.26-1.5-1.41-1.75-.15-.25-.02-.39.11-.51.11-.11.25-.29.38-.44.13-.14.17-.25.25-.42.08-.17.04-.31-.02-.44-.06-.13-.56-1.34-.76-1.84-.2-.49-.4-.42-.56-.43h-.48c-.17 0-.44.06-.67.31-.23.25-.88.86-.88 2.1 0 1.24.9 2.44 1.03 2.61.13.17 1.77 2.7 4.29 3.78.6.26 1.07.41 1.44.53.6.19 1.15.16 1.58.1.48-.07 1.47-.6 1.68-1.18.21-.58.21-1.07.14-1.18-.06-.12-.22-.19-.47-.32z"
            ));
        }

        // Eitaa
        if (!string.IsNullOrWhiteSpace(eitaa))
        {
            list.Add(new SocialChannel(
                "eitaa",
                "ایتا",
                "Eitaa",
                FormatHandle(eitaa),
                ResolveUrl("eitaa", eitaa),
                "#e67e22",
                "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 14.5h-2v-5h2v5zm0-7h-2V7h2v2.5z"
            ));
        }

        // Bale
        if (!string.IsNullOrWhiteSpace(bale))
        {
            list.Add(new SocialChannel(
                "bale",
                "بله",
                "Bale",
                FormatHandle(bale),
                ResolveUrl("bale", bale),
                "#00a859",
                "M12 2C6.48 2 2 6.48 2 12c0 2.12.67 4.09 1.81 5.71L2.6 21.4a.75.75 0 00.93.93l3.69-1.21A9.95 9.95 0 0012 22c5.52 0 10-4.48 10-10S17.52 2 12 2zm1 14.5h-2v-2h2v2zm0-4h-2V7h2v5.5z"
            ));
        }

        // Rubika
        if (!string.IsNullOrWhiteSpace(rubika))
        {
            list.Add(new SocialChannel(
                "rubika",
                "روبیکا",
                "Rubika",
                FormatHandle(rubika),
                ResolveUrl("rubika", rubika),
                "#9b51e0",
                "M12 2.5l8 4.6v9.8l-8 4.6-8-4.6V7.1l8-4.6zm0 2.3L6 8.3v7.4l6 3.5 6-3.5V8.3l-6-3.5zm0 3.2a3 3 0 110 6 3 3 0 010-6z"
            ));
        }

        // Twitter / X
        if (!string.IsNullOrWhiteSpace(twitter))
        {
            list.Add(new SocialChannel(
                "twitter",
                "ایکس (توییتر)",
                "X (Twitter)",
                FormatHandle(twitter),
                ResolveUrl("twitter", twitter),
                "#000000",
                "M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z"
            ));
        }

        // LinkedIn
        if (!string.IsNullOrWhiteSpace(linkedin))
        {
            list.Add(new SocialChannel(
                "linkedin",
                "لینکدین",
                "LinkedIn",
                FormatHandle(linkedin),
                ResolveUrl("linkedin", linkedin),
                "#0a66c2",
                "M19 3a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h14m-.5 15.5v-5.3a3.26 3.26 0 0 0-3.26-3.26c-.85 0-1.84.52-2.28 1.3v-1.11h-2.79v8.37h2.79v-4.93c0-.77.62-1.4 1.39-1.4a1.4 1.4 0 0 1 1.4 1.4v4.93h2.75M6.88 8.56a1.68 1.68 0 0 0 1.68-1.68c0-.93-.75-1.69-1.68-1.69a1.69 1.69 0 0 0-1.69 1.69c0 .93.76 1.68 1.69 1.68m1.39 9.94v-8.37H5.5v8.37h2.77z"
            ));
        }

        // Aparat
        if (!string.IsNullOrWhiteSpace(aparat))
        {
            list.Add(new SocialChannel(
                "aparat",
                "آپارات",
                "Aparat",
                FormatHandle(aparat),
                ResolveUrl("aparat", aparat),
                "#ed145b",
                "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1.5 14.5v-9l7 4.5-7 4.5z"
            ));
        }

        // Website
        if (!string.IsNullOrWhiteSpace(website))
        {
            var webVal = website.Trim();
            var display = webVal;
            try
            {
                var uri = new Uri(ResolveUrl("website", webVal));
                display = uri.Authority;
            }
            catch { }

            list.Add(new SocialChannel(
                "website",
                "وب‌سایت",
                "Website",
                display,
                ResolveUrl("website", webVal),
                "#2563eb",
                "M12 2a10 10 0 100 20 10 10 0 000-20zm7.93 9h-3.18a15.7 15.7 0 00-1.2-4.83A8.02 8.02 0 0119.93 11zM12 4.07c.88 1.4 1.57 3.32 1.88 5.43H10.12c.31-2.11 1-4.03 1.88-5.43zm-4.45 2.1A15.7 15.7 0 006.35 11H3.17a8.02 8.02 0 014.38-4.83zM3.17 13h3.18c.28 1.8.84 3.49 1.6 4.83A8.02 8.02 0 013.17 13zm8.83 6.93c-.88-1.4-1.57-3.32-1.88-5.43h3.76c-.31 2.11-1 4.03-1.88 5.43zm2.55-2.1c.76-1.34 1.32-3.03 1.6-4.83h3.18a8.02 8.02 0 01-4.78 4.83z"
            ));
        }

        return list;
    }
}
