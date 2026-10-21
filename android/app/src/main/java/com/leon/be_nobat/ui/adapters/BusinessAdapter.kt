package com.leon.be_nobat.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.leon.be_nobat.databinding.ItemBusinessBinding
import com.leon.be_nobat.domain.model.Business

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
    }

    inner class VH(val binding: ItemBusinessBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Business) {
            binding.apply {
                tvBusinessName.text = item.name
                tvCategory.text = item.category
                tvCity.text = item.city
                tvDescription.text = item.description
                tvBranchCount.text = if (item.branchCount == 1) {
                    root.context.getString(com.leon.be_nobat.R.string.branch_count_single)
                } else {
                    root.context.getString(com.leon.be_nobat.R.string.branch_count, com.leon.be_nobat.helpers.PersianUtils.toPersianDigits(item.branchCount))
                }
                tvServiceCount.text = if (item.serviceCount == 1) {
                    root.context.getString(com.leon.be_nobat.R.string.service_count_single)
                } else {
                    root.context.getString(com.leon.be_nobat.R.string.service_count, com.leon.be_nobat.helpers.PersianUtils.toPersianDigits(item.serviceCount))
                }
                tvRating.text = item.rating?.let {
                    "★ ${com.leon.be_nobat.helpers.PersianUtils.toPersianDigits(it)}"
                } ?: ""
                tvRequiresApproval.visibility = if (item.requiresApproval) android.view.View.VISIBLE else android.view.View.GONE
                val fav = isFavorite(item.id)
                val favColor = androidx.core.content.ContextCompat.getColor(
                    root.context,
                    if (fav) com.leon.be_nobat.R.color.favorite_active else com.leon.be_nobat.R.color.favorite_inactive
                )
                btnFavorite.setImageResource(
                    if (fav) com.leon.be_nobat.R.drawable.ic_heart_filled else com.leon.be_nobat.R.drawable.ic_heart_outline
                )
                androidx.core.widget.ImageViewCompat.setImageTintList(
                    btnFavorite,
                    android.content.res.ColorStateList.valueOf(favColor)
                )
                btnFavorite.contentDescription = root.context.getString(
                    if (fav) com.leon.be_nobat.R.string.remove_favorite else com.leon.be_nobat.R.string.add_to_favorites
                )
                btnFavorite.setOnClickListener { onFavoriteToggle(item, fav) }
                root.setOnClickListener { onClick(item) }
                btnBook.setOnClickListener { onClick(item) }
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
