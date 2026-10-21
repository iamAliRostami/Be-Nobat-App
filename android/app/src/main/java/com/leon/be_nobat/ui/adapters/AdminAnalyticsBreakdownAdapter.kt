package com.leon.be_nobat.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.leon.be_nobat.R
import com.leon.be_nobat.domain.model.ManagementBreakdownItem
import java.text.NumberFormat
import java.util.Locale

class AdminAnalyticsBreakdownAdapter(
    private var items: List<ManagementBreakdownItem> = emptyList(),
    private var maxRevenue: Long = 1L,
) : RecyclerView.Adapter<AdminAnalyticsBreakdownAdapter.ViewHolder>() {

    fun submitList(newItems: List<ManagementBreakdownItem>) {
        items = newItems
        maxRevenue = maxOf(1L, newItems.maxOfOrNull { it.revenue } ?: 1L)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_analytics_breakdown, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], maxRevenue)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvBreakdownName)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvBreakdownSubtitle)
        private val tvRevenue: TextView = itemView.findViewById(R.id.tvBreakdownRevenue)
        private val tvHours: TextView = itemView.findViewById(R.id.tvBreakdownHours)
        private val tvAppointments: TextView = itemView.findViewById(R.id.tvBreakdownAppointments)
        private val tvRate: TextView = itemView.findViewById(R.id.tvBreakdownRate)
        private val pbProgress: ProgressBar = itemView.findViewById(R.id.pbBreakdownProgress)

        fun bind(item: ManagementBreakdownItem, maxRevenue: Long) {
            val ctx = itemView.context
            tvName.text = item.name
            val sub = item.branchName ?: item.businessName
            if (!sub.isNullOrBlank()) {
                tvSubtitle.visibility = View.VISIBLE
                tvSubtitle.text = sub
            } else {
                tvSubtitle.visibility = View.GONE
            }

            val nf = NumberFormat.getNumberInstance(Locale("fa", "IR"))
            tvRevenue.text = "${nf.format(item.revenue)} ${ctx.getString(R.string.analytics_toman_suffix)}"

            val hours = String.format(Locale("fa", "IR"), "%.1f", item.workMinutes / 60.0)
            tvHours.text = "$hours ${ctx.getString(R.string.analytics_hours_suffix)}"

            tvAppointments.text = "${nf.format(item.completedAppointments)} از ${nf.format(item.totalAppointments)}"

            val rate = if (item.totalAppointments > 0) {
                (item.completedAppointments * 100) / item.totalAppointments
            } else 0
            tvRate.text = "$rate٪"

            val progress = if (maxRevenue > 0) ((item.revenue * 100) / maxRevenue).toInt().coerceIn(5, 100) else 0
            pbProgress.progress = progress
        }
    }
}
