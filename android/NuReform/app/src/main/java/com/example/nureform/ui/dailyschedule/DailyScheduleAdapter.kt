package com.example.nureform.ui.dailyschedule

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.nureform.R

class DailyScheduleAdapter : ListAdapter<DaySummary, DailyScheduleAdapter.DaySummaryViewHolder>(DIFF) {

    inner class DaySummaryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDayTitle: TextView = itemView.findViewById(R.id.tvDayTitle)
        private val tvMorningNurses: TextView = itemView.findViewById(R.id.tvMorningNurses)
        private val tvNoonNurses: TextView = itemView.findViewById(R.id.tvNoonNurses)
        private val tvEveningNurses: TextView = itemView.findViewById(R.id.tvEveningNurses)

        fun bind(day: DaySummary) {
            tvDayTitle.text = if (day.date.isNotEmpty()) "${day.dayName}  ${day.date}" else day.dayName
            tvMorningNurses.text = if (day.morning.isEmpty()) "—" else day.morning.joinToString("\n")
            tvNoonNurses.text = if (day.noon.isEmpty()) "—" else day.noon.joinToString("\n")
            tvEveningNurses.text = if (day.evening.isEmpty()) "—" else day.evening.joinToString("\n")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DaySummaryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_day_summary, parent, false)
        return DaySummaryViewHolder(view)
    }

    override fun onBindViewHolder(holder: DaySummaryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DaySummary>() {
            override fun areItemsTheSame(a: DaySummary, b: DaySummary) = a.dayName == b.dayName
            override fun areContentsTheSame(a: DaySummary, b: DaySummary) = a == b
        }
    }
}
