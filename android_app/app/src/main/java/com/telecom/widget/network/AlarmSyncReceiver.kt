package com.telecom.widget.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Trigger sync with 3-minute throttle check
        SmartSyncManager.triggerSync(context, force = false)

        // Reschedule next 5-minute alarm pulse
        SmartSyncManager.scheduleNextAlarm(context)
    }
}
