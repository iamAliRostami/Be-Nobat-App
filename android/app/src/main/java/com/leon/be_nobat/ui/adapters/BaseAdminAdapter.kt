package com.leon.be_nobat.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.leon.be_nobat.databinding.ItemAdminAppointmentBinding

class AdminViewHolder(val binding: ItemAdminAppointmentBinding) : RecyclerView.ViewHolder(binding.root)

/**
 * Generic list adapter for management list items. Subclasses provide a
 * [bindHolder] implementation that maps the typed item to the row binding.
 */
abstract class BaseAdminAdapter<T>(
    diff: DiffUtil.ItemCallback<T>,
) : ListAdapter<T, AdminViewHolder>(diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdminViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return AdminViewHolder(ItemAdminAppointmentBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: AdminViewHolder, position: Int) {
        val b = holder.binding
        b.viewStatusIndicator.visibility = android.view.View.GONE
        b.btnQrCode.visibility = android.view.View.GONE
        b.btnAction3.visibility = android.view.View.GONE
        b.btnAction3.icon = null
        b.btnAction1.icon = null
        b.btnAction2.icon = null
        b.actionRow.visibility = android.view.View.VISIBLE

        val context = b.root.context
        val density = context.resources.displayMetrics.density
        val primaryColor = com.google.android.material.color.MaterialColors.getColor(b.btnAction1, androidx.appcompat.R.attr.colorPrimary)
        val onPrimaryColor = com.google.android.material.color.MaterialColors.getColor(b.btnAction2, com.google.android.material.R.attr.colorOnPrimary)

        // Reset btnAction1 default (Outlined)
        b.btnAction1.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
        b.btnAction1.setTextColor(primaryColor)
        b.btnAction1.iconTint = android.content.res.ColorStateList.valueOf(primaryColor)
        b.btnAction1.strokeColor = android.content.res.ColorStateList.valueOf(primaryColor)
        b.btnAction1.strokeWidth = (1 * density).toInt()

        // Reset btnAction2 default (Filled Primary)
        b.btnAction2.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
        b.btnAction2.setTextColor(onPrimaryColor)
        b.btnAction2.iconTint = android.content.res.ColorStateList.valueOf(onPrimaryColor)
        b.btnAction2.strokeWidth = 0

        bindHolder(holder, getItem(position))
    }

    abstract fun bindHolder(holder: AdminViewHolder, item: T)
}

/**
 * Convenience factory for a DiffUtil.ItemCallback that compares by id+contents.
 *
 * NOTE: The lambdas are kept as `Boolean`-returning (T, T) -> Boolean rather
 * than `crossinline` so the function can return a real DiffUtil.ItemCallback
 * instance (the `object :` literal needs a concrete class at compile time,
 * which is provided via the [T] type parameter even when erased to Object
 * at the JVM level — the runtime call dispatch is by interface, so it works
 * transparently with the actual T of each adapter).
 */
class SimpleDiffCallback<T : Any>(
    private val itemsSame: (T, T) -> Boolean,
    private val contentsSame: (T, T) -> Boolean,
) : DiffUtil.ItemCallback<T>() {
    override fun areItemsTheSame(oldItem: T, newItem: T): Boolean = itemsSame(oldItem, newItem)
    override fun areContentsTheSame(oldItem: T, newItem: T): Boolean = contentsSame(oldItem, newItem)
}

fun <T : Any> simpleDiff(itemsSame: (T, T) -> Boolean, contentSame: (T, T) -> Boolean): DiffUtil.ItemCallback<T> =
    SimpleDiffCallback(itemsSame, contentSame)
