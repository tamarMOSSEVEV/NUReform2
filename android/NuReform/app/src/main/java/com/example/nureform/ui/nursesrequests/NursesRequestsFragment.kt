package com.example.nureform.ui.nursesrequests

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nureform.databinding.FragmentNursesRequestsBinding
import com.example.nureform.ui.BaseFragment
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class NursesRequestsFragment : BaseFragment<FragmentNursesRequestsBinding>(
    FragmentNursesRequestsBinding::inflate
) {
    private val viewModel: NursesRequestsViewModel by viewModel()
    private val adapter = NursesRequestsAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObservers()
        viewModel.loadNursesRequests()
    }

    private fun setupRecyclerView() {
        binding.rvNursesRequests.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@NursesRequestsFragment.adapter
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    is NursesRequestsState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.rvNursesRequests.visibility = View.GONE
                        binding.tvError.visibility = View.GONE
                        binding.tvEmptyState.visibility = View.GONE
                        binding.legendLayout.visibility = View.GONE
                    }

                    is NursesRequestsState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.tvError.visibility = View.GONE
                        binding.tvWeekInfo.text = state.weekInfo

                        if (state.requests.isEmpty()) {
                            binding.rvNursesRequests.visibility = View.GONE
                            binding.tvEmptyState.visibility = View.VISIBLE
                            binding.legendLayout.visibility = View.GONE
                        } else {
                            binding.rvNursesRequests.visibility = View.VISIBLE
                            binding.tvEmptyState.visibility = View.GONE
                            binding.legendLayout.visibility = View.VISIBLE
                            adapter.submitList(state.requests)
                        }
                    }

                    is NursesRequestsState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.rvNursesRequests.visibility = View.GONE
                        binding.tvEmptyState.visibility = View.GONE
                        binding.legendLayout.visibility = View.GONE
                        binding.tvError.visibility = View.VISIBLE
                        binding.tvError.text = state.message
                    }
                }
            }
        }
    }
}

