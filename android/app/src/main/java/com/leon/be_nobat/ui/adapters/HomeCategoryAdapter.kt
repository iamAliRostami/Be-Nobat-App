package com.leon.be_nobat.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.leon.be_nobat.R

data class HomeCategoryItem(
    val name: String,
    val label: String,
    val icon: String,
    val isSelected: Boolean = false,
)

class HomeCategoryAdapter(
    private var items: List<HomeCategoryItem>,
    private val onCategoryClick: (HomeCategoryItem) -> Unit,
) : RecyclerView.Adapter<HomeCategoryAdapter.ViewHolder>() {

    fun submitList(newItems: List<HomeCategoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_home_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView.findViewById(R.id.cardCategory)
        private val tvIcon: TextView = itemView.findViewById(R.id.tvCategoryIcon)
        private val tvName: TextView = itemView.findViewById(R.id.tvCategoryName)

        fun bind(item: HomeCategoryItem) {
            tvName.text = item.label
            tvIcon.text = item.icon
            val ctx = itemView.context

            val density = ctx.resources.displayMetrics.density
            if (item.isSelected) {
                card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.category_chip_selected_bg))
                card.strokeColor = ContextCompat.getColor(ctx, R.color.category_chip_selected_bg)
                card.cardElevation = 4f * density
                tvName.setTextColor(ContextCompat.getColor(ctx, R.color.category_chip_selected_text))
                tvIcon.setTextColor(ContextCompat.getColor(ctx, R.color.category_chip_selected_text))
            } else {
                card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.colorSurface))
                card.strokeColor = ContextCompat.getColor(ctx, R.color.colorOutline)
                card.cardElevation = 1f * density
                tvName.setTextColor(ContextCompat.getColor(ctx, R.color.colorOnSurface))
                tvIcon.setTextColor(ContextCompat.getColor(ctx, R.color.colorPrimary))
            }

            card.setOnClickListener {
                onCategoryClick(item)
            }
        }
    }
}
