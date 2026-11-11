package com.leon.be_nobat.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.leon.be_nobat.R
import com.leon.be_nobat.databinding.ItemBusinessBinding
import com.leon.be_nobat.domain.model.Business
import com.leon.be_nobat.helpers.PersianUtils
import java.util.Locale

class BusinessAdapter(
    private val onClick: (Business) -> Unit,
    private val onFavoriteToggle: (Business, Boolean) -> Unit,
    private val isFavorite: (String) -> Boolean = { false },
) : ListAdapter<Business, BusinessAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Business>() {
            override fun areItemsTheSame(a: Business, b: Business) = a.id == b.id
            override fun areContentsTheSame(a: Business, b: Business) = a == b
        }

        private fun getCoverBackground(category: String): Int {
            return when {
                category.contains("دندان") -> R.drawable.bg_business_cover_blue
                category.contains("پزشک") || category.contains("درمان") || category.contains("کلینیک") -> R.drawable.bg_business_cover_blue
                category.contains("ماساژ") || category.contains("تندرست") || category.contains("ورزش") -> R.drawable.bg_business_cover_teal
                category.contains("زیبایی") || category.contains("پوست") || category.contains("ناخن") -> R.drawable.bg_business_cover_rose
                category.contains("پیرایش") || category.contains("مو") || category.contains("آموزش") || category.contains("زبان") -> R.drawable.bg_business_cover_purple
                else -> R.drawable.bg_business_cover_teal
            }
        }

        private fun getCoverIcon(category: String): String {
            return when {
                category.contains("دندان") -> "✚"
                category.contains("پزشک") || category.contains("درمان") -> "✚"
                category.contains("ماساژ") || category.contains("تندرست") -> "♧"
                category.contains("ورزش") -> "⚖"
                category.contains("پیرایش") || category.contains("مو") -> "✂"
                category.contains("دامپزشک") -> "🐾"
                category.contains("روان") || category.contains("مشاور") -> "⌁"
                category.contains("آموزش") || category.contains("زبان") -> "✦"
                else -> "✦"
            }
        }
    }

    inner class VH(val binding: ItemBusinessBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Business) {
            binding.apply {
                val cat = item.category.trim()
                tvCategory.text = cat.ifBlank { root.context.getString(R.string.cat_all) }
                tvBusinessName.text = item.name
                tvCity.text = item.city.ifBlank { "ایران" }

                // Decorative Cover
                coverLayout.setBackgroundResource(getCoverBackground(cat))
                tvCoverIcon.text = getCoverIcon(cat)

                // Rating & Reviews
                if (item.rating != null && item.rating > 0) {
                    val rateFormatted = PersianUtils.toPersianDigits(String.format(Locale.US, "%.1f", item.rating))
                    tvRating.text = "★ $rateFormatted"
                    if (item.reviewCount > 0) {
                        tvReviewCount.isVisible = true
                        tvReviewCount.text = "(${root.context.getString(R.string.review_count, PersianUtils.toPersianDigits(item.reviewCount))})"
                    } else {
                        tvReviewCount.isVisible = false
                    }
                } else {
                    tvRating.text = root.context.getString(R.string.no_reviews_short)
                    tvReviewCount.isVisible = false
                }

                // Description
                tvDescription.isVisible = item.description.isNotBlank()
                tvDescription.text = item.description

                // Branch & Service Counts
                tvBranchCount.text = if (item.branchCount == 1) {
                    root.context.getString(R.string.branch_count_single)
                } else {
                    root.context.getString(R.string.branch_count, PersianUtils.toPersianDigits(item.branchCount))
                }
                tvServiceCount.text = if (item.serviceCount == 1) {
                    root.context.getString(R.string.service_count_single)
                } else {
                    root.context.getString(R.string.service_count, PersianUtils.toPersianDigits(item.serviceCount))
                }

                // Requires Approval
                tvRequiresApproval.isVisible = item.requiresApproval
                if (item.requiresApproval) {
                    tvRequiresApproval.setText(R.string.requires_approval)
                }

                // Favorite
                val fav = isFavorite(item.id)
                btnFavorite.setImageResource(
                    if (fav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                )
                androidx.core.widget.ImageViewCompat.setImageTintList(
                    btnFavorite,
                    android.content.res.ColorStateList.valueOf(
                        if (fav) Color.parseColor("#EF4444") else Color.WHITE
                    )
                )
                btnFavorite.contentDescription = root.context.getString(
                    if (fav) R.string.remove_favorite else R.string.add_to_favorites
                )
                btnFavorite.setOnClickListener { onFavoriteToggle(item, fav) }

                // Actions
                root.setOnClickListener { onClick(item) }
                btnBook.setOnClickListener { onClick(item) }
                btnDetails.setOnClickListener { onClick(item) }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        return VH(ItemBusinessBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }
}