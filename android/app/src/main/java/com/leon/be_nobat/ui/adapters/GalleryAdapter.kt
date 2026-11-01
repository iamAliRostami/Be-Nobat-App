package com.leon.be_nobat.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.leon.be_nobat.R
import com.leon.be_nobat.data.remote.BeNobatApiConfig
import com.leon.be_nobat.domain.model.GalleryItem

class GalleryAdapter(
    private val onClick: (GalleryItem) -> Unit = {}
) : ListAdapter<GalleryItem, GalleryAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<GalleryItem>() {
            override fun areItemsTheSame(a: GalleryItem, b: GalleryItem) = a.id == b.id
            override fun areContentsTheSame(a: GalleryItem, b: GalleryItem) = a == b
        }
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPhoto: ImageView = itemView.findViewById(R.id.ivGalleryPhoto)
        private val tvCaption: TextView = itemView.findViewById(R.id.tvGalleryCaption)

        fun bind(item: GalleryItem) {
            val url = BeNobatApiConfig.resolveMediaUrl(item.imageUrl) ?: item.imageUrl
            ivPhoto.load(url) {
                crossfade(true)
                placeholder(R.drawable.badge_booked_background)
                error(R.drawable.badge_booked_background)
            }
            tvCaption.isVisible = !item.caption.isNullOrBlank()
            tvCaption.text = item.caption
            itemView.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gallery_photo, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }
}
