package com.example.nureform.ui.nurses

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.nureform.data.model.Nurse
import com.example.nureform.databinding.ItemNurseBinding

class NursesAdapter(
    private val shouldHideId: Boolean = false
) : ListAdapter<Nurse, NursesAdapter.NurseViewHolder>(NurseDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NurseViewHolder {
        val binding = ItemNurseBinding.inflate(
            /* inflater = */ LayoutInflater.from(parent.context),
            /* parent = */ parent,
            /* attachToParent = */ false
        )
        return NurseViewHolder(binding, shouldHideId)
    }

    override fun onBindViewHolder(holder: NurseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NurseViewHolder(
        private val binding: ItemNurseBinding,
        private val shouldHideId: Boolean
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(nurse: Nurse) {
            binding.tvNurseName.text = nurse.name

            // Hide ID number if user is a nurse
            if (shouldHideId) {
                binding.tvIdNumber.visibility = View.GONE
            } else {
                binding.tvIdNumber.visibility = View.VISIBLE
                binding.tvIdNumber.text = "ת.ז: ${nurse.idNumber}"
            }

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

