package com.example.nureform.ui.nurses

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.nureform.databinding.FragmentDeleteNurseBinding
import com.example.nureform.ui.BaseFragment
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class DeleteNurseFragment : BaseFragment<FragmentDeleteNurseBinding>(
    FragmentDeleteNurseBinding::inflate
) {

    private val viewModel: DeleteNurseViewModel by viewModel()
    private var currentIdNumber: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.deleteNurseState.collect { state ->
                when (state) {
                    DeleteNurseState.Idle -> {
                        showLoading(false)
                    }
                    DeleteNurseState.Loading -> {
                        showLoading(true)
                    }
                    is DeleteNurseState.NurseFound -> {
                        showLoading(false)
                        currentIdNumber = state.nurse.idNumber
                        showDeleteConfirmationDialog(state.nurse.name, state.nurse.idNumber)
                    }
                    DeleteNurseState.NurseNotFound -> {
                        showLoading(false)
                        binding.tilIdNumber.error = "אחות לא נמצאה במערכת"
                        Toast.makeText(requireContext(), "אחות לא נמצאה", Toast.LENGTH_SHORT).show()
                    }
                    is DeleteNurseState.DeleteSuccess -> {
                        showLoading(false)
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    }
                    is DeleteNurseState.Error -> {
                        showLoading(false)
                        handleError(state.message, state.field)
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnCheck.setOnClickListener {
            val idNumber = binding.etIdNumber.text.toString().trim()
            binding.tilIdNumber.error = null
            viewModel.checkNurseExists(idNumber)
        }

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun showDeleteConfirmationDialog(nurseName: String, idNumber: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("מחיקת אחות")
            .setMessage("האם אתה בטוח שברצונך למחוק את $nurseName (ת.ז: $idNumber)?")
            .setPositiveButton("מחק") { dialog, _ ->
                currentIdNumber?.let { viewModel.deleteNurse(it) }
                dialog.dismiss()
            }
            .setNegativeButton("ביטול") { dialog, _ ->
                dialog.dismiss()
                viewModel.resetState()
            }
            .setCancelable(false)
            .show()
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnCheck.isEnabled = !isLoading
        binding.etIdNumber.isEnabled = !isLoading
    }

    private fun handleError(message: String, field: String?) {
        binding.tilIdNumber.error = null

        when (field) {
            "idNumber" -> binding.tilIdNumber.error = message
            else -> Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.resetState()
    }
}

