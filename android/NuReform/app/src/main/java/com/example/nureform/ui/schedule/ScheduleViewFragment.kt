package com.example.nureform.ui.schedule

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nureform.databinding.FragmentScheduleViewBinding
import com.example.nureform.ui.BaseFragment
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class ScheduleViewFragment : BaseFragment<FragmentScheduleViewBinding>(
    FragmentScheduleViewBinding::inflate
) {

    private val viewModel: ScheduleViewModel by viewModel()
    private val args: ScheduleViewFragmentArgs by navArgs()
    private val scheduleAdapter = ScheduleAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        viewModel.loadSchedule(
            args.showAllNurses, args.nurseName)
    }

    private fun setupRecyclerView() {
        binding.rvSchedule.apply {
            adapter = scheduleAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    is ScheduleViewState.Idle -> {
                        showLoading(false)
                        hideEmptyState()
                    }

                    is ScheduleViewState.Loading -> {
                        showLoading(true)
                        hideEmptyState()
                    }

                    is ScheduleViewState.Success -> {
                        showLoading(false)
                        hideEmptyState()
                        scheduleAdapter.submitList(state.schedules)
                    }

                    is ScheduleViewState.Empty -> {
                        showLoading(false)
                        showEmptyState()
                    }

                    is ScheduleViewState.Error -> {
                        showLoading(false)
                        showEmptyState()
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.weekInfo.collect { weekInfo ->
                binding.tvWeekInfo.text = weekInfo
            }
        }

        lifecycleScope.launch {
            viewModel.title.collect { title ->
                binding.tvTitle.text = title
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.rvSchedule.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    private fun showEmptyState() {
        binding.tvEmptyState.visibility = View.VISIBLE
        binding.rvSchedule.visibility = View.GONE
    }

    private fun hideEmptyState() {
        binding.tvEmptyState.visibility = View.GONE
        binding.rvSchedule.visibility = View.VISIBLE
    }
}

