package com.example.nureform.ui.scheduling

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nureform.databinding.FragmentRunSchedulingBinding
import com.example.nureform.ui.BaseFragment
import com.example.nureform.ui.nursesrequests.NursesRequestsAdapter
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class RunSchedulingFragment : BaseFragment<FragmentRunSchedulingBinding>(
    FragmentRunSchedulingBinding::inflate
) {
    private val viewModel: RunSchedulingViewModel by viewModel()
    private val adapter = NursesRequestsAdapter()
    private var isSchedulingComplete = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        viewModel.loadNursesRequests()
    }

    private fun setupRecyclerView() {
        binding.rvNursesRequests.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@RunSchedulingFragment.adapter
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.weekInfo.collect { weekInfo ->
                binding.tvWeekInfo.text = weekInfo
            }
        }

        lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    is RunSchedulingState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.rvNursesRequests.visibility = View.GONE
                        binding.tvEmptyState.visibility = View.GONE
                        binding.btnRunScheduling.isEnabled = false
                    }

                    is RunSchedulingState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.rvNursesRequests.visibility = View.VISIBLE
                        binding.tvEmptyState.visibility = View.GONE
                        binding.btnRunScheduling.isEnabled = !isSchedulingComplete
                        adapter.submitList(state.requests)
                    }

                    is RunSchedulingState.Empty -> {
                        binding.progressBar.visibility = View.GONE
                        binding.rvNursesRequests.visibility = View.GONE
                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.btnRunScheduling.isEnabled = false
                    }

                    is RunSchedulingState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.rvNursesRequests.visibility = View.GONE
                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.tvEmptyState.text = state.message
                        binding.btnRunScheduling.isEnabled = false
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    }

                    is RunSchedulingState.RunningScheduling -> {
                        binding.progressBarScheduling.visibility = View.VISIBLE
                        binding.btnRunScheduling.isEnabled = false
                        binding.btnRunScheduling.text = "מריץ שיבוץ..."
                    }

                    is RunSchedulingState.SchedulingSuccess -> {
                        binding.progressBarScheduling.visibility = View.GONE
                        binding.btnRunScheduling.isEnabled = false
                        binding.btnRunScheduling.text = "שיבוץ הושלם ✓"
                        isSchedulingComplete = true
                        Toast.makeText(
                            requireContext(),
                            state.message,
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    is RunSchedulingState.SchedulingError -> {
                        binding.progressBarScheduling.visibility = View.GONE
                        binding.btnRunScheduling.isEnabled = !isSchedulingComplete
                        binding.btnRunScheduling.text = "הרץ שיבוץ"
                        Toast.makeText(
                            requireContext(),
                            state.message,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnRunScheduling.setOnClickListener {
            viewModel.runScheduling()
        }
    }
}
