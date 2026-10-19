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
