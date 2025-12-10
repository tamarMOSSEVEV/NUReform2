package com.example.nureform.di

import com.example.nureform.ui.AppCoroutineScope
import org.koin.dsl.module

val appModule = module {
    // Provide application-wide coroutine scope
    single { AppCoroutineScope() }

    // Add more dependencies here as needed
}

