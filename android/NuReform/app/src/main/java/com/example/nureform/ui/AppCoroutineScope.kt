package com.example.nureform.ui

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

class AppCoroutineScope : CoroutineScope {

    private val exceptionHandler: CoroutineExceptionHandler = CoroutineExceptionHandler { _, e ->
        Timber.e(Throwable(e))
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + SupervisorJob() + exceptionHandler
}
