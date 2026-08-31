package com.telecom.widget.network

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.SystemClock
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.telecom.widget.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object SmartSyncManager {

    // 3 minutes minimum throttle between any automatic triggers (Anti-spam / Rate-limit protection)
    const val MIN_AUTO_SYNC_INTERVAL_MS = 3 * 60 * 1000L

    // 5 minutes periodic background pulse
    const val ALARM_INTERVAL_MS = 5 * 60 * 1000L

    private val SAVED_ACCOUNTS_KEY = stringPreferencesKey("saved_accounts_json")
    private var isNetworkMonitoringActive = false

    fun triggerSync(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        if (!force) {
            val shouldSync = runCatching {
                runBlocking {
                    val prefs = appContext.dataStore.data.first()
                    val accountsJson = prefs[SAVED_ACCOUNTS_KEY]
                    val listType = object : TypeToken<List<SavedAccount>>() {}.type
                    val accounts: List<SavedAccount> = if (!accountsJson.isNullOrEmpty()) {
                        try { Gson().fromJson(accountsJson, listType) } catch (_: Exception) { emptyList() }
                    } else emptyList()

                    if (accounts.isEmpty()) return@runBlocking false

                    // Check if at least one account has live notification or if accounts exist
                    val lastSync = accounts.maxOfOrNull { it.lastUpdated } ?: 0L
                    val now = System.currentTimeMillis()
                    (now - lastSync) >= MIN_AUTO_SYNC_INTERVAL_MS
                }
            }.getOrDefault(true)

            if (!shouldSync) {
                return
            }
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWork = OneTimeWorkRequestBuilder<ConsumptionSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "SmartConsumptionSync",
            ExistingWorkPolicy.REPLACE,
            syncWork
        )
    }

    fun startNetworkMonitoring(context: Context) {
        if (isNetworkMonitoringActive) return
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                private var lastTransport: Int? = null

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    val currentTransport = when {
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkCapabilities.TRANSPORT_CELLULAR
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkCapabilities.TRANSPORT_WIFI
                        else -> null
                    }

                    if (currentTransport != null && currentTransport != lastTransport) {
                        val isSwitchToCellular = currentTransport == NetworkCapabilities.TRANSPORT_CELLULAR
                        lastTransport = currentTransport
                        // Trigger smart sync on network transition (especially switching to 4G/5G Cellular)
                        triggerSync(appContext, force = false)
                    }
                }
            })
            isNetworkMonitoringActive = true
        } catch (_: Exception) {}
    }

    fun scheduleNextAlarm(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(appContext, AlarmSyncReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            8881,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = SystemClock.elapsedRealtime() + ALARM_INTERVAL_MS

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }
}
