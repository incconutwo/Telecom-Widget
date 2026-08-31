package com.telecom.widget.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            // 1. Re-register periodic background sync
            val workRequest = PeriodicWorkRequestBuilder<ConsumptionSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ConsumptionSync",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            // 2. Trigger an immediate one-time sync so widget updates immediately after boot
            val oneTimeSync = OneTimeWorkRequestBuilder<ConsumptionSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueue(oneTimeSync)

            // 3. Schedule 5-minute smart alarm pulse
            SmartSyncManager.scheduleNextAlarm(context)

            // 4. Start network transition monitoring (Wi-Fi <-> Cellular)
            SmartSyncManager.startNetworkMonitoring(context)
        }
    }
}
