package app.goallive.live.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarms are cleared on reboot and on app update; start the alert chain again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        LiveAlerts.start(ctx.applicationContext)
    }
}
