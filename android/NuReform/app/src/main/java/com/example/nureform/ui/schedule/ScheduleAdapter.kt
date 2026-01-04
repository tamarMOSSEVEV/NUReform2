package com.example.nureform.ui.schedule

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.nureform.data.model.NurseSchedule
import com.example.nureform.databinding.ItemNurseScheduleBinding

class ScheduleAdapter : ListAdapter<NurseSchedule, ScheduleAdapter.ScheduleViewHolder>(ScheduleDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val binding = ItemNurseScheduleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ScheduleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ScheduleViewHolder(private val binding: ItemNurseScheduleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(schedule: NurseSchedule) {
            binding.tvNurseName.text = schedule.nurseName
            binding.tvSunday.text = schedule.sunday
            binding.tvMonday.text = schedule.monday
            binding.tvTuesday.text = schedule.tuesday
            binding.tvWednesday.text = schedule.wednesday
            binding.tvThursday.text = schedule.thursday
            binding.tvFriday.text = schedule.friday
            binding.tvSaturday.text = schedule.saturday
        }
    }

    private class ScheduleDiffCallback : DiffUtil.ItemCallback<NurseSchedule>() {
        override fun areItemsTheSame(oldItem: NurseSchedule, newItem: NurseSchedule): Boolean {
            return oldItem.nurseName == newItem.nurseName
        }

        override fun areContentsTheSame(oldItem: NurseSchedule, newItem: NurseSchedule): Boolean {
            return oldItem == newItem
        }
    }
}

