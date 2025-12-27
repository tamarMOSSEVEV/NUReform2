package com.example.nureform.ui.nurses

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nureform.databinding.FragmentNursesListBinding
import com.example.nureform.ui.BaseFragment
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class NursesListFragment : BaseFragment<FragmentNursesListBinding>(
    FragmentNursesListBinding::inflate
) {

    private val viewModel: NursesListViewModel by viewModel()
    private val nursesAdapter = NursesAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        viewModel.loadNurses()
    }

    private fun setupRecyclerView() {
        binding.rvNurses.apply {
            adapter = nursesAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.nursesListState.collect { state ->
                when (state) {
                    NursesListState.Idle -> {
                        showLoading(false)
                    }
                    NursesListState.Loading -> {
                        showLoading(true)
                        hideEmptyState()
                    }
                    is NursesListState.Success -> {
                        showLoading(false)
                        hideEmptyState()
                        nursesAdapter.submitList(state.nurses)
                        binding.tvNursesCount.text = "סך הכל: ${state.nurses.size} אחיות"
                    }
                    NursesListState.Empty -> {
                        showLoading(false)
                        showEmptyState()
                        nursesAdapter.submitList(emptyList())
                        binding.tvNursesCount.text = "סך הכל: 0 אחיות"
                    }
                    is NursesListState.Error -> {
                        showLoading(false)
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    }
                }
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
        binding.rvNurses.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    private fun showEmptyState() {
        binding.emptyState.visibility = View.VISIBLE
        binding.rvNurses.visibility = View.GONE
    }

    private fun hideEmptyState() {
        binding.emptyState.visibility = View.GONE
        binding.rvNurses.visibility = View.VISIBLE
    }
}

