package app.goallive.live.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.goallive.live.L
import app.goallive.live.MainActivity
import app.goallive.live.R
import app.goallive.live.data.Store
import app.goallive.live.ui.fa
import app.goallive.live.ui.statusText
import app.goallive.live.ui.teamName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "اعلان فوری": while a followed match is live the phone checks the scores every ten seconds instead of every minute,
 * so goal alerts arrive about as soon as FotMob has them (usually before the TV picture). Android only allows that from a
 * foreground service, so a quiet notification shows the live scores; it goes away by itself when no followed match is on.
 */
class LiveWatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CH, L("اعلان فوری گل"), NotificationManager.IMPORTANCE_LOW))
        ServiceCompat.startForeground(this, ID, notification(L("در حال دنبال کردن بازی‌های زنده…")),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { Store.useFastAlerts(false); stopSelf(); return START_NOT_STICKY }
        if (loop == null) loop = scope.launch {
            var idle = 0
            while (true) {
                LiveAlerts.check(this@LiveWatchService, fast = true)
                val live = LiveAlerts.liveNow
                if (live.isNotEmpty()) {
                    idle = 0
                    val text = live.take(3).joinToString("  ·  ") {
                        "${teamName(it.home.id, it.home.name)} ${fa(it.home.score ?: 0)}-${fa(it.away.score ?: 0)} ${teamName(it.away.id, it.away.name)} ${statusText(it)}"
                    }
                    getSystemService(NotificationManager::class.java).notify(ID, notification(text))
                } else if (!LiveAlerts.liveSoon || !Store.fastAlerts) idle++
                // nothing followed is on any more: back to the ordinary minute-by-minute alarms
                if (idle >= 3 || !Store.fastAlerts) break
                delay(if (live.isNotEmpty()) 10_000L else 30_000L)
            }
            LiveAlerts.start(this@LiveWatchService)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun notification(text: String) = NotificationCompat.Builder(this, CH)
        .setSmallIcon(R.drawable.ic_stat_ball)
        .setColor(0xFF9BEA00.toInt())
        .setContentTitle(L("⚡ اعلان فوری گل روشن است"))
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(PendingIntent.getActivity(this, 90, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .addAction(0, L("خاموش کن"), PendingIntent.getService(this, 91, Intent(this, LiveWatchService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE))
        .build()

    companion object {
        private const val CH = "live_watch"
        private const val ID = 78
        private const val STOP = "stop"

        /** Starts the watcher; Android may refuse from the background (then the minute alarms carry on). */
        fun start(ctx: Context) {
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, LiveWatchService::class.java)) }
        }
    }
}
