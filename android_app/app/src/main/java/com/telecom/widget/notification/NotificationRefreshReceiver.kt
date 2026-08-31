package com.telecom.widget.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.telecom.widget.network.ConsumptionSyncWorker

class NotificationRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<ConsumptionSyncWorker>().build()
        )
    }
}
