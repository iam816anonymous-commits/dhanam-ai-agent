package com.aistudio.dhanam.expense

import android.app.Application
import com.aistudio.dhanam.expense.data.FinanceDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Opens/migrates the encrypted Room database off the main thread before the UI creates ViewModels.
 * Background workers that request the database also serialize through FinanceDatabase.getInstance().
 */
class DhanamApplication : Application() {
    val databaseInitialization = CompletableDeferred<Unit>()
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startupScope.launch {
            try {
                FinanceDatabase.getInstance(applicationContext)
                databaseInitialization.complete(Unit)
            } catch (error: Exception) {
                databaseInitialization.completeExceptionally(error)
            }
        }
    }
}
