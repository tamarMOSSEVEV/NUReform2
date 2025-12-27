package com.example.nureform.ui.nurses

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.nureform.data.model.Nurse
import com.example.nureform.databinding.ItemNurseBinding

class NursesAdapter : ListAdapter<Nurse, NursesAdapter.NurseViewHolder>(NurseDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NurseViewHolder {
        val binding = ItemNurseBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NurseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NurseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NurseViewHolder(private val binding: ItemNurseBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(nurse: Nurse) {
            binding.tvNurseName.text = nurse.name
            binding.tvIdNumber.text = "ת.ז: ${nurse.idNumber}"
            binding.tvPhone.text = nurse.phone
            binding.tvEmail.text = nurse.email
        }
    }

    private class NurseDiffCallback : DiffUtil.ItemCallback<Nurse>() {
        override fun areItemsTheSame(oldItem: Nurse, newItem: Nurse): Boolean {
            return oldItem.idNumber == newItem.idNumber
        }

        override fun areContentsTheSame(oldItem: Nurse, newItem: Nurse): Boolean {
            return oldItem == newItem
        }
    }
}

