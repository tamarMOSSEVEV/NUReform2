package com.example.nureform.ui.dailyschedule

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.nureform.R
import com.example.nureform.databinding.FragmentDailyScheduleBinding
import com.example.nureform.ui.BaseFragment
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class DailyScheduleFragment : BaseFragment<FragmentDailyScheduleBinding>(
    FragmentDailyScheduleBinding::inflate
) {
    private val viewModel: DailyScheduleViewModel by viewModel()
    private val adapter = DailyScheduleAdapter()
    private var weekObserverJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvDailySummary.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.rvDailySummary.adapter = adapter

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        setupObservers()
        viewModel.loadSummaries()

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                switchToWeek(isCurrentWeek = tab.position == 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.currentWeekLabel.collect { label ->
                binding.tabLayout.getTabAt(0)?.text = label
            }
        }
        lifecycleScope.launch {
            viewModel.nextWeekLabel.collect { label ->
                binding.tabLayout.getTabAt(1)?.text = label
            }
        }
        switchToWeek(isCurrentWeek = true)
    }

    private fun switchToWeek(isCurrentWeek: Boolean) {
        weekObserverJob?.cancel()
        val stateFlow = if (isCurrentWeek) viewModel.currentWeekState else viewModel.nextWeekState
        weekObserverJob = lifecycleScope.launch {
            stateFlow.collect { state ->
                when (state) {
                    is DailyScheduleState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.rvDailySummary.visibility = View.GONE
                        binding.tvEmptyState.visibility = View.GONE
                    }
                    is DailyScheduleState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.tvEmptyState.visibility = View.GONE
                        binding.rvDailySummary.visibility = View.VISIBLE
                        adapter.submitList(state.days)
                    }
                    is DailyScheduleState.Empty, is DailyScheduleState.Idle -> {
                        binding.progressBar.visibility = View.GONE
                        binding.rvDailySummary.visibility = View.GONE
                        binding.tvEmptyState.visibility = View.VISIBLE
                    }
                    is DailyScheduleState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.rvDailySummary.visibility = View.GONE
                        binding.tvEmptyState.visibility = View.VISIBLE
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun findNavController() =
        androidx.navigation.fragment.NavHostFragment.findNavController(this)
}
