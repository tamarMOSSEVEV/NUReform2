package com.example.nureform.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.nureform.LoginActivity
import com.example.nureform.R
import com.example.nureform.data.model.User
import com.example.nureform.data.model.UserRole
import com.example.nureform.databinding.FragmentHomeBinding
import com.example.nureform.ui.BaseFragment
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class HomeFragment : BaseFragment<FragmentHomeBinding>(
    FragmentHomeBinding::inflate
) {
    private val viewModel: HomeViewModel by viewModel()
    private var user: User? = null
    private val actionCardsAdapter = ActionCardsAdapter { actionType ->
        handleCardClick(actionType)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        viewModel.loadUserData()
    }

    private fun setupRecyclerView() {
        binding.rvActionCards.apply {
            adapter = actionCardsAdapter
            layoutManager = GridLayoutManager(requireContext(), 2)
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.user.collect { user ->
                if (user != null) {
                    this@HomeFragment.user = user
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
        val managerCards = listOf(
            ActionCard(
                id = "add_nurses",
                title = getString(R.string.add_nurses),
                icon = android.R.drawable.ic_input_add,
                tintColor = R.color.secondary_turquoise,
                action = ActionCardType.ADD_NURSES
            ),
            ActionCard(
                id = "nurses_details",
                title = getString(R.string.nurses_details),
                icon = android.R.drawable.ic_menu_info_details,
                tintColor = R.color.secondary_turquoise,
                action = ActionCardType.NURSES_DETAILS
            ),
            ActionCard(
                id = "nurses_requests",
                title = getString(R.string.nurses_requests),
                icon = android.R.drawable.ic_menu_recent_history,
                tintColor = R.color.secondary_turquoise,
                action = ActionCardType.NURSES_REQUESTS
            ),
            ActionCard(
                id = "delete_nurse",
                title = getString(R.string.delete_nurse),
                icon = android.R.drawable.ic_menu_delete,
                tintColor = R.color.error,
                action = ActionCardType.DELETE_NURSE
            ),
            ActionCard(
                id = "run_scheduling",
                title = getString(R.string.run_scheduling),
                icon = android.R.drawable.ic_menu_manage,
                tintColor = R.color.primary_dark_blue,
                action = ActionCardType.RUN_SCHEDULING
            ),
            ActionCard(
                id = "all_nurses_schedule",
                title = getString(R.string.all_nurses_schedule),
                icon = android.R.drawable.ic_menu_agenda,
                tintColor = R.color.secondary_turquoise,
                action = ActionCardType.ALL_NURSES_SCHEDULE
            )
        )
        actionCardsAdapter.submitList(managerCards)
    }

    private fun showNurseButtons() {
        val nurseCards = listOf(
            ActionCard(
                id = "nurses_details",
                title = getString(R.string.nurses_details),
                icon = android.R.drawable.ic_menu_info_details,
                tintColor = R.color.secondary_turquoise,
                action = ActionCardType.NURSES_DETAILS
            ),
            ActionCard(
                id = "choose_shifts",
                title = getString(R.string.choose_weekly_shifts),
                icon = android.R.drawable.ic_menu_edit,
                tintColor = R.color.primary_dark_blue,
                action = ActionCardType.CHOOSE_SHIFTS
            ),
            ActionCard(
                id = "my_schedule",
                title = getString(R.string.my_schedule),
                icon = android.R.drawable.ic_menu_my_calendar,
                tintColor = R.color.secondary_turquoise,
                action = ActionCardType.MY_SCHEDULE
            ),
            ActionCard(
                id = "all_nurses_schedule",
                title = getString(R.string.all_nurses_schedule),
                icon = android.R.drawable.ic_menu_agenda,
                tintColor = R.color.secondary_turquoise,
                action = ActionCardType.ALL_NURSES_SCHEDULE
            )
        )
        actionCardsAdapter.submitList(nurseCards)
    }

    private fun handleCardClick(actionType: ActionCardType) {
        when (actionType) {
            ActionCardType.ADD_NURSES -> {
                findNavController().navigate(R.id.action_homeFragment_to_addNurseFragment)
            }
            ActionCardType.NURSES_DETAILS -> {
                user?.let { currentUser ->
                    val action = HomeFragmentDirections.actionHomeFragmentToNursesListFragment(currentUser)
                    findNavController().navigate(action)
                }
            }
            ActionCardType.NURSES_REQUESTS -> {
                findNavController().navigate(R.id.action_homeFragment_to_nursesRequestsFragment)
            }
            ActionCardType.DELETE_NURSE -> {
                findNavController().navigate(R.id.action_homeFragment_to_deleteNurseFragment)
            }
            ActionCardType.WEEKLY_SHIFTS -> {
                // TODO: Navigate to weekly shifts screen
                Toast.makeText(requireContext(), "בקרוב...", Toast.LENGTH_SHORT).show()
            }
            ActionCardType.RUN_SCHEDULING -> {
                // TODO: Navigate to run scheduling screen
                Toast.makeText(requireContext(), "בקרוב...", Toast.LENGTH_SHORT).show()
            }
            ActionCardType.CHOOSE_SHIFTS -> {
                val nurseId = FirebaseAuth.getInstance().currentUser?.uid
                if (nurseId != null) {
                    lifecycleScope.launch {
                        when (viewModel.canSubmitShifts(nurseId)) {
                            is ShiftSubmissionStatus.CanSubmit -> {
                                findNavController().navigate(R.id.action_homeFragment_to_chooseShiftsFragment)
                            }
                            is ShiftSubmissionStatus.WindowClosed -> {
                                Toast.makeText(
                                    requireContext(),
                                    "חלון הבחירה סגור. ניתן לבחור משמרות רק בין יום ראשון 00:00 ליום שלישי 23:59",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            is ShiftSubmissionStatus.AlreadySubmitted -> {
                                Toast.makeText(
                                    requireContext(),
                                    "כבר בחרת משמרות לשבוע זה",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            }
            ActionCardType.MY_SCHEDULE -> {
                val nurseName = user?.name
                val action = HomeFragmentDirections.actionHomeFragmentToScheduleViewFragment(
                    showAllNurses = false,
                    nurseName = nurseName
                )
                findNavController().navigate(action)
            }
            ActionCardType.ALL_NURSES_SCHEDULE -> {
                val action = HomeFragmentDirections.actionHomeFragmentToScheduleViewFragment(
                    showAllNurses = true,
                    nurseName = null
                )
                findNavController().navigate(action)
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnLogout.setOnClickListener {
            viewModel.logout()
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }
}

