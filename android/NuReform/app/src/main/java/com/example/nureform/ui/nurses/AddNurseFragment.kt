package com.example.nureform.ui.nurses

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.nureform.R
import com.example.nureform.databinding.FragmentAddNurseBinding
import com.example.nureform.ui.BaseFragment
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class AddNurseFragment : BaseFragment<FragmentAddNurseBinding>(
    FragmentAddNurseBinding::inflate
) {
    private val viewModel: AddNurseViewModel by viewModel()

    private val jobPercentageOptions = listOf("50%", "75%", "100%")
    private val jobPercentageValues = listOf(50, 75, 100)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupJobPercentageDropdown()
        setupObservers()
        setupClickListeners()
    }

    private fun setupJobPercentageDropdown() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, jobPercentageOptions)
        binding.actvJobPercentage.setAdapter(adapter)
        binding.actvJobPercentage.setText("100%", false)
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.addNurseState.collect { state ->
                when (state) {
                    is AddNurseState.Idle -> {
                        showLoading(false)
                    }

                    is AddNurseState.Loading -> {
                        showLoading(true)
                    }

                    is AddNurseState.Success -> {
                        showLoading(false)
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    }

                    is AddNurseState.Error -> {
                        showLoading(false)
                        handleError(state.message, state.field)
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnAdd.setOnClickListener {
            val idNumber = binding.etIdNumber.text.toString().trim()
            val name = binding.etName.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val selectedIndex = jobPercentageOptions.indexOf(binding.actvJobPercentage.text.toString())
            val jobPercentage = if (selectedIndex >= 0) jobPercentageValues[selectedIndex] else 100

            viewModel.addNurse(
                idNumber = idNumber,
                name = name,
                phone = phone,
                email = email,
                password = password,
                jobPercentage = jobPercentage
            )
        }

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnAdd.isEnabled = !isLoading
        binding.etIdNumber.isEnabled = !isLoading
        binding.etName.isEnabled = !isLoading
        binding.etPhone.isEnabled = !isLoading
        binding.etEmail.isEnabled = !isLoading
        binding.etPassword.isEnabled = !isLoading
        binding.actvJobPercentage.isEnabled = !isLoading
    }

    private fun handleError(message: String, field: String?) {
        // Clear all errors first
        binding.tilIdNumber.error = null
        binding.tilName.error = null
        binding.tilPhone.error = null
        binding.tilEmail.error = null
        binding.tilPassword.error = null

        // Set error on specific field
        when (field) {
            "idNumber" -> binding.tilIdNumber.error = message
            "name" -> binding.tilName.error = message
            "phone" -> binding.tilPhone.error = message
            "email" -> binding.tilEmail.error = message
            "password" -> binding.tilPassword.error = message
            else -> Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }
}

