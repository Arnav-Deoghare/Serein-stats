package com.serein.stats.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives broadcasts from Zen Launcher.
 * Zen sends: com.zen.launcher.LIMIT_UPDATE
 * with extra: "packageName" (String), "blocked" (Boolean)
 *
 * Use this to sync limit state between the two apps.
 */
class ZenBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra("packageName") ?: return
        val blocked = intent.getBooleanExtra("blocked", false)
        Log.d("SereinStats", "Received from Zen: $pkg blocked=$blocked")
        // Future: update local DB, show notification, etc.
    }
}
