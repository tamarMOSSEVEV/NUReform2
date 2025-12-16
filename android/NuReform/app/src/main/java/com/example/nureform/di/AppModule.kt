package com.example.nureform.di

import com.example.nureform.data.repository.AuthRepository
import com.example.nureform.ui.AppCoroutineScope
import com.example.nureform.ui.auth.LoginViewModel
import com.example.nureform.ui.home.HomeViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Provide application-wide coroutine scope
    single { AppCoroutineScope() }

    // Repository
    single { AuthRepository() }

    // ViewModels
    viewModel { LoginViewModel(get()) }
    viewModel { HomeViewModel(get()) }
}

