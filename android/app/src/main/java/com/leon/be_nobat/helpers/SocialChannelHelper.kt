package com.leon.be_nobat.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.leon.be_nobat.R
import com.leon.be_nobat.domain.model.Branch
import com.leon.be_nobat.domain.model.BusinessDetail

data class SocialChannelItem(
    val key: String,
    val name: String,
    val handle: String,
    val badgeText: String,
    val badgeColor: Int,
    val url: String,
    val isPhone: Boolean = false
)

object SocialChannelHelper {

    fun resolveSocialUrl(key: String, raw: String): String {
        val clean = raw.trim()
        if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
            return clean
        }
        val handle = clean.removePrefix("@").trim()
        return when (key.lowercase()) {
            "telegram" -> "https://t.me/$handle"
            "instagram" -> "https://instagram.com/$handle"
            "eitaa" -> "https://eitaa.com/$handle"
            "bale" -> "https://ble.ir/$handle"
            "rubika" -> "https://rubika.ir/$handle"
            "twitter" -> "https://x.com/$handle"
            "whatsapp" -> {
                val digits = handle.filter { it.isDigit() }
                "https://wa.me/$digits"
            }
            "linkedin" -> {
                if (handle.startsWith("company/") || handle.startsWith("in/")) {
                    "https://linkedin.com/$handle"
                } else {
                    "https://linkedin.com/in/$handle"
                }
            }
            "aparat" -> "https://aparat.com/$handle"
            "website" -> "https://$clean"
            "phone" -> "tel:$clean"
            else -> clean
        }
    }

    fun formatDisplayHandle(key: String, raw: String): String {
        val clean = raw.trim()
        if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
            return clean.removePrefix("https://").removePrefix("http://").removeSuffix("/")
        }
        val handle = clean.removePrefix("@").trim()
        return when (key.lowercase()) {
            "telegram", "instagram", "eitaa", "bale", "rubika", "twitter", "aparat" -> "@$handle"
            else -> clean
        }
    }

    /**
     * Resolves the effective channels for a business and optional selected branch.
     * If a branch has a channel set, it uses the branch's channel;
     * otherwise falls back to the business's channel.
     * Also includes branch phone if provided.
     */
    fun resolveEffectiveChannels(
        business: BusinessDetail?,
        branch: Branch? = null
    ): List<SocialChannelItem> {
        val items = mutableListOf<SocialChannelItem>()

        // 1. Phone (Branch specific)
        val phone = branch?.phone?.trim()?.takeIf { it.isNotBlank() }
        if (!phone.isNullOrBlank()) {
            items.add(
                SocialChannelItem(
                    key = "phone",
                    name = "تلفن تماس شعبه",
                    handle = PersianUtils.toPersianDigits(phone),
                    badgeText = "TEL",
                    badgeColor = 0xFF0D9488.toInt(),
                    url = "tel:$phone",
                    isPhone = true
                )
            )
        }

        // Helper to check branch first, then business
        fun getVal(branchVal: String?, bizVal: String?): String? {
            return branchVal?.trim()?.takeIf { it.isNotBlank() }
                ?: bizVal?.trim()?.takeIf { it.isNotBlank() }
        }

        // 2. Instagram
        getVal(branch?.instagram, business?.instagram)?.let { v ->
            items.add(
                SocialChannelItem(
                    key = "instagram",
                    name = "اینستاگرام",
                    handle = formatDisplayHandle("instagram", v),
                    badgeText = "IG",
                    badgeColor = 0xFFE1306C.toInt(),
                    url = resolveSocialUrl("instagram", v)
                )
            )
        }

        // 3. Telegram
        getVal(branch?.telegram, business?.telegram)?.let { v ->
            items.add(
                SocialChannelItem(
                    key = "telegram",
                    name = "تلگرام",
                    handle = formatDisplayHandle("telegram", v),
                    badgeText = "TG",
                    badgeColor = 0xFF229ED9.toInt(),
                    url = resolveSocialUrl("telegram", v)
                )
            )
        }

        // 4. WhatsApp
        getVal(branch?.whatsApp, business?.whatsApp)?.let { v ->
            items.add(
                SocialChannelItem(
                    key = "whatsapp",
                    name = "واتساپ",
                    handle = formatDisplayHandle("whatsapp", v),
                    badgeText = "WA",
                    badgeColor = 0xFF25D366.toInt(),
                    url = resolveSocialUrl("whatsapp", v)
                )
            )
        }

        // 5. Eitaa
        getVal(branch?.eitaa, business?.eitaa)?.let { v ->
            items.add(
                SocialChannelItem(
                    key = "eitaa",
                    name = "ایتا",
                    handle = formatDisplayHandle("eitaa", v),
                    badgeText = "ET",
                    badgeColor = 0xFFEE822F.toInt(),
                    url = resolveSocialUrl("eitaa", v)
                )
            )
        }

        // 6. Bale
        getVal(branch?.bale, business?.bale)?.let { v ->
            items.add(
                SocialChannelItem(
                    key = "bale",
                    name = "بله",
                    handle = formatDisplayHandle("bale", v),
                    badgeText = "BL",
                    badgeColor = 0xFF14A360.toInt(),
                    url = resolveSocialUrl("bale", v)
                )
            )
        }

        // 7. Rubika
        getVal(branch?.rubika, business?.rubika)?.let { v ->
            items.add(
                SocialChannelItem(
                    key = "rubika",
                    name = "روبیکا",
                    handle = formatDisplayHandle("rubika", v),
                    badgeText = "RB",
                    badgeColor = 0xFF7B3FE4.toInt(),
                    url = resolveSocialUrl("rubika", v)
                )
            )
        }

        // 8. LinkedIn
        getVal(branch?.linkedIn, business?.linkedIn)?.let { v ->
            items.add(
                SocialChannelItem(
                    key = "linkedin",
                    name = "لینکدین",
                    handle = formatDisplayHandle("linkedin", v),
                    badgeText = "IN",
                    badgeColor = 0xFF0077B5.toInt(),
                    url = resolveSocialUrl("linkedin", v)
                )
            )
        }

        // 9. Twitter / X
        getVal(branch?.twitter, business?.twitter)?.let { v ->
            items.add(
                SocialChannelItem(
                    key = "twitter",
                    name = "ایکس (توییتر)",
                    handle = formatDisplayHandle("twitter", v),
                    badgeText = "X",
                    badgeColor = 0xFF1E293B.toInt(),
                    url = resolveSocialUrl("twitter", v)
                )
            )
        }

        // 10. Aparat
        getVal(branch?.aparat, business?.aparat)?.let { v ->
            items.add(
                SocialChannelItem(
                    key = "aparat",
                    name = "آپارات",
                    handle = formatDisplayHandle("aparat", v),
                    badgeText = "AP",
                    badgeColor = 0xFFED145B.toInt(),
                    url = resolveSocialUrl("aparat", v)
                )
            )
        }

        // 11. Website
        getVal(branch?.website, business?.website)?.let { v ->
            items.add(
                SocialChannelItem(
                    key = "website",
                    name = "وب‌سایت",
                    handle = formatDisplayHandle("website", v),
                    badgeText = "WEB",
                    badgeColor = 0xFF00897B.toInt(),
                    url = resolveSocialUrl("website", v)
                )
            )
        }

        return items
    }

    fun bindSocialChannels(
        context: Context,
        container: LinearLayout,
        channels: List<SocialChannelItem>,
        onOpen: ((SocialChannelItem) -> Unit)? = null
    ) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(context)

        for (item in channels) {
            val itemView = inflater.inflate(R.layout.item_contact_social, container, false)
            val cardIcon = itemView.findViewById<MaterialCardView>(R.id.cardSocialIcon)
            val tvBadge = itemView.findViewById<TextView>(R.id.tvSocialBadge)
            val tvName = itemView.findViewById<TextView>(R.id.tvSocialName)
            val tvHandle = itemView.findViewById<TextView>(R.id.tvSocialHandle)
            val btnOpen = itemView.findViewById<MaterialButton>(R.id.btnOpenSocial)

            cardIcon.setCardBackgroundColor(item.badgeColor)
            tvBadge.text = item.badgeText
            tvName.text = item.name
            tvHandle.text = item.handle

            if (item.isPhone) {
                btnOpen.text = "تماس"
            } else {
                btnOpen.text = context.getString(R.string.action_view)
            }

            val openAction = {
                if (onOpen != null) {
                    onOpen(item)
                } else {
                    openChannel(context, item)
                }
            }

            itemView.setOnClickListener { openAction() }
            btnOpen.setOnClickListener { openAction() }

            container.addView(itemView)
        }
    }

    fun openChannel(context: Context, item: SocialChannelItem) {
        if (item.url.isBlank()) return
        runCatching {
            val intent = if (item.isPhone) {
                Intent(Intent.ACTION_DIAL, Uri.parse(item.url))
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
            }
            context.startActivity(intent)
        }.onFailure {
            DeepLinkHelper.copyToClipboard(context, item.url, item.name)
            Toast.makeText(context, item.url, Toast.LENGTH_SHORT).show()
        }
    }
}
