package com.example.nureform.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.nureform.LoginActivity
import com.example.nureform.R
import com.example.nureform.data.model.UserRole
import com.example.nureform.databinding.FragmentHomeBinding
import com.example.nureform.ui.BaseFragment
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class HomeFragment : BaseFragment<FragmentHomeBinding>(
    FragmentHomeBinding::inflate
) {

    private val viewModel: HomeViewModel by viewModel()


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
        setupClickListeners()
        viewModel.loadUserData()
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.user.collect { user ->
                if (user != null) {
                    binding.tvWelcome.text = getString(R.string.welcome_user, user.name)
                    when (user.role) {
                        UserRole.MANAGER -> {
                            showManagerButtons()
                        }

                        UserRole.NURSE -> {
                            showNurseButtons()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.loading.collect { isLoading ->
                binding.progressContainer.visibility = if (isLoading) View.VISIBLE else View.GONE
                binding.contentGroup.visibility = if (isLoading) View.GONE else View.VISIBLE
            }
        }
    }

    private fun showManagerButtons() {
        // Show all manager buttons
        binding.cardAddNurses.visibility = View.VISIBLE
        binding.cardNursesDetails.visibility = View.VISIBLE
        binding.cardNursesRequests.visibility = View.VISIBLE
        binding.cardDeleteNurse.visibility = View.VISIBLE
        binding.cardWeeklyShifts.visibility = View.VISIBLE
    }

    private fun showNurseButtons() {
        // Hide manager-only buttons for nurses
        binding.cardAddNurses.visibility = View.GONE
        binding.cardNursesDetails.visibility = View.VISIBLE  // Nurses can see other nurses
        binding.cardNursesRequests.visibility = View.GONE
        binding.cardDeleteNurse.visibility = View.GONE
        binding.cardWeeklyShifts.visibility = View.VISIBLE  // Nurses can see weekly shifts
    }

    private fun setupClickListeners() {
        binding.btnLogout.setOnClickListener {
            viewModel.logout()
            navigateToLogin()
        }

        binding.btnAddNurses.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_addNurseFragment)
        }

        binding.btnNursesDetails.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_nursesListFragment)
        }

        binding.btnNursesRequests.setOnClickListener {
            // TODO: Navigate to nurses requests screen
        }

        binding.btnDeleteNurse.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_deleteNurseFragment)
        }

        binding.btnWeeklyShifts.setOnClickListener {
            // TODO: Navigate to weekly shifts screen
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }
}

