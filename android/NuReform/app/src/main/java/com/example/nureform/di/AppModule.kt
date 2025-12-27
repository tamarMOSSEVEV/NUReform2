package com.example.nureform.di

import com.example.nureform.data.repository.AuthRepository
import com.example.nureform.data.repository.NursesRepository
import com.example.nureform.ui.AppCoroutineScope
import com.example.nureform.ui.auth.LoginViewModel
import com.example.nureform.ui.home.HomeViewModel
import com.example.nureform.ui.nurses.AddNurseViewModel
import com.example.nureform.ui.nurses.DeleteNurseViewModel
import com.example.nureform.ui.nurses.NursesListViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.*
import org.koin.dsl.module

val appModule = module {
    // Provide application-wide coroutine scope
    single { AppCoroutineScope() }

    // Repositories
    single { AuthRepository() }
    single { NursesRepository() }

    // ViewModels
    viewModel { LoginViewModel(androidApplication(), get()) }
    viewModel { HomeViewModel(get()) }
    viewModel { AddNurseViewModel(get()) }
    viewModel { DeleteNurseViewModel(get()) }
    viewModel { NursesListViewModel(get()) }
}

