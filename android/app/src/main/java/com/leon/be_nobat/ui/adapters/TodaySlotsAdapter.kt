package com.leon.be_nobat.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.leon.be_nobat.R
import com.leon.be_nobat.databinding.ItemTodaySlotBinding
import com.leon.be_nobat.domain.model.TodaySlotItem
import com.leon.be_nobat.helpers.PersianUtils

class TodaySlotsAdapter(
    private val onBookClick: (TodaySlotItem) -> Unit,
) : ListAdapter<TodaySlotItem, TodaySlotsAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<TodaySlotItem>() {
            override fun areItemsTheSame(a: TodaySlotItem, b: TodaySlotItem): Boolean =
                a.businessId == b.businessId && a.serviceId == b.serviceId && a.startsAt == b.startsAt

            override fun areContentsTheSame(a: TodaySlotItem, b: TodaySlotItem): Boolean = a == b
        }
    }

    inner class VH(val binding: ItemTodaySlotBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TodaySlotItem) {
            binding.apply {
                tvSlotTime.text = item.timeLabel
                tvBusinessName.text = item.businessName
                tvServiceName.text = "${item.serviceName} · ${PersianUtils.formatDuration(item.serviceDurationMinutes)}"

                val providerPart = if (item.resourceName.isNotBlank()) " · ${item.resourceName}" else ""
                tvBranchAndProvider.text = "${item.branchName}$providerPart"

                val priceText = if (item.price <= 0.0) {
                    if (PersianUtils.isEnglishLocale()) "Free" else "رایگان"
                } else {
                    PersianUtils.formatPrice(item.price, item.currency)
                }
                tvPrice.text = priceText

                if (!item.requiresApproval) {
                    tvApprovalBadge.visibility = View.VISIBLE
                    tvApprovalBadge.text = root.context.getString(R.string.today_slots_instant_badge)
                } else {
                    tvApprovalBadge.visibility = View.GONE
                }

                btnBookSlot.setOnClickListener { onBookClick(item) }
                root.setOnClickListener { onBookClick(item) }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        return VH(ItemTodaySlotBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }
}
