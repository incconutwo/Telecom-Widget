package com.telecom.widget.network

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.telecom.widget.dataStore
import com.telecom.widget.glance.ConsumptionWidget
import com.telecom.widget.security.CryptoManager
import com.telecom.widget.security.toDecryptedFromStorage
import com.telecom.widget.security.toEncryptedForStorage
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.first

class ConsumptionSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val gson = Gson()
    private val SAVED_ACCOUNTS_KEY = stringPreferencesKey("saved_accounts_json")
    private val ACTIVE_ACCOUNT_ID_KEY = stringPreferencesKey("active_account_id")
    private val CACHED_DATA_KEY = stringPreferencesKey("cached_data")

    override suspend fun doWork(): Result {
        return try {
            val prefs = applicationContext.dataStore.data.first()
            val accountsJson = prefs[SAVED_ACCOUNTS_KEY]
            val accountsListType = object : TypeToken<List<SavedAccount>>() {}.type
            var rawAccounts: MutableList<SavedAccount> = if (!accountsJson.isNullOrEmpty()) {
                try { gson.fromJson(accountsJson, accountsListType) } catch (_: Exception) { mutableListOf() }
            } else mutableListOf()

            var needsMigration = false
            if (rawAccounts.isEmpty()) {
                val operator = prefs[stringPreferencesKey("operator")]
                val pass = prefs[stringPreferencesKey("password")]
                if (!operator.isNullOrEmpty() && !pass.isNullOrEmpty()) {
                    rawAccounts.add(
                        SavedAccount(
                            operator = operator,
                            email = prefs[stringPreferencesKey("email")] ?: "",
                            phone = prefs[stringPreferencesKey("phone")] ?: "",
                            password = pass,
                            selectedLine = prefs[stringPreferencesKey("selected_line")]
                        )
                    )
                    needsMigration = true
                }
            } else if (rawAccounts.any { !CryptoManager.isEncrypted(it.password) && it.password.isNotEmpty() }) {
                needsMigration = true
            }

            val accounts = rawAccounts.toDecryptedFromStorage()
            if (accounts.isEmpty()) return Result.failure()

            val updatedAccounts = mutableListOf<SavedAccount>()
            for (acc in accounts) {
                try {
                    val client = if (acc.operator == "Maroc Telecom") {
                        MarocTelecomClient(acc.email, acc.password, acc.phone, acc.cookies)
                    } else if (acc.operator == "Orange") {
                        val selectedLine = acc.selectedLine ?: acc.phone
                        OrangeClient(acc.phone, acc.password, selectedLine, acc.cookies)
                    } else {
                        val selectedLine = acc.selectedLine ?: acc.phone
                        InwiClient(acc.phone, acc.password, selectedLine, acc.cookies)
                    }

                    val data = client.fetchConsumption()
                    updatedAccounts.add(
                        acc.copy(
                            cachedData = data,
                            cookies = client.currentCookies,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    updatedAccounts.add(acc)
                }
            }

            val activeId = prefs[ACTIVE_ACCOUNT_ID_KEY] ?: updatedAccounts.firstOrNull()?.id
            val activeAcc = updatedAccounts.find { it.id == activeId } ?: updatedAccounts.firstOrNull()

            applicationContext.dataStore.edit { p ->
                p[SAVED_ACCOUNTS_KEY] = gson.toJson(updatedAccounts.toEncryptedForStorage())
                if (activeAcc?.cachedData != null) {
                    p[CACHED_DATA_KEY] = gson.toJson(activeAcc.cachedData)
                }
                if (needsMigration) {
                    p.remove(stringPreferencesKey("operator"))
                    p.remove(stringPreferencesKey("password"))
                    p.remove(stringPreferencesKey("email"))
                    p.remove(stringPreferencesKey("phone"))
                    p.remove(stringPreferencesKey("selected_line"))
                }
            }

            ConsumptionWidget().updateAll(applicationContext)
            com.telecom.widget.notification.TelecomLiveNotificationHelper.updateAll(applicationContext, updatedAccounts)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
