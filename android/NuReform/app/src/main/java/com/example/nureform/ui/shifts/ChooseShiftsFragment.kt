package com.example.nureform.ui.shifts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.nureform.databinding.FragmentChooseShiftsBinding
import com.example.nureform.databinding.ItemDayShiftBinding
import com.example.nureform.ui.BaseFragment
import com.example.nureform.utils.ShiftConstants
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class ChooseShiftsFragment : BaseFragment<FragmentChooseShiftsBinding>(
    FragmentChooseShiftsBinding::inflate
) {

    private val viewModel: ChooseShiftsViewModel by viewModel()
    private val dayViewsMap = mutableMapOf<String, DayShiftViews>()


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDaysViews()
        setupObservers()
        setupClickListeners()
        viewModel.initialize()
    }

    private fun setupDaysViews() {
        ShiftConstants.DAYS_OF_WEEK.forEach { (englishDay, hebrewDay) ->
            val itemBinding = ItemDayShiftBinding.inflate(
                LayoutInflater.from(requireContext()),
                binding.daysContainer,
                false
            )

            itemBinding.tvDayName.text = hebrewDay

            dayViewsMap[englishDay] = DayShiftViews(
                itemBinding.cbMorning,
                itemBinding.cbNoon,
                itemBinding.cbEvening
            )

            binding.daysContainer.addView(itemBinding.root)
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    is ChooseShiftsState.Idle -> {
                        showLoading(false)
                        enableForm(true)
                    }

                    is ChooseShiftsState.Loading -> {
                        showLoading(true)
                        enableForm(false)
                    }

                    is ChooseShiftsState.Success -> {
                        showLoading(false)
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        findNavController().navigateUp()
                    }

                    is ChooseShiftsState.Error -> {
                        showLoading(false)
                        enableForm(true)
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    }

                    is ChooseShiftsState.AlreadySubmitted -> {
                        showLoading(false)
                        enableForm(false)
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    }

                    is ChooseShiftsState.WindowClosed -> {
                        showLoading(false)
                        enableForm(false)
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.weekInfo.collect { weekInfo ->
                binding.tvWeekInfo.text = weekInfo
            }
        }

        // Observe existing shifts and pre-populate checkboxes
        lifecycleScope.launch {
            viewModel.existingShifts.collect { existingShifts ->
                populateExistingShifts(existingShifts)
            }
        }
    }

    private fun populateExistingShifts(shifts: Map<String, List<String>>) {
        if (shifts.isEmpty()) return

        shifts.forEach { (day, selectedShifts) ->
            dayViewsMap[day]?.let { views ->
                views.cbMorning.isChecked = selectedShifts.contains(ShiftConstants.SHIFT_MORNING)
                views.cbNoon.isChecked = selectedShifts.contains(ShiftConstants.SHIFT_NOON)
                views.cbEvening.isChecked = selectedShifts.contains(ShiftConstants.SHIFT_EVENING)
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnSubmit.setOnClickListener {
            val shifts = collectShifts()
            viewModel.submitShifts(shifts)
        }
    }

    private fun collectShifts(): Map<String, List<String>> {
        val shifts = mutableMapOf<String, List<String>>()

        dayViewsMap.forEach { (day, views) ->
            val selectedShifts = mutableListOf<String>()

            if (views.cbMorning.isChecked) selectedShifts.add(ShiftConstants.SHIFT_MORNING)
            if (views.cbNoon.isChecked) selectedShifts.add(ShiftConstants.SHIFT_NOON)
            if (views.cbEvening.isChecked) selectedShifts.add(ShiftConstants.SHIFT_EVENING)

            if (selectedShifts.isNotEmpty()) {
                shifts[day] = selectedShifts
            }
        }

        return shifts
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSubmit.isEnabled = !isLoading
    }

    private fun enableForm(enabled: Boolean) {
        binding.btnSubmit.isEnabled = enabled
        dayViewsMap.values.forEach { views ->
            views.cbMorning.isEnabled = enabled
            views.cbNoon.isEnabled = enabled
            views.cbEvening.isEnabled = enabled
        }
    }

    private data class DayShiftViews(
        val cbMorning: CheckBox,
        val cbNoon: CheckBox,
        val cbEvening: CheckBox
    )
}

