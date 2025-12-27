package com.example.nureform.ui.nurses

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.nureform.databinding.FragmentAddNurseBinding
import com.example.nureform.ui.BaseFragment
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class AddNurseFragment : BaseFragment<FragmentAddNurseBinding>(
    FragmentAddNurseBinding::inflate
) {

    private val viewModel: AddNurseViewModel by viewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
        setupClickListeners()
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

            viewModel.addNurse(
                idNumber = idNumber,
                name = name,
                phone = phone,
                email = email,
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
    }

    private fun handleError(message: String, field: String?) {
        // Clear all errors first
        binding.tilIdNumber.error = null
        binding.tilName.error = null
        binding.tilPhone.error = null
        binding.tilEmail.error = null

        // Set error on specific field
        when (field) {
            "idNumber" -> binding.tilIdNumber.error = message
            "name" -> binding.tilName.error = message
            "phone" -> binding.tilPhone.error = message
            "email" -> binding.tilEmail.error = message
            else -> Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }
}

