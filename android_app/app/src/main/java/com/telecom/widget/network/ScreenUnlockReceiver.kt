package com.telecom.widget.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScreenUnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_USER_PRESENT ||
            action == Intent.ACTION_SCREEN_ON ||
            action == "android.net.conn.CONNECTIVITY_CHANGE"
        ) {
            // Trigger sync (only executes if >= 3 min since last sync)
            SmartSyncManager.triggerSync(context, force = false)
        }
    }
}
