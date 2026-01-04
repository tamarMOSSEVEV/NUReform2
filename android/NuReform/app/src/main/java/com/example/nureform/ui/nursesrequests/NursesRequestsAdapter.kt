package com.example.nureform.ui.nursesrequests

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.nureform.R
import com.example.nureform.data.model.NurseShiftRequest
import com.example.nureform.utils.ShiftConstants

class NursesRequestsAdapter : ListAdapter<NurseShiftRequest, NursesRequestsAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_nurse_shift_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvNurseName: TextView = itemView.findViewById(R.id.tvNurseName)
        private val tvSunday: TextView = itemView.findViewById(R.id.tvSunday)
        private val tvMonday: TextView = itemView.findViewById(R.id.tvMonday)
        private val tvTuesday: TextView = itemView.findViewById(R.id.tvTuesday)
        private val tvWednesday: TextView = itemView.findViewById(R.id.tvWednesday)
        private val tvThursday: TextView = itemView.findViewById(R.id.tvThursday)
        private val tvFriday: TextView = itemView.findViewById(R.id.tvFriday)
        private val tvSaturday: TextView = itemView.findViewById(R.id.tvSaturday)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)

        fun bind(request: NurseShiftRequest) {
            tvNurseName.text = request.nurseName

            if (!request.hasSubmitted) {
                tvStatus.visibility = View.VISIBLE
                tvStatus.text = "לא בחר/ה משמרות"
                tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.error))

                // Set all days to X
                tvSunday.text = "X"
                tvMonday.text = "X"
                tvTuesday.text = "X"
                tvWednesday.text = "X"
                tvThursday.text = "X"
                tvFriday.text = "X"
                tvSaturday.text = "X"

                setDayTextColor(tvSunday, false)
                setDayTextColor(tvMonday, false)
                setDayTextColor(tvTuesday, false)
                setDayTextColor(tvWednesday, false)
                setDayTextColor(tvThursday, false)
                setDayTextColor(tvFriday, false)
                setDayTextColor(tvSaturday, false)
            } else {
                tvStatus.visibility = View.GONE

                // Display shifts for each day
                tvSunday.text = formatShifts(request.shifts[ShiftConstants.SUNDAY])
                tvMonday.text = formatShifts(request.shifts[ShiftConstants.MONDAY])
                tvTuesday.text = formatShifts(request.shifts[ShiftConstants.TUESDAY])
                tvWednesday.text = formatShifts(request.shifts[ShiftConstants.WEDNESDAY])
                tvThursday.text = formatShifts(request.shifts[ShiftConstants.THURSDAY])
                tvFriday.text = formatShifts(request.shifts[ShiftConstants.FRIDAY])
                tvSaturday.text = formatShifts(request.shifts[ShiftConstants.SATURDAY])

                setDayTextColor(tvSunday, request.shifts[ShiftConstants.SUNDAY]?.isNotEmpty() == true)
                setDayTextColor(tvMonday, request.shifts[ShiftConstants.MONDAY]?.isNotEmpty() == true)
                setDayTextColor(tvTuesday, request.shifts[ShiftConstants.TUESDAY]?.isNotEmpty() == true)
                setDayTextColor(tvWednesday, request.shifts[ShiftConstants.WEDNESDAY]?.isNotEmpty() == true)
                setDayTextColor(tvThursday, request.shifts[ShiftConstants.THURSDAY]?.isNotEmpty() == true)
                setDayTextColor(tvFriday, request.shifts[ShiftConstants.FRIDAY]?.isNotEmpty() == true)
                setDayTextColor(tvSaturday, request.shifts[ShiftConstants.SATURDAY]?.isNotEmpty() == true)
            }
        }

        private fun formatShifts(shifts: List<String>?): String {
            return if (shifts.isNullOrEmpty()) {
                "X"
            } else {
                shifts.joinToString(", ") { shift ->
                    when (shift) {
                        ShiftConstants.SHIFT_MORNING -> "ב"
                        ShiftConstants.SHIFT_NOON -> "צ"
                        ShiftConstants.SHIFT_EVENING -> "ע"
                        else -> shift
                    }
                }
            }
        }

        private fun setDayTextColor(textView: TextView, hasShifts: Boolean) {
            val color = if (hasShifts) {
                ContextCompat.getColor(itemView.context, R.color.secondary_turquoise)
            } else {
                ContextCompat.getColor(itemView.context, R.color.error)
            }
            textView.setTextColor(color)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<NurseShiftRequest>() {
        override fun areItemsTheSame(oldItem: NurseShiftRequest, newItem: NurseShiftRequest): Boolean {
            return oldItem.nurseId == newItem.nurseId
        }

        override fun areContentsTheSame(oldItem: NurseShiftRequest, newItem: NurseShiftRequest): Boolean {
            return oldItem == newItem
        }
    }
}

